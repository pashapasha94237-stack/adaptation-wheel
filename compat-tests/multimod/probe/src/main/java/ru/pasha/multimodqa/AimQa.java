package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Actual BowItem/CrossbowItem and original CGM/Scorched collision callbacks, with HP controls. */
final class AimQa {
 static final String KEY="environment:near_misses";
 static Zombie target;static ItemStack wheel;static float hp;static int ticks,phase;
 static void run(ServerPlayer p)throws Exception{
  damageTypes=p.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
  learnedCases(p);reset(p);wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();
  p.teleportTo(0,100,0);p.serverLevel().setDayTime(18000);p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,p.server);p.getInventory().add(new ItemStack(Items.ARROW,64));
  for(int x=-8;x<=8;x++)for(int z=-8;z<=28;z++){
   p.serverLevel().setBlock(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=100;y<=105;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
  }
  target=new Zombie(p.serverLevel());target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.moveTo(0,100,12);p.serverLevel().addFreshEntity(target);
  hp=target.getHealth();bow(p,-9); // about 1.5 blocks from the target's bounding box
 }
 static void steady(ServerPlayer p,float yaw){
  p.setDeltaMovement(0,0,0);p.hurtMarked=true;p.connection.teleport(0,100,0,yaw,0);p.setYHeadRot(yaw);p.setYBodyRot(yaw);
  target.moveTo(0,100,12);target.setDeltaMovement(0,0,0);target.invulnerableTime=0;
 }
 static void bow(ServerPlayer p,float yaw){
  steady(p,yaw);ItemStack bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.ARROW,64));
  check(!p.getProjectile(bow).isEmpty(),"Native bow fixture needs actual arrow ammunition");
  Items.BOW.releaseUsing(bow,p.level(),p,72000-20);
  LogUtils.getLogger().info("AIM_QA SHOT player={} target={} arrows={}",p.position(),target.position(),p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(100)).stream().map(a->a.position()+" v="+a.getDeltaMovement()+" owner="+(a.getOwner()==p)).toList());
 }
 static String diagnostics(ServerPlayer p)throws Exception{
  var field=Class.forName(BASE+"GuidedShots").getDeclaredField("SHOTS");field.setAccessible(true);
  var shots=(Map<?,?>)field.get(null);StringBuilder details=new StringBuilder();
  for(Object level:shots.values())for(Object shot:((Map<?,?>)level).values()){
   for(String name:List.of("entity","previous","hit","missed")){var f=shot.getClass().getDeclaredField(name);f.setAccessible(true);Object value=f.get(shot);details.append(name).append('=').append(value instanceof Entity e?e.position()+" v="+e.getDeltaMovement():value).append(' ');}
  }
  return "active="+call(BASE+"AdaptationEvents","active",new Class<?>[]{ServerPlayer.class},p)+" flag="+UtilityQa.meta("active",p,"MISSES")+" tracked="+details+" arrows="+p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(100)).stream().map(a->a.position()+" v="+a.getDeltaMovement()).toList();
 }
 static void crossbow(ServerPlayer p)throws Exception{
  steady(p,120);ItemStack bow=new ItemStack(Items.CROSSBOW);ListTag ammo=new ListTag();ammo.add(new ItemStack(Items.ARROW).save(new CompoundTag()));bow.getOrCreateTag().put("ChargedProjectiles",ammo);CrossbowItem.setCharged(bow,true);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
  CrossbowItem.performShooting(p.level(),p,InteractionHand.MAIN_HAND,bow,3.15F,0);
 }
 static void gun(ServerPlayer p,String mod,float yaw)throws Exception{
  steady(p,yaw);String prefix=mod.equals("cgm")?"com.mrcrayfish.guns":"top.ribs.scguns";
  var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(mod,mod.equals("cgm")?"pistol":"pistol"));
  if(item==null||!item.getClass().getName().startsWith(prefix))item=ForgeRegistries.ITEMS.getValues().stream().filter(i->i.getClass().getName().equals(prefix+".item.GunItem")).findFirst().orElseThrow();
  ItemStack stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
  Object gun=item.getClass().getMethod("getModifiedGun",ItemStack.class).invoke(item,stack);
  Class<?> projectile=Class.forName(prefix+(mod.equals("cgm")?".entity.ProjectileEntity":".entity.projectile.ProjectileEntity"));
  var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(mod,"projectile"));check(type!=null,"Native gun projectile registry missing: "+mod);
  var ctor=projectile.getConstructor(EntityType.class,Level.class,LivingEntity.class,ItemStack.class,Class.forName(prefix+".item.GunItem"),Class.forName(prefix+".common.Gun"));
  Entity bullet=(Entity)ctor.newInstance(type,p.level(),p,stack,item,gun);check(p.serverLevel().addFreshEntity(bullet),"Native gun entity failed to spawn");LogUtils.getLogger().info("AIM_QA GUN mod={} pos={} v={}",mod,bullet.position(),bullet.getDeltaMovement());
 }
 static void server(java.util.function.Consumer<ServerPlayer> action)throws Exception{
  var mc=Minecraft.getInstance();mc.getSingleplayerServer().submit(()->action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).get();
 }
 static boolean clientTick()throws Exception{
  if(++ticks>1200)throw new AssertionError("Aim QA timeout");if(ticks%20!=0)return false;
  server(p->{try{
   target.setDeltaMovement(0,0,0);
   switch(phase){
    case 0->{check(target.getHealth()==hp,"Unadapted near-miss bow control changed HP: "+hp+" -> "+target.getHealth()+" fire="+target.isOnFire());check(snapshot(p,KEY)!=null&&stage(p,KEY)<4,"Actual close bow miss did not begin adaptation: "+diagnostics(p));learn(p,KEY,4);hp=target.getHealth();bow(p,120);}
    case 1->{check(target.getHealth()<hp,"Fully adapted bow fired away failed to cause actual collision damage");LogUtils.getLogger().info("MULTIMOD_QA AIM_BOW PASS: native near miss starts learning, bow fired away curves into actual HP hit after stage four");controlDisable(p,KEY);hp=target.getHealth();bow(p,-9);}
    case 2->{check(target.getHealth()==hp,"Disabled homing still hit target");state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),KEY,true);hp=target.getHealth();crossbow(p);}
    case 3->{check(target.getHealth()<hp,"Fully adapted native crossbow did not hit");hp=target.getHealth();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);bow(p,-9);}
    case 4->{check(target.getHealth()==hp,"Homing remained active without the physical wheel");p.setItemSlot(EquipmentSlot.HEAD,wheel);for(int x=-3;x<=3;x++)for(int y=100;y<=104;y++)p.serverLevel().setBlock(new BlockPos(x,y,6),Blocks.STONE.defaultBlockState(),2);hp=target.getHealth();bow(p,120);}
    case 5->{check(target.getHealth()==hp,"Guidance bypassed a solid wall or applied fake damage");for(int x=-3;x<=3;x++)for(int y=100;y<=104;y++)p.serverLevel().setBlock(new BlockPos(x,y,6),Blocks.AIR.defaultBlockState(),2);LogUtils.getLogger().info("MULTIMOD_QA AIM_CONTROLS PASS: native crossbow HP hit, disable/unequip restore misses, solid wall does not cause synthetic damage");reset(p);hp=target.getHealth();if(ModList.get().isLoaded("cgm"))gun(p,"cgm",-9);}
    case 6->{if(ModList.get().isLoaded("cgm")){check(target.getHealth()==hp,"Native CGM close-miss control hit");check(snapshot(p,KEY)!=null,"Native CGM bullet miss did not start adaptation: "+diagnostics(p));learn(p,KEY,4);hp=target.getHealth();gun(p,"cgm",120);}}
    case 7->{if(ModList.get().isLoaded("cgm")){check(target.getHealth()<hp,"Native MrCrayfish bullet fired away failed to deal actual HP damage after guidance");LogUtils.getLogger().info("MULTIMOD_QA AIM_CGM PASS: original CGM 1.4.20 ProjectileEntity close miss learned; native bullet fired away redirects and actual native damage removes HP");controlDisable(p,KEY);hp=target.getHealth();gun(p,"cgm",-9);}}
    case 8->{if(ModList.get().isLoaded("cgm"))check(target.getHealth()==hp,"Disabled CGM homing still hit");reset(p);hp=target.getHealth();if(ModList.get().isLoaded("scguns"))gun(p,"scguns",-9);}
    case 9->{if(ModList.get().isLoaded("scguns")){check(target.getHealth()==hp,"Native Scorched near-miss control hit");check(snapshot(p,KEY)!=null,"Scorched bullet miss did not start adaptation");learn(p,KEY,4);hp=target.getHealth();gun(p,"scguns",120);}}
    case 10->{if(ModList.get().isLoaded("scguns")){check(target.getHealth()<hp,"Native Scorched guided bullet failed actual HP damage");LogUtils.getLogger().info("MULTIMOD_QA AIM_SCGUNS PASS: actual non-Projectile gun entity close miss, redirect and native HP collision");}reset(p);hp=target.getHealth();bow(p,0);}
    case 11->{check(target.getHealth()<hp,"Native direct-hit control failed to remove actual HP");check(snapshot(p,KEY)==null,"A genuine arrow hit was incorrectly learned as a miss");learn(p,KEY,4);var board=p.server.getScoreboard();var team=board.addPlayerTeam("aimqa");board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(target.getScoreboardName(),team);hp=target.getHealth();bow(p,120);}
    case 12->{check(target.getHealth()==hp,"Guidance targeted an allied entity");p.server.getScoreboard().removePlayerTeam(p.server.getScoreboard().getPlayerTeam("aimqa"));LogUtils.getLogger().info("MULTIMOD_QA AIM_BOUNDARIES PASS: genuine native arrow hit is not a miss; learned guidance respects scoreboard allies");target.discard();reset(p);}
   }
  }catch(Exception error){throw new RuntimeException(error);}});
  return ++phase>12;
 }
 static void learnedCases(ServerPlayer p)throws Exception{
  reset(p);var owner=new Zombie(p.serverLevel());owner.setNoAi(true);owner.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(owner);
  Entity pulse=UNKNOWN_PULSE.get().create(p.serverLevel());String unknown="damage:multimodqa:pattern_opaque";
  try{
   for(String name:List.of("pattern_cold_a","pattern_cold_b","pattern_cold_c")){
    check(hit(p,new DamageSource(custom("multimodqa:"+name,"multimodqa:"+name),pulse,owner),4)>0,"Training impact must remove actual HP");wheelTick(p);
   }
   float hp=health(p);hit(p,new DamageSource(custom("multimodqa:pattern_opaque","multimodqa:pattern_opaque"),pulse,owner),4);wheelTick(p);
   Object row=required(p,unknown);check(getter(row,"profile").toString().equals("COLD")&&getter(row,"analysisReason").equals("learned.pattern")&&number(row,"analysisConfidence")<85,"Factual repeated ability signatures failed to teach the new opaque projectile");
   check(health(p)<hp&&stage(p,unknown)==0,"Learned category invented protection instead of requiring earned stages");
   call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);call(BASE+"SmartAttackAnalyzer","forget",new Class<?>[]{ServerPlayer.class},p);
   check(hit(p,new DamageSource(custom("multimodqa:pattern_opaque","multimodqa:pattern_opaque"),pulse,owner),4)>0,"Restored tentative category must still require adaptation");wheelTick(p);
   check(getter(required(p,unknown),"analysisReason").equals("learned.pattern"),"Case knowledge lost after NBT reload");
   LogUtils.getLogger().info("MULTIMOD_QA LOCAL_CASE_MEMORY PASS: three registry-confirmed incoming cold abilities teach a new opaque projectile; actual HP still lost at stage zero; NBT restores local learned patterns");
  }finally{owner.discard();pulse.discard();reset(p);}
 }
}
