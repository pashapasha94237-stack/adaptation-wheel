package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.AnalysisInfo;
import static ru.pasha.adaptationwheel.core.AdaptationAnalysis.Evidence.*;

/** Server-authoritative local inference from native facts; no network/model services. */
public final class SmartAttackAnalyzer {
    private static final AdaptationProfile[] PROFILES=AdaptationProfile.values();
    private static final Map<ServerPlayer,Map<String,AdaptationAnalysis.Memory>> MEMORIES=new WeakHashMap<>();
    private static final Map<ServerPlayer,LearnedPatterns> PATTERNS=new WeakHashMap<>();
    private static final Map<UUID,Map<String,DamagePace>> PACE=new HashMap<>();
    private SmartAttackAnalyzer() {}
    private static LearnedPatterns patterns(ServerPlayer p){return PATTERNS.computeIfAbsent(p,player->{
        var rows=new ArrayList<LearnedPatterns.Example>();var list=player.getPersistentData().getCompound(AdaptationWheel.MOD_ID).getList("LearnedPatterns",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){var t=list.getCompound(i);try{rows.add(new LearnedPatterns.Example(t.getString("Key"),t.getString("Owner"),t.getString("Direct"),t.getString("Effects"),t.getString("Phase"),AdaptationProfile.valueOf(t.getString("Profile"))));}catch(IllegalArgumentException ignored){}}
        return LearnedPatterns.restore(rows);
    });}

