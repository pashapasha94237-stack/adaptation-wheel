package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Causal observations and measured health, driven through real native events. */
final class LocalAnalyzerQa {
 static boolean injectLeak;
 private LocalAnalyzerQa(){}
 static Zombie caster(ServerPlayer p,double offset){
  var caster=new Zombie(p.serverLevel());caster.setNoAi(true);caster.setInvulnerable(true);
  caster.moveTo(p.getX()+offset,p.getY(),p.getZ());p.serverLevel().addFreshEntity(caster);return caster;
 }
 static Object insight(ServerPlayer p,String key)throws Exception{return getter(info(p,key),"insight");}
 static DamageSource beam(Zombie owner,String name)throws Exception{
  return new DamageSource(custom("multimodqa:"+name,"multimodqa:"+(name.equals("manual_light_beam")?"light_beam":name)),owner);
 }
 static void run(ServerPlayer p)throws Exception{
  sameTickDebuff(p);mixedSameTickDebuffs(p);ambientDebuff(p);ambiguousDebuff(p);projectileDebuff(p);absorptionExpiry(p);healedLeak(p);disabledVerification(p);reset(p);
 }
 static void sameTickDebuff(ServerPlayer p)throws Exception{
  reset(p);var attacker=caster(p,3);String key=CLIENT_KEY;
  try{
   DamageSource source=beam(attacker,"unknown_light_beam");check(hit(p,source,4)>0,"Same-tick weak beam failed to remove actual HP");
   p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),attacker);
   wheelTick(p);check(getter(required(p,key),"profile").toString().equals("POISON"),"Poison applied before receipt processing was lost during first beam classification");
   check(getter(insight(p,key),"effects").toString().contains("minecraft:poison"),"Same-tick beam lost its actually attributed poison evidence");
   p.removeAllEffects();learn(p,key,4);float hp=health(p);hit(p,source,4);check(health(p)==hp,"Same-tick causally classified beam failed to protect actual HP after full adaptation");
   controlDisable(p,key);check(hit(p,source,4)>0,"Disabling same-tick causally classified beam failed to restore actual HP damage");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_SAME_TICK PASS: real beam plus attributed poison before receipt processing learns POISON with effect evidence; own ability stage-four HP immunity and disable verified");
  }finally{attacker.discard();reset(p);}
 }
 static void mixedSameTickDebuffs(ServerPlayer p)throws Exception{
  reset(p);var attacker=caster(p,3);String key=CLIENT_KEY;
  try{
   DamageSource source=beam(attacker,"unknown_light_beam");check(hit(p,source,4)>0,"Mixed-effect native beam failed to remove actual HP");
   p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),attacker);
   p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200,0),attacker);
   wheelTick(p);check(getter(required(p,key),"profile").toString().equals("EFFECT"),"Mixed attributed effects failed to replace weak light-name guess with general effect category");
   String effects=getter(insight(p,key),"effects").toString();check(effects.contains("minecraft:poison")&&effects.contains("minecraft:slowness"),"Mixed-effect beam lost actual poison/slowness evidence");
   check(p.hasEffect(MobEffects.POISON)&&p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Mixed-effect fixture lost real negative effects before adaptation");
   p.removeAllEffects();learn(p,key,4);float hp=health(p);hit(p,source,4);check(health(p)==hp,"Mixed-effect categorized ability failed to prevent actual HP loss after full adaptation");
   controlDisable(p,key);check(hit(p,source,4)>0,"Disabled mixed-effect categorized ability still blocked actual HP damage");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_MIXED_EFFECT PASS: real same-tick poison plus slowness classified EFFECT with both causal effect IDs; own ability stage-four HP protection and disable verified");
  }finally{attacker.discard();reset(p);}
 }
 static void ambientDebuff(ServerPlayer p)throws Exception{
  reset(p);var poisoner=caster(p,3);var attacker=caster(p,-3);String key=CLIENT_KEY;
  try{
   p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),poisoner);
   DamageSource source=beam(attacker,"unknown_light_beam");float baseline=hit(p,source,4);wheelTick(p);
   check(baseline>0,"Local analyzer causal fixture failed to remove real HP");
   check(getter(required(p,key),"profile").toString().equals("LIGHT"),"Unrelated active poison reclassified another caster's light beam");
   check(!getter(insight(p,key),"effects").toString().contains("minecraft:poison"),"Ambient poison was falsely attributed to another caster's beam");
   p.removeAllEffects();learn(p,key,4);float before=health(p);hit(p,source,4);
   check(health(p)==before,"Causally observed light beam failed to protect actual HP after full adaptation");
   controlDisable(p,key);check(hit(p,source,4)>0,"Disabled causally observed beam still prevented actual HP loss");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_CAUSAL PASS: preexisting poison from another actor excluded from beam evidence; real stage-four HP immunity and disable verified");
  }finally{poisoner.discard();attacker.discard();reset(p);}
 }
 static void ambiguousDebuff(ServerPlayer p)throws Exception{
  reset(p);var attacker=caster(p,3);String first=CLIENT_KEY,second="damage:multimodqa:manual_light_beam";
  try{
   check(hit(p,beam(attacker,"unknown_light_beam"),4)>0,"First ambiguous native beam did not remove HP");wheelTick(p);
   check(hit(p,beam(attacker,"manual_light_beam"),4)>0,"Second ambiguous native beam did not remove HP");wheelTick(p);
   Object a=required(p,first),b=required(p,second);double firstWork=number(a,"work"),secondWork=number(b,"work");int firstDuration=duration(a),secondDuration=duration(b);
   p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),attacker);
   a=required(p,first);b=required(p,second);
   check(getter(a,"profile").toString().equals("LIGHT")&&getter(b,"profile").toString().equals("LIGHT"),"Ambiguous same-caster poison arbitrarily rewrote the last of two different attacks");
   check(number(a,"work")==firstWork&&number(b,"work")==secondWork&&duration(a)==firstDuration&&duration(b)==secondDuration,"Ambiguous side-effect observation changed running adaptation clocks");
   check(p.hasEffect(MobEffects.POISON),"Ambiguous-cause handling removed the real negative effect without adaptation");
   wheelTick(p);check(snapshot(p,"effect:minecraft:poison")!=null,"Ambiguous causality prevented poison's own real adaptation");
   learn(p,"effect:minecraft:poison",4);call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
   check(!p.hasEffect(MobEffects.POISON)&&!p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),attacker),"Ambiguous cause bypassed full standalone poison protection");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_AMBIGUITY PASS: overlapping same-caster attacks do not arbitrarily inherit poison; active clocks frozen; poison separately learned and actually purged/blocked");
  }finally{attacker.discard();reset(p);}
 }
 static void projectileDebuff(ServerPlayer p)throws Exception{
  reset(p);var attacker=caster(p,3);var arrow=new Arrow(p.serverLevel(),attacker);var snowball=new Snowball(p.serverLevel(),attacker);
  String first=CLIENT_KEY,second="damage:multimodqa:manual_light_beam";
  try{
   var a=new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"),arrow,attacker);
   var b=new DamageSource(custom("multimodqa:manual_light_beam","multimodqa:light_beam"),snowball,attacker);
   check(hit(p,a,4)>0,"First native direct-projectile fixture did not remove HP");wheelTick(p);
   check(hit(p,b,4)>0,"Second native direct-projectile fixture did not remove HP");wheelTick(p);
   Object before=required(p,first);int frozen=duration(before);double work=number(before,"work");
   // Since 4.16.2 a named light beam fired as a projectile keeps LIGHT; what matters is that the poison changes neither.
   String firstProfile=getter(before,"profile").toString(),secondProfile=getter(required(p,second),"profile").toString();
   p.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),arrow);
   check(getter(insight(p,first),"effects").toString().contains("minecraft:poison"),"Exact Arrow effect source failed to disambiguate its ability from another same-caster projectile");
   check(!getter(insight(p,second),"effects").toString().contains("minecraft:poison"),"Arrow-sourced poison contaminated same-caster Snowball ability");
   check(!firstProfile.equals("POISON")&&!firstProfile.equals("EFFECT")&&getter(required(p,first),"profile").toString().equals(firstProfile)
           &&getter(required(p,second),"profile").toString().equals(secondProfile),"Attributed effect overrode native projectile evidence: "+firstProfile+" -> "+getter(required(p,first),"profile"));
   check(duration(required(p,first))==frozen&&number(required(p,first),"work")==work,"Projectile side-effect evidence changed running clock");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_PROJECTILE_CAUSE PASS: exact Arrow poison source disambiguates overlapping same-caster abilities, Snowball stays clean; native projectile category and running clock retained");
  }finally{arrow.discard();snowball.discard();attacker.discard();reset(p);}
 }
 static void absorptionExpiry(ServerPlayer p)throws Exception{
  reset(p);String key="damage:minecraft:fall";hit(p,p.damageSources().fall(),4);wheelTick(p);learn(p,key,4);p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(8);
  call(BASE+"CombatObserver","start",new Class<?>[]{ServerPlayer.class},p);float hp=p.getHealth();hit(p,p.damageSources().fall(),4);
  check(p.getHealth()==hp&&p.getAbsorptionAmount()==8,"Full fall adaptation consumed actual HP or absorption");
  // Models native absorption-effect expiry after the blocked hit in the same tick.
  p.setAbsorptionAmount(0);call(BASE+"CombatObserver","finish",new Class<?>[]{ServerPlayer.class},p);
  check(p.getHealth()==hp&&number(insight(p,key),"leaks")==0,"Absorption expiry was falsely reported as an HP protection bypass");
  LogUtils.getLogger().info("MULTIMOD_QA LOCAL_VERIFICATION PASS: real blocked fall retains HP and absorption; independent absorption expiry does not produce a false HP leak");reset(p);
 }
 static void healedLeak(ServerPlayer p)throws Exception{
  reset(p);String key="damage:minecraft:fall";hit(p,p.damageSources().fall(),4);wheelTick(p);learn(p,key,4);p.setHealth(p.getMaxHealth());
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(LivingAttackEvent e)->{if(injectLeak&&e.getEntity()==p&&e.getSource().is(DamageTypes.FALL))p.setHealth(p.getHealth()-3);});
  call(BASE+"CombatObserver","start",new Class<?>[]{ServerPlayer.class},p);float hp=p.getHealth();
  try{injectLeak=true;hit(p,p.damageSources().fall(),4);}finally{injectLeak=false;}
  check(p.getHealth()==hp-3,"Deliberate heal-masking fixture failed to create its actual 3 HP bypass");
  p.heal(3);check(p.getHealth()==hp,"Native healing fixture did not restore actual HP");call(BASE+"CombatObserver","finish",new Class<?>[]{ServerPlayer.class},p);
  check(number(insight(p,key),"leaks")==1&&number(insight(p,key),"maxLeak")==3,"Same-tick native healing concealed measured 3 HP protection bypass");
  LogUtils.getLogger().info("MULTIMOD_QA LOCAL_HEALED_LEAK PASS: real 3 HP bypass still diagnosed after actual same-tick p.heal(3) restores HP");reset(p);
 }
 static void disabledVerification(ServerPlayer p)throws Exception{
  reset(p);String key="damage:minecraft:fall";hit(p,p.damageSources().fall(),4);wheelTick(p);learn(p,key,4);p.setHealth(p.getMaxHealth());
  double verified=number(insight(p,key),"verified"),leaks=number(insight(p,key),"leaks");
  call(BASE+"CombatObserver","start",new Class<?>[]{ServerPlayer.class},p);check(hit(p,p.damageSources().fall(),4)==0,"Disabled-analysis fixture failed to block actual fall before disabling analysis");
  try{
   call(CORE+"AdaptationTimings","setAnalysisEnabled",new Class<?>[]{boolean.class},false);
   call(BASE+"CombatObserver","finish",new Class<?>[]{ServerPlayer.class},p);
  }finally{call(CORE+"AdaptationTimings","setAnalysisEnabled",new Class<?>[]{boolean.class},true);}
  check(number(insight(p,key),"verified")==verified&&number(insight(p,key),"leaks")==leaks,"Disabling analyzer mid-frame still recorded protection diagnostics");
  LogUtils.getLogger().info("MULTIMOD_QA LOCAL_DISABLED_ANALYSIS PASS: actual fully blocked fall followed by disabling analysis before finish records neither verification nor leak");reset(p);
 }
}
