package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;

/** World Slash in a real client: a long diagonal cut through a wall, seen while it travels. */
final class SlashQa {
 static int step,ticks;static BlockPos WALL;static int count(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.block.Block block,int dx0,int dx1,int h,int dz){int n=0;for(int x=dx0;x<=dx1;x++)for(int y=0;y<h;y++)if(level.getBlockState(WALL.offset(x,y,dz)).is(block))n++;return n;}static final List<UUID> ZOMBIES=new ArrayList<>();
 static void run(ServerPlayer p)throws Exception{
  reset(p);var s=state(p);
  s.getClass().getMethod("hit",String.class,boolean.class).invoke(s,"mob:minecraft:wither",true);
  for(int n=0;n<200000&&stage(p,"mob:minecraft:wither")<5;n++)s.getClass().getMethod("tick").invoke(s);
  check(stage(p,"mob:minecraft:wither")==5,"Existence adaptation not reached");
  var level=p.serverLevel();level.setDayTime(6000);
  var sword=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","extermination_sword")));
  sword.getOrCreateTag().putBoolean("AdaptationWorldSlash",true);p.setItemInHand(InteractionHand.MAIN_HAND,sword);
  BlockPos base=p.blockPosition();
  for(int x=-4;x<=4;x++)for(int y=0;y<6;y++)level.setBlockAndUpdate(base.offset(x,y,6),Blocks.STONE_BRICKS.defaultBlockState());WALL=base;
  for(int x=-3;x<=3;x++)for(int y=0;y<10;y++)level.setBlockAndUpdate(base.offset(x+8,y,24),Blocks.QUARTZ_BLOCK.defaultBlockState());
  for(int z:new int[]{12,30,55}){Zombie zombie=EntityType.ZOMBIE.create(level);zombie.setPos(base.getX()+.5,base.getY(),base.getZ()+z+.5);zombie.setNoAi(true);zombie.setPersistenceRequired();zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));level.addFreshEntity(zombie);ZOMBIES.add(zombie.getUUID());}
  p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);p.connection.teleport(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 static ServerPlayer server(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
 static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"slash-"+name+".png",mc.getMainRenderTarget(),m->{});}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(ticks>1200)throw new AssertionError("Slash QA timed out at "+step);
  switch(step){
   case 0->{mc.options.hideGui=true;mc.player.setYRot(0);mc.player.setXRot(0);if(ticks<80)return false;shot("before");step=1;ticks=0;}
   case 1->{
    mc.getSingleplayerServer().submit(()->{try{ServerPlayer p=server();p.setYRot(0);p.setXRot(0);p.yHeadRot=0;
     call("ru.pasha.adaptationwheel.server.WorldSlash","cast",new Class<?>[]{ServerPlayer.class,ItemStack.class,InteractionHand.class},p,p.getMainHandItem(),InteractionHand.MAIN_HAND);}catch(Exception e){throw new RuntimeException(e);}}).get();
    step=2;ticks=0;}
   case 2->{if(ticks==2)shot("travel");if(ticks==5)shot("full");if(ticks<30)return false;
    String report=mc.getSingleplayerServer().submit(()->{
     ServerPlayer p=server();int dead=0;
     try{Object plane=call("ru.pasha.adaptationwheel.server.WorldSlash","plane",new Class<?>[]{ServerPlayer.class},p);
      var hits=plane.getClass().getMethod("hits",double.class,double.class,double.class,double.class,double.class,double.class);
      LogUtils.getLogger().info("SLASH_DIAG player {} eye {} rot {}/{}",p.position(),p.getEyePosition(),p.getYRot(),p.getXRot());
      for(UUID id:ZOMBIES){var e=p.serverLevel().getEntity(id);if(e==null){LogUtils.getLogger().info("SLASH_DIAG zombie {} missing",id);continue;}var b=e.getBoundingBox();
       LogUtils.getLogger().info("SLASH_DIAG zombie at {} alive {} health {} hitNow {}",e.position(),e.isAlive(),((net.minecraft.world.entity.LivingEntity)e).getHealth(),hits.invoke(plane,(b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2,b.getXsize()/2,b.getYsize()/2,b.getZsize()/2));}
     }catch(Exception diag){LogUtils.getLogger().warn("SLASH_DIAG failed",diag);}
     for(UUID id:ZOMBIES){var e=p.serverLevel().getEntity(id);if(e==null||!e.isAlive())dead++;}
     float cooldown=p.getCooldowns().getCooldownPercent(p.getMainHandItem().getItem(),0);
     int bricks=count(p.serverLevel(),Blocks.STONE_BRICKS,-4,4,6,6),quartz=count(p.serverLevel(),Blocks.QUARTZ_BLOCK,5,11,10,24);
     LogUtils.getLogger().info("MULTIMOD_QA SLASH BLOCKS: stone bricks left {}/54, quartz left {}/70",bricks,quartz);
     return dead+" "+cooldown+" "+(54-bricks);}).get();
    String[] parts=report.split(" ");int dead=Integer.parseInt(parts[0]);float cooldown=Float.parseFloat(parts[1]);
    check(dead==3,"World Slash cut "+dead+" of 3 zombies behind the wall (12, 30, 55 blocks)");
    check(cooldown>0.9F,"Cooldown missing after the slash");
    check(Integer.parseInt(parts[2])>=3,"The default World Slash did not cut the wall: "+parts[2]);
    LogUtils.getLogger().info("MULTIMOD_QA SLASH PASS: 3 zombies at 12/30/55 blocks cut through a wall, cooldown {}",cooldown);
    step=3;ticks=0;}
   case 3->{if(ticks<40)return false;shot("after");return true;}
   default->{}
  }
  return false;
 }
}
