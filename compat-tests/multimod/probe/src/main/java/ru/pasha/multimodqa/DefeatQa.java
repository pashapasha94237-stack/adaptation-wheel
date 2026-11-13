package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.damagesource.*;
import net.minecraft.core.registries.Registries;
import static ru.pasha.multimodqa.MultiModQa.*;

final class DefeatQa {
 static Zombie attacker;static int step,ticks;static long began;static ServerPlayer original;
 static void run(ServerPlayer p)throws Exception{
  reset(p);call(BASE+"DefeatMemory","forget",new Class<?>[]{ServerPlayer.class},p);original=p;
  p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(true,p.server);
  attacker=new Zombie(p.serverLevel());attacker.setNoAi(true);attacker.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(attacker);
 }
 static void attack(ServerPlayer p,net.minecraft.resources.ResourceKey<DamageType> type,float amount){p.invulnerableTime=0;p.hurt(new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),attacker),amount);}
 static void server(java.util.function.Consumer<ServerPlayer> action)throws Exception{var mc=Minecraft.getInstance();mc.getSingleplayerServer().submit(()->action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).get();}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();if(System.nanoTime()-began>50_000_000_000L)throw new AssertionError("Defeat fixture timeout "+step);
  ticks++;
  if(step==0&&ticks>=20){server(p->attack(p,DamageTypes.MOB_ATTACK,4));step++;ticks=0;}
  else if(step==1&&ticks>=20){server(p->attack(p,DamageTypes.INDIRECT_MAGIC,4));step++;ticks=0;}
  else if(step==2&&ticks>=20){server(p->attack(p,DamageTypes.SONIC_BOOM,10000));step++;ticks=0;}
  else if(step==3&&mc.player.isDeadOrDying()&&ticks>25){
   var memories=original.getPersistentData().getCompound("adaptationwheel_meta").getList("Defeats",10);check(memories.size()==1&&memories.getCompound(0).getList("Pattern",10).size()==3,"Actual death failed to persist three attack events");
   mc.player.respawn();mc.setScreen(null);step++;ticks=0;
  }
  else if(step==4&&mc.player.isAlive()&&ticks>90){server(p->{try{
   check(p!=original,"Player was not actually recreated by respawn");var data=p.getPersistentData().getCompound("adaptationwheel_meta");check(data.getList("Defeats",10).size()==1,"Respawn lost defeat memory");
   learn(p,"environment:defeat_memory",4);call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);p.teleportTo(original.getX(),original.getY(),original.getZ());
   call(BASE+"DefeatMemory","tick",new Class<?>[]{ServerPlayer.class},p);
  }catch(Exception e){throw new RuntimeException(e);}});step++;ticks=0;}
  else if(step==5&&ticks>=20){server(p->attack(p,DamageTypes.MOB_ATTACK,4));step++;ticks=0;}
  else if(step==6&&ticks>=20){server(p->attack(p,DamageTypes.INDIRECT_MAGIC,4));step++;ticks=0;}
  else if(step==7&&ticks>=10){
   server(p->{try{LogUtils.getLogger().info("DEFEAT DEBUG stage={} active={} saved={} live={}",stage(p,"environment:defeat_memory"),UtilityQa.meta("full",p,"DEFEAT"),p.getPersistentData().getCompound("adaptationwheel_meta"),UtilityQa.field(BASE+"DefeatMemory","LIVE"));var map=(java.util.Map<?,?>)UtilityQa.field(BASE+"DefeatMemory","LIVE");var live=map.get(p);if(live!=null)for(var f:live.getClass().getDeclaredFields()){f.setAccessible(true);LogUtils.getLogger().info("DEFEAT DEBUG {}={}",f.getName(),f.get(live));}}catch(Exception e){throw new RuntimeException(e);}});
   check(!UtilityQa.field("ru.pasha.adaptationwheel.client.MetaClient","warning").toString().isBlank(),"Matching real prefix did not warn before lethal third hit");
   check(!((java.util.List<?>)UtilityQa.field("ru.pasha.adaptationwheel.client.MetaClient","trail")).isEmpty(),"Saved path not delivered to ghost renderer");
   check(mc.player.isAlive(),"Warning killed player or repeated lethal attack itself");
   LogUtils.getLogger().info("MULTIMOD_QA DEFEAT PASS: real three-hit death, keepInventory respawn/cloned NBT, learned prefix warns before lethal move, saved trail delivered, living player retains manual control");return true;
  }
  return false;
 }
}
