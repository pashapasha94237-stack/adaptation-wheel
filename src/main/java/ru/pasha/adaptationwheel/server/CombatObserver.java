package ru.pasha.adaptationwheel.server;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;

/** Native observations, boss/sequence memory and measured protection checks. */
public final class CombatObserver {
    private static final Gson JSON=new Gson();
    private static final Map<ServerPlayer,CombatKnowledge> KNOWLEDGE=new WeakHashMap<>();
    private static final Map<ServerPlayer,HealthFrame> FRAMES=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> WARNED=new WeakHashMap<>();
    private record ActorIdentity(UUID uuid,net.minecraft.resources.ResourceLocation dimension,String key){}
    private static final Map<Entity,ActorIdentity> ACTOR_IDS=new WeakHashMap<>();
    private static final class Contact {
        final String key;final UUID actor,direct;final long tick;final DamageSource source;
        final Set<String> effects=new TreeSet<>();
        Contact(String key,DamageSource source,long tick){this.key=key;this.source=source;this.tick=tick;actor=source.getEntity().getUUID();direct=source.getDirectEntity()==null?null:source.getDirectEntity().getUUID();}
    }
    private static final Map<ServerPlayer,Deque<Contact>> CONTACTS=new WeakHashMap<>();
    private static final class HealthFrame {
        final long tick;final float health,absorption;float lowestHealth;boolean uncertain;final Set<String> immune=new HashSet<>();
        HealthFrame(ServerPlayer p){tick=p.serverLevel().getGameTime();health=lowestHealth=p.getHealth();absorption=p.getAbsorptionAmount();}
        void sample(ServerPlayer p){lowestHealth=Math.min(lowestHealth,p.getHealth());}
    }
    private CombatObserver(){}
    public static CombatKnowledge knowledge(ServerPlayer p){return KNOWLEDGE.computeIfAbsent(p,x->{
        var tag=x.getPersistentData().getCompound(AdaptationWheel.MOD_ID);var rows=new ArrayList<CombatKnowledge.Saved>();var flow=new ArrayList<CombatKnowledge.Transition>();
        var entries=tag.getList("CombatKnowledge",Tag.TAG_STRING);for(int i=0;i<entries.size();i++){String json=entries.getString(i);try{var row=JSON.fromJson(json,CombatKnowledge.Saved.class);if(row!=null&&row.key()!=null)rows.add(row);}catch(RuntimeException ignored){}}
        var transitions=tag.getList("CombatTransitions",Tag.TAG_COMPOUND);for(int i=0;i<transitions.size();i++){var row=transitions.getCompound(i);flow.add(new CombatKnowledge.Transition(row.getString("Prefix"),row.getString("Next"),row.getInt("Count")));}
        return CombatKnowledge.restore(rows,flow);
    });}
    private static String id(Entity entity){return entity==null?"":Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()),"");}
    private static String phase(Entity entity){
        if(entity instanceof EnderDragon dragon)return "dragon:"+dragon.getPhaseManager().getCurrentPhase().getPhase().getId();
        if(entity instanceof WitherBoss wither)return wither.isPowered()?"wither:armored":"wither:normal";
        if(entity instanceof LivingEntity living&&living.getMaxHealth()>=100)return "health-band:"+Math.max(1,Math.min(4,(int)Math.ceil(living.getHealth()/living.getMaxHealth()*4)));
        return "";
    }
    private static CombatKnowledge.Fact fact(ServerPlayer p,String key,DamageSource source,CombatKnowledge.Kind kind,Entity actor,String effect){
        Entity owner=actor!=null?actor:source==null?null:source.getEntity(),direct=source==null?null:source.getDirectEntity();
        double distance=owner==null?0:owner.distanceTo(p);String range=owner==null?"unknown":distance<=4?"near":distance<=16?"medium":"far";
        // An already active poison may belong to another encounter. Only a source-attributed
        // application is evidence that this particular attack carries that effect.
        String effects=effect.isBlank()?attributedEffects(p,key,source):effect;
        String damage=source==null?"":source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse(source.getMsgId());
        String actorId=actorId(owner);
        return new CombatKnowledge.Fact(key,actorId,id(owner),id(direct),damage,effects,range,phase(owner),kind,p.serverLevel().getGameTime());
    }
    private static String actorId(Entity owner){
        if(owner==null)return "";var dimension=owner.level().dimension().location();ActorIdentity cached=ACTOR_IDS.get(owner);
        if(cached!=null&&cached.uuid().equals(owner.getUUID())&&cached.dimension().equals(dimension))return cached.key();
        String key=UUID.nameUUIDFromBytes((owner.getUUID()+"|"+dimension).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if(ACTOR_IDS.size()>=512)ACTOR_IDS.clear();ACTOR_IDS.put(owner,new ActorIdentity(owner.getUUID(),dimension,key));return key;
    }
    private static String key(ServerPlayer p,DamageSource source){var precise=ModAttackCompatibility.exposure(p,source);if(precise==null)precise=ObservedAbilities.exposure(source);if(precise!=null)return precise.key();var analyzed=DamageCategories.analyze(source,p);if(analyzed.isEmpty())return "";return analyzed.stream().filter(e->!e.key().startsWith("mob:")).findFirst().orElse(analyzed.get(0)).key();}
    public static void describe(ServerPlayer p,String key,DamageSource source){
        if(!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled())return;
        if(source==null&&knowledge(p).summary(key).variants()>0)return;
        knowledge(p).describe(fact(p,key,source,key.startsWith("effect:")?CombatKnowledge.Kind.DEBUFF:source!=null&&source.is(DamageTypeTags.IS_EXPLOSION)?CombatKnowledge.Kind.EXPLOSION:CombatKnowledge.Kind.IMPACT,null,""));
    }
    /** Exact committed native loss, measured before stage-completion healing can hide a leak. */
    public static void directHealth(ServerPlayer p,AutonomousHealthGuard.Site site,double loss,boolean expectedImmune){
        if(!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled())return;
        var data=knowledge(p);
        data.describe(new CombatKnowledge.Fact(site.key(),"",site.mod()+":"+site.owner(),site.method(),"native:health_write","","unknown","",CombatKnowledge.Kind.IMPACT,p.serverLevel().getGameTime()));
        if(expectedImmune)data.protection(site.key(),loss>.001,loss);
    }
    public static void start(ServerPlayer p){
        HealthFrame old=FRAMES.get(p);if(old!=null&&old.tick!=p.serverLevel().getGameTime())finish(p);
        if(!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled()){FRAMES.remove(p);CONTACTS.remove(p);}
        else if(p.serverLevel().getGameTime()%20==0)knowledge(p).expire(p.serverLevel().getGameTime());
    }
    /** Runs before wheel completion healing, so a healing animation cannot hide a leak. */
    public static void finish(ServerPlayer p){
        HealthFrame f=FRAMES.remove(p);if(f==null||f.immune.isEmpty()||f.uncertain||!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled())return;
        f.sample(p);double loss=Math.max(0,f.health-f.lowestHealth);
        // Expiring/removed absorption is not proof of an attack bypass. Mixed immune
        // contacts with HP loss cannot identify which ability caused it either.
        if(loss<=.05&&p.getAbsorptionAmount()+.05<f.absorption||loss>.05&&f.immune.size()>1)return;
        for(String key:f.immune)knowledge(p).protection(key,loss>.05,loss);
    }
    /** Capture damage before native healing can make the end-of-tick HP look unchanged. */
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void healing(LivingHealEvent event){
        if(event.getEntity() instanceof ServerPlayer p){HealthFrame f=FRAMES.get(p);if(f!=null)f.sample(p);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void contact(LivingAttackEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled()||event.getAmount()<=0)return;
        DamageSource source=ModAttackCompatibility.impactSource(p,event.getSource());HealthFrame f=FRAMES.computeIfAbsent(p,HealthFrame::new);
        f.sample(p);
        if(DamageCategories.excluded(source.typeHolder())){f.uncertain=true;return;}
        String key=key(p,source);if(key.isBlank())return;
        if(source.getEntity()!=null){contacts(p).addLast(new Contact(key,source,p.serverLevel().getGameTime()));}
        if(AdaptationEvents.expectedMultiplier(p,source)==0)f.immune.add(key);else f.uncertain=true;
        var fact=fact(p,key,source,source.is(DamageTypeTags.IS_EXPLOSION)?CombatKnowledge.Kind.EXPLOSION:CombatKnowledge.Kind.IMPACT,null,"");
        knowledge(p).describe(fact);String prediction=knowledge(p).event(fact);warn(p,prediction);
    }
    private static void warn(ServerPlayer p,String prediction){
        long now=p.serverLevel().getGameTime();if(prediction.isEmpty()||now-WARNED.getOrDefault(p,-10000L)<100)return;
        WARNED.put(p,now);int colon=prediction.indexOf(':');String key=colon<0?prediction:prediction.substring(colon+1);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("analysis.adaptationwheel.prediction",DamageCategories.displayName(key)),true);
    }
    private static void observed(ServerPlayer p,Entity owner,String key,CombatKnowledge.Kind kind,String effect){
        if(!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled())return;
        var f=fact(p,key,null,kind,owner,effect);knowledge(p).describe(f);warn(p,knowledge(p).event(f));
    }
    @SubscribeEvent public static void shot(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Projectile projectile)||!(projectile.getOwner() instanceof LivingEntity owner))return;
        String key="attack:projectile/"+id(projectile).replace(':','/');
        for(var p:event.getLevel().getEntitiesOfClass(ServerPlayer.class,projectile.getBoundingBox().inflate(48)))if(p!=owner&&p.hasLineOfSight(projectile))observed(p,owner,key,CombatKnowledge.Kind.PROJECTILE,"");
    }
    @SubscribeEvent public static void windup(LivingEntityUseItemEvent.Start event){
        var owner=event.getEntity();if(owner.level().isClientSide()||event.getDuration()<5||!Set.of(net.minecraft.world.item.UseAnim.BOW,net.minecraft.world.item.UseAnim.SPEAR,net.minecraft.world.item.UseAnim.CROSSBOW).contains(event.getItem().getUseAnimation()))return;
        for(var p:owner.level().getEntitiesOfClass(ServerPlayer.class,owner.getBoundingBox().inflate(32)))if(p!=owner&&p.hasLineOfSight(owner))observed(p,owner,"mob:"+id(owner),CombatKnowledge.Kind.WINDUP,"");
    }
    @SubscribeEvent public static void effect(MobEffectEvent.Added event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!DamageCategories.isHarmful(event.getEffectInstance().getEffect()))return;
        Entity direct=event.getEffectSource(),owner=direct;if(owner instanceof Projectile projectile)owner=projectile.getOwner();
        String key=NegativeEffects.key(event.getEffectInstance().getEffect());observed(p,owner,key,CombatKnowledge.Kind.DEBUFF,key.substring(7));
        if(owner==null||!AdaptationEvents.active(p)||!AdaptationTimings.analysisEnabled())return;
        List<Contact> matching=new ArrayList<>();Set<String> keys=new HashSet<>();
        for(Contact contact:contacts(p))if(owner.getUUID().equals(contact.actor)
                &&(!(direct instanceof Projectile)||direct.getUUID().equals(contact.direct))){matching.add(contact);keys.add(contact.key);}
        // Two overlapping abilities from the same caster are ambiguous unless the
        // actual projectile identifies the cause. Never rewrite an arbitrary last hit.
        if(keys.size()!=1)return;
        for(Contact contact:matching)if(contact.effects.size()<6)contact.effects.add(key.substring(7));
        Contact contact=matching.get(matching.size()-1);describe(p,contact.key,contact.source);
        SmartAttackAnalyzer.sideEffect(p,contact.key,DamageCategories.effectProfile(event.getEffectInstance().getEffect()));
    }
    private static Deque<Contact> contacts(ServerPlayer p){
        Deque<Contact> contacts=CONTACTS.computeIfAbsent(p,ignored->new ArrayDeque<>());long now=p.serverLevel().getGameTime();
        contacts.removeIf(c->now<c.tick||now-c.tick>5||c.source.getEntity().level()!=p.level());return contacts;
    }
    private static String attributedEffects(ServerPlayer p,String key,DamageSource source){
        var effects=new TreeSet<String>();for(Contact c:contacts(p))if(c.key.equals(key)
                &&(source==null||source.getEntity()!=null&&source.getEntity().getUUID().equals(c.actor))
                &&(source==null||source.getDirectEntity()==null||source.getDirectEntity().getUUID().equals(c.direct)))effects.addAll(c.effects);
        return effects.stream().limit(6).reduce((a,b)->a+","+b).orElse("");
    }
    public static AdaptationProfile attributedProfile(ServerPlayer p,String key,DamageSource source){
        String effects=attributedEffects(p,key,source);if(effects.isEmpty())return null;
        AdaptationProfile profile=null;
        for(String id:effects.split(",")){var effect=ForgeRegistries.MOB_EFFECTS.getValue(new net.minecraft.resources.ResourceLocation(id));if(effect==null)continue;
            var next=DamageCategories.effectProfile(effect);if(profile!=null&&profile!=next)return AdaptationProfile.EFFECT;profile=next;}
        return profile;
    }
    public static void write(ServerPlayer p,CompoundTag tag){
        ListTag rows=new ListTag();for(var row:knowledge(p).save())rows.add(StringTag.valueOf(JSON.toJson(row)));tag.put("CombatKnowledge",rows);
        ListTag transitions=new ListTag();for(var row:knowledge(p).transitions()){var t=new CompoundTag();t.putString("Prefix",row.prefix());t.putString("Next",row.next());t.putInt("Count",row.count());transitions.add(t);}tag.put("CombatTransitions",transitions);
    }
    public static void copy(ServerPlayer old,ServerPlayer replacement){var k=knowledge(old);KNOWLEDGE.put(replacement,CombatKnowledge.restore(k.save(),k.transitions()));forget(old);}
    public static void forget(ServerPlayer p){KNOWLEDGE.remove(p);FRAMES.remove(p);WARNED.remove(p);CONTACTS.remove(p);}
    public static void clearAll(){KNOWLEDGE.clear();FRAMES.clear();WARNED.clear();CONTACTS.clear();ACTOR_IDS.clear();}
}
