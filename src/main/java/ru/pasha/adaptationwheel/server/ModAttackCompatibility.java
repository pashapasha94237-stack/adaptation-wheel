package ru.pasha.adaptationwheel.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.fml.ModList;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;

/** Audited attack-site attribution. No optional mod classes are linked into the common code. */
public final class ModAttackCompatibility {
    private record Entry(String mod,String key,String label,String owner,String method,boolean shared) {}
    private static final class Contact {
        final Entity victim;final DamageSource source;final String key;final AdaptationProfile profile;
        final Set<String> accepted=new HashSet<>();
        boolean contacted;
        double rawAmount;
        Contact(Entity victim,DamageSource source,String key,AdaptationProfile profile){this.victim=victim;this.source=source;this.key=key;this.profile=profile;}
    }
    private static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    private static final Map<String,Entry> CALLERS=new HashMap<>();
    private static final Map<String,String> PROJECTILES=new LinkedHashMap<>();
    private static final StackWalker WALKER=StackWalker.getInstance();
    private static final ThreadLocal<Contact> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<ServerPlayer> DIRECT_HEALTH=new ThreadLocal<>();
    private static final ThreadLocal<ProjectileFrame> PROJECTILE_FRAME=new ThreadLocal<>();
    private static final class ProjectileFrame {
        final Entity entity;final String key;final Map<Entity,Contact> contacts=new IdentityHashMap<>();
        ProjectileFrame(Entity entity,String key){this.entity=entity;this.key=key;}
        Contact contact(Entity victim,DamageSource source){
            DamageSource impact=source.getEntity()==null&&entity instanceof net.minecraft.world.entity.projectile.Projectile projectile
                    ?new DamageSource(source.typeHolder(),entity,rootOwner(projectile.getOwner())):source;
            return contacts.computeIfAbsent(victim,e->new Contact(victim,impact,key,AdaptationProfile.fromName(key,DamageCategories.profile(impact))));
        }
    }
    private record ProjectileScope(ProjectileFrame previous,ProjectileFrame entered) {}
    private record DamageScope(Contact previous,Contact entered) {}
    private static final Set<String> NEGATIVE_NEUTRAL=Set.of(
        "AntiEscapeMobEffect","EffectFear","EffectPowerDown","EffectDebilitatingSting",
        "InfestationofFliesMobEffect","LivingCocoonPlayerSideMobEffect","FearEffectMobEffect",
        "WoundedPotion","MisfortunePotion","BadOmenEffect","EffectEclipsed",
        "BlackBurningPotion","BoundPotion","TerrifyingPresenceMobEffect");
    private static final Set<String> NEUTRAL_MODS=Set.of("anomaly_rephased","alexsmobs","born_in_chaos_v1","eternal_tales","mahoutsukai","vampirism","mowziesmobs");
    static {
        try(var in=ModAttackCompatibility.class.getResourceAsStream("/compat/mod-attacks.json")) {
            if(in==null)throw new IllegalStateException("Missing audited mod attack manifest");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.has("projectiles"))root.getAsJsonObject("projectiles").entrySet().forEach(e->PROJECTILES.put(e.getKey(),e.getValue().getAsJsonObject().get("key").getAsString()));
            for(var value:root.getAsJsonArray("entries")) {
                var e=value.getAsJsonObject();String key=e.get("key").getAsString();
                Entry entry=new Entry(e.get("mod").getAsString(),key,e.get("label").getAsString(),e.get("owner").getAsString(),e.get("method").getAsString(),e.has("shared")&&e.get("shared").getAsBoolean());
                ENTRIES.put(key,entry);
                if(!entry.shared())CALLERS.put(entry.owner().replace('/','.')+"#"+semantic(entry.method()),entry);
            }
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    private ModAttackCompatibility() {}
    private static Entity rootOwner(Entity entity){
        return entity instanceof net.minecraftforge.entity.PartEntity<?> part?part.getParent():entity;
    }
    private static String projectileKey(Entity entity){
        var id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String known=PROJECTILES.get(id.toString());
        return known!=null?known:entity instanceof net.minecraft.world.entity.projectile.Projectile&&!id.getNamespace().equals("minecraft")
                ?"attack:"+id.getNamespace()+":dynamic_projectile/"+id.getPath():null;
    }
    private static String semantic(String method){return method.replaceFirst("^lambda\\$([^$]+)\\$[0-9]+$","$1");}
    public static boolean negativeNeutral(MobEffect effect) {
        var id=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id!=null&&NEUTRAL_MODS.contains(id.getNamespace())&&NEGATIVE_NEUTRAL.contains(effect.getClass().getSimpleName());
    }
    private static String actualKey(String family,DamageSource source) {
        ProjectileFrame projectileFrame=PROJECTILE_FRAME.get();if(projectileFrame!=null)return projectileFrame.key;
        if(family.equals("attack:iceandfire:dragon_breath")||family.equals("attack:iceandfire:dragon_charge")) {
            var profile=DamageCategories.profile(source);
            return family+switch(profile){case ELECTRIC->"/lightning";case COLD->"/ice";default->"/fire";};
        }
        Entity direct=source.getDirectEntity();
        if(direct!=null&&!(direct instanceof LivingEntity)) {
            String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString();
            String dynamic=projectileKey(direct);if(dynamic!=null)return dynamic;
        }
        Entry entry=ENTRIES.get(family);
        if(entry!=null&&entry.shared()) {
            return WALKER.walk(frames->frames.limit(48).map(frame->CALLERS.get(frame.getClassName()+"#"+semantic(frame.getMethodName())))
                .filter(Objects::nonNull).map(Entry::key).findFirst().orElse(family));
        }
        return family;
    }
    public static DamageCategories.Exposure exposure(ServerPlayer victim,DamageSource source) {
        if(DamageCategories.excluded(source.typeHolder()))return null;
        Contact c=CURRENT.get();
        if(c!=null&&c.victim==victim&&c.key!=null)return new DamageCategories.Exposure(c.key,c.profile,false);
        ProjectileFrame frame=PROJECTILE_FRAME.get();if(frame!=null){Contact impact=frame.contact(victim,source);return new DamageCategories.Exposure(impact.key,impact.profile,false);}
        // A mod arrow can inherit vanilla's entire collision handler. It still has
        // its own registered entity type even though no mod hurt call exists to wrap.
        Entity direct=source.getDirectEntity();
        if(direct==null||direct instanceof LivingEntity)return null;
        String key=projectileKey(direct);
        if(key==null)return null;
        var nativeProfile=DamageCategories.profile(source);
        var profile=nativeProfile!=AdaptationProfile.ENVIRONMENT&&nativeProfile!=AdaptationProfile.PHYSICAL&&nativeProfile!=AdaptationProfile.PROJECTILE
            ?nativeProfile:AdaptationProfile.fromName(key,nativeProfile);
        return new DamageCategories.Exposure(key,profile,false);
    }
    public static DamageSource impactSource(ServerPlayer victim,DamageSource source) {
        Contact c=CURRENT.get();return c!=null&&c.victim==victim?c.source:source;
    }
    public static double rawAmount(ServerPlayer victim,double fallback){Contact c=CURRENT.get();return c!=null&&c.victim==victim?Math.max(c.rawAmount,fallback):fallback;}
    public static boolean repeatedContact(ServerPlayer victim) {
        Contact c=CURRENT.get();if(c==null||c.victim!=victim)return false;
        boolean seen=c.contacted;c.contacted=true;return seen;
    }
    public static boolean repeatedAccepted(ServerPlayer victim,String key) {
        Contact c=CURRENT.get();return c!=null&&c.victim==victim&&!c.accepted.add(key);
    }
    /** First Aid may translate a direct setter into several generic limb notifications. */
    public static boolean healthWriteScoped(ServerPlayer victim) { return DIRECT_HEALTH.get()==victim; }
    public static boolean directHealthWrite(ServerPlayer victim) { return healthWriteScoped(victim)||AutonomousHealthGuard.isWriting(victim); }
    /** Native collision/tick scope includes a projectile's secondary source-less damage. */
    public static Object beginProjectile(Entity projectile){
        if(projectile.level().isClientSide)return null;
        String key=projectileKey(projectile);
        if(key==null)return null;
        ProjectileFrame previous=PROJECTILE_FRAME.get();if(previous!=null&&previous.entity==projectile)return null;
        ProjectileFrame entered=new ProjectileFrame(projectile,key);ProjectileScope token=new ProjectileScope(previous,entered);PROJECTILE_FRAME.set(entered);return token;
    }
    public static void endProjectile(Object token){
        if(!(token instanceof ProjectileScope scope)||PROJECTILE_FRAME.get()!=scope.entered())return;
        if(scope.previous()==null)PROJECTILE_FRAME.remove();else PROJECTILE_FRAME.set(scope.previous());
    }
    /** Audited hostile Soul projectile payloads include fire, forces and extra melee. */
    public static boolean skipProjectileEffect(Entity projectile,LivingEntity victim){
        if(!(victim instanceof ServerPlayer p)||!AdaptationEvents.active(p))return false;
        String key=projectileKey(projectile);
        if(key==null)return false;
        AdaptationEvents.continuousExposure(p,key);
        if(AdaptationEvents.state(p).isImmune(key))return true;
        Entity owner=projectile instanceof net.minecraft.world.entity.projectile.Projectile shot?shot.getOwner():null;
        ProjectileFrame frame=PROJECTILE_FRAME.get();Contact contact=frame==null?null:frame.contacts.get(p);
        if(owner==null&&frame!=null&&frame.entity==projectile&&contact!=null)owner=contact.source.getEntity();
        return owner instanceof LivingEntity living&&AdaptationEvents.state(p).existenceAdapted(DamageCategories.forEntity(living));
    }
    /** Method scope preserves other mods' original hurt invocation injection points. */
    public static Object beginHurt(Entity victim,DamageSource source,float amount) {
        if(!(victim instanceof ServerPlayer player)||!AdaptationEvents.active(player)||amount<=0||DamageCategories.excluded(source.typeHolder()))return null;
        Contact previous=CURRENT.get();
        if(previous!=null&&previous.victim==victim){previous.rawAmount=Math.max(previous.rawAmount,amount);return null;}
        ProjectileFrame frame=PROJECTILE_FRAME.get();
        Contact entered=frame==null?new Contact(victim,source,null,DamageCategories.profile(source)):frame.contact(victim,source);
        entered.rawAmount=Math.max(entered.rawAmount,amount);
        DamageScope scope=new DamageScope(previous,entered);CURRENT.set(entered);return scope;
    }
    public static void endHurt(Object token) {
        if(!(token instanceof DamageScope scope)||CURRENT.get()!=scope.entered())return;
        if(scope.previous()==null)CURRENT.remove();else CURRENT.set(scope.previous());
    }
    /** Each original virtual hurt call is retained exactly once, including its boolean result. */
    public static boolean hurt(Entity victim,DamageSource source,float amount,String family) {
        return hurtOwned(victim,source,amount,family,null);
    }
    /** Keep the native DamageSource object for dispatch, restoring its lost owner only for analysis. */
    public static boolean hurtOwned(Entity victim,DamageSource source,float amount,String family,Entity owner) {
        if(!(victim instanceof ServerPlayer)||amount<=0||!ENTRIES.containsKey(family)||DamageCategories.excluded(source.typeHolder()))
            return victim.hurt(source,amount);
        // A downstream mod can rebuild and reissue a hit while processing it.
        // Preserve the original ability and count the outer contact only once.
        Contact previous=CURRENT.get();
        if(previous!=null&&previous.victim==victim)return victim.hurt(source,amount);
        String key=actualKey(family,source);
        var nativeProfile=DamageCategories.profile(source);
        var guessed=key.contains(":projectile/")&&nativeProfile!=AdaptationProfile.ENVIRONMENT&&nativeProfile!=AdaptationProfile.PHYSICAL&&nativeProfile!=AdaptationProfile.PROJECTILE
            ?nativeProfile:AdaptationProfile.fromName(key,nativeProfile);
        ProjectileFrame frame=PROJECTILE_FRAME.get();
        DamageSource impact=owner!=null&&source.getEntity()==null?new DamageSource(source.typeHolder(),rootOwner(owner),rootOwner(owner)):source;
        Contact entered=frame==null?new Contact(victim,impact,key,guessed):frame.contact(victim,impact);entered.rawAmount=Math.max(entered.rawAmount,amount);CURRENT.set(entered);
        try{return victim.hurt(source,amount);}
        finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    /** Only audited attack setters use this hook; healing and initialization stay native. */
    public static void setHealth(LivingEntity victim,float requested,String family) {
        setHealthFromSource(victim,requested,family,null);
    }
    /** Guardian's charged beam has an explicit owner even though it bypasses hurt(). */
    public static void setHealthFromSource(LivingEntity victim,float requested,String family,DamageSource source) {
        if(!(victim instanceof ServerPlayer player)||!player.isAlive()||requested>=player.getHealth()||!AdaptationEvents.active(player)) {
            victim.setHealth(requested);return;
        }
        float lost=Math.max(0,player.getHealth()-Math.max(0,requested));
        ProjectileFrame frame=PROJECTILE_FRAME.get();String key=frame==null?family:frame.key;
        if(frame==null||frame.contact(player,player.damageSources().generic()).accepted.add(key))AdaptationEvents.recordExposure(player,key,false,source,lost,lost);
        double multiplier=AdaptationEvents.state(player).damageMultiplier(key);
        if(source!=null){
            for(var exposure:DamageCategories.analyze(source,player))AdaptationEvents.recordExposure(player,exposure.key(),exposure.boss());
            if(source.getEntity() instanceof LivingEntity owner&&AdaptationEvents.state(player).existenceAdapted(DamageCategories.forEntity(owner)))multiplier=0;
        }
        ServerPlayer previous=DIRECT_HEALTH.get();DIRECT_HEALTH.set(player);
        try {if(multiplier>0)victim.setHealth(player.getHealth()-(float)(lost*multiplier));}
        finally {if(previous==null)DIRECT_HEALTH.remove();else DIRECT_HEALTH.set(previous);}
    }
    public static String translationKey(String key) {
        String control=MobControlCompatibility.translationKey(key);if(!control.isEmpty())return control;
        if(HazardCompatibility.ICE.equals(key))return "condition.adaptationwheel.iceandfire_frozen";
        if(HazardCompatibility.TORNADO.equals(key))return "condition.adaptationwheel.weather2_tornado";
        if(HazardCompatibility.SIREN.equals(key))return "condition.adaptationwheel.iceandfire_siren_charm";
        if(HazardCompatibility.ANOMALY.equals(key))return "condition.adaptationwheel.anomaly_execution";
        if(key==null||!key.startsWith("attack:"))return "";
        return "attack.adaptationwheel."+key.substring(7).replace(':','.').replace('/','.');
    }
    public static Component displayName(String key) {
        Component direct=AutonomousHealthGuard.name(key);if(direct!=null)return direct;
        Component observed=ObservedAbilities.name(key);if(observed!=null)return observed;
        String base=key.replaceAll("/(fire|ice|lightning)$","");
        Entry entry=ENTRIES.get(base);
        String fallback=entry==null?key:entry.label()+" ["+entry.mod()+"]";
        return Component.translatableWithFallback(translationKey(key),fallback);
    }
    public static List<CatalogueEntry> catalogue() {
        var result=new ArrayList<CatalogueEntry>();
        for(String[] nativeState:List.of(new String[]{"toughasnails","condition:toughasnails:thirst"},new String[]{"sanitydim","condition:sanitydim:insanity"},new String[]{"mekanism","condition:mekanism:radiation"},new String[]{"nuclearcraft",CorpusStateCompatibility.NUCLEAR},new String[]{"dragonsurvival",CorpusStateCompatibility.SEA}))
            if(ModList.get().isLoaded(nativeState[0]))result.add(new CatalogueEntry(nativeState[1],DamageCategories.translationKey(nativeState[1]),DamageCategories.fallbackName(nativeState[1]),AdaptationProfile.forKey(nativeState[1],false)));
        result.addAll(MobControlCompatibility.catalogue());
        PROJECTILES.forEach((id,key)->{
            String mod=id.substring(0,id.indexOf(':'));
            if(ModList.get().isLoaded(mod)&&net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(new net.minecraft.resources.ResourceLocation(id)))
                result.add(new CatalogueEntry(key,translationKey(key),id+" projectile",AdaptationProfile.forKey(key,false)));
        });
        if(ModList.get().isLoaded("iceandfire"))result.add(new CatalogueEntry(HazardCompatibility.ICE,translationKey(HazardCompatibility.ICE),"Dragon freezing",AdaptationProfile.COLD));
        if(ModList.get().isLoaded("weather2"))result.add(new CatalogueEntry(HazardCompatibility.TORNADO,translationKey(HazardCompatibility.TORNADO),"Tornado suction",AdaptationProfile.ENVIRONMENT));
        if(ModList.get().isLoaded("iceandfire"))result.add(new CatalogueEntry(HazardCompatibility.SIREN,translationKey(HazardCompatibility.SIREN),"Siren charm",AdaptationProfile.EFFECT));
        if(ModList.get().isLoaded("anomaly_rephased"))result.add(new CatalogueEntry(HazardCompatibility.ANOMALY,translationKey(HazardCompatibility.ANOMALY),"Anomaly execution",AdaptationProfile.MAGIC));
        for(var entry:ENTRIES.values())if(!entry.shared()&&ModList.get().isLoaded(entry.mod())
                &&ModAttackCompatibility.class.getClassLoader().getResource(entry.owner()+".class")!=null) {
            if(entry.key().equals("attack:iceandfire:dragon_breath")||entry.key().equals("attack:iceandfire:dragon_charge")) {
                for(String kind:List.of("fire","ice","lightning")) {
                    String key=entry.key()+"/"+kind;result.add(new CatalogueEntry(key,translationKey(key),displayName(key).getString(),AdaptationProfile.forKey(key,false)));
                }
            }else result.add(new CatalogueEntry(entry.key(),translationKey(entry.key()),entry.label()+" ["+entry.mod()+"]",AdaptationProfile.forKey(entry.key(),false)));
        }
        return result;
    }
}
