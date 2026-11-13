package ru.pasha.adaptationwheel.server;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.LearningConfig;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.LearningNetwork;

/** Server-owned factual learning. Research never grants stages or performs world actions. */
public final class LearningController {
    private static final Gson JSON=new Gson();
    private static final String ROOT="adaptationwheel_learning",FLAVOR=readPretrained()?"trained":"untrained";
    private static final int SCHEMA=1;
    private static final List<ResearchMemory.Fact> SEEDS=readSeeds();
    private static final Map<ServerPlayer,Context> MODELS=new WeakHashMap<>();
    private record Definitions(int revision,String fingerprint,Map<String,String> rows){}
    private static final Map<MinecraftServer,Definitions> DEFINITIONS=new WeakHashMap<>();
    private static final ConcurrentMap<Path,Archive> PENDING=new ConcurrentHashMap<>();
    private static final AtomicBoolean WRITING=new AtomicBoolean();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"AdaptationLearningIO");t.setDaemon(true);return t;});
    private record SeedPack(int schema,List<ResearchMemory.Fact> facts){}
    private record Archive(int schema,List<ResearchMemory.Fact> facts,List<ResearchMemory.Sample> samples,
                           List<ResearchMemory.Context> metadata,String fingerprint,Map<String,String> definitions){}
    private static final class Context {
        ResearchMemory model;boolean dirty,archiveDirty;long checkpoint=Long.MIN_VALUE/2;int worldRevision=-1;
        long observedTick=Long.MIN_VALUE;final Set<ResearchMemory.Context> observedThisTick=new HashSet<>();
        String fingerprint="";Map<String,String> definitions;final Set<ResearchMemory.Context> metadata=new HashSet<>();CompletableFuture<Archive> loading;
        Context(ResearchMemory model){this.model=model;}
    }
    public record Status(LearningMode mode,boolean pretrained,int seedFacts,int learnedFacts,int observations,
                         int contradictions,int inspected,int total,boolean scanning){}
    private LearningController(){}
    public static boolean pretrained(){return FLAVOR.equals("trained");}
    public static LearningMode mode(ServerPlayer p){
        if(!LearningConfig.playersMayChoose())return LearningConfig.defaultMode();
        String saved=p.getPersistentData().getCompound(ROOT).getString("Mode");
        try{return LearningMode.valueOf(saved);}catch(IllegalArgumentException ignored){return LearningConfig.defaultMode();}
    }
    public static void setMode(ServerPlayer p,LearningMode mode){
        if(mode==null||!LearningConfig.playersMayChoose())return;
        CompoundTag root=root(p);root.putString("Mode",mode.name());
        if(mode.researches())WorldStudy.invalidate(p);
        LearningNetwork.sendStatus(p);
    }
    private static CompoundTag root(ServerPlayer p){
        var data=p.getPersistentData();if(!data.contains(ROOT,Tag.TAG_COMPOUND))data.put(ROOT,new CompoundTag());return data.getCompound(ROOT);
    }
    private static Context context(ServerPlayer p){
        Context c=MODELS.get(p);
        if(c==null){
            var saved=root(p).getCompound(FLAVOR);
            c=new Context(saved.getInt("Schema")==SCHEMA?readNbt(saved):new ResearchMemory());
            c.fingerprint=saved.getString("Fingerprint");c.definitions=readDefinitions(saved);c.metadata.addAll(readMetadata(saved));
            for(var fact:SEEDS)c.model.learnFact(fact);
            MODELS.put(p,c);
            if(share()){Path path=archivePath(p);c.loading=CompletableFuture.supplyAsync(()->readArchive(path),IO);}
        }
        int revision=WorldStudy.registryRevision(p);
        if(c.worldRevision!=revision)invalidateWorldFacts(p,c,revision);
        if(c.loading!=null&&c.loading.isDone()){
            Archive archive;try{archive=c.loading.join();}catch(CompletionException ignored){archive=null;}c.loading=null;
            if(archive!=null){
                var imported=ResearchMemory.restore(archive.facts(),archive.samples());var metadata=archive.metadata()==null?List.<ResearchMemory.Context>of():archive.metadata();
                if(!c.fingerprint.equals(archive.fingerprint())){
                    var changed=changed(archive.definitions(),c.definitions);
                    imported=withoutStaleDefinitions(imported,new HashSet<>(metadata),changed);
                    if(changed!=null)for(var row:metadata)if(!changed.contains(row.key()))c.metadata.add(row);
                }
                else c.metadata.addAll(metadata);
                c.model=ResearchMemory.merge(c.model,imported);c.dirty=c.archiveDirty=true;
            }
        }
        return c;
    }
    public static boolean archiveReady(ServerPlayer p){return context(p).loading==null;}
    public static ResearchMemory memory(ServerPlayer p){return context(p).model;}
    public static boolean acceptsObservations(ServerPlayer p){
        return AdaptationTimings.analysisEnabled()&&AdaptationEvents.active(p)&&mode(p).learns();
    }
    public static boolean acceptWorldFacts(ServerPlayer p){return acceptsObservations(p)&&mode(p).researches();}
    public static void learnFact(ServerPlayer p,ResearchMemory.Fact fact){
        if(!acceptWorldFacts(p)||fact==null||!fact.source().startsWith("world:"))return;
        Context c=context(p);c.model.learnFact(fact);c.dirty=true;
    }
    public static void worldRevision(ServerPlayer p,int revision){
        Context c=MODELS.get(p);if(c==null){context(p);c=MODELS.get(p);}
        if(c.worldRevision!=revision)invalidateWorldFacts(p,c,revision);
    }
    private static boolean dependsOnDefinitions(String source){return source.startsWith("world:")||source.startsWith("registry:")||source.startsWith("live:registry.")||source.equals("live:compat.known");}
    /** Keys whose definition differs, each also under its canonical name. Null: there is nothing to compare with, so all of it counts as changed. */
    public static Set<String> changed(Map<String,String> old,Map<String,String> now){
        if(old==null||now==null||now.isEmpty())return null;
        var keys=new HashSet<String>();
        old.forEach((key,row)->{if(!row.equals(now.get(key)))keys.add(key);});
        now.forEach((key,row)->{if(!old.containsKey(key))keys.add(key);});
        for(String key:List.copyOf(keys))keys.add(DamageCategories.canonicalKey(key));
        return keys;
    }
    private static boolean stale(ResearchMemory.Fact fact,Set<String> changed){
        // Saves from before 4.43 also carry "harmful effect" as a fact, which hid the prepared element of that effect.
        if(changed==null)return dependsOnDefinitions(fact.source())||fact.source().equals("live:native.harmful_effect");
        return dependsOnDefinitions(fact.source())&&changed.contains(fact.key());
    }
    public static ResearchMemory withoutStaleDefinitions(ResearchMemory model,Set<ResearchMemory.Context> metadata,Set<String> changed){
        var staleKeys=new HashSet<String>();for(var fact:model.facts())if(stale(fact,changed))staleKeys.add(fact.key());
        var facts=model.facts().stream().filter(f->!stale(f,changed)).toList();
        var samples=model.samples().stream().map(s->metadata.contains(s.context())&&(changed==null||changed.contains(s.context().key()))||staleKeys.contains(s.context().key())
                ?new ResearchMemory.Sample(s.context(),s.profile(),s.observations(),0,s.contradictions(),s.meanRaw(),s.meanActual(),s.meanFraction()):s).toList();
        return ResearchMemory.restore(facts,samples);
    }
    private static void invalidateWorldFacts(ServerPlayer p,Context c,int revision){
        var current=definitions(p.server,revision);
        if(!current.fingerprint().equals(c.fingerprint)){
            var changed=changed(c.definitions,current.rows());
            c.model=withoutStaleDefinitions(c.model,c.metadata,changed);
            // A removed fact may have been hiding a prepared one.
            for(var fact:SEEDS)c.model.learnFact(fact);
            if(changed==null)c.metadata.clear();else c.metadata.removeIf(row->changed.contains(row.key()));
            c.fingerprint=current.fingerprint();c.definitions=current.rows();c.dirty=true;
            SmartAttackAnalyzer.invalidatePatterns(p,changed);
        }
        c.worldRevision=revision;
    }
    /** Empirical data are collected only from attributed gameplay, never from reference examples. */
    public static ResearchMemory.Proposal observe(ServerPlayer p,ResearchMemory.Context identity,
            AdaptationAnalysis.Decision decision,double raw,double actual){
        return observe(p,identity,decision,raw,actual,identity.key());
    }
    public static ResearchMemory.Proposal observe(ServerPlayer p,ResearchMemory.Context identity,
            AdaptationAnalysis.Decision decision,double raw,double actual,String ability){
        Context c=context(p);
        var canonical=new ResearchMemory.Context(ability,identity.owner(),identity.direct(),identity.effects(),identity.phase(),identity.range());
        if(acceptsObservations(p)){
            boolean trusted=LearnedPatterns.trusted(decision.reason(),decision.confidence());
            long tick=p.serverLevel().getGameTime();
            if(c.observedTick!=tick){c.observedTick=tick;c.observedThisTick.clear();}
            // At most one empirical sample per source/context/tick. Alias callbacks must not
            // manufacture three independent examples from one impact; rapid same-tick hits
            // remain fully counted by the separate adaptation/acceleration engine.
            if(c.observedThisTick.add(canonical)){
                c.model.observe(canonical,decision.profile(),trusted,raw,actual,p.getMaxHealth());
                if(trusted){if(dependsOnDefinitions("live:"+decision.reason()))c.metadata.add(canonical);else c.metadata.remove(canonical);}
                var original=c.model.fact(identity.key());
                if(!canonical.equals(identity)&&original!=null&&original.source().startsWith("seed:"))
                    c.model.observe(identity,decision.profile(),false,raw,actual,p.getMaxHealth());
            }
            if(trusted)c.model.learnFact(new ResearchMemory.Fact(identity.key(),decision.profile(),decision.confidence(),"live:"+decision.reason()));
            c.dirty=true;
        }
        var proposed=c.model.infer(identity);
        if(proposed!=null&&(proposed.reason().equals("learned.pretrained")||proposed.reason().equals("learned.research")))return proposed;
        return c.model.infer(canonical);
    }
    public static Status status(ServerPlayer p){
        Context c=context(p);int learned=0;for(var f:c.model.facts())if(!f.source().startsWith("seed:"))learned++;
        int inspected=WorldStudy.inspected(p),total=WorldStudy.total(p);
        return new Status(mode(p),pretrained(),SEEDS.size(),learned,c.model.observationCount(),
                c.model.contradictionCount(),inspected,total,acceptWorldFacts(p)&&!WorldStudy.complete(p));
    }
    /** NBT is batched during play; explicit save/clone/logout checkpoints include the latest model. */
    public static void checkpoint(ServerPlayer p,boolean force){
        Context c=MODELS.get(p);if(c==null)return;c=context(p);if(!c.dirty&&!c.archiveDirty)return;long now=p.serverLevel().getGameTime();
        if(!force&&now>=c.checkpoint&&now-c.checkpoint<200)return;
        var tag=new CompoundTag();tag.putInt("Schema",SCHEMA);tag.putString("Fingerprint",c.fingerprint);
        ListTag facts=new ListTag();for(var f:c.model.facts())if(!f.source().startsWith("seed:"))facts.add(StringTag.valueOf(JSON.toJson(f)));
        ListTag samples=new ListTag();for(var row:c.model.samples())samples.add(StringTag.valueOf(JSON.toJson(row)));
        ListTag metadata=new ListTag();for(var row:c.model.samples())if(c.metadata.contains(row.context()))metadata.add(StringTag.valueOf(JSON.toJson(row.context())));
        tag.put("Facts",facts);tag.put("Samples",samples);tag.put("Metadata",metadata);
        if(c.definitions!=null){var rows=new CompoundTag();c.definitions.forEach(rows::putString);tag.put("Definitions",rows);}
        root(p).put(FLAVOR,tag);
        c.checkpoint=now;c.dirty=false;
        var retainedSamples=c.model.samples();var retainedMetadata=new ArrayList<ResearchMemory.Context>();for(var sample:retainedSamples)if(c.metadata.contains(sample.context()))retainedMetadata.add(sample.context());
        c.metadata.retainAll(new HashSet<>(retainedMetadata));
        if(share())c.archiveDirty=c.loading!=null||!enqueue(archivePath(p),new Archive(SCHEMA,c.model.facts().stream().filter(f->!f.source().startsWith("seed:")).toList(),retainedSamples,List.copyOf(retainedMetadata),c.fingerprint,c.definitions));
    }
    private static Map<String,String> readDefinitions(CompoundTag tag){
        if(!tag.contains("Definitions",Tag.TAG_COMPOUND))return null;
        var rows=new HashMap<String,String>();var saved=tag.getCompound("Definitions");
        for(String key:saved.getAllKeys())rows.put(key,saved.getString(key));
        return rows;
    }
    private static List<ResearchMemory.Context> readMetadata(CompoundTag tag){
        var result=new ArrayList<ResearchMemory.Context>();var list=tag.getList("Metadata",Tag.TAG_STRING);
        for(int i=0;i<list.size();i++)try{String value=list.getString(i);if(value.length()<=2048){var context=JSON.fromJson(value,ResearchMemory.Context.class);if(context!=null&&!context.key().isBlank())result.add(context);}}catch(RuntimeException ignored){}
        return result;
    }
    private static ResearchMemory readNbt(CompoundTag tag){
        var facts=new ArrayList<ResearchMemory.Fact>();var samples=new ArrayList<ResearchMemory.Sample>();
        var fs=tag.getList("Facts",Tag.TAG_STRING);for(int i=0;i<fs.size();i++)try{
            String value=fs.getString(i);if(value.length()<=2048){var f=JSON.fromJson(value,ResearchMemory.Fact.class);if(f!=null&&!f.source().startsWith("seed:"))facts.add(f);}
        }catch(RuntimeException ignored){}
        var ss=tag.getList("Samples",Tag.TAG_STRING);for(int i=0;i<ss.size();i++)try{
            String value=ss.getString(i);if(value.length()<=8192){var s=JSON.fromJson(value,ResearchMemory.Sample.class);if(s!=null)samples.add(s);}
        }catch(RuntimeException ignored){}
        return ResearchMemory.restore(facts,samples);
    }
    private static boolean share(){return !LearningConfig.SPEC.isLoaded()||LearningConfig.SHARE_ACROSS_WORLDS.get();}
    private static Path archivePath(ServerPlayer p){return OutsideServer.home().resolve("learning").resolve(FLAVOR).resolve(p.getUUID()+".json");}
    private static Archive readArchive(Path path){
        if(!Files.isRegularFile(path))return null;
        try{
            Archive archive=JSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),Archive.class);
            if(archive!=null&&archive.schema()==SCHEMA)return new Archive(SCHEMA,archive.facts()==null?List.of():archive.facts().stream().filter(Objects::nonNull).filter(f->!f.source().startsWith("seed:")).toList(),archive.samples()==null?List.of():archive.samples().stream().filter(Objects::nonNull).toList(),archive.metadata()==null?List.of():archive.metadata().stream().filter(Objects::nonNull).toList(),Objects.toString(archive.fingerprint(),""),archive.definitions());
        }catch(IOException|RuntimeException error){LogUtils.getLogger().warn("Learning archive could not be read: {}",error.toString());}
        return null;
    }
    private static boolean enqueue(Path path,Archive snapshot){
        if(PENDING.size()>=128&&!PENDING.containsKey(path))return false;
        PENDING.put(path,snapshot);drain();return true;
    }
    private static void drain(){
        if(!WRITING.compareAndSet(false,true))return;
        IO.execute(()->{try{
            while(!PENDING.isEmpty())for(var item:PENDING.entrySet()){
                if(!PENDING.remove(item.getKey(),item.getValue()))continue;
                Path path=item.getKey(),temporary=path.resolveSibling(path.getFileName()+".tmp");
                try{
                    Files.createDirectories(path.getParent());String json=JSON.toJson(item.getValue());
                    byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                    Files.write(temporary,bytes);
                    try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                    catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
                }catch(IOException|RuntimeException error){LogUtils.getLogger().warn("Learning archive could not be saved: {}",error.toString());}
            }
        }finally{WRITING.set(false);if(!PENDING.isEmpty())drain();}});
    }
    public static void forget(ServerPlayer p){MODELS.remove(p);}
    public static void clear(ServerPlayer p){
        var model=new ResearchMemory();for(var f:SEEDS)model.learnFact(f);var c=new Context(model);c.dirty=true;MODELS.put(p,c);WorldStudy.invalidate(p);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer p&&p.tickCount%200==0)checkpoint(p,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone event){
        if(event.getOriginal() instanceof ServerPlayer old&&event.getEntity() instanceof ServerPlayer replacement){
            checkpoint(old,true);replacement.getPersistentData().put(ROOT,root(old).copy());
            Context previous=MODELS.remove(old);if(previous!=null){var copy=new Context(ResearchMemory.restore(previous.model.facts(),previous.model.samples()));copy.worldRevision=previous.worldRevision;copy.fingerprint=previous.fingerprint;copy.definitions=previous.definitions;copy.metadata.addAll(previous.metadata);copy.loading=previous.loading;copy.archiveDirty=previous.archiveDirty;MODELS.put(replacement,copy);}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer p){awaitIO();checkpoint(p,true);Context c=MODELS.get(p);if(c!=null&&c.archiveDirty){awaitIO();checkpoint(p,true);}forget(p);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void stopping(ServerStoppingEvent event){
        awaitIO();for(var p:event.getServer().getPlayerList().getPlayers())checkpoint(p,true);awaitIO();
    }
    private static void awaitIO(){try{IO.submit(()->{}).get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException ignored){}}
    @SubscribeEvent public static void started(ServerStartedEvent event){definitions(event.getServer(),WorldStudy.registryRevision(null));DeathTexts.warm();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){MODELS.clear();DEFINITIONS.clear();}
    /** Validate a cache once at world load/reload; normal hits use this constant-time cached value. */
    private static Definitions definitions(MinecraftServer server,int revision){
        var cached=DEFINITIONS.get(server);if(cached!=null&&cached.revision()==revision)return cached;
        if(server.overworld()==null)return new Definitions(revision,"unavailable",Map.of());
        long start=System.nanoTime();
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            put(digest,"learning-schema-1");
            var registry=server.overworld().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            // One row per definition: a new release or one changed tag must cost only what really changed.
            var rows=new TreeMap<String,String>();
            registry.holders().forEach(h->rows.put("damage:"+h.key().location(),h.tags().map(t->t.location().toString()).sorted().reduce((a,b)->a+","+b).orElse("")+"|"+Objects.toString(DamageCategories.vanillaProfile(h),"")));
            ForgeRegistries.MOB_EFFECTS.getValues().forEach(effect->{
                String attributes=effect.getAttributeModifiers().entrySet().stream().map(e->Objects.toString(ForgeRegistries.ATTRIBUTES.getKey(e.getKey()),"unregistered")+":"+e.getValue().getAmount()+":"+e.getValue().getOperation()).sorted().reduce((a,b)->a+","+b).orElse("");
                rows.put("effect:"+ForgeRegistries.MOB_EFFECTS.getKey(effect),effect.getCategory()+"|"+DamageCategories.isHarmful(effect)+"|"+DamageCategories.effectProfile(effect)+"|"+attributes);
            });
            rows.forEach((key,row)->put(digest,key+"|"+row));
            rows.replaceAll((key,row)->Integer.toHexString(row.hashCode()));
            var result=new Definitions(revision,HexFormat.of().formatHex(digest.digest()),rows);DEFINITIONS.put(server,result);
            LogUtils.getLogger().info("Learning definition cache validated in {} ms",(System.nanoTime()-start)/1_000_000.0);return result;
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void put(MessageDigest digest,String value){digest.update(value.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);}
    private static boolean readPretrained(){
        try(var in=LearningController.class.getResourceAsStream("/META-INF/adaptation-learning.properties")){
            if(in==null)return true;var properties=new Properties();properties.load(in);return Boolean.parseBoolean(properties.getProperty("pretrained","true"));
        }catch(IOException ignored){return false;}
    }
    private static List<ResearchMemory.Fact> readSeeds(){
        if(!pretrained())return List.of();
        try(var in=LearningController.class.getResourceAsStream("/knowledge/pretrained-v1.json")){
            if(in==null)return List.of();byte[] bytes=in.readNBytes(2_000_001);if(bytes.length>2_000_000)return List.of();
            SeedPack pack=JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),SeedPack.class);
            if(pack==null||pack.schema()!=SCHEMA||pack.facts()==null)return List.of();
            return pack.facts().stream().filter(Objects::nonNull).filter(f->f.source().startsWith("seed:")&&f.confidence()<=80).limit(2000).toList();
        }catch(IOException|RuntimeException error){LogUtils.getLogger().warn("Prepared learning knowledge unavailable: {}",error.toString());return List.of();}
    }
}
