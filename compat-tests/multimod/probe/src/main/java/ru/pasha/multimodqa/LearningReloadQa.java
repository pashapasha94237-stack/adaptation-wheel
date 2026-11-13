package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TagsUpdatedEvent;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Real damage and real holder tags: frozen recall must survive unchanged definitions, not removed ones. */
final class LearningReloadQa {
    private static final String CONTROLLER=BASE+"LearningController",STUDY=BASE+"WorldStudy";
    private static final String KEY="damage:multimodqa:pattern_opaque",WORLD="damage:minecraft:in_fire";
    private static Holder.Reference<DamageType> holder;
    private static List<TagKey<DamageType>> originalTags;
    private static int phase,ticks,hits,observations;
    private static long previousHitTick=Long.MIN_VALUE;
    private static boolean restored;

    private LearningReloadQa(){}
    private static Object invoke(String method,ServerPlayer p)throws Exception{return call(CONTROLLER,method,new Class<?>[]{ServerPlayer.class},p);}
    private static Object memory(ServerPlayer p)throws Exception{return invoke("memory",p);}
    private static int count(Object value,String method)throws Exception{return ((Number)getter(value,method)).intValue();}
    private static int observations(ServerPlayer p)throws Exception{return count(memory(p),"observationCount");}
    private static Object fact(ServerPlayer p,String key)throws Exception{
        Object model=memory(p);return model.getClass().getMethod("fact",String.class).invoke(model,key);
    }
    private static boolean ready(ServerPlayer p)throws Exception{
        return (boolean)call(CONTROLLER,"archiveReady",new Class<?>[]{ServerPlayer.class},p);
    }
    private static void update(ServerPlayer p)throws Exception{
        // Exact Forge 47.4.0 constructor: (RegistryAccess, clientPacket, integratedServer).
        call(STUDY,"tagsUpdated",new Class<?>[]{TagsUpdatedEvent.class},new TagsUpdatedEvent(p.level().registryAccess(),false,true));
        memory(p); // Production validation is lazy, so exercise its actual next-use entry point.
    }
    private static void bind(Collection<TagKey<DamageType>> tags){
        // javap against target mapped + SRG jars: public bindTags(Collection), SRG m_205769_.
        holder.bindTags(tags);
    }
    private static void worldFact(ServerPlayer p,String message)throws Exception{
        Object known=fact(p,WORLD);
        check(known!=null&&getter(known,"profile").toString().equals("FIRE")&&getter(known,"source").toString().startsWith("world:"),message);
    }
    private static int support(ServerPlayer p)throws Exception{
        int support=0;for(Object sample:(List<?>)getter(memory(p),"samples")){
            Object context=getter(sample,"context");if(getter(context,"key").equals(KEY))support+=count(sample,"trustedObservations");
        }
        return support;
    }
    private static double raw(ServerPlayer p)throws Exception{
        double raw=0;for(Object sample:(List<?>)getter(memory(p),"samples"))if(getter(getter(sample,"context"),"key").equals(KEY))raw+=number(sample,"meanRaw");return raw;
    }
    static void run(ServerPlayer p)throws Exception{
        reset(p);damageTypes=p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,p.server);
        holder=damageTypes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,new net.minecraft.resources.ResourceLocation("multimodqa:pattern_opaque")));
        originalTags=holder.tags().toList();check(!originalTags.contains(DamageTypeTags.IS_FREEZING),"Opaque reload fixture unexpectedly starts with a freezing tag");
        try{
            var tagged=new ArrayList<>(originalTags);tagged.add(DamageTypeTags.IS_FREEZING);bind(tagged);update(p);
            LearningQa.mode(p,"RESEARCH");
            LogUtils.getLogger().info("MULTIMOD_QA LEARNING_RELOAD START: native opaque damage holder temporarily tagged freezing; waiting for real bounded world study");
        }catch(Throwable failure){restore(p);throw failure;}
    }
    private static void restore(ServerPlayer p)throws Exception{
        if(restored||holder==null||originalTags==null)return;restored=true;bind(originalTags);update(p);
    }
    private static void step(ServerPlayer p)throws Exception{
        if(!ready(p))return;
        switch(phase){
            case 0->{
                if(!(boolean)call(STUDY,"complete",new Class<?>[]{ServerPlayer.class},p))return;
                worldFact(p,"Real world study failed to retain a vanilla fire metadata fact");
                LearningQa.mode(p,"ADAPTIVE");phase=1;
            }
            case 1->{
                long now=p.serverLevel().getGameTime();if(now==previousHitTick)return;previousHitTick=now;
                float hp=health(p);check(hit(p,new DamageSource(holder),4)>0,"Real tagged training damage failed to remove HP");wheelTick(p);
                Object row=required(p,KEY);check(getter(row,"profile").toString().equals("COLD")&&getter(row,"analysisReason").toString().startsWith("registry."),"Native freezing tag did not provide factual COLD training");
                check(health(p)<hp&&stage(p,KEY)==0,"Factual category learning granted an unearned protection stage");
                if(++hits<3)return;
                observations=observations(p);check(support(p)>=3&&raw(p)>0,"Three actual impacts failed to retain factual support and measured damage");
                Object known=fact(p,KEY);check(known!=null&&getter(known,"source").toString().startsWith("live:registry."),"Tagged gameplay did not create the live registry fact under test");
                LearningQa.mode(p,"FROZEN");phase=2;
            }
            case 2->{
                update(p);worldFact(p,"Identical tag-update notification removed valid world metadata while FROZEN");
                check(observations(p)==observations&&support(p)>=3,"Identical tag update reset genuine empirical observations/support");
                call(CONTROLLER,"checkpoint",new Class<?>[]{ServerPlayer.class,boolean.class},p,true);invoke("forget",p);memory(p);phase=3;
            }
            case 3->{
                worldFact(p,"Reloading unchanged saved world NBT lost research metadata in FROZEN mode");
                check(observations(p)==observations&&support(p)>=3,"Checkpoint/reload lost empirical measurements with unchanged definitions");
                check(invoke("mode",p).toString().equals("FROZEN"),"NBT reload lost the player's frozen learning mode");
                LogUtils.getLogger().info("MULTIMOD_QA LEARNING_RELOAD_UNCHANGED PASS: real research facts and three factual impacts survive identical tag notifications and saved-NBT reload while frozen");
                bind(originalTags);update(p);phase=4;
            }
            case 4->{
                check(!holder.is(DamageTypeTags.IS_FREEZING),"Native holder still has the removed freezing tag");
                Object known=fact(p,KEY);check(known==null||!getter(known,"profile").toString().equals("COLD"),"Removed freezing tag survived as an exact live COLD fact");
                check(observations(p)==observations&&support(p)==0&&raw(p)>0,"Tag invalidation must retire factual support while preserving genuine observation counts and raw measurements");
                Object before=required(p,KEY);double work=number(before,"work");int duration=duration(before);
                float hp=health(p);check(hit(p,new DamageSource(holder),4)>0,"Now-untagged stage-zero attack incorrectly received free immunity");
                check(number(required(p,KEY),"work")==work&&duration(required(p,KEY))==duration,"Registry invalidation or the next hit changed the running adaptation clock");
                wheelTick(p);Object row=required(p,KEY);String reason=getter(row,"analysisReason").toString();
                check(health(p)<hp&&stage(p,KEY)==0,"Changed definitions incorrectly earned a stage or blocked actual damage");
                check(!getter(row,"profile").toString().equals("COLD")&&!reason.startsWith("learned."),"Removed registry evidence was resurrected by stale empirical/legacy COLD recall: "+reason);
                check(observations(p)==observations,"FROZEN mode trained new examples after metadata invalidation");
                LogUtils.getLogger().info("MULTIMOD_QA LEARNING_RELOAD_REMOVED PASS: removed real tag retires live registry fact and factual sample support, preserves counts/raw damage, and next actual untagged hit still loses HP without stale learned COLD or timer jump");
                restore(p);phase=5;
            }
            default->{}
        }
    }
    static boolean clientTick()throws Exception{
        if(++ticks>1600){LearningQa.server(LearningReloadQa::restore);throw new AssertionError("Learning registry-reload QA timeout");}
        if(ticks%6!=0)return false;
        try{LearningQa.server(LearningReloadQa::step);}
        catch(Throwable failure){try{LearningQa.server(LearningReloadQa::restore);}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}
        return phase==5;
    }
}