    private static Map<String,AdaptationAnalysis.Memory> memories(ServerPlayer player){
        return MEMORIES.computeIfAbsent(player,p->{
            Map<String,AdaptationAnalysis.Memory> result=new LinkedHashMap<>();
            ListTag saved=p.getPersistentData().getCompound(AdaptationWheel.MOD_ID).getList("Analysis",Tag.TAG_COMPOUND);
            for(int n=0;n<saved.size();n++){
                CompoundTag e=saved.getCompound(n);String key=AdaptationEvents.migrateKey(e.getString("Key"));
                if(key.isBlank()||key.length()>256)continue;
                var memory=new AdaptationAnalysis.Memory(e.getInt("Count"),e.getDouble("MeanRisk"),e.getDouble("PeakRisk"),e.getInt("HarmfulCount"),e.getLong("Evidence"),votes(e.getCompound("Votes")));
                result.merge(key,memory,(a,b)->b.observations()>a.observations()?b:a);
            }
            return result;
        });
    }
    private static Map<AdaptationProfile,Integer> votes(CompoundTag saved){
        var result=new EnumMap<AdaptationProfile,Integer>(AdaptationProfile.class);
        for(String name:saved.getAllKeys())try{result.put(AdaptationProfile.valueOf(name),saved.getInt(name));}catch(IllegalArgumentException ignored){}
        return result;
    }
    /** Called once per correlated damage receipt, never once per limb/event layer. */
    public static void observe(ServerPlayer player,String key,boolean boss,AdaptationProfile fallback,DamageSource source,double raw,double actual){
        if(!AdaptationTimings.analysisEnabled()||!AdaptationEvents.active(player)||!AdaptationEvents.state(player).categoryEnabled(key))return;
        var evidence=EnumSet.noneOf(AdaptationAnalysis.Evidence.class);
        double consequenceRisk=0;
        boolean explicit=false;
        if(source!=null){
            if(DamageCategories.excluded(source.typeHolder()))return;
            evidence.add(SOURCE_CHECKED);
            // An explicit datapack profile is the strongest available evidence.
            for(AdaptationProfile profile:PROFILES)if(source.typeHolder().is(DamageCategories.profileTag(profile))){addRegistry(evidence,profile);explicit=true;break;}
            if(!explicit){
                if(source.is(DamageTypeTags.IS_LIGHTNING))evidence.add(REGISTRY_ELECTRIC);
                if(source.is(DamageTypeTags.IS_EXPLOSION))evidence.add(REGISTRY_EXPLOSION);
                if(source.is(DamageTypeTags.IS_FIRE))evidence.add(REGISTRY_FIRE);
                if(source.is(DamageTypeTags.IS_FREEZING))evidence.add(REGISTRY_COLD);
                if(source.is(DamageTypeTags.IS_DROWNING))evidence.add(REGISTRY_SUFFOCATION);
                if(source.is(DamageTypeTags.IS_FALL))evidence.add(REGISTRY_PHYSICAL);
                // Untagged vanilla types (a bee's sting, suffocating in a wall, magic) are as authoritative as tags.
                AdaptationProfile vanilla=DamageCategories.vanillaProfile(source.typeHolder());
                if(vanilla!=null)addRegistry(evidence,vanilla);
                if(evidence.stream().noneMatch(SmartAttackAnalyzer::registry))
                    for(AdaptationProfile tagged:DamageCategories.tagProfiles(source.typeHolder()))addTag(evidence,tagged);
            }
            if(source.is(DamageTypeTags.IS_PROJECTILE))evidence.add(PROJECTILE);
            if(source.getDirectEntity() instanceof Projectile)evidence.add(PROJECTILE);
            if(source.is(DamageTypes.MOB_ATTACK)||source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)||source.is(DamageTypes.PLAYER_ATTACK))evidence.add(PHYSICAL);
            if(bodyBlow(player,key,source))evidence.add(PHYSICAL);
        }
        if(key.startsWith("effect:")){
            ResourceLocation id=ResourceLocation.tryParse(key.substring(7));
            MobEffect effect=id==null?null:BuiltInRegistries.MOB_EFFECT.get(id);
            if(effect!=null){
                if(!DamageCategories.isHarmful(effect))return;
                evidence.add(HARMFUL_EFFECT);
                MobEffectInstance present=player.getEffect(effect);
                if(present!=null)consequenceRisk=EffectThreat.estimate(DamageCategories.effectProfile(effect),present.getAmplifier(),present.getDuration(),present.isInfiniteDuration());
                if(!explicit){
                    if(effect==MobEffects.POISON)evidence.add(REGISTRY_POISON);
                    if(effect==MobEffects.WITHER||effect==MobEffects.HARM)evidence.add(REGISTRY_MAGIC);
                    if(effect==MobEffects.HUNGER)evidence.add(REGISTRY_HUNGER);
                    if(effect==MobEffects.BLINDNESS||effect==MobEffects.DARKNESS)evidence.add(REGISTRY_DARKNESS);
                }
            }
        }
        AdaptationProfile exactEffect=ExtraEffects.knownProfile(key);
        if(exactEffect!=null){fallback=exactEffect;addNative(evidence,exactEffect);}
        if(key.equals(AdaptationProfile.COLD_KEY))evidence.add(REGISTRY_COLD);
        if(key.equals(AdaptationProfile.DARK_KEY))evidence.add(REGISTRY_DARKNESS);
        if(key.startsWith("condition:")&&(key.contains("capture/")||key.contains("constriction")||key.contains("_hold")||key.contains("disarm")||key.contains("charm")))evidence.add(CONTROL);
        if(key.contains(":health_write/"))evidence.add(DIRECT_HEALTH);
        if(compatibilityProfile(key))evidence.add(COMPAT_PROFILE);
        // A debuff can arrive in the same tick, before the queued damage receipt is
        // analyzed. Preserve that causal observation instead of losing it to ordering.
        if(evidence.isEmpty()||evidence.equals(EnumSet.of(SOURCE_CHECKED))){
            AdaptationProfile effect=CombatObserver.attributedProfile(player,key,source);
            if(effect!=null){evidence.add(HARMFUL_EFFECT);addNative(evidence,effect);}
        }
        var data=memories(player);
        double recent=source==null?0:PACE.computeIfAbsent(player.getUUID(),id->new HashMap<>()).computeIfAbsent(key,row->new DamagePace()).add(player.serverLevel().getGameTime(),Math.max(raw,actual));
        var decision=AdaptationAnalysis.observe(data.get(key),new AdaptationAnalysis.Observation(key,fallback,evidence,raw,actual,player.getMaxHealth(),boss||source!=null&&DamageCategories.boss(source),consequenceRisk,recent));
        if(!decision.eligible())return;
        var previous=AdaptationEvents.state(player).snapshot(key);
        CombatObserver.describe(player,key,source);
        LearningController.memory(player);
        var identity=CombatObserver.knowledge(player).summary(key);
        // Several protection keys can describe one received hit. They are not independent abilities.
        String ability=source==null?key:source.typeHolder().unwrapKey().map(k->DamageCategories.canonicalKey("damage:"+k.location())).orElse(key);
        // Abilities are told apart by their own row; a creature's row only repeats what its abilities taught.
        var example=new LearnedPatterns.Example(key.startsWith("attack:")?key:ability,identity.owner(),identity.direct(),identity.effects(),identity.phase(),decision.profile());
        if(LearnedPatterns.trusted(decision.reason(),decision.confidence())){if(LearningController.mode(player).learns()&&!key.startsWith("mob:"))patterns(player).remember(example);}
        else decision=AdaptationAnalysis.learned(decision,patterns(player).infer(example));
        var context=new ResearchMemory.Context(key,identity.owner(),identity.direct(),identity.effects(),identity.phase(),identity.range());
        var proposal=LearningController.observe(player,context,decision,raw,actual,ability);
        if(proposal!=null){
            var refined=AdaptationAnalysis.learned(decision,new LearnedPatterns.Match(proposal.profile(),proposal.confidence(),proposal.support()));
            if(refined!=decision)decision=new AdaptationAnalysis.Decision(refined.profile(),refined.durations(),refined.confidence(),proposal.reason(),refined.observations(),refined.memory(),refined.eligible());
        }
        decision=NeuralAdvisor.consider(player,key,decision);
        if(previous!=null)CombatObserver.knowledge(player).revised(key,previous.profile(),decision.profile());
        data.put(key,decision.memory());
        AdaptationEvents.state(player).applyAnalysis(key,decision);
    }
    /** Nothing names this type, but the blow came straight from a creature at arm's length: that is melee, whatever the mod called it. */
    private static boolean bodyBlow(ServerPlayer player,String key,DamageSource source){
        if(key.startsWith("effect:")||!(source.getDirectEntity() instanceof LivingEntity direct)||direct==player)return false;
        if(DamageCategories.profile(source)!=AdaptationProfile.ENVIRONMENT)return false;
        var known=source.typeHolder().unwrapKey().map(k->LearningController.memory(player).fact(DamageCategories.canonicalKey("damage:"+k.location()))).orElse(null);
        return (known==null||!known.source().startsWith("seed:"))&&direct.distanceTo(player)<=4+direct.getBbWidth();
    }
    private static void addRegistry(Set<AdaptationAnalysis.Evidence> evidence,AdaptationProfile profile){
        try{evidence.add(AdaptationAnalysis.Evidence.valueOf("REGISTRY_"+profile.name()));}catch(IllegalArgumentException ignored){}
    }
    private static void addTag(Set<AdaptationAnalysis.Evidence> evidence,AdaptationProfile profile){
        try{evidence.add(AdaptationAnalysis.Evidence.valueOf("TAG_"+profile.name()));}catch(IllegalArgumentException ignored){}
    }
    private static boolean registry(AdaptationAnalysis.Evidence evidence){return evidence.name().startsWith("REGISTRY_");}
    /** Conditions exist only through the wheel's compatibility code, which names their category; an unlisted Jujutsu technique does not. */
    private static boolean compatibilityProfile(String key){
        return key.startsWith("condition:")&&(!JujutsuCompatibility.techniqueKey(key)||JujutsuCompatibility.knownTechnique(key));
    }
    private static void addNative(Set<AdaptationAnalysis.Evidence> evidence,AdaptationProfile profile){
        if(profile==AdaptationProfile.EFFECT){evidence.add(ATTRIBUTED_EFFECT);return;}
        try{evidence.add(AdaptationAnalysis.Evidence.valueOf(profile.name()));}catch(IllegalArgumentException ignored){}
    }
    /** Existing continuous conditions are sampled only when first discovered. */
    public static void continuous(ServerPlayer player,String key){
        if(!memories(player).containsKey(key))observe(player,key,false,AdaptationProfile.forKey(key,false),null,0,0);
    }
    public static AnalysisInfo info(ServerPlayer player,String key){
        if(!AdaptationTimings.analysisEnabled())return AnalysisInfo.NONE;
        var snapshot=AdaptationEvents.state(player).snapshot(key);
        return info(player,snapshot);
    }
    public static AnalysisInfo info(ServerPlayer player,AdaptationState.Snapshot snapshot){
        if(!AdaptationTimings.analysisEnabled())return AnalysisInfo.NONE;
        if(snapshot==null)return AnalysisInfo.NONE;
        String key=snapshot.key();
        var insight=CombatObserver.knowledge(player).summary(key);
        if((snapshot.analysisObservations()==0||snapshot.analyzedDurations()==null)&&insight.variants()==0&&insight.verified()==0&&insight.leaks()==0)return AnalysisInfo.NONE;
        String kind=key.startsWith("attack:")?(key.contains("projectile/")?"projectile":"damage"):key.substring(0,key.indexOf(':'));
        if(!Set.of("mob","projectile","damage","effect","condition","environment").contains(kind))kind="damage";
        return withOrigin(player,snapshot,new AnalysisInfo(kind,snapshot.analysisConfidence(),snapshot.analysisReason(),snapshot.analysisObservations(),snapshot.analyzedDurations()==null?snapshot.timings():snapshot.analyzedDurations(),insight));
    }
    /** «Откуда знание»: the verdict came from the wheel's compatibility table (damage type tag or a compatibility condition). */
    static AnalysisInfo withOrigin(ServerPlayer player,AdaptationState.Snapshot snapshot,AnalysisInfo info){
        String key=snapshot.key();
        if(key.startsWith("damage:")){
            var id=net.minecraft.resources.ResourceLocation.tryParse(key.substring("damage:".length()));
            if(id==null||player.server==null)return info;
            var holder=player.server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,id));
            if(holder.isEmpty())return info;
            var table=DamageCategories.tableProfile(holder.get());
            if(table==null||table!=snapshot.profile())return info;
            var without=DamageCategories.profileWithoutTable(holder.get());
            return info.withOrigin(modName(id.getNamespace()),without==table?"":without.name());
        }
        if(compatibilityProfile(key)){
            String rest=key.substring("condition:".length());
            int colon=rest.indexOf(':');
            if(colon>0)return info.withOrigin(modName(rest.substring(0,colon)),"");
        }
        return info;
    }
    static String modName(String namespace){
        String name=net.minecraftforge.fml.ModList.get()==null?namespace:net.minecraftforge.fml.ModList.get().getModContainerById(namespace).map(c->c.getModInfo().getDisplayName()).orElse(namespace);
        return name.length()>64?name.substring(0,64):name;
    }
    /** A row the player may correct: the analyzer has an opinion on it, or a correction is there to take back. Mechanics have no category. */
    public static boolean fixable(ServerPlayer player,AdaptationState.Snapshot snapshot){
        return snapshot!=null&&MetaMechanic.fromKey(snapshot.key())==null&&(snapshot.manualProfile()!=null||info(player,snapshot).present());
    }
    public static void write(ServerPlayer player,CompoundTag tag){
        ListTag list=new ListTag();
        memories(player).forEach((key,m)->{CompoundTag e=new CompoundTag();e.putString("Key",key);e.putInt("Count",m.observations());e.putDouble("MeanRisk",m.meanRisk());e.putDouble("PeakRisk",m.peakRisk());e.putInt("HarmfulCount",m.harmfulObservations());e.putLong("Evidence",m.evidenceMask());
            if(!m.votes().isEmpty()){CompoundTag votes=new CompoundTag();m.votes().forEach((profile,count)->votes.putInt(profile.name(),count));e.put("Votes",votes);}
            list.add(e);});
        tag.put("Analysis",list);
        ListTag examples=new ListTag();for(var row:patterns(player).save()){
            var t=new CompoundTag();t.putString("Key",row.key());t.putString("Owner",row.owner());t.putString("Direct",row.direct());t.putString("Effects",row.effects());t.putString("Phase",row.phase());t.putString("Profile",row.profile().name());examples.add(t);
        }tag.put("LearnedPatterns",examples);
        LearningController.checkpoint(player,false);
    }
    public static void copy(ServerPlayer original,ServerPlayer replacement){MEMORIES.put(replacement,new LinkedHashMap<>(memories(original)));PATTERNS.put(replacement,LearnedPatterns.restore(patterns(original).save()));MEMORIES.remove(original);PATTERNS.remove(original);}
    public static void forget(ServerPlayer player){MEMORIES.remove(player);PATTERNS.remove(player);PACE.remove(player.getUUID());}
    /** Null means no one knows what changed, so nothing learned can be kept. */
    public static void invalidatePatterns(ServerPlayer player,Set<String> changed){
        if(changed!=null){patterns(player).forget(changed);return;}
        PATTERNS.put(player,new LearnedPatterns());player.getPersistentData().getCompound(AdaptationWheel.MOD_ID).remove("LearnedPatterns");
    }
    public static void clear(ServerPlayer player){MEMORIES.put(player,new LinkedHashMap<>());PATTERNS.put(player,new LearnedPatterns());PACE.remove(player.getUUID());CombatObserver.forget(player);LearningController.clear(player);}
    public static void clearAll(){MEMORIES.clear();PATTERNS.clear();PACE.clear();}
    public static AdaptationAnalysis.Memory memory(ServerPlayer player,String key){return memories(player).get(key);}
    /** Only a source-attributed debuff within five ticks may revise a weak text/fallback guess. */
    public static void sideEffect(ServerPlayer p,String key,AdaptationProfile effect){
        if(!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled()||!AdaptationEvents.state(p).categoryEnabled(key))return;
        var previous=AdaptationEvents.state(p).snapshot(key);
        if(previous==null||previous.analysisConfidence()>=85)return;
        var evidence=EnumSet.of(HARMFUL_EFFECT);addNative(evidence,effect);
        var decision=AdaptationAnalysis.observe(memories(p).get(key),new AdaptationAnalysis.Observation(key,previous.profile(),evidence,0,0,p.getMaxHealth(),previous.boss()));
        memories(p).put(key,decision.memory());CombatObserver.knowledge(p).revised(key,previous.profile(),decision.profile());AdaptationEvents.state(p).applyAnalysis(key,decision);
    }
}
