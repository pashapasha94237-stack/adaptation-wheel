package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import static ru.pasha.multimodqa.MultiModQa.*;

/**
 * The 4.29.0 utility adaptations in a real client: every coremod hook under production names, each one at stage IV
 * and again switched off in the J menu. The server half runs once; the client half walks through the rest tick by tick.
 */
final class Adapt429Qa {
 static final String[] ALL={"DAY_SLEEP","RESTLESS","INSOMNIA","FOREIGN_BED","STICKY","PORTAL","AIR_MINING","FARMLAND","SMELTING","NETHER_WATER","BROKEN_TOOL","ANVIL","QUIVER","COOLDOWN","ENDERMAN","CREEPER","PIGLIN","TRADE","SURROUNDED","MURKY","LAVA_SIGHT","UNSEEN","BATTERY"};
 static final String CLIENT="ru.pasha.adaptationwheel.client.";
 static BlockPos base,bed,nearFurnace,farFurnace,netherBed;
 static UUID nearHusk,farHusk,plainHusk;
 static int step,t,smeltStart;static long began,dayTime;static boolean asleep;
 static final java.util.List<String> phantomResults=new java.util.ArrayList<>();static boolean phantomEvents;
 static Object dead;static boolean listening,traced;static FogType fogType;static float vanillaFar=-1,finalFar=-1,view;
 interface Act{void run(ServerPlayer p)throws Exception;}

