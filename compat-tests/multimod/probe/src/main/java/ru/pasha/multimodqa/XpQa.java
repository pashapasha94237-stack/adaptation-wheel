package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Experience orb merging in the real game: 300 orbs worth 3 dropped the way mob deaths drop them, once with Boost off
 * and once on. Counts the orb entities left after three merge scans and checks the experience they carry is unchanged.
 */
final class XpQa {
 static final int ORBS=300,VALUE=3;
 static int step,ticks;static ServerPlayer player;static BlockPos pit;static int offCount=-1;
 static final Field COUNT=ObfuscationReflectionHelper.findField(ExperienceOrb.class,"f_147072_");
 static void run(ServerPlayer p){
  player=p;var level=p.serverLevel();
  // A walled stone pit in open air, 24 blocks from the player so the orbs do not fly to them.
  pit=new BlockPos(p.getBlockX()+24,170,p.getBlockZ());
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){level.setBlock(pit.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<4;y++)level.setBlock(pit.offset(x,y,z),Math.abs(x)==3||Math.abs(z)==3?Blocks.GLASS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
  var base=new BlockPos(p.getBlockX(),170,p.getBlockZ());
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<3;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);p.setInvulnerable(true);
  LogUtils.getLogger().info("MULTIMOD_QA XP STAGE: pit ready at {}",pit);
 }
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();if(player==null||mc.player==null)return false;
  if(++ticks>2400)throw new AssertionError("XP probe timed out at step "+step);
  var server=mc.getSingleplayerServer();
  switch(step){
   case 0->{PerfQa.boost(false);ticks=0;step=1;}
   case 1->{if(ticks<40)return false;server.execute(()->drop(player.serverLevel()));ticks=0;step=2;}
   case 2->{if(ticks<70)return false;server.execute(()->offCount=report("off",player.serverLevel()));ticks=0;step=3;}
   case 3->{if(offCount<0)return false;PerfQa.boost(true);ticks=0;step=4;}
   case 4->{if(!PerfQa.boostActive()||ticks<40)return false;server.execute(()->drop(player.serverLevel()));ticks=0;step=5;}
   case 5->{if(ticks<70)return false;server.execute(()->{int on=report("on",player.serverLevel());
    if(on>=offCount)throw new AssertionError("Boost did not reduce orb entities: off="+offCount+" on="+on);
    LogUtils.getLogger().info("MULTIMOD_QA XP PASS: {} orbs worth {} became {} entities with Boost off and {} with Boost on, experience unchanged",ORBS,VALUE,offCount,on);});
    ticks=0;step=6;}
   case 6->{return ticks>20;}
  }
  return false;
 }
 static void drop(ServerLevel level){
  for(var orb:orbs(level))orb.discard();
  var center=Vec3.atBottomCenterOf(pit);
  for(int i=0;i<ORBS;i++)ExperienceOrb.award(level,center.add((i%5-2)*0.4,0.2,(i/5%5-2)*0.4),VALUE);
 }
 /** Logs the orb entities in the pit and checks they still carry all the experience; returns their number. */
 static int report(String phase,ServerLevel level){
  List<ExperienceOrb> orbs=orbs(level);long total=0;
  try{for(var orb:orbs)total+=(long)orb.getValue()*COUNT.getInt(orb);}catch(IllegalAccessException e){throw new AssertionError(e);}
  LogUtils.getLogger().info("MULTIMOD_QA XP RESULT {} entities={} experience={} boostActive={}",phase,orbs.size(),total,PerfQa.boostActiveQuiet());
  if(total!=(long)ORBS*VALUE)throw new AssertionError("Experience changed with Boost "+phase+": "+total);
  return orbs.size();
 }
 static List<ExperienceOrb> orbs(ServerLevel level){return level.getEntitiesOfClass(ExperienceOrb.class,new AABB(pit).inflate(4));}
}
