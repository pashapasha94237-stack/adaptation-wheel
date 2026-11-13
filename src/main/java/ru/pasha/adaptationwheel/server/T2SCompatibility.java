package ru.pasha.adaptationwheel.server;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;

/** Optional T2S 2.0 integration. No T2S classes are linked when the mod is absent. */
public final class T2SCompatibility {
    private static final String MOD="trytosurvivetwo",PREFIX="condition:"+MOD+":";
    private static final String PROCEDURES="net.mcreator.trytosurvivetwo.procedures.";
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final StackWalker WALKER=StackWalker.getInstance();
    private static Schema schema;
    private static boolean checked;
    private record Metric(String field,double healthy,double threshold,boolean low) {
        double bad(double value){return Math.max(0,low?healthy-value:value-healthy);}
        double value(double bad){return healthy+(low?-bad:bad);}
    }
    public record Condition(String id,String key,AdaptationProfile profile,String fallback,List<Metric> metrics,List<String> flags) {}
    private static Metric m(String field){return new Metric(field,0,0,false);}
    private static Metric m(String field,double healthy,double threshold,boolean low){return new Metric(field,healthy,threshold,low);}
    private static Condition disease(String id,String fallback,String stem,String... extras){
        List<Metric> metrics=new ArrayList<>();metrics.add(m(stem+"Progression"));
        for(String extra:extras)metrics.add(m(extra));
        return new Condition(id,PREFIX+id,AdaptationProfile.EFFECT,fallback,List.copyOf(metrics),List.of(stem+"Check"));
    }
    private static Condition permanent(String id,String fallback,String stem){
        return new Condition(id,PREFIX+id,AdaptationProfile.EFFECT,fallback,List.of(),List.of(stem+"Check"));
    }
    private static Condition infection(String id,String name,String stem,String effect){
        return new Condition(id,"effect:"+MOD+":"+effect,AdaptationProfile.EFFECT,name,
                List.of(m(stem+"InfectionProgression"),m(stem+"Countdown"),m(stem+"Level"),m(stem+"LevelVariable")),List.of());
    }
    private static Condition stat(String id,String name,AdaptationProfile profile,Metric... metrics){
        return new Condition(id,PREFIX+id,profile,name,List.of(metrics),List.of());
    }
    private static final List<Condition> CONDITIONS=List.of(
        infection("flu","Flu","T2SFlu","t_2_s_flu_disease_effect"),
        infection("cold","Cold","T2SCold","t_2_s_cold_disease_effect"),
        infection("fever","Fever","T2SFever","t_2_s_fever_disease_effect"),
        infection("measles","Measles","T2SMeasles","t_2_s_measles_disease_effect"),
        infection("rabies","Rabies","T2SRabies","t_2_s_rabies_disease"),
        new Condition("dread","effect:"+MOD+":t_2_s_dread_effect",AdaptationProfile.EFFECT,"Dread",
            List.of(m("T2SDreadProgression"),m("T2SStressRegenerationBlock")),List.of()),
        new Condition("out_of_stamina","effect:"+MOD+":t_2_s_out_of_stamina_effect",AdaptationProfile.EFFECT,"Out of stamina",
            List.of(m("T2SOutOfStamingaCountdown"),m("T2SOutOfStaminaEffectResetTrigger")),List.of("T2SConditionBlock")),
        disease("enteritis","Enteritis","T2SEnteritis","T2SEnteritisPermanentProgression"),
        disease("encephalitis","Encephalitis","T2SEncephalitis","T2SEncephalitsPermanentProgression"),
        disease("hypothermia","Hypothermia","T2SHypothermia","T2SHypothermiaPermanentProgression"),
        disease("stomach_ulcer","Stomach ulcer","T2SStomachUcler","T2SStomachUclerPermanentProgression"),
        disease("heatstroke","Heatstroke","T2SStroke","T2SStrokePermanentProgression"),
        disease("brain_damage","Brain damage","T2SBrainDamage"),
        permanent("nyctophobia","Nyctophobia","T2SNyktophobia"),
        permanent("arachnophobia","Arachnophobia","T2SArachnophobia"),
        permanent("kinemortophobia","Kinemortophobia","T2SKinemortophobia"),
        permanent("crohns_disease","Crohn's disease","T2SCrohnsDisease"),
        permanent("sleeplessness","Sleeplessness","T2SSleeplessness"),
        stat("exhaustion","Exhaustion",AdaptationProfile.EFFECT,m("T2SExhaustionValue",0,16000,false)),
        stat("panting","Panting",AdaptationProfile.EFFECT,m("T2SPantingValue",1,1,false)),
        stat("stress","Stress",AdaptationProfile.EFFECT,m("T2SStressValue",0,100,false)),
        stat("encumbrance","Encumbrance",AdaptationProfile.PHYSICAL,m("T2SEncumberanceValue",100,0,false)),
        stat("weak_immunity","Weakened immunity",AdaptationProfile.EFFECT,m("T2SImmunityCount",110,0,false),m("T2SImmunityValue",1,0,false)),
        stat("poor_rest","Poor rest",AdaptationProfile.EFFECT,m("T2SRestResetValue",300,0,true)),
        stat("chill","Cold exposure (T2S)",AdaptationProfile.COLD,m("T2STemperatureCount",300,100,true),m("T2SBodyTemperatureValue",300,100,true)),
        stat("overheating","Overheating (T2S)",AdaptationProfile.FIRE,m("T2STemperatureCount",400,0,false),m("T2SBodyTemperatureValue",400,0,false))
    );
    private static final Map<String,Condition> BY_ID=new HashMap<>(),BY_KEY=new HashMap<>();
    static {for(Condition c:CONDITIONS){BY_ID.put(c.id,c);BY_KEY.put(c.key,c);}}
    private static final Map<String,String> CAUSES=Map.ofEntries(
        Map.entry("T2SEnteritisProceduresProcedure","enteritis"),Map.entry("T2SEncephalitisProceduresProcedure","encephalitis"),
        Map.entry("T2SHypothermiaProceduresProcedure","hypothermia"),Map.entry("T2SStomachUclerProceduresProcedure","stomach_ulcer"),
        Map.entry("T2SStrokeProceduresProcedure","heatstroke"),Map.entry("T2SBrainDamageProceduresProcedure","brain_damage"),
        Map.entry("T2SExhaustionProceduresProcedure","exhaustion"),Map.entry("T2SFluDiseaseEffectOnEffectActiveTickProcedure","flu"),
        Map.entry("T2SFeverDiseaseEffectOnEffectActiveTickProcedure","fever"),Map.entry("T2SRabiesDiseaseOnEffectActiveTickProcedure","rabies")
    );
    private static final class Schema {
        final Capability<Object> capability;final Map<String,Field> fields=new HashMap<>();final Method sync;
        @SuppressWarnings("unchecked") Schema()throws ReflectiveOperationException {
            Class<?> owner=Class.forName("net.mcreator.trytosurvivetwo.network.TrytosurvivetwoModVariables");
            capability=(Capability<Object>)owner.getField("PLAYER_VARIABLES_CAPABILITY").get(null);
            Class<?> vars=Class.forName(owner.getName()+"$PlayerVariables");
            for(Field f:vars.getFields())fields.put(f.getName(),f);
            for(Condition c:CONDITIONS){for(Metric metric:c.metrics)required(metric.field);for(String flag:c.flags)required(flag);}
            sync=vars.getMethod("syncPlayerVariables",Entity.class);
        }
        void required(String name)throws NoSuchFieldException {if(!fields.containsKey(name))throw new NoSuchFieldException(name);}
        double number(Object vars,String name){try{return fields.get(name).getDouble(vars);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        boolean flag(Object vars,String name){try{return fields.get(name).getBoolean(vars);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        boolean set(Object vars,String name,double value){if(!fields.containsKey(name))return false;try{Field f=fields.get(name);if(f.getDouble(vars)==value)return false;f.setDouble(vars,value);return true;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        boolean flag(Object vars,String name,boolean value){if(!fields.containsKey(name))return false;try{Field f=fields.get(name);if(f.getBoolean(vars)==value)return false;f.setBoolean(vars,value);return true;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    }
    private static final class Session {
        final Set<String> exposed=new HashSet<>();
        final Map<String,Integer> stages=new HashMap<>(),lastExposure=new HashMap<>();
        final Map<String,Double> before=new HashMap<>();boolean dirty;
    }
    private T2SCompatibility(){}
    public static synchronized boolean available(){
        if(!ModList.get().isLoaded(MOD))return false;
        if(!checked){checked=true;try{schema=new Schema();}catch(ReflectiveOperationException|LinkageError e){LogUtils.getLogger().warn("T2S compatibility disabled: unsupported player data schema",e);}}
        return schema!=null;
    }
    private static Object vars(Player p){return available()?p.getCapability(schema.capability).orElse(null):null;}
    /** Client-only callers pass keys already granted by the server; no client state grants server immunity. */
    public static void mirrorClient(Player player,Set<String> immuneKeys){
        if(!player.level().isClientSide()||immuneKeys.isEmpty())return;
        Object vars=vars(player);if(vars==null)return;
        for(Condition c:CONDITIONS)if(immuneKeys.contains(c.key))neutralize(player,vars,c);
        if(immuneKeys.contains(AdaptationProfile.HUNGER_KEY))schema.set(vars,"T2SFoodDelpetionValue",0);
    }
    public static List<Condition> conditions(){return available()?CONDITIONS:List.of();}
    public static String translationKey(String key){Condition c=BY_KEY.get(key);return c!=null?"condition.adaptationwheel.t2s."+c.id:"";}
    public static void addCatalogue(Map<String,CatalogueEntry> entries){for(Condition c:conditions())entries.putIfAbsent(c.key,new CatalogueEntry(c.key,
        c.key.startsWith("effect:")?DamageCategories.translationKey(c.key):translationKey(c.key),c.fallback+" [T2S]",c.profile));}
    public static String damageCause(){
        if(!ModList.get().isLoaded(MOD))return null;
        return WALKER.walk(frames->frames.map(StackWalker.StackFrame::getClassName).filter(name->name.startsWith(PROCEDURES))
            .map(name->CAUSES.get(name.substring(PROCEDURES.length()))).filter(Objects::nonNull).map(id->BY_ID.get(id).key).findFirst().orElse(null));
    }
    /** Called only by the optional coremod guard at a T2S disease procedure's entry. */
    public static boolean skipDiseaseProcedure(Entity entity,String id){
        if(!(entity instanceof ServerPlayer p)||!AdaptationEvents.active(p))return false;
        Condition c=BY_ID.get(id);if(c==null||!AdaptationEvents.state(p).isImmune(c.key))return false;
        Object vars=vars(p);if(vars==null)return false;
        SESSIONS.computeIfAbsent(p,ignored->new Session()).dirty|=neutralize(p,vars,c);return true;
    }
    private static void observe(ServerPlayer p,Object vars,Session session){
        Set<String> known=new HashSet<>();for(var entry:AdaptationEvents.state(p).snapshots())known.add(entry.key());
        for(Condition c:CONDITIONS){
            boolean present=false;
            for(Metric m:c.metrics)if(m.bad(schema.number(vars,m.field))>m.threshold){present=true;break;}
            for(String flag:c.flags)if(schema.flag(vars,flag)){present=true;break;}
            if(c.key.startsWith("effect:")){MobEffect effect=effect(c);present|=effect!=null&&p.hasEffect(effect);}
            if(present){if(session.exposed.add(c.key)||!known.contains(c.key))expose(p,session,c.key);}else session.exposed.remove(c.key);
        }
    }
    private static void expose(ServerPlayer p,Session session,String key){
        int last=session.lastExposure.getOrDefault(key,Integer.MIN_VALUE/2);
        if(p.tickCount-last<20)return;
        session.lastExposure.put(key,p.tickCount);AdaptationEvents.recordExposure(p,key,false);
    }
    /** Icon refreshes every tick are not new infections and must not grant +30% per refresh. */
    public static boolean effectAdded(ServerPlayer p,MobEffectEvent.Added event){
        Condition c=BY_KEY.get(NegativeEffects.key(event.getEffectInstance().getEffect()));
        if(c==null||!available())return false;
        Session session=SESSIONS.computeIfAbsent(p,ignored->new Session());
        boolean first=session.exposed.add(c.key);
        if(first||event.getOldEffectInstance()!=null&&event.getEffectInstance().getAmplifier()>event.getOldEffectInstance().getAmplifier())expose(p,session,c.key);
        return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void start(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.START||!(event.player instanceof ServerPlayer p)||!p.isAlive()||!AdaptationEvents.active(p))return;
        Object vars=vars(p);if(vars==null)return;
        Session s=SESSIONS.computeIfAbsent(p,ignored->new Session());observe(p,vars,s);
        for(Condition c:CONDITIONS){
            if(AdaptationEvents.state(p).isImmune(c.key))s.dirty|=neutralize(p,vars,c);
            for(Metric m:c.metrics)s.before.put(c.id+"/"+m.field,schema.number(vars,m.field));
        }
        sustainFood(p,vars,s);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void end(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!p.isAlive()||!AdaptationEvents.active(p)){SESSIONS.remove(p);return;}
        Object vars=vars(p);if(vars==null)return;
        Session s=SESSIONS.computeIfAbsent(p,ignored->new Session());observe(p,vars,s);
        var state=AdaptationEvents.state(p);
        for(Condition c:CONDITIONS){
            int stage=state.stage(c.key),old=s.stages.getOrDefault(c.key,stage);s.stages.put(c.key,stage);
            double factor=state.damageMultiplier(c.key);
            if(factor==0){s.dirty|=neutralize(p,vars,c);continue;}
            for(Metric m:c.metrics){
                double now=schema.number(vars,m.field),bad=m.bad(now);
                Double before=s.before.get(c.id+"/"+m.field);
                if(before!=null&&factor<1&&bad>m.bad(before))bad=m.bad(before)+(bad-m.bad(before))*factor;
                if(stage>old&&factor<1&&state.categoryEnabled(c.key)){
                    double previous=1-ru.pasha.adaptationwheel.core.AdaptationState.reductionPercent(old)/100.0;
                    if(previous>0)bad*=factor/previous;
                }
                // Preserve beneficial values below/above the healthy reference point.
                if(m.bad(now)>0)s.dirty|=schema.set(vars,m.field,m.value(bad));
            }
        }
        sustainFood(p,vars,s);
        if(s.dirty&&(p.tickCount%5==0)){try{schema.sync.invoke(vars,p);s.dirty=false;}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot synchronize T2S player data",e);}}
    }
    private static void sustainFood(ServerPlayer p,Object vars,Session s){
        if(AdaptationEvents.state(p).isImmune(AdaptationProfile.HUNGER_KEY)){
            s.dirty|=schema.set(vars,"T2SFoodDelpetionValue",0);NegativeEffects.sustainHunger(p);
        }
    }
    private static MobEffect effect(Condition c){return c.key.startsWith("effect:")?BuiltInRegistries.MOB_EFFECT.getOptional(new ResourceLocation(c.key.substring(7))).orElse(null):null;}
    private static boolean neutralize(Player p,Object vars,Condition c){
        boolean changed=false;
        for(Metric m:c.metrics)if(m.bad(schema.number(vars,m.field))>0)changed|=schema.set(vars,m.field,m.healthy);
        for(String flag:c.flags)changed|=schema.flag(vars,flag,false);
        MobEffect effect=effect(c);if(effect!=null&&p.hasEffect(effect)){p.removeEffect(effect);changed=true;}
        if(c.id.equals("rabies"))changed|=schema.flag(vars,"T2SRabiesBiteMark",false);
        if(c.id.equals("stomach_ulcer"))changed|=schema.flag(vars,"T2SStomachUclerDisableRegeneration",false);
        if(c.id.equals("out_of_stamina")){
            double maximum=schema.number(vars,"T2SConditionMaximumValue");
            if(schema.number(vars,"T2SConditionValue")<maximum)changed|=schema.set(vars,"T2SConditionValue",Math.max(1,maximum));
        }
        return changed;
    }
}
