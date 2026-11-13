package ru.pasha.adaptationwheel.server;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.level.ServerPlayer;
import ru.pasha.adaptationwheel.NeuralConfig;
import ru.pasha.adaptationwheel.analysis.NeuralTransport;
import ru.pasha.adaptationwheel.core.*;

/** Optional remote advice. Damage prevention, stages and manual settings remain local. */
public final class NeuralAdvisor {
    private record Cached(String identity,NeuralAdvice advice){}
    private static final Map<ServerPlayer,Map<String,Cached>> CACHE=new WeakHashMap<>();
    private static final LinkedHashMap<String,Long> RETRIES=new LinkedHashMap<>();
    private static final ArrayDeque<Long> REQUESTS=new ArrayDeque<>();
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"Adaptation-neural-advisor");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static long nextRequest,generation;private static volatile String serviceStatus="unconfigured";
    private static NeuralTransport transport;
    private NeuralAdvisor(){}
    private static String identity(CombatKnowledge.Summary s){return s.owner()+"|"+s.direct()+"|"+s.damage()+"|"+s.effects();}
    private static AdaptationAnalysis.Decision withAdvice(AdaptationAnalysis.Decision d,NeuralAdvice a){return new AdaptationAnalysis.Decision(a.profile(),a.timings(),Math.min(80,a.confidence()),"online.advisory",d.observations(),d.memory(),true);}
    public static String status(){return NeuralConfig.ONLINE?serviceStatus:"offline";}
    public static AdaptationAnalysis.Decision consider(ServerPlayer p,String key,AdaptationAnalysis.Decision local){
        if(!NeuralConfig.ONLINE||!local.eligible()||MetaMechanic.fromKey(key)!=null)return local;
        var knowledge=CombatObserver.knowledge(p);
        if(local.confidence()>=85){knowledge.advisor(key,"local");return local;}
        if(!NeuralConfig.configured()||OutsideServer.offlineFor(p)){knowledge.advisor(key,"fallback");return local;}
        var summary=knowledge.summary(key);String identity=identity(summary);
        Cached cached=CACHE.getOrDefault(p,Map.of()).get(key);var answer=local;
        if(cached!=null&&cached.identity.equals(identity)){answer=withAdvice(local,cached.advice);knowledge.advisor(key,"online");}
        else knowledge.advisor(key,"pending");
        if(local.observations()<2)return answer;
        long now=System.nanoTime();String requestKey=key+"|"+identity;
        REQUESTS.removeIf(t->now-t>=3_600_000_000_000L);
        if(now<nextRequest||now-RETRIES.getOrDefault(requestKey,Long.MIN_VALUE/2)<300_000_000_000L||REQUESTS.size()>=NeuralConfig.REQUESTS_PER_HOUR.get())return answer;
        if(RETRIES.size()>=256)RETRIES.remove(RETRIES.keySet().iterator().next());RETRIES.put(requestKey,now);REQUESTS.addLast(now);nextRequest=now+60_000_000_000L;
        String endpoint=NeuralConfig.ENDPOINT.get(),model=NeuralConfig.MODEL.get(),env=NeuralConfig.KEY_ENV.get();String apiKey=env.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")?System.getenv(env):null;
        boolean schema=NeuralConfig.STRICT_SCHEMA.get();int timeout=NeuralConfig.TIMEOUT.get();JsonObject facts=facts(key,local,summary);
        var server=p.server;var uuid=p.getUUID();long epoch=generation;
        try{WORKER.execute(()->{
            NeuralAdvice result=null;String status="fallback";
            try{if(transport==null)transport=new NeuralTransport();result=transport.request(endpoint,model,apiKey,facts,schema,timeout);if(result.confidence()<65)throw new IllegalArgumentException("Low confidence");status="online";serviceStatus="ready";}
            catch(Exception failure){serviceStatus="fallback";com.mojang.logging.LogUtils.getLogger().warn("Neural advice unavailable ({}); using local analysis",failure.getClass().getSimpleName());}
            NeuralAdvice received=result;String mode=status;
            if(server.isStopped())return;server.execute(()->{
                if(epoch!=generation||!NeuralConfig.configured()||!endpoint.equals(NeuralConfig.ENDPOINT.get())||!model.equals(NeuralConfig.MODEL.get()))return;ServerPlayer current=server.getPlayerList().getPlayer(uuid);if(current!=p||!AdaptationEvents.active(p)||!AdaptationEvents.state(p).categoryEnabled(key))return;
                var currentSummary=CombatObserver.knowledge(p).summary(key);if(!identity.equals(identity(currentSummary)))return;
                if(received!=null&&"online".equals(mode)){
                    var snapshot=AdaptationEvents.state(p).snapshot(key);
                    if(snapshot==null)return;
                    if(snapshot.analysisConfidence()>=85){CombatObserver.knowledge(p).advisor(key,"local");AdaptationEvents.saveAndSync(p);return;}
                    Map<String,Cached> data=CACHE.computeIfAbsent(p,x->new LinkedHashMap<>());if(data.size()>=256)data.remove(data.keySet().iterator().next());data.put(key,new Cached(identity,received));
                    var memory=SmartAttackAnalyzer.memory(p,key);if(memory!=null){var decision=new AdaptationAnalysis.Decision(received.profile(),received.timings(),Math.min(80,received.confidence()),"online.advisory",memory.observations(),memory,true);CombatObserver.knowledge(p).revised(key,snapshot.profile(),decision.profile());AdaptationEvents.state(p).applyAnalysis(key,decision);}
                }
                CombatObserver.knowledge(p).advisor(key,mode);AdaptationEvents.saveAndSync(p);
            });
        });}catch(RejectedExecutionException busy){knowledge.advisor(key,"fallback");serviceStatus="fallback";}
        return answer;
    }
    /** Only attack facts: no UUID, player name, chat, position, terrain, file path or secret. */
    public static JsonObject facts(String key,AdaptationAnalysis.Decision decision,CombatKnowledge.Summary s){
        JsonObject f=new JsonObject();f.addProperty("version",1);f.addProperty("adaptationKey",key);f.addProperty("ownerType",s.owner());f.addProperty("directType",s.direct());f.addProperty("damageType",s.damage());f.addProperty("negativeEffects",s.effects());f.addProperty("distanceBand",s.range());f.addProperty("observedSequence",s.flow());f.addProperty("bossPhase",s.phase());f.addProperty("localProfile",decision.profile().name());f.addProperty("localConfidence",decision.confidence());f.addProperty("observations",decision.observations());f.addProperty("meanRisk",decision.memory().meanRisk());f.addProperty("peakRisk",decision.memory().peakRisk());f.addProperty("protectionLeaks",s.leaks());JsonArray evidence=new JsonArray();for(var e:AdaptationAnalysis.Evidence.values())if((decision.memory().evidenceMask()&(1L<<e.ordinal()))!=0)evidence.add(e.name());f.add("evidence",evidence);return f;
    }
    public static void forget(ServerPlayer p){CACHE.remove(p);}
    public static void reset(){generation++;CACHE.clear();RETRIES.clear();REQUESTS.clear();nextRequest=0;serviceStatus="unconfigured";WORKER.getQueue().clear();}
}
