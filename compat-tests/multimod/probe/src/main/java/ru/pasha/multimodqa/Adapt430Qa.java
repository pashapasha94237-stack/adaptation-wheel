package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * The 4.30.0 travel adaptations in a real client: a Riptide trident drawn and let go on dry land by the client's own
 * use key, and an Eye of Ender thrown at a real stronghold (the group asks for a world with structures), each at
 * stage IV and again switched off in the J menu.
 */
final class Adapt430Qa {
 static final String[] ALL={"RIPTIDE","ENDER_EYE"};
 static final String OWNER="adaptationwheel_eye_owner";
 /** Vanilla's dash of a Riptide I trident: 3 * (1 + 1) / 4. */
 static final double DASH=1.5;
 static BlockPos base;static int step,t,endedAt=-1;static long began;static double power;static Vec3 start,eyeEnd;static UUID eye;
 static final List<String> overlay=new ArrayList<>();static boolean listening;

 // ---------- server half ----------
 static void run(ServerPlayer p)throws Exception{
  reset(p);for(var name:ALL)learn(p,Adapt429Qa.key(name),4);Adapt429Qa.sync(p);
  var level=p.serverLevel();level.setWeatherParameters(24000,0,false,false);
  // A wide dry platform high in the open air: no water, no rain, room to dash and to land again.
  base=new BlockPos(p.getBlockX(),200,p.getBlockZ());
  for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<=6;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  stand(p);
  power=((Number)((Supplier<?>)Class.forName("ru.pasha.adaptationwheel.MetaConfig").getField("RIPTIDE_DRY_POWER").get(null)).get()).doubleValue()/100;
  p.setItemInHand(InteractionHand.MAIN_HAND,trident());
 }
 static ItemStack trident(){var stack=new ItemStack(Items.TRIDENT);stack.enchant(Enchantments.RIPTIDE,1);return stack;}
 static void stand(ServerPlayer p){p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}

