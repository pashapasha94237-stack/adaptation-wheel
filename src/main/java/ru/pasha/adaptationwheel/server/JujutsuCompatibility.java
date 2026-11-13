package ru.pasha.adaptationwheel.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;

/** Optional v51 integration. All references to Jujutsu Craft are registry ids/data, never class links. */
public final class JujutsuCompatibility {
    private static final String MOD="jujutsucraft",PREFIX="condition:"+MOD+":technique/";
    private static final String PROCEDURES="net.mcreator.jujutsucraft.procedures.";
    private static final String SHOT="adaptationwheelJjcTechnique";
    private static final Map<String,Technique> TECHNIQUES=new LinkedHashMap<>();
    private static final Map<String,String> PROCEDURE_IDS=new HashMap<>();
    private static final StackWalker WALKER=StackWalker.getInstance();
    private static Boolean loaded;
    private record Technique(String id,String name,String english,AdaptationProfile profile) {}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final class Session {
        final Map<String,Integer> seen=new HashMap<>();
        String control="";
    }
    static {
        try(var in=JujutsuCompatibility.class.getResourceAsStream("/compat/jujutsucraft-v51.json")) {
            if(in==null)throw new IOException("Missing Jujutsu compatibility manifest");
            JsonObject json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            json.getAsJsonObject("skills").entrySet().forEach(e->{
                JsonObject t=e.getValue().getAsJsonObject();String english=t.get("en").getAsString();
                AdaptationProfile profile=AdaptationProfile.fromName(english,AdaptationProfile.MAGIC);
                TECHNIQUES.put(e.getKey(),new Technique(e.getKey(),t.get("key").getAsString(),english,profile));
            });
            json.getAsJsonObject("procedures").entrySet().forEach(e->PROCEDURE_IDS.put(e.getKey(),e.getValue().getAsString()));
        } catch(IOException|RuntimeException error) { throw new ExceptionInInitializerError(error); }
    }
    private JujutsuCompatibility() {}
    public static boolean available(){if(loaded==null)loaded=ModList.get().isLoaded(MOD);return loaded;}
    public static boolean techniqueKey(String key){return key!=null&&key.startsWith(PREFIX);}
    public static boolean knownTechnique(String key){return techniqueKey(key)&&TECHNIQUES.containsKey(key.substring(PREFIX.length()));}
    public static String key(int id){return PREFIX+id;}
    public static AdaptationProfile profile(String key){
        Technique t=techniqueKey(key)?TECHNIQUES.get(key.substring(PREFIX.length())):null;
        return t==null?AdaptationProfile.MAGIC:t.profile;
    }
    public static String translationKey(String key){
        if(techniqueKey(key))return "condition.adaptationwheel.jjc.technique."+key.substring(PREFIX.length());
        return switch(key){
            case "condition:jujutsucraft:projection_freeze"->"condition.adaptationwheel.jjc.projection_freeze";
            default->"";
        };
    }
    public static void addCatalogue(Map<String,CatalogueEntry> entries){
        if(!available())return;
        for(Technique t:TECHNIQUES.values()) {
            String key=PREFIX+t.id;
            entries.put(key,new CatalogueEntry(key,translationKey(key),t.english,t.profile));
        }
        String freeze="condition:jujutsucraft:projection_freeze";
        entries.put(freeze,new CatalogueEntry(freeze,translationKey(freeze),"Projection sorcery freeze",AdaptationProfile.MAGIC));
    }
    /** The original marks cooldowns and Sukuna's power-up as harmful. They are not enemy afflictions. */
    public static boolean serviceEffect(MobEffect effect){
        ResourceLocation id=BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if(id==null||!MOD.equals(id.getNamespace()))return false;
        return id.getPath().contains("cooldown")||id.getPath().equals("sukuna_effect");
    }
    public static Entity owner(Entity source){
        Entity result=source;
        Set<UUID> visited=new HashSet<>();
        for(int n=0;result!=null&&n<5;n++) {
            if(!visited.add(result.getUUID()))break;
            String stored=result.getPersistentData().getString("OWNER_UUID");
            if(stored.isEmpty()||!(result.level() instanceof net.minecraft.server.level.ServerLevel level))break;
            Entity next;
            try{next=level.getEntity(UUID.fromString(stored));}catch(IllegalArgumentException ignored){break;}
            if(next==null)break;result=next;
        }
        return result;
    }
    private static String stackTechnique(boolean activeOnly){
        return WALKER.walk(frames->frames.filter(f->f.getClassName().startsWith(PROCEDURES))
            .filter(f->!activeOnly||f.getClassName().endsWith("ActiveProcedure"))
            .map(f->PROCEDURE_IDS.get(f.getClassName().substring(PROCEDURES.length())))
            .filter(Objects::nonNull).findFirst().orElse(null));
    }
    public static String technique(Entity source){
        if(!available())return null;
        String stored=source==null?"":source.getPersistentData().getString(SHOT);
        if(techniqueKey(stored))return stored;
        String active=stackTechnique(true);
        if(active!=null)return PREFIX+active;
        if(source==null){String stack=stackTechnique(false);return stack==null?null:PREFIX+stack;}
        double number=source.getPersistentData().getDouble("skill");
        if(number<=0) {Entity resolved=owner(source);if(resolved!=null)number=resolved.getPersistentData().getDouble("skill");}
        if(Double.isFinite(number)&&number>=100&&number<100000&&number==Math.rint(number))return key((int)number);
        String stack=stackTechnique(false);return stack==null?null:PREFIX+stack;
    }
    /** Capture a projectile's original technique before its caster changes skills or disconnects. */
    public static void projectile(Entity caster,Entity projectile){
        if(caster==null||projectile==null||caster==projectile||projectile.level().isClientSide)return;
        String technique=technique(caster);
        if(technique!=null)projectile.getPersistentData().putString(SHOT,technique);
    }
    public static List<DamageCategories.Exposure> exposures(DamageSource source){
        if(!available())return List.of();
        ResourceLocation type=source.typeHolder().unwrapKey().map(k->k.location()).orElse(null);
        Entity carrier=source.getEntity()!=null?source.getEntity():source.getDirectEntity();
        if((type==null||!MOD.equals(type.getNamespace()))&&(carrier==null||!MOD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(carrier.getType()).getNamespace())))return List.of();
        List<DamageCategories.Exposure> result=new ArrayList<>();
        Entity resolved=owner(carrier);
        if(resolved instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player)){
            String key=DamageCategories.forEntity(living);boolean boss=DamageCategories.boss(living);
            result.add(new DamageCategories.Exposure(key,AdaptationProfile.forKey(key,boss),boss));
        }
        String technique=technique(carrier);
        if(technique!=null)result.add(new DamageCategories.Exposure(technique,profile(technique),false));
        return result;
    }
    private static boolean immune(ServerPlayer p,Entity caster,String technique){
        var state=AdaptationEvents.state(p);
        if(technique!=null&&state.isImmune(technique))return true;
        Entity resolved=owner(caster);
        return resolved instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player)
                &&state.isImmune(DamageCategories.forEntity(living));
    }
    private static void observe(ServerPlayer p,String key,boolean continuous){
        if(key==null||!AdaptationEvents.state(p).categoryEnabled(key))return;
        Session session=SESSIONS.computeIfAbsent(p,ignored->new Session());
        Integer before=session.seen.put(key,p.tickCount);
        // Repainting an ongoing domain/status is one exposure, not a hit every game tick.
        if(before==null||p.tickCount-before>(continuous?40:0))AdaptationEvents.recordExposure(p,key,false);
    }
    /** Invoked only on successful target checks, after the original team's/range/domain rules. */
    public static boolean allowTarget(boolean original,Entity caster,Entity target,boolean domain){
        if(!original||!(target instanceof ServerPlayer p)||caster==target||!AdaptationEvents.active(p))return original;
        String technique=technique(caster);
        boolean blocked=immune(p,caster,technique);
        if(domain&&!blocked){observe(p,technique,true);SESSIONS.computeIfAbsent(p,ignored->new Session()).control=technique==null?"":technique;}
        Entity resolved=owner(caster);
        if(blocked&&!domain&&resolved instanceof LivingEntity living&&DamageCategories.boss(living)) {
            String species=DamageCategories.forEntity(living);
            var state=AdaptationEvents.state(p);
            if(state.stage(species)==4&&!state.existenceAdapted(species)) {
                Session session=SESSIONS.computeIfAbsent(p,ignored->new Session());
                Integer before=session.seen.put("contact:"+species,p.tickCount);
                if(before==null||before!=p.tickCount)AdaptationEvents.recordExposure(p,species,true);
            }
        }
        return !blocked;
    }
    /** Entry guard for confirmed hostile, target-specific secondary effects (speech, soul, control). */
    public static boolean skipTargetEffect(Entity caster,Entity target,String explicit){
        if(caster==null||!(target instanceof ServerPlayer p)||caster==target||!AdaptationEvents.active(p))return false;
        String technique=explicit.isEmpty()?technique(caster):PREFIX+explicit;
        if(immune(p,caster,technique))return true;
        observe(p,technique,false);
        SESSIONS.computeIfAbsent(p,ignored->new Session()).control=technique==null?"":technique;
        return false;
    }
    /** Existing generic harmful-effect handling, with refresh deduplication for this mod. */
    public static boolean effectAdded(ServerPlayer p,MobEffectEvent.Added event){
        ResourceLocation id=BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect());
        if(id==null||!MOD.equals(id.getNamespace()))return false;
        var old=event.getOldEffectInstance();
        
        return old!=null&&event.getEffectInstance().getAmplifier()<=old.getAmplifier();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void projection(MobEffectEvent.Applicable event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!AdaptationEvents.active(p))return;
        ResourceLocation id=BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect());
        if(id==null||!id.toString().equals("jujutsucraft:projection_sorcery")||p.getPersistentData().getDouble("select")!=0)return;
        String key="condition:jujutsucraft:projection_freeze";
        observe(p,key,true);
        if(AdaptationEvents.state(p).isImmune(key))event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!AdaptationEvents.active(p)||!p.isAlive()){SESSIONS.remove(p);return;}
        var state=AdaptationEvents.state(p);Session session=SESSIONS.get(p);
        if(session!=null&&!session.control.isEmpty()&&p.getPersistentData().getDouble("skill")>-900
                &&p.tickCount-session.seen.getOrDefault(session.control,p.tickCount)>40)session.control="";
        boolean release=session!=null&&!session.control.isEmpty()&&state.isImmune(session.control);
        String projection="condition:jujutsucraft:projection_freeze";
        if(state.isImmune(projection)&&p.getPersistentData().getDouble("select")==0){remove(p,"projection_sorcery");release=true;}
        if(release&&p.getPersistentData().getDouble("skill")<=-900){
            // Negative skill ids are the original mod's forced incapacitation states.
            remove(p,"cursed_technique");p.getPersistentData().putDouble("skill",0);
            }
        if(release&&session!=null)session.control="";
    }
    private static void remove(ServerPlayer p,String id){
        BuiltInRegistries.MOB_EFFECT.getOptional(new ResourceLocation(MOD,id)).ifPresent(p::removeEffect);
    }
}