 static String key(String name)throws Exception{Object m=UtilityQa.mechanic(name);return (String)m.getClass().getField("key").get(m);}
 static void sync(ServerPlayer p)throws Exception{call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);}
 /** The J menu switch of one entry. */
 static void enable(ServerPlayer p,String name,boolean on)throws Exception{state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key(name),on);sync(p);}
 static void ok(String what){LogUtils.getLogger().info("ADAPT429 OK {}",what);}
 static void stand(ServerPlayer p){p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);}
 static boolean near(Vec3 v,double x,double y,double z){return Math.abs(v.x-x)<1e-6&&Math.abs(v.y-y)<1e-6&&Math.abs(v.z-z)<1e-6;}
 /** Logs the wheel's anchor around every event of a death and respawn, so a lost anchor names its culprit. */
 static void traceRespawn(){
  if(traced)return;traced=true;
  for(var pri:new EventPriority[]{EventPriority.HIGHEST,EventPriority.LOWEST}){
   MinecraftForge.EVENT_BUS.addListener(pri,true,net.minecraftforge.event.entity.player.PlayerEvent.Clone.class,e->trace("Clone "+pri+" death="+e.isWasDeath(),e.getEntity()));
   MinecraftForge.EVENT_BUS.addListener(pri,true,net.minecraftforge.event.entity.player.PlayerSetSpawnEvent.class,e->trace("SetSpawn "+pri+" "+e.getSpawnLevel().location()+" "+e.getNewSpawn()+" canceled="+e.isCanceled(),e.getEntity()));
   MinecraftForge.EVENT_BUS.addListener(pri,true,net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class,e->trace("Respawn "+pri+" end="+e.isEndConquered(),e.getEntity()));
   MinecraftForge.EVENT_BUS.addListener(pri,true,net.minecraftforge.event.entity.living.LivingDeathEvent.class,e->{if(e.getEntity() instanceof ServerPlayer)trace("Death "+pri,e.getEntity());});
  }
 }
 static void trace(String what,Entity e){
  if(!(e instanceof ServerPlayer p))return;
  String full;try{var m=(Enum<?>)UtilityQa.mechanic("FOREIGN_BED");full=String.valueOf(call(BASE+"MetaServer","full",new Class<?>[]{ServerPlayer.class,m.getDeclaringClass()},p,m));}catch(Exception x){full="?"+x;}
  var bedState=netherBed==null?"-":p.server.getLevel(Level.NETHER).getBlockState(netherBed).getBlock().toString();
  LogUtils.getLogger().info("ADAPT429 TRACE {} player@{} in {} head={} full={} netherBed={} meta={}",what,Integer.toHexString(System.identityHashCode(p)),p.level().dimension().location(),p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem(),full,bedState,p.getPersistentData().getCompound("adaptationwheel_meta"));
 }

 // ---------- server half ----------
 static void run(ServerPlayer p)throws Exception{
  reset(p);for(var name:ALL)learn(p,key(name),4);sync(p);
  var level=p.serverLevel();
  base=new BlockPos(p.getBlockX(),-60,p.getBlockZ());stand(p);
  rest(p,level);surrounded(p,level);insomnia(p,level);portal(p);sticky(p,level);airMining(p);farmland(p,level);netherWater(p);brokenTool(p);mobs(p,level);creeper(p,level);trade(p,level);
  // Left in place for the client half: two furnaces, invisible husks and an open anvil (opened last: any other menu closes it).
  nearFurnace=base.offset(2,0,2);farFurnace=base.offset(24,0,0);furnace(level,nearFurnace);furnace(level,farFurnace);smeltStart=p.server.getTickCount();
  nearHusk=husk(level,base.offset(-5,0,0),true);farHusk=husk(level,base.offset(-40,0,0),true);plainHusk=husk(level,base.offset(-5,0,-3),false);
  anvil(p,level);
 }
 static void bed(ServerLevel level,BlockPos head){
  var b=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
  level.setBlock(head.south(),b.setValue(BedBlock.PART,BedPart.FOOT),18);level.setBlock(head,b.setValue(BedBlock.PART,BedPart.HEAD),18);
 }
 static void rest(ServerPlayer p,ServerLevel level)throws Exception{
  long time=level.getDayTime();level.setDayTime(18000);
  bed=base.offset(0,0,-2);bed(level,bed);
  var zombie=new Zombie(level);zombie.setNoAi(true);zombie.moveTo(bed.getX()+3.5,bed.getY(),bed.getZ()+.5);level.addFreshEntity(zombie);
  try{
   check(!zombie.isPreventingPlayerRest(p),"Stage IV: a zombie near the bed still counts as a monster nearby");
   var slept=p.startSleepInBed(bed);check(slept.right().isPresent(),"Stage IV could not lie down next to a zombie: "+slept.left());
   p.stopSleepInBed(true,true);
   enable(p,"RESTLESS",false);
   slept=p.startSleepInBed(bed);
   if(p.isSleeping())p.stopSleepInBed(true,true);
   enable(p,"RESTLESS",true);
   check(slept.left().filter(x->x==Player.BedSleepingProblem.NOT_SAFE).isPresent(),"Switched off, a zombie near the bed did not stop sleep: "+slept);
  }finally{zombie.discard();level.setDayTime(time);stand(p);}
  ok("restless_sleep: Monster.isPreventingPlayerRest lets the wearer lie down by a zombie at night; switched off vanilla says NOT_SAFE");
 }
 static void insomnia(ServerPlayer p,ServerLevel level)throws Exception{
  long time=level.getDayTime();var difficulty=level.getDifficulty();var stat=Stats.CUSTOM.get(Stats.TIME_SINCE_REST);int rested=p.getStats().getValue(stat);
  var rule=level.getGameRules().getRule(GameRules.RULE_DOINSOMNIA);boolean insomniaRule=rule.get();
  // Vanilla sends phantoms only to a player at or above sea level (63 here) under the open sky; no tick passes up there.
  p.teleportTo(base.getX()+.5,level.getSeaLevel()+8,base.getZ()+.5);var sky=new AABB(p.blockPosition()).inflate(48,48,48);
  rule.set(true,p.server);level.setDayTime(18000);level.updateSkyBrightness();p.server.setDifficulty(Difficulty.HARD,true);p.getStats().setValue(p,stat,240000);
  try{
   if(!phantomEvents){phantomEvents=true;MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent.class,e->phantomResults.add(String.valueOf(e.getResult())));}
   phantomResults.clear();int full=phantoms(level,sky);String fullResults=phantomResults.toString();
   phantomResults.clear();enable(p,"INSOMNIA",false);int off=phantoms(level,sky);enable(p,"INSOMNIA",true);
   var at=p.blockPosition();
   String why=" (spawner events IV "+fullResults+", off "+phantomResults+"; skyDarken "+level.getSkyDarken()+", at "+at+", sea "+level.getSeaLevel()+", sky "+level.canSeeSky(at)+", local difficulty "+level.getCurrentDifficultyAt(at).getEffectiveDifficulty()+", rest "+p.getStats().getValue(stat)+", spectator "+p.isSpectator()+", rule "+rule.get()+")";
   check(full==0,"Stage IV: "+full+" phantoms came after ten sleepless days"+why);check(off>0,"Switched off, no phantom came after ten sleepless days"+why);
   ok("insomnia: 40 real PhantomSpawner runs above sea level after ten sleepless days send no phantom at IV and "+off+" switched off");
  }finally{p.getStats().setValue(p,stat,rested);p.server.setDifficulty(difficulty,true);rule.set(insomniaRule,p.server);level.setDayTime(time);level.updateSkyBrightness();for(var ph:level.getEntitiesOfClass(Phantom.class,sky))ph.discard();stand(p);p.resetFallDistance();}
 }
 /** Forty fresh spawners (each acts on its first tick) under the night sky: the phantoms they sent. */
 static int phantoms(ServerLevel level,AABB sky){
  int n=0;for(int i=0;i<40;i++)n+=new PhantomSpawner().tick(level,true,false);
  for(var ph:level.getEntitiesOfClass(Phantom.class,sky))ph.discard();
  return n;
 }
 static void portal(ServerPlayer p)throws Exception{
  int full=p.getPortalWaitTime();enable(p,"PORTAL",false);int off=p.getPortalWaitTime();enable(p,"PORTAL",true);
  check(full==1,"Stage IV portal wait is "+full);check(off==80,"Switched off portal wait is "+off);
  ok("portal_wait: Player.getPortalWaitTime is 1 tick at IV and 80 switched off");
 }
 static void sticky(ServerPlayer p,ServerLevel level)throws Exception{
  Field stuck=ObfuscationReflectionHelper.findField(Entity.class,"f_19865_");
  Method speed=ObfuscationReflectionHelper.findMethod(Entity.class,"m_6041_"),jump=ObfuscationReflectionHelper.findMethod(Entity.class,"m_20098_");
  var under=base.below();var old=level.getBlockState(under);level.setBlock(under,Blocks.HONEY_BLOCK.defaultBlockState(),2);stand(p);
  try{
   p.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(),new Vec3(.25,.05,.25));Vec3 full=(Vec3)stuck.get(p);
   float fullSpeed=(float)speed.invoke(p),fullJump=(float)jump.invoke(p);
   enable(p,"STICKY",false);
   p.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(),new Vec3(.25,.05,.25));Vec3 off=(Vec3)stuck.get(p);
   float offSpeed=(float)speed.invoke(p),offJump=(float)jump.invoke(p);
   enable(p,"STICKY",true);
   check(near(full,1,1,1),"Stage IV cobweb multiplier "+full);check(near(off,.25,.05,.25),"Switched off cobweb multiplier "+off);
   check(Math.abs(fullSpeed-1)<1e-4&&Math.abs(fullJump-1)<1e-4,"Stage IV honey speed "+fullSpeed+", jump "+fullJump);
   check(Math.abs(offSpeed-.4F)<1e-4&&Math.abs(offJump-.5F)<1e-4,"Switched off honey speed "+offSpeed+", jump "+offJump);
  }finally{stuck.set(p,Vec3.ZERO);level.setBlock(under,old,2);}
  ok("sticky_blocks: makeStuckInBlock (cobweb), getBlockSpeedFactor and getBlockJumpFactor (honey) are vanilla speed at IV, unchanged switched off");
 }
 static void airMining(ServerPlayer p)throws Exception{
  var stone=Blocks.STONE.defaultBlockState();
  try{
   p.setOnGround(true);float ground=p.getDigSpeed(stone,base);p.setOnGround(false);float air=p.getDigSpeed(stone,base);
   enable(p,"AIR_MINING",false);float offAir=p.getDigSpeed(stone,base);enable(p,"AIR_MINING",true);
   check(Math.abs(air-ground)<1e-4,"Stage IV digs "+air+" in the air, "+ground+" on the ground");
   check(Math.abs(offAir*5-ground)<1e-4,"Switched off the air penalty is not vanilla's 1/5: "+offAir+" vs "+ground);
  }finally{p.setOnGround(true);}
  ok("loose_footing: digging in the air is as fast as on the ground at IV, a fifth switched off");
 }
 static void farmland(ServerPlayer p,ServerLevel level)throws Exception{
  var pos=base.offset(3,-1,0);var old=level.getBlockState(pos);
  try{
   level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),2);Blocks.FARMLAND.fallOn(level,level.getBlockState(pos),pos,p,3F);boolean kept=level.getBlockState(pos).is(Blocks.FARMLAND);
   enable(p,"FARMLAND",false);
   level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),2);Blocks.FARMLAND.fallOn(level,level.getBlockState(pos),pos,p,3F);BlockState off=level.getBlockState(pos);
   enable(p,"FARMLAND",true);
   check(kept,"Stage IV landing trampled the farmland");check(off.is(Blocks.DIRT),"Switched off landing left "+off);
  }finally{level.setBlock(pos,old,2);p.fallDistance=0;}
  ok("trampled_farmland: a 3-block landing keeps the farmland at IV and turns it to dirt switched off");
 }
 static void netherWater(ServerPlayer p)throws Exception{
  var nether=p.server.getLevel(Level.NETHER);var pos=new BlockPos(64,200,64);nether.getChunk(pos);
  var bucket=(BucketItem)Items.WATER_BUCKET;
  try{
   for(var d:Direction.values())if(d!=Direction.UP)nether.setBlock(pos.relative(d),Blocks.GLASS.defaultBlockState(),2);
   nether.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
   bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket));boolean kept=nether.getFluidState(pos).isSource();
   nether.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
   enable(p,"NETHER_WATER",false);bucket.emptyContents(p,nether,pos,null,new ItemStack(bucket));boolean offGone=nether.getBlockState(pos).isAir();enable(p,"NETHER_WATER",true);
   bucket.emptyContents(null,nether,pos,null,new ItemStack(bucket));boolean dispenserGone=nether.getBlockState(pos).isAir();
   check(kept,"Stage IV water evaporated in the Nether");check(offGone,"Switched off, water stayed in the Nether");check(dispenserGone,"Water placed without a player stayed in the Nether");
  }finally{for(var d:Direction.values())nether.setBlock(pos.relative(d),Blocks.AIR.defaultBlockState(),2);nether.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
  ok("nether_water: BucketItem.emptyContents keeps a water source in the Nether at IV; switched off and without a player it evaporates");
 }
 static ItemStack worn(Item item,int left){var s=new ItemStack(item);s.setDamageValue(s.getMaxDamage()-left);return s;}
 static void brokenTool(ServerPlayer p)throws Exception{
  var pick=worn(Items.IRON_PICKAXE,1);pick.hurtAndBreak(1,p,x->{});
  var elytra=worn(Items.ELYTRA,2);elytra.hurtAndBreak(1,p,x->{});
  enable(p,"BROKEN_TOOL",false);var off=worn(Items.IRON_PICKAXE,1);off.hurtAndBreak(1,p,x->{});enable(p,"BROKEN_TOOL",true);
  check(!pick.isEmpty()&&pick.getDamageValue()==0,"Stage IV did not fully restore the pickaxe: "+pick+" damage "+pick.getDamageValue());
  check(elytra.getDamageValue()==0&&ElytraItem.isFlyEnabled(elytra),"Stage IV did not fully restore the worn-out elytra: damage "+elytra.getDamageValue());
  check(off.isEmpty(),"Switched off, the pickaxe did not break");
  ok("broken_tool: ItemStack.hurtAndBreak gives a breaking pickaxe and a worn-out elytra full durability at IV; switched off the pickaxe breaks");
 }
 static void mobs(ServerPlayer p,ServerLevel level)throws Exception{
  var ender=new EnderMan(EntityType.ENDERMAN,level);ender.setNoAi(true);ender.moveTo(base.getX()+5.5,base.getY(),base.getZ()-4.5);level.addFreshEntity(ender);
  try{
   var head=p.getInventory().armor.get(3);
   boolean calm=ForgeHooks.shouldSuppressEnderManAnger(ender,p,head),gold=PiglinAi.isWearingGold(p);
   enable(p,"ENDERMAN",false);enable(p,"PIGLIN",false);
   boolean offCalm=ForgeHooks.shouldSuppressEnderManAnger(ender,p,head),offGold=PiglinAi.isWearingGold(p);
   enable(p,"ENDERMAN",true);enable(p,"PIGLIN",true);
   check(calm&&!offCalm,"Enderman stare: IV "+calm+", switched off "+offCalm);check(gold&&!offGold,"Piglin gold: IV "+gold+", switched off "+offGold);
  }finally{ender.discard();}
  ok("enderman_gaze and piglin_gold: the wheel is an ender mask and counts as gold at IV, neither switched off");
 }
 static void surrounded(ServerPlayer p,ServerLevel level)throws Exception{
  var hand=p.getMainHandItem().copy();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); // a bare fist: no vanilla sweep to blur the splash
  try{
   float[] full=strike(p,level),off;
   enable(p,"SURROUNDED",false);try{off=strike(p,level);}finally{enable(p,"SURROUNDED",true);}
   check(full[0]>0&&full[1]==3,"Stage IV: the struck zombie lost "+full[0]+", "+full[1]+" of 3 others were splashed");
   check(off[0]>0&&off[1]==0,"Switched off: the struck zombie lost "+off[0]+", "+off[1]+" others were splashed");
   ok("surrounded: a real blow on one of four zombies targeting the wearer splashes the other 3 at IV and none switched off");
  }finally{p.setItemInHand(InteractionHand.MAIN_HAND,hand);}
 }
 /** Four still zombies by the wearer, all targeting them, and one real blow: the struck one's loss and how many others were hurt. */
 static float[] strike(ServerPlayer p,ServerLevel level){
  var zombies=new java.util.ArrayList<Zombie>();
  for(var a:new double[][]{{1.5,0},{2.5,1.2},{2.5,-1.2},{3.2,0}}){var z=new Zombie(level);z.setNoAi(true);z.setSilent(true);z.moveTo(base.getX()+.5+a[0],base.getY(),base.getZ()+.5+a[1]);level.addFreshEntity(z);z.setTarget(p);zombies.add(z);}
  try{
   var struck=zombies.get(0);p.attack(struck);int splashed=0;
   for(var z:zombies)if(z!=struck&&z.getHealth()<z.getMaxHealth())splashed++;
   return new float[]{struck.getMaxHealth()-struck.getHealth(),splashed};
  }finally{for(var z:zombies)z.discard();}
 }
 static int pile(ServerLevel level,BlockPos c,boolean place){
  int n=0;
  for(int x=-2;x<=2;x++)for(int y=-1;y<=1;y++)for(int z=-2;z<=2;z++){var q=c.offset(x,y,z);if(place)level.setBlock(q,Blocks.DIRT.defaultBlockState(),2);if(level.getBlockState(q).is(Blocks.DIRT))n++;}
  return n;
 }
 static void blast(ServerLevel level,BlockPos c){
  var creeper=new Creeper(EntityType.CREEPER,level);creeper.moveTo(c.getX()+.5,c.getY(),c.getZ()+.5);
  level.explode(creeper,c.getX()+.5,c.getY()+.5,c.getZ()+.5,3F,Level.ExplosionInteraction.MOB);
 }
 static void creeper(ServerPlayer p,ServerLevel level)throws Exception{
  var rule=level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);boolean griefing=rule.get();rule.set(true,p.server);
  var centre=base.offset(0,1,12);
  try{
   int placed=pile(level,centre,true);blast(level,centre);int kept=pile(level,centre,false);
   enable(p,"CREEPER",false);pile(level,centre,true);blast(level,centre);int offKept=pile(level,centre,false);enable(p,"CREEPER",true);
   check(kept==placed,"Stage IV creeper blast destroyed "+(placed-kept)+" of "+placed+" blocks");
   check(offKept<placed,"Switched off, the creeper blast destroyed nothing");
   ok("creeper_grief: a real creeper explosion with mobGriefing on destroys 0 of "+placed+" blocks at IV and "+(placed-offKept)+" switched off");
  }finally{rule.set(griefing,p.server);for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(centre).inflate(12)))item.discard();}
 }
 static void trade(ServerPlayer p,ServerLevel level)throws Exception{
  var v=new Villager(EntityType.VILLAGER,level);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
  v.moveTo(base.getX()+.5,base.getY(),base.getZ()+4.5);level.addFreshEntity(v);
  try{
   // A level 1 farmer gets two random trades; bread for 1 emerald has no demand surcharge even in vanilla
   // (1 x 16 x 0.05 < 1), so take the first trade that has one.
   MerchantOffer offer=null;int price=0,surcharge=0;
   for(var o:v.getOffers()){
    o.setToOutOfStock();o.updateDemand();o.resetUses();
    int c=o.getBaseCostA().getCount(),s=Math.max(0,Mth.floor((float)(c*o.getDemand())*o.getPriceMultiplier()));
    if(s>0){offer=o;price=c;surcharge=s;break;}
   }
   boolean forced=Boolean.getBoolean("multimodqa.vanillaTrade"); // runs the fallback below on purpose
   if(forced)offer=null;
   if(offer==null){
    // In the full pack Farmer's Delight, Abyssal Decor and Dungeons add 8 level 1 farmer trades to vanilla's 5, and 4
    // of the 13 cost a single unit, so both drawn trades lack a surcharge 1 time in 13: name them, then trade
    // vanilla's 20 wheat for an emerald on the same villager.
    var seen=new StringBuilder();
    for(var o:v.getOffers())seen.append(seen.length()==0?"":", ").append(o.getBaseCostA().getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(o.getBaseCostA().getItem()))
     .append(" -> ").append(o.getResult().getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(o.getResult().getItem())).append(" x").append(o.getPriceMultiplier());
    LogUtils.getLogger().info("ADAPT429 NOTE villager_trade: {} ({}); a vanilla 20 wheat -> 1 emerald trade is added",forced?"-Dmultimodqa.vanillaTrade skips the farmer's own trades":"none of the farmer's own trades has a demand surcharge",seen);
    offer=new MerchantOffer(new ItemStack(Items.WHEAT,20),new ItemStack(Items.EMERALD),16,2,0.05F);v.getOffers().add(offer);
    offer.setToOutOfStock();offer.updateDemand();offer.resetUses();
    price=20;surcharge=Math.max(0,Mth.floor((float)(price*offer.getDemand())*offer.getPriceMultiplier()));
   }
   check(surcharge>0,"No demand surcharge to cut in any of the farmer's "+v.getOffers().size()+" trades");
   v.interact(p,InteractionHand.MAIN_HAND);check(p.containerMenu instanceof MerchantMenu,"The villager did not open trading");
   int full=offer.getCostA().getCount();p.closeContainer();
   enable(p,"TRADE",false);v.interact(p,InteractionHand.MAIN_HAND);int off=offer.getCostA().getCount();p.closeContainer();enable(p,"TRADE",true);
   int vanilla=Math.min(offer.getCostA().getMaxStackSize(),price+surcharge);
   check(full==price,"Stage IV price "+full+" instead of the base "+price);check(off==vanilla,"Switched off price "+off+" instead of vanilla "+vanilla);
   ok("villager_trade: opening a real trade costs the base "+price+" at IV and "+vanilla+" switched off");
  }finally{v.discard();}
 }
 static void furnace(ServerLevel level,BlockPos pos){
  level.setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);var f=(AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
  f.setItem(0,new ItemStack(Items.RAW_IRON,16));f.setItem(1,new ItemStack(Items.COAL,8));
 }
 static UUID husk(ServerLevel level,BlockPos at,boolean invisible){
  var h=new Husk(EntityType.HUSK,level);h.setNoAi(true);h.setSilent(true);h.setPersistenceRequired();h.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,90F,0F);
  if(invisible)h.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*600,0,false,false));
  level.addFreshEntity(h);return h.getUUID();
 }
 static ItemStack sword(){var s=new ItemStack(Items.DIAMOND_SWORD);s.setDamageValue(500);s.setRepairCost(30);return s;}
 static void anvil(ServerPlayer p,ServerLevel level)throws Exception{
  var pos=base.offset(-2,0,2);level.setBlock(pos,Blocks.ANVIL.defaultBlockState(),3);
  p.openMenu(new SimpleMenuProvider((id,inventory,player)->new AnvilMenu(id,inventory,ContainerLevelAccess.create(level,pos)),Component.literal("QA")));
  var menu=(AnvilMenu)p.containerMenu;menu.getSlot(0).set(sword());menu.getSlot(1).set(sword());
  int cost=menu.getCost();boolean result=menu.getSlot(2).hasItem();
  enable(p,"ANVIL",false);menu.createResult();boolean offResult=menu.getSlot(2).hasItem();enable(p,"ANVIL",true);menu.createResult();
  check(cost>=40,"The repair is not over vanilla's limit: "+cost);check(result,"Stage IV refused a repair costing "+cost);
  check(!offResult,"Switched off, the anvil still offered a repair costing "+cost);check(menu.getSlot(2).hasItem(),"Switched back on, the result did not come back");
  ok("too_expensive: AnvilMenu.createResult keeps a "+cost+"-level repair at IV and refuses it switched off");
 }

 // ---------- client half ----------
 static void server(Act a)throws Exception{
  var mc=Minecraft.getInstance();var srv=mc.getSingleplayerServer();var id=mc.player.getUUID();
  srv.submit(()->{try{a.run(srv.getPlayerList().getPlayer(id));}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new RuntimeException(e);}}).get();
 }
 static int cstage(String name)throws Exception{return (int)call(CLIENT+"MetaClient","stage",new Class<?>[]{Class.forName(UtilityQa.META)},UtilityQa.mechanic(name));}
 static boolean cactive(String name)throws Exception{return (boolean)call(CLIENT+"MetaClient","active",new Class<?>[]{Class.forName(UtilityQa.META)},UtilityQa.mechanic(name));}
 static Entity find(UUID id){for(var e:Minecraft.getInstance().level.entitiesForRendering())if(e.getUUID().equals(id))return e;return null;}
 static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
 static void next(){step++;t=0;}
 static void waiting(int limit,String what){if(t>limit)throw new AssertionError("adapt429: "+what);}
 /** The terrain fog of the last frame: HIGHEST sees vanilla's value, LOWEST (also when cancelled) the one used. */
 static void listen(){
  if(listening)return;listening=true;
  MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,ViewportEvent.RenderFog.class,e->{if(e.getMode()==FogRenderer.FogMode.FOG_TERRAIN){fogType=e.getType();vanillaFar=e.getFarPlaneDistance();view=e.getRenderer().getRenderDistance();finalFar=-1;}});
  MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,ViewportEvent.RenderFog.class,e->{if(e.getMode()==FogRenderer.FogMode.FOG_TERRAIN)finalFar=e.getFarPlaneDistance();});
 }
 static void cooldown()throws Exception{server(p->p.getCooldowns().addCooldown(Items.ENDER_PEARL,40));}
 static boolean[] onCooldown()throws Exception{boolean[] out=new boolean[2];server(p->out[0]=p.getCooldowns().isOnCooldown(Items.ENDER_PEARL));out[1]=Minecraft.getInstance().player.getCooldowns().isOnCooldown(Items.ENDER_PEARL);return out;}
 static void basin(ServerLevel level,BlockPos c,BlockState fill){
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=3;y++){
   boolean inner=Math.abs(x)<=1&&Math.abs(z)<=1&&y>=0&&y<=2,wall=!inner&&y<=2;
   level.setBlock(c.offset(x,y,z),inner?fill:wall?Blocks.GLASS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
 }

 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();listen();
  if(began==0)began=System.nanoTime();
  if(System.nanoTime()-began>300_000_000_000L)throw new AssertionError("adapt429 timed out at step "+step);
  t++;
  switch(step){
   case 0->{ // the anvil the server opened, synced to the client screen
    if(!(mc.screen instanceof AnvilScreen)||!(mc.player.containerMenu instanceof AnvilMenu menu)||menu.getCost()<40||!menu.getSlot(2).hasItem()){waiting(200,"the anvil screen never showed the result");return false;}
    next();
   }
   case 1->{
    if(t==10){
     var menu=(AnvilMenu)mc.player.containerMenu;int cost=menu.getCost();
     check((boolean)call(CLIENT+"ItemAdaptationsClient","screenAllows",new Class<?>[]{boolean.class},false),"The anvil screen still says Too Expensive at IV");
     var label=((Component)call(CLIENT+"ItemAdaptationsClient","label",new Class<?>[]{Component.class},Component.literal("x"))).getString();
     check(label.contains(String.valueOf(cost)),"The anvil label does not show the price "+cost+": "+label);
     shot("adapt429-anvil.png");ok("too_expensive client: AnvilScreen.renderLabels shows the "+cost+"-level price, label '"+label+"'");
    }
    if(t==14){mc.player.closeContainer();next();}
   }
   case 2->{ // invisible mobs: outlined near, not far, never a visible one
    var near=find(nearHusk);var far=find(farHusk);var plain=find(plainHusk);
    if(near==null||far==null||plain==null||!near.isInvisible()||!far.isInvisible()||cstage("UNSEEN")!=4){waiting(200,"the husks never reached the client");return false;}
    mc.player.setYRot(90F);mc.player.setXRot(5F);next();
   }
   case 3->{
    if(t<10)return false;
    var near=find(nearHusk);var far=find(farHusk);var plain=find(plainHusk);
    check(mc.shouldEntityAppearGlowing(near),"Stage IV does not outline an invisible husk 5 blocks away");
    check(!mc.shouldEntityAppearGlowing(far),"Stage IV outlines an invisible husk 40 blocks away");
    check(!mc.shouldEntityAppearGlowing(plain),"A visible husk was outlined");
    shot("adapt429-unseen.png");
    check(mc.player.getPortalWaitTime()==1,"Client portal wait at IV is "+mc.player.getPortalWaitTime());
    Field stuck=ObfuscationReflectionHelper.findField(Entity.class,"f_19865_");
    mc.player.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(),new Vec3(.25,.05,.25));Vec3 v=(Vec3)stuck.get(mc.player);stuck.set(mc.player,Vec3.ZERO);
    check(near(v,1,1,1),"Client cobweb multiplier at IV "+v);
    server(p->enable(p,"UNSEEN",false));next();
   }
   case 4->{
    if(cactive("UNSEEN")||t<5){waiting(100,"UNSEEN was not switched off on the client");return false;}
    check(!mc.shouldEntityAppearGlowing(find(nearHusk)),"Switched off, the invisible husk is still outlined");
    server(p->{enable(p,"UNSEEN",true);for(var id:new UUID[]{nearHusk,farHusk,plainHusk}){var e=p.serverLevel().getEntity(id);if(e!=null)e.discard();}});
    ok("unseen_foes: Minecraft.shouldEntityAppearGlowing outlines an invisible husk at 5 blocks, not at 40 nor a visible one, nothing switched off; client portal wait and cobweb hooks agree");
    next();
   }
   case 5->{cooldown();next();}
   case 6->{
    if(t==14){var on=onCooldown();check(on[0]&&on[1],"A cut ender pearl cooldown ended too early: server "+on[0]+", client "+on[1]);}
    if(t==30){var on=onCooldown();check(!on[0]&&!on[1],"Stage IV did not halve a 40-tick cooldown: server "+on[0]+", client "+on[1]);server(p->enable(p,"COOLDOWN",false));next();}
   }
   case 7->{
    if(t==5)cooldown();
    if(t==35){var on=onCooldown();check(on[0]&&on[1],"Switched off, the 40-tick cooldown ended within 30 ticks: server "+on[0]+", client "+on[1]);}
    if(t==50){var on=onCooldown();check(!on[0]&&!on[1],"Switched off, the cooldown never ended");server(p->enable(p,"COOLDOWN",true));ok("item_cooldown: ItemCooldowns.addCooldown halves 40 ticks on server and, through handleItemCooldown, client at IV; 40 ticks switched off");next();}
   }
   case 8->{server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));p.getInventory().clearOrCountMatchingItems(s->s.is(Items.ARROW),-1,p.inventoryMenu.getCraftSlots());});next();}
   case 9->{ // a real bow draw with no arrow anywhere
    if(t==5){check(mc.player.getMainHandItem().is(Items.BOW),"The bow did not reach the client hand");mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    if(t>5&&t<30)mc.options.keyUse.setDown(true);
    if(t==8)check(mc.player.isUsingItem(),"Stage IV could not draw a bow without arrows");
    if(t==30){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);next();}
   }
   case 10->{
    if(t<8)return false;
    server(p->{
     var arrows=p.serverLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(80),a->a.getOwner()==p);
     check(!arrows.isEmpty(),"No arrow left the empty bow");check(arrows.get(0).pickup==AbstractArrow.Pickup.CREATIVE_ONLY,"The wheel's arrow can be picked up");
     int damage=p.getMainHandItem().getDamageValue();check(damage==1,"Stage IV bow wear is "+damage+" instead of 1");
     arrows.forEach(Entity::discard);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    });
    ok("empty_quiver: a real draw and release with no arrows fires a non-pickup arrow and wears the bow by 1 at IV");
    step=100;t=0;
   }
   case 100->{if(Adapt429ModsQa.tick()){step=11;t=0;}} // the player's own mods, where installed
   case 11->{ // laptop battery (fake 40% through -Dadaptationwheel.fakeBattery)
    if(mc.options.framerateLimit().get()!=30||mc.options.renderDistance().get()!=8){waiting(400,"the battery caps never applied: fps "+mc.options.framerateLimit().get()+", distance "+mc.options.renderDistance().get());return false;}
    next();
   }
   case 12->{
    if(t<10)return false;
    String hud=String.valueOf(UtilityQa.field(CLIENT+"SenseClient","hud"));check(!hud.isBlank(),"The battery HUD is empty");
    Path record=mc.gameDirectory.toPath().resolve("adaptationwheel").resolve("battery-options.txt");
    String text=Files.isRegularFile(record)?Files.readString(record,StandardCharsets.UTF_8):"";
    check(text.contains("maxFps=60,30")&&text.contains("renderDistance=12,8"),"The crash record does not hold the caps: '"+text+"'");
    shot("adapt429-battery.png");ok("laptop_battery: on battery at IV the frame limit is 30 and render distance 12->8, HUD '"+hud+"', crash record written");
    server(p->enable(p,"BATTERY",false));next();
   }
   case 13->{
    if(cactive("BATTERY")||mc.options.framerateLimit().get()!=60||mc.options.renderDistance().get()!=12){waiting(200,"switched off, the battery caps were not given back");return false;}
    check(!Files.exists(mc.gameDirectory.toPath().resolve("adaptationwheel").resolve("battery-options.txt")),"The crash record stayed after the restore");
    String options=Files.readString(mc.gameDirectory.toPath().resolve("options.txt"),StandardCharsets.UTF_8);
    check(options.contains("renderDistance:12")&&options.contains("maxFps:60"),"options.txt was not saved with the player's values");
    ok("laptop_battery: switched off the player's 60 FPS and distance 12 come back, options.txt saved, crash record removed");next();
   }
   case 14->{ // furnaces placed when the server half ran
    int[] r=new int[3];
    server(p->{var level=p.server.overworld();r[0]=p.server.getTickCount()-smeltStart;r[1]=((AbstractFurnaceBlockEntity)level.getBlockEntity(nearFurnace)).getItem(2).getCount();r[2]=((AbstractFurnaceBlockEntity)level.getBlockEntity(farFurnace)).getItem(2).getCount();});
    if(r[0]<230)return false;
    // The full pack reaches this step later (230-436 ticks seen), so compare with the vanilla pace of one iron per
    // 200 ticks: the far furnace keeps it, the near one at IV smelts three times as fast.
    int vanilla=r[0]/200;
    check(r[2]<=vanilla&&r[1]>=2*Math.max(1,vanilla),"After "+r[0]+" ticks the near furnace made "+r[1]+" and the far one "+r[2]+" (vanilla pace "+vanilla+")");
    ok("smelting_wait: in "+r[0]+" ticks a furnace 3 blocks away smelted "+r[1]+" iron, one 24 blocks away "+r[2]);next();
   }
   case 15->{server(p->{var pool=base.offset(0,0,-8);basin(p.serverLevel(),pool,Blocks.WATER.defaultBlockState());p.teleportTo(pool.getX()+.5,pool.getY(),pool.getZ()+.5);});next();}
   case 16->{
    if(!mc.player.isEyeInFluid(FluidTags.WATER)||t<20||fogType!=FogType.WATER||finalFar<0){waiting(300,"the camera never went under water");return false;}
    float limit=Math.max(vanillaFar,Math.max(32F,view)),want=Math.min(vanillaFar*3,limit);
    check(Math.abs(finalFar-want)<.01F&&finalFar>vanillaFar,"Stage IV water fog "+finalFar+" instead of "+want+" (vanilla "+vanillaFar+", view "+view+")");
    shot("adapt429-water.png");ok("murky_water: under water the fog ends at "+finalFar+" instead of vanilla's "+vanillaFar);
    server(p->enable(p,"MURKY",false));next();
   }
   case 17->{
    if(cactive("MURKY")||t<10||fogType!=FogType.WATER||finalFar<0){waiting(200,"MURKY was not switched off on the client");return false;}
    check(Math.abs(finalFar-vanillaFar)<.01F,"Switched off, the water fog is "+finalFar+" instead of vanilla's "+vanillaFar);
    server(p->{enable(p,"MURKY",true);var pit=base.offset(-8,0,-8);basin(p.serverLevel(),pit,Blocks.LAVA.defaultBlockState());p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,20*300,0));p.teleportTo(pit.getX()+.5,pit.getY(),pit.getZ()+.5);});
    next();
   }
   case 18->{
    if(!mc.player.isEyeInFluid(FluidTags.LAVA)||!mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)||t<30||fogType!=FogType.LAVA||finalFar<0){waiting(300,"the camera never went into lava");return false;}
    check(Math.abs(finalFar-20)<.01F,"Stage IV lava fog "+finalFar+" (vanilla "+vanillaFar+")");
    shot("adapt429-lava.png");ok("lava_sight: in lava with Fire Resistance the fog ends at 20 instead of vanilla's "+vanillaFar);
    server(p->enable(p,"LAVA_SIGHT",false));next();
   }
   case 19->{
    if(cactive("LAVA_SIGHT")||t<15||fogType!=FogType.LAVA||finalFar<0){waiting(200,"LAVA_SIGHT was not switched off on the client");return false;}
    check(Math.abs(finalFar-vanillaFar)<.01F,"Switched off, the lava fog is "+finalFar+" instead of vanilla's "+vanillaFar);
    server(p->{enable(p,"LAVA_SIGHT",true);stand(p);p.clearFire();p.serverLevel().setDayTime(6000);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);});
    next();
   }
   case 20->{
    if(t==20){mc.player.clearFire();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(bed),Direction.UP,bed,false));next();}
   }
   case 21->{
    if(!mc.player.isSleeping()){waiting(60,"stage IV could not lie down at noon (a real click on the bed)");return false;}
    next();
   }
   case 22->{ // the server keeps the day sleeper asleep and skips to the evening
    server(p->{dayTime=p.serverLevel().getDayTime()%24000;if(t==40)asleep=p.isSleeping();});
    if(t==40)check(asleep,"The day sleeper was woken by the daylight check");
    if(t<=40||dayTime<12000){waiting(400,"the day sleep never ended (time "+dayTime+")");return false;}
    check(dayTime<14000,"The day sleep skipped to "+dayTime+" instead of the evening");
    ok("day_sleep: a real click on a bed at 6000 sleeps and wakes at "+dayTime+" (evening)");next();
   }
   case 23->{
    if(mc.player.isSleeping()){waiting(100,"the day sleeper was never woken");return false;}
    server(p->{
     var nether=p.server.getLevel(Level.NETHER);netherBed=new BlockPos(0,200,-2);nether.getChunk(netherBed);
     for(int x=-3;x<=3;x++)for(int z=-4;z<=3;z++){nether.setBlock(new BlockPos(x,199,z),Blocks.OBSIDIAN.defaultBlockState(),2);for(int y=200;y<=203;y++)nether.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
     bed(nether,netherBed);p.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,p.server);
     traceRespawn();
     p.teleportTo(nether,.5,200,1.5,180F,0F);
    });
    next();
   }
   case 24->{
    if(mc.level.dimension()!=Level.NETHER||mc.screen!=null||t<40){waiting(400,"the client never arrived in the Nether");return false;}
    mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(netherBed),Direction.UP,netherBed,false));next();
   }
   case 25->{
    if(t<10)return false;
    server(p->{
     check(p.getPersistentData().getCompound("adaptationwheel_meta").contains("ForeignBed",10),"A click on a Nether bed at IV did not set the wheel's anchor");
     check(p.serverLevel().getBlockState(netherBed).getBlock() instanceof BedBlock,"The Nether bed exploded at IV");
     p.kill();
    });
    next();
   }
   case 26->{
    if(!mc.player.isDeadOrDying()||t<25){waiting(200,"the player did not die");return false;}
    dead=mc.player;mc.player.respawn();mc.setScreen(null);next();
   }
   case 27->{
    if(mc.player==dead||mc.player.isDeadOrDying()||mc.level.dimension()!=Level.NETHER||t<40){waiting(600,"the respawn did not end at the Nether bed (in "+mc.level.dimension().location()+")");return false;}
    double d=Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(netherBed)));
    check(d<3,"Respawned "+d+" blocks from the Nether bed");
    shot("adapt429-nether-bed.png");
    ok("foreign_bed: a real click on a Nether bed keeps it whole and anchors; after a real death and respawn the player is "+String.format("%.1f",d)+" blocks from it in the Nether");
    LogUtils.getLogger().info("MULTIMOD_QA ADAPT429 PASS: 23 utility adaptations at IV and switched off in the real client");
    return true;
   }
   default->throw new AssertionError("adapt429: unknown step "+step);
  }
  return false;
 }
}
