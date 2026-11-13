package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Native events and a deliberately leaking fixture mod, with actual HP assertions. */
final class DeepAnalysisQa {
 static Zombie owner;static Arrow arrow;static int ticks,cycle,step,frames;static boolean injectLeak;static Screen detail;
 static final String LEAK="damage:minecraft:fall";
 static Object insight(ServerPlayer p,String key)throws Exception{return getter(info(p,key),"insight");}
 static void run(ServerPlayer p)throws Exception{
  reset(p);damageTypes=p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);LocalAnalyzerQa.run(p);
  var caster=UNKNOWN_CASTER.get().create(p.serverLevel());caster.setNoAi(true);caster.setInvulnerable(true);caster.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(caster);
  DamageSource opaque=new DamageSource(p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC),caster);
  learn(p,"damage:minecraft:generic",4);learn(p,"mob:multimodqa:unknown_caster",4);check(hit(p,opaque,4)>0,"Broad generic/species adaptation incorrectly grants new ability immunity");wheelTick(p);String composite="attack:multimodqa:observed/unknown_caster/damage/minecraft/generic";check(snapshot(p,composite)!=null,"Opaque new mod caster failed to get separate ability");
  learn(p,composite,4);check(hit(p,opaque,4)==0,"Full observed ability failed to prevent actual HP loss");controlDisable(p,composite);check(hit(p,opaque,4)>0,"Disabling observed ability failed to restore actual damage");caster.discard();reset(p);
  owner=new Zombie(p.serverLevel());owner.setNoAi(true);owner.setInvulnerable(true);owner.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(owner);
  String key=CLIENT_KEY;DamageSource beam=new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"),owner);
  hit(p,beam,4);wheelTick(p);var before=required(p,key);int duration=duration(before);double work=number(before,"work");
  p.addEffect(new MobEffectInstance(MobEffects.POISON,100,0),owner);
  var revised=required(p,key);check(getter(revised,"profile").toString().equals("POISON"),"Attributed poison failed to correct weak beam guess");
  check(duration(revised)==duration&&number(revised,"work")==work,"Correction changed active clock");
  check(getter(insight(p,key),"previousProfile").equals("LIGHT")&&getter(insight(p,key),"revisedProfile").equals("POISON"),"Correction explanation missing");
  p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,0),owner);check(snapshot(p,"effect:minecraft:speed")==null,"Beneficial effect learned");p.removeAllEffects();
  Arrow a=new Arrow(p.serverLevel(),owner);Snowball b=new Snowball(p.serverLevel(),owner);
  hit(p,new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"),a,owner),4);wheelTick(p);
  hit(p,new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"),b,owner),4);wheelTick(p);
  check(number(insight(p,key),"variants")>=3,"Different native direct entities collapsed");
  hit(p,p.damageSources().fall(),4);wheelTick(p);learn(p,LEAK,4);p.setHealth(p.getMaxHealth());
  call(BASE+"CombatObserver","start",new Class<?>[]{ServerPlayer.class},p);hit(p,p.damageSources().fall(),4);call(BASE+"CombatObserver","finish",new Class<?>[]{ServerPlayer.class},p);
  check(number(insight(p,LEAK),"verified")>=1,"True immunity not verified from actual HP");
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(LivingAttackEvent e)->{if(injectLeak&&e.getEntity()==p&&e.getSource().is(DamageTypes.FALL))p.setHealth(p.getHealth()-3);});
  injectLeak=true;float old=p.getHealth();call(BASE+"CombatObserver","start",new Class<?>[]{ServerPlayer.class},p);hit(p,p.damageSources().fall(),4);call(BASE+"CombatObserver","finish",new Class<?>[]{ServerPlayer.class},p);injectLeak=false;
  check(p.getHealth()==old-3&&number(insight(p,LEAK),"leaks")==1&&number(insight(p,LEAK),"maxLeak")==3,"Actual bypass hidden or diagnosed as successful protection");
  Warden boss=new Warden(EntityType.WARDEN,p.serverLevel());boss.setNoAi(true);boss.moveTo(p.getX()+4,p.getY(),p.getZ());p.serverLevel().addFreshEntity(boss);
  hit(p,p.damageSources().sonicBoom(boss),4);wheelTick(p);check(getter(insight(p,"damage:minecraft:sonic_boom"),"phase").equals("health-band:4"),"Boss full-health band not observed");
  boss.setHealth(100);hit(p,p.damageSources().sonicBoom(boss),4);wheelTick(p);check(getter(insight(p,"damage:minecraft:sonic_boom"),"phase").equals("health-band:1"),"Boss health-state change not observed");boss.discard();
  call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);var saved=p.getPersistentData().getCompound("adaptationwheel").copy();call(BASE+"CombatObserver","forget",new Class<?>[]{ServerPlayer.class},p);p.getPersistentData().put("adaptationwheel",saved);
  check(number(insight(p,LEAK),"leaks")==1,"Persisted measured leak lost on reload");owner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
  LogUtils.getLogger().info("MULTIMOD_QA DEEP_SERVER PASS: attributed debuff corrects category with frozen clock; native direct-entity identity; positive excluded; actual immunity verified; injected 3HP bypass flagged; Warden state; NBT reload");
 }
 static void server(java.util.function.Consumer<ServerPlayer> action)throws Exception{var mc=Minecraft.getInstance();mc.getSingleplayerServer().submit(()->action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).get();}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();if(++ticks>1800)throw new AssertionError("Deep analyzer timeout");
  if(cycle<4&&ticks%20==0){server(p->{try{
   owner.teleportTo(p.getX()+3,p.getY(),p.getZ());
   switch(step){
    case 0->{owner.stopUsingItem();owner.startUsingItem(InteractionHand.MAIN_HAND);}
    case 1->{arrow=new Arrow(p.serverLevel(),owner);arrow.setPos(owner.getX(),owner.getEyeY(),owner.getZ());p.serverLevel().addFreshEntity(arrow);arrow.setNoGravity(true);}
    case 2->{hit(p,p.damageSources().arrow(arrow,owner),4);wheelTick(p);if(cycle==3)check(!getter(insight(p,"damage:minecraft:arrow"),"predicted").toString().isEmpty(),"Real repeated windup/shot/impact did not predict explosion");}
    case 3->{p.invulnerableTime=0;p.serverLevel().explode(owner,p.getX(),p.getY(),p.getZ(),.5F,net.minecraft.world.level.Level.ExplosionInteraction.NONE);}
    case 4->{p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0),owner);p.removeEffect(MobEffects.POISON);if(arrow!=null)arrow.discard();}
   }
  }catch(Exception e){throw new RuntimeException(e);}});if(++step==5){step=0;cycle++;}return false;}
  if(cycle<4)return false;
  if(detail==null){server(p->{try{call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);}catch(Exception e){throw new RuntimeException(e);}});if(++frames<8)return false;
   Object state=call("ru.pasha.adaptationwheel.client.ClientWheelState","get",new Class<?>[]{int.class},mc.player.getId());Object entry=null;for(Object row:(List<?>)getter(state,"entries"))if(getter(row,"key").equals(LEAK))entry=row;
   check(entry!=null&&number(getter(getter(entry,"analysis"),"insight"),"leaks")==1,"Client packet lost measured bypass");
   Class<?> outer=Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen"),inner=Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen$DetailScreen");var constructor=inner.getDeclaredConstructor(outer,String.class);constructor.setAccessible(true);detail=(Screen)constructor.newInstance(outer.getConstructor().newInstance(),LEAK);mc.setScreen(detail);frames=0;
  }
  if(++frames==8){Screenshot.grab(mc.gameDirectory,"deep-analysis-measured-protection.png",mc.getMainRenderTarget(),m->{});LogUtils.getLogger().info("MULTIMOD_QA DEEP_CLIENT PASS: real bow windup/projectile/impact/explosion/debuff chain; next-event prediction; actual packet and rendered Russian diagnostics");return true;}return false;
 }
}