 // ---------- client half ----------
 /** Action bar messages the client received, by translation key. */
 static void listen(){
  if(listening)return;listening=true;
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,ClientChatReceivedEvent.System.class,e->{if(e.isOverlay()&&e.getMessage().getContents() instanceof TranslatableContents c)overlay.add(c.getKey());});
 }
 static void ok(String what){LogUtils.getLogger().info("ADAPT430 OK {}",what);}
 static void next(){step++;t=0;endedAt=-1;}
 /** Ten client ticks after the eye ended, so what the server did then has reached the client. */
 static boolean settled(){if(endedAt<0)endedAt=t;return t-endedAt>=10;}
 static void waiting(int limit,String what){if(t>limit)throw new AssertionError("adapt430: "+what);}
 static double flat(Vec3 v){return Math.sqrt(v.x*v.x+v.z*v.z);}
 static EyeOfEnder thrown(ServerPlayer p){
  for(var e:p.serverLevel().getEntitiesOfClass(EyeOfEnder.class,p.getBoundingBox().inflate(48)))
   if(e.getPersistentData().hasUUID(OWNER)&&e.getPersistentData().getUUID(OWNER).equals(p.getUUID()))return e;
  return null;
 }
 static int eyesAround(ServerPlayer p,Vec3 at){
  int n=0;for(var item:p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at,at).inflate(4),i->i.getItem().is(Items.ENDER_EYE)))n+=item.getItem().getCount();return n;
 }
 /** Finds the eye the client just threw, checks the thrower tag and forces vanilla's one-in-five break. */
 static boolean caught()throws Exception{
  boolean[] found={false};
  Adapt429Qa.server(p->{var e=thrown(p);if(e==null)return;found[0]=true;eye=e.getUUID();ObfuscationReflectionHelper.setPrivateValue(EyeOfEnder.class,e,false,"f_36954_");});
  return found[0];
 }
 static boolean ended()throws Exception{
  boolean[] gone={false};
  Adapt429Qa.server(p->{var e=p.serverLevel().getEntity(eye);if(e==null||e.isRemoved()){gone[0]=true;}else eyeEnd=e.position();});
  return gone[0];
 }

 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();listen();
  if(began==0)began=System.nanoTime();
  if(System.nanoTime()-began>400_000_000_000L)throw new AssertionError("adapt430 timed out at step "+step);
  t++;
  switch(step){
   case 0->{ // a Riptide trident on a dry platform at stage IV
    if(!mc.player.getMainHandItem().is(Items.TRIDENT)||Adapt429Qa.cstage("RIPTIDE")!=4||!mc.player.onGround()||mc.player.isInWaterOrRain()||t<20){waiting(400,"the trident, stage IV or the dry platform never reached the client");return false;}
    mc.player.setYRot(0F);mc.player.setXRot(0F);overlay.clear();
    mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next();
   }
   case 1->{
    mc.options.keyUse.setDown(true);
    if(t==4){
     check(mc.player.isUsingItem(),"Stage IV could not draw a Riptide trident on dry land (client)");
     boolean[] using={false};Adapt429Qa.server(p->using[0]=p.isUsingItem());check(using[0],"Stage IV could not draw a Riptide trident on dry land (server)");
    }
    if(t==16){
     start=mc.player.position();mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
     double speed=flat(mc.player.getDeltaMovement()),want=DASH*power;
     check(Math.abs(speed-want)<.05,"Stage IV dashed at "+speed+" on dry land, wanted "+want+" ("+Math.round(power*100)+"% of "+DASH+")");
     LogUtils.getLogger().info("ADAPT430 dash speed {} (wanted {})",speed,want);
     next();
    }
   }
   case 2->{
    if(t==6){ // the spin flag is set by the server and synced back
     boolean[] spin={false};Adapt429Qa.server(p->spin[0]=p.isAutoSpinAttack());
     check(spin[0],"The server did not start the spin attack of the dash");check(mc.player.isAutoSpinAttack(),"The spin attack of the dash did not reach the client");
    }
    if(t<12)return false;
    double moved=flat(mc.player.position().subtract(start));
    check(moved>4,"The client moved only "+moved+" blocks after the dash");
    Adapt429Qa.server(p->{
     int damage=p.getMainHandItem().getDamageValue();check(damage==1,"The server wore the trident by "+damage+" instead of 1: it did not take the dash");
     stand(p);Adapt429Qa.enable(p,"RIPTIDE",false);
    });
    ok("dry_riptide: a real draw and release on a dry platform at IV dashes "+String.format("%.1f",moved)+" blocks at "+Math.round(power*100)+"% of the water dash; the server wears the trident by 1");
    next();
   }
   case 3->{
    if(Adapt429Qa.cactive("RIPTIDE")||t<10||!mc.player.onGround()){waiting(200,"RIPTIDE was not switched off on the client");return false;}
    start=mc.player.position();overlay.clear();
    mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next();
   }
   case 4->{
    if(t<16){mc.options.keyUse.setDown(true);return false;}
    check(!mc.player.isUsingItem(),"Switched off, the client still drew the trident on dry land");
    boolean[] using={true};int[] damage={0};Adapt429Qa.server(p->{using[0]=p.isUsingItem();damage[0]=p.getMainHandItem().getDamageValue();});
    check(!using[0],"Switched off, the server still drew the trident on dry land");
    mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
    check(flat(mc.player.getDeltaMovement())<.2&&flat(mc.player.position().subtract(start))<.5,"Switched off, the trident still dashed");
    check(damage[0]==1,"Switched off, the trident was worn again");
    check(!overlay.contains("meta.adaptationwheel.dry_riptide.need")&&!overlay.contains("meta.adaptationwheel.dry_riptide.dried"),"Switched off, the refusal was still explained: "+overlay);
    Adapt429Qa.server(p->{Adapt429Qa.enable(p,"RIPTIDE",true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.ENDER_EYE));stand(p);});
    ok("dry_riptide switched off: TridentItem.use refuses on dry land on both sides, no dash, no wear, no message");
    next();
   }
   case 5->{ // an Eye of Ender thrown at a real stronghold at stage IV
    if(!mc.player.getMainHandItem().is(Items.ENDER_EYE)||Adapt429Qa.cstage("ENDER_EYE")!=4||t<10){waiting(200,"the eye or stage IV never reached the client");return false;}
    overlay.clear();mc.player.setXRot(-10F);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next();
   }
   case 6->{
    if(!caught()){waiting(600,"no eye flew: no stronghold found (does the group's world have structures?)");return false;}
    check(overlay.contains("meta.adaptationwheel.shattered_eye.distance")||overlay.contains("meta.adaptationwheel.shattered_eye.here"),"Stage IV did not tell the distance to the stronghold: "+overlay);
    next();
   }
   case 7->{
    if(!ended()){waiting(200,"the eye never ended");return false;}
    if(!settled())return false; // the returned stack reaches the client
    int[] lying={0},eyes={0};boolean[] hand={false};
    Adapt429Qa.server(p->{lying[0]=eyesAround(p,eyeEnd);eyes[0]=p.getInventory().countItem(Items.ENDER_EYE);hand[0]=p.getMainHandItem().is(Items.ENDER_EYE);});
    check(lying[0]==0,"Stage IV left "+lying[0]+" eye(s) on the ground");
    check(eyes[0]==1&&hand[0],"The eye did not come back to the empty hand (server has "+eyes[0]+", in hand "+hand[0]+")");
    check(mc.player.getMainHandItem().is(Items.ENDER_EYE),"The returned eye did not reach the client hand");
    check(overlay.contains("meta.adaptationwheel.shattered_eye.returned"),"Stage IV did not say the eye came back: "+overlay);
    Adapt429Qa.server(p->{Adapt429Qa.enable(p,"ENDER_EYE",false);stand(p);});
    ok("shattered_eye: a real throw at a real stronghold names the distance at IV; an eye vanilla breaks comes back to the hand");
    next();
   }
   case 8->{
    if(Adapt429Qa.cactive("ENDER_EYE")||t<10){waiting(200,"ENDER_EYE was not switched off on the client");return false;}
    overlay.clear();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);next();
   }
   case 9->{
    if(!caught()){waiting(600,"switched off, no eye flew");return false;}
    next();
   }
   case 10->{
    if(!ended()){waiting(200,"the switched-off eye never ended");return false;}
    if(!settled())return false;
    int[] lying={0},eyes={0};
    Adapt429Qa.server(p->{lying[0]=eyesAround(p,eyeEnd);eyes[0]=p.getInventory().countItem(Items.ENDER_EYE);Adapt429Qa.enable(p,"ENDER_EYE",true);});
    check(lying[0]==0&&eyes[0]==0,"Switched off, the eye vanilla broke survived ("+lying[0]+" on the ground, "+eyes[0]+" in the inventory)");
    check(overlay.stream().noneMatch(k->k.startsWith("meta.adaptationwheel.shattered_eye")),"Switched off, the eye still spoke: "+overlay);
    ok("shattered_eye switched off: vanilla's break stands, no distance, nothing comes back");
    LogUtils.getLogger().info("MULTIMOD_QA ADAPT430 PASS: 2 travel adaptations at IV and switched off in the real client");
    return true;
   }
   default->throw new AssertionError("adapt430: unknown step "+step);
  }
  return false;
 }
}
