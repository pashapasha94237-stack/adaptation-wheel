package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import static ru.pasha.multimodqa.MultiModQa.*;

/** Invokes actual installed boss callbacks; never synthesizes their damage. */
final class BossPhaseQa {
 static void field(Object obj,String name,Object value)throws Exception{
  for(Class<?> c=obj.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static Object invoke(Object obj,String name,Class<?>[] types,Object...args)throws Exception{
  for(Class<?> c=obj.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(obj,args);}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException(name);
 }
 interface Attack {void run()throws Exception;}
 static void ability(ServerPlayer p,String label,String key,Attack attack)throws Exception{
  reset(p);float hp=health(p);attack.run();wheelTick(p);float loss=hp-health(p);
  check(p.isAlive(),label+" killed the QA fixture before attribution could be checked");
  check(loss>0,label+" native baseline removed no HP");
  check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),label+" failed exact attribution "+key+"; actual="+state(p).getClass().getMethod("pending").invoke(state(p)));
  learn(p,key,4);p.removeAllEffects();p.invulnerableTime=0;p.setDeltaMovement(Vec3.ZERO);hp=health(p);attack.run();
  check(health(p)==hp,label+" bypassed stage 4");
  controlDisable(p,key);p.invulnerableTime=0;hp=health(p);attack.run();check(health(p)<hp,label+" disable did not restore native HP loss");
  LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: {} native HP loss {} -> stage 4 no HP loss -> disabled HP loss",label,loss);reset(p);
 }
 static void run(ServerPlayer p)throws Exception{
  java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> cap=e->{if(e.getEntity()==p)e.setAmount(Math.min(e.getAmount(),2F));};
  boolean firstAid=ModList.get().isLoaded("firstaid");
  if(firstAid){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH,false,cap);LogUtils.getLogger().info("MULTIMOD_QA BOSS_FIXTURE: First Aid incoming ordinary damage capped at 2 HP to keep native critical body parts alive; source/callback/side effects unchanged");}
  Vec3 original=p.position();
  if(!p.level().canSeeSky(p.blockPosition())){
   int surface=p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,p.getBlockX(),p.getBlockZ());
   p.teleportTo(p.getX(),surface+2,p.getZ());
  }
  check(p.level().canSeeSky(p.blockPosition()),"Boss fixture must expose the player to sky for native Ur-Ghast tantrum");
  try{runPhases(p);}finally{p.teleportTo(original.x,original.y,original.z);if(firstAid)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cap);}
 }
 static void runPhases(ServerPlayer p)throws Exception{
  if(group.has("guardianOnly")){guardian(p);return;}
  if(ModList.get().isLoaded("twilightforest")){
   LivingEntity queen=spawn(p,"twilightforest:snow_queen",3);
   try{
    String key=family("SnowQueen","doBreathAttack");
    ability(p,"Snow Queen breath",key,()->invoke(queen,"doBreathAttack",new Class<?>[]{Entity.class},p));
    invoke(queen,"doBreathAttack",new Class<?>[]{Entity.class},p);wheelTick(p);learn(p,"mob:twilightforest:snow_queen",5);controlDisable(p,key);float hp=health(p);p.invulnerableTime=0;invoke(queen,"doBreathAttack",new Class<?>[]{Entity.class},p);check(health(p)==hp,"Snow Queen breath bypassed boss existence");reset(p);
    LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Snow Queen breath retains boss ownership and existence immunity");
   }finally{queen.discard();}
   hydra(p);
   LivingEntity ghast=spawn(p,"twilightforest:ur_ghast",0);ghast.setPos(p.position().add(0,8,0));
   try{
    String key=family("UrGhast","doTantrumDamageEffects");
    ability(p,"Ur-Ghast tantrum",key,()->invoke(ghast,"doTantrumDamageEffects",new Class<?>[]{}));
    invoke(ghast,"doTantrumDamageEffects",new Class<?>[]{});wheelTick(p);learn(p,"mob:twilightforest:ur_ghast",5);controlDisable(p,key);float hp=health(p);p.invulnerableTime=0;invoke(ghast,"doTantrumDamageEffects",new Class<?>[]{});check(health(p)==hp,"Ur-Ghast tantrum bypassed boss existence");reset(p);
    LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Ur-Ghast tantrum retains boss ownership and existence immunity");
   }finally{ghast.discard();}
  }
  if(ModList.get().isLoaded("alexscaves")){
   LivingEntity kaiju=spawn(p,"alexscaves:tremorzilla",4);
   try{for(boolean beam:new boolean[]{false,true})ability(p,"Tremorzilla "+(beam?"radioactive beam":"stomp"),family("TremorzillaEntity","hurtEntitiesAround"),()->invoke(kaiju,"hurtEntitiesAround",new Class<?>[]{Vec3.class,float.class,float.class,float.class,boolean.class,boolean.class,boolean.class},p.position(),3F,5F,.5F,beam,true,false));}finally{kaiju.discard();}
  }
  if(ModList.get().isLoaded("draconicevolution"))guardian(p);
  if(ModList.get().isLoaded("aoa3"))smash(p);
 }
 static void smash(ServerPlayer p)throws Exception{
  LivingEntity boss=spawn(p,"aoa3:smash",.6);boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(3);
  Class<?> slamType=Class.forName("net.tslat.aoa3.content.entity.brain.task.custom.GroundSlamAttack");Object slam=slamType.getConstructor(int.class).newInstance(0);
  Class<?> chargeType=Class.forName("net.tslat.aoa3.content.entity.brain.task.custom.ChargeAttack");Object charge=chargeType.getConstructor(int.class).newInstance(0);field(charge,"chargeVelocity",new Vec3(.1,0,0));field(charge,"delayFinishedAt",0L);
  String key="mob:aoa3:smash",attack=family("AoAMonster","m_7327_");
  try{
   reset(p);p.setOnGround(true);float hp=health(p);invoke(slam,"doSlam",new Class<?>[]{LivingEntity.class},boss);wheelTick(p);check(health(p)<hp,"AoA Smash native slam removed no HP");
   float baseline=hp-health(p);check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(attack),"AoA native slam did not learn its precise physical attack key");
   learn(p,key,4);int parallelStage=stage(p,attack);
   // Every key has its own progress and acceleration. Completing a creature's
   // species entry must not assume its precise ability completed incidentally.
   learn(p,attack,4);hp=health(p);p.invulnerableTime=0;invoke(slam,"doSlam",new Class<?>[]{LivingEntity.class},boss);check(health(p)==hp,"AoA Smash slam bypassed its own completed physical attack adaptation");
   controlDisable(p,attack);p.invulnerableTime=0;invoke(slam,"doSlam",new Class<?>[]{LivingEntity.class},boss);check(health(p)<hp,"Disabling AoA physical attack protection did not restore actual slam HP loss");
   state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),attack,true);hp=health(p);
   p.setDeltaMovement(Vec3.ZERO);p.invulnerableTime=20;invoke(charge,"tick",new Class<?>[]{net.minecraft.world.entity.PathfinderMob.class},boss);check(health(p)==hp&&p.getDeltaMovement().lengthSqr()==0,"AoA Smash charge bypassed full physical adaptation with force");
   controlDisable(p,key);p.setDeltaMovement(Vec3.ZERO);invoke(charge,"tick",new Class<?>[]{net.minecraft.world.entity.PathfinderMob.class},boss);check(p.getDeltaMovement().lengthSqr()>0,"Disabled AoA charge force remained blocked");
   state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,true);learn(p,key,5);controlDisable(p,attack);hp=health(p);p.setDeltaMovement(Vec3.ZERO);p.invulnerableTime=0;
   invoke(charge,"tick",new Class<?>[]{net.minecraft.world.entity.PathfinderMob.class},boss);check(health(p)==hp&&p.getDeltaMovement().lengthSqr()==0,"AoA Smash charge bypassed species existence immunity with own physical attack disabled");
   controlDisable(p,key);p.setDeltaMovement(Vec3.ZERO);p.invulnerableTime=0;invoke(charge,"tick",new Class<?>[]{net.minecraft.world.entity.PathfinderMob.class},boss);check(health(p)<hp&&p.getDeltaMovement().lengthSqr()>0,"Disabled AoA existence/attack protections did not restore actual charge HP loss and force");
   LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: AoA Smash actual slam HP loss {} -> precise ability stage 4 blocks HP -> disabled restores HP; species stage 4 blocks charge force; existence alone blocks HP and force with precise ability disabled; disabling both restores HP/force; parallel ability was stage {} when species first reached stage 4",baseline,parallelStage);
  }finally{boss.discard();reset(p);}
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 static void hydra(ServerPlayer p)throws Exception{
  LivingEntity boss=spawn(p,"twilightforest:hydra",5);
  Object container=((Object[])boss.getClass().getField("hc").get(boss))[0];
  Entity head=(Entity)container.getClass().getField("headEntity").get(container);head.setPos(p.position());
  Class<?> states=Class.forName("twilightforest.entity.boss.HydraHeadContainer$State");
  invoke(head,"setState",new Class<?>[]{states},Enum.valueOf((Class)states,"BITING"));
  String key=family("HydraHeadContainer","executeAttacks");
  try{
   ability(p,"Hydra bite",key,()->invoke(container,"executeAttacks",new Class<?>[]{}));
   reset(p);learn(p,key,4);shield(p);int wear=p.getOffhandItem().getDamageValue();float hp=health(p);
   invoke(container,"executeAttacks",new Class<?>[]{});
   check(health(p)==hp&&p.getOffhandItem().getDamageValue()==wear&&!p.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.SHIELD),"Hydra bite bypassed protection with shield damage/cooldown");
   controlDisable(p,key);shield(p);invoke(container,"executeAttacks",new Class<?>[]{});check(p.getOffhandItem().getDamageValue()>0&&p.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.SHIELD),"Disabled Hydra protection did not restore shield wear/cooldown");
   p.stopUsingItem();p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);reset(p);invoke(container,"executeAttacks",new Class<?>[]{});wheelTick(p);learn(p,"mob:twilightforest:hydra",5);controlDisable(p,key);shield(p);hp=health(p);invoke(container,"executeAttacks",new Class<?>[]{});check(health(p)==hp&&p.getOffhandItem().getDamageValue()==0&&!p.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.SHIELD),"Hydra boss existence left shield wear/cooldown");
   LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Hydra immune bite preserves shield and cooldown");
  }finally{p.stopUsingItem();p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);boss.discard();reset(p);}
 }
 static void shield(ServerPlayer p)throws Exception{
  p.stopUsingItem();
  p.getCooldowns().removeCooldown(net.minecraft.world.item.Items.SHIELD);
  p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
  Field f=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(LivingEntity.class,"f_20936_");f.setInt(p,f.getInt(p)-10);check(p.isBlocking(),"Native shield fixture is not blocking");
 }
 static void guardian(ServerPlayer p)throws Exception{
  LivingEntity boss=spawn(p,"draconicevolution:draconic_guardian",3);
  // Clear the complete beam path including the guardian's offset head.
  for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=0;y<8;y++)p.serverLevel().setBlock(p.blockPosition().offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  Class<?> type=Class.forName("com.brandon3055.draconicevolution.entity.guardian.control.LaserBeamPhase");
  Object phase=type.getConstructor(boss.getClass()).newInstance(boss);
  String key=family("LaserBeamPhase","serverTick");
  Attack primary=()->{invoke(phase,"initPhase",new Class<?>[]{});field(phase,"attackTarget",p);field(phase,"chargeTime",60);field(phase,"chargedTime",90);invoke(phase,"serverTick",new Class<?>[]{});};
  Attack secondary=()->{invoke(phase,"initPhase",new Class<?>[]{});field(phase,"attackTarget",p);field(phase,"chargeTime",60);field(phase,"chargedTime",151);invoke(phase,"serverTick",new Class<?>[]{});};
  try{
   String contact=family("DraconicGuardianEntity","collideWithEntities");
   reset(p);p.setDeltaMovement(Vec3.ZERO);invoke(boss,"collideWithEntities",new Class<?>[]{List.class},List.of(p));double force=p.getDeltaMovement().length();check(force>0,"Guardian native collision force absent");
   learn(p,contact,1);p.setDeltaMovement(Vec3.ZERO);invoke(boss,"collideWithEntities",new Class<?>[]{List.class},List.of(p));check(Math.abs(p.getDeltaMovement().length()-force*.8)<1E-5,"Guardian partial collision force wrong");
   learn(p,contact,4);p.setDeltaMovement(Vec3.ZERO);invoke(boss,"collideWithEntities",new Class<?>[]{List.class},List.of(p));check(p.getDeltaMovement().lengthSqr()==0,"Guardian collision bypassed full adaptation with force");
   controlDisable(p,contact);p.setDeltaMovement(Vec3.ZERO);invoke(boss,"collideWithEntities",new Class<?>[]{List.class},List.of(p));check(p.getDeltaMovement().length()>0,"Disabled guardian collision still blocked");
   LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Guardian native collision force baseline -> 20% reduction -> immune -> disabled");
   ability(p,"Guardian primary laser",key,primary);
   reset(p);learn(p,key,4);float hp=health(p);secondary.run();check(p.isAlive()&&health(p)==hp,"Guardian secondary laser bypassed own stage 4");
   LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Guardian secondary direct-health laser blocked by own stage 4");
   reset(p);primary.run();wheelTick(p);learn(p,"mob:draconicevolution:draconic_guardian",5);controlDisable(p,key);hp=health(p);secondary.run();
   check(p.isAlive()&&health(p)==hp,"Guardian secondary laser bypassed boss existence adaptation");
   LogUtils.getLogger().info("MULTIMOD_QA BOSS_PHASE PASS: Guardian secondary direct-health laser blocked by boss existence alone");
  }finally{boss.discard();reset(p);}
 }
}
