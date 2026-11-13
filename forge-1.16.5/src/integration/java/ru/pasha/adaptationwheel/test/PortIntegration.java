package ru.pasha.adaptationwheel.test;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.boss.dragon.*;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;
import ru.pasha.adaptationwheel.block.AdaptationGlowBlock;

@Mod.EventBusSubscriber(modid="adaptationwheel")
public final class PortIntegration {
 private static int checks;
 private static net.minecraft.server.MinecraftServer pending;private static int delay,phase;
 @SubscribeEvent public static void started(FMLServerStartedEvent event){
  pending=event.getServer();delay=20;phase=0;
 }
 @SubscribeEvent public static void run(TickEvent.ServerTickEvent event){
  if(pending==null||event.phase!=TickEvent.Phase.END||--delay>0)return;
  try{
   ServerWorld world=pending.overworld();
   if(phase==0){stagesAndContact(world);bossAndDeath(world);effectsAndVision(world);moddedDamageAndCatalogue(world);prepareCave(world);phase=1;delay=20;return;}
   lights(world);
   System.out.println("PORT_INTEGRATION:PASS "+checks+" assertions on Forge 1.16.5");
  }catch(Throwable t){System.out.println("PORT_INTEGRATION:FAIL");t.printStackTrace();}
  pending.halt(false);pending=null;
 }
 private static void check(boolean b,String text){if(!b)throw new AssertionError(text);checks++;}
 private static TestPlayer player(ServerWorld world){return new TestPlayer(world);}
 private static void tick(TestPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
 private static void advance(TestPlayer p,String key,int target){int limit=30000;while(AdaptationEvents.state(p).stage(key)<target&&limit-->0)tick(p);check(limit>0,"Adaptation must complete "+key);}
 private static void stagesAndContact(ServerWorld world){
  TestPlayer p=player(world);ZombieEntity zombie=EntityType.ZOMBIE.create(world);DamageSource hit=DamageSource.mobAttack(zombie);String key="mob:minecraft:zombie";
  p.damage(hit,2);tick(p);check(p.getHealth()==18,"First real attack loses health");
  for(int stage=1;stage<=4;stage++){
   p.setHealth(3);advance(p,key,stage);check(p.getHealth()==20,"Every stage fully heals");
   p.setHealth(20);p.damage(hit,10);check(Math.abs(p.getHealth()-(20-10*(1-AdaptationState.reductionPercent(stage)/100F)))<.01,"Actual damage mitigation "+stage);tick(p);
  }
  LivingAttackEvent immune=new LivingAttackEvent(p,DamageSource.mobAttack(EntityType.HUSK.create(world)),5);MinecraftForge.EVENT_BUS.post(immune);check(immune.isCanceled(),"A different zombie variant shares immunity");
  p.setItemSlot(EquipmentSlotType.HEAD,ItemStack.EMPTY);p.damage(hit,3);check(p.getHealth()==17,"Without wheel immunity is removed");
  TestPlayer a=player(world);a.damage(DamageSource.FALL,1);tick(a);double before=AdaptationEvents.state(a).snapshots().stream().filter(v->v.key().equals("damage:fall")).findFirst().get().work();
  MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(a,DamageSource.FALL,1));a.damage(DamageSource.FALL,1);
  MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(a,DamageSource.FALL,1));tick(a);
  AdaptationState.Snapshot entry=AdaptationEvents.state(a).snapshots().stream().filter(v->v.key().equals("damage:fall")).findFirst().get();
  check(entry.hits()==2&&Math.abs(entry.speed()-1.6)<.0001,"Contact repeats add 30 percent and deduplicate health path: key="+entry.key()+" hits="+entry.hits()+" speed="+entry.speed());
  check(Math.abs(entry.work()-before-1.6)<.0001,"Clock advances at speed instead of changing its starting value");
  AdaptationEvents.state(a).setCategoryEnabled(entry.key(),false);before=entry.work();tick(a);check(AdaptationEvents.state(a).snapshots().stream().filter(v->v.key().equals("damage:fall")).findFirst().get().work()==before,"Category toggle pauses time");
  AdaptationEvents.save(a);check(AdaptationEvents.loadState(a).snapshots().equals(AdaptationEvents.state(a).snapshots()),"NBT round trip keeps speed/work/profile/settings");
  NegativeEffects.clear(p);NegativeEffects.clear(a);
 }
 private static void bossAndDeath(ServerWorld world){
  TestPlayer p=player(world);ZombieEntity zombie=EntityType.ZOMBIE.create(world);zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
  DamageSource hit=DamageSource.mobAttack(zombie);p.damage(hit,1);tick(p);String key="mob:minecraft:zombie";advance(p,key,4);
  MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(p,DamageSource.GENERIC));
  LivingAttackEvent immune=new LivingAttackEvent(p,hit,3);MinecraftForge.EVENT_BUS.post(immune);tick(p);
  check(immune.isCanceled()&&AdaptationEvents.state(p).snapshots().stream().filter(e->e.key().equals(key)).findFirst().get().running(),"Blocked boss attack restarts fifth stage after death");advance(p,key,5);
  check(AdaptationEvents.executeBoss(p,zombie)&&!zombie.isAlive(),"Existence adaptation executes boss with a fist");
  ZombieEntity exact=EntityType.ZOMBIE.create(world);exact.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);check(!DamageCategories.boss(exact),"100 HP does not open existence stage");
  TestPlayer dragonPlayer=player(world);String dragonKey="mob:minecraft:ender_dragon";AdaptationEvents.state(dragonPlayer).hit(dragonKey,true);advance(dragonPlayer,dragonKey,5);
  EnderDragonEntity dragon=EntityType.ENDER_DRAGON.create(world);check(AdaptationEvents.executeBoss(dragonPlayer,dragon.head)&&dragon.getHealth()==0,"Dragon part supports one-hit execution and dying phase");
  TestPlayer w=player(world);net.minecraft.entity.boss.WitherEntity wither=EntityType.WITHER.create(world);w.damage(DamageSource.mobAttack(wither),1);tick(w);advance(w,"mob:minecraft:wither",4);w.setHealth(20);w.damage(DamageSource.WITHER,3);check(w.getHealth()==20,"Wither lingering damage shares learned immunity");
 }
 private static void effectsAndVision(ServerWorld world)throws java.io.IOException{
  TestPlayer p=player(world);p.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN,1000));tick(p);String slow=DamageCategories.effectKey(Effects.MOVEMENT_SLOWDOWN);check(AdaptationEvents.state(p).stage(slow)==0&&!AdaptationEvents.state(p).snapshots().isEmpty(),"Negative effect automatically starts");
  advance(p,slow,1);p.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN,1000));tick(p);check(p.getEffect(Effects.MOVEMENT_SLOWDOWN).getDuration()==800,"Partial effect shortens correctly");
  advance(p,slow,4);check(!p.hasEffect(Effects.MOVEMENT_SLOWDOWN)&&!p.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN,1000)),"Full harmful effect removed and blocked");
  int size=AdaptationEvents.state(p).snapshots().size();p.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED,1000));tick(p);check(AdaptationEvents.state(p).snapshots().size()==size,"Positive effects create no adaptation");
  p.addEffect(new EffectInstance(Effects.HUNGER,600));tick(p);advance(p,AdaptationProfile.HUNGER_KEY,4);p.getFoodData().setFoodLevel(0);p.getFoodData().addExhaustion(30);NegativeEffects.sustainHunger(p);check(p.getFoodData().getFoodLevel()==20&&p.getFoodData().getSaturationLevel()==20,"Hunger maintains full-food gameplay without food");p.setHealth(20);p.damage(DamageSource.STARVE,5);check(p.getHealth()==20,"Starvation immunity");
  AdaptationEvents.state(p).hit("effect:minecraft:instant_damage",false);advance(p,"effect:minecraft:instant_damage",4);p.damage(DamageSource.MAGIC,5);check(p.getHealth()==20,"Instant harm protected");
  TestPlayer v=player(world);AdaptationEvents.state(v).hit(AdaptationProfile.DARK_KEY,false);for(int i=0;i<6000;i++)AdaptationEvents.state(v).tick();
  check(AdaptationEvents.state(v).nightVisionUnlocked(),"Vision unlock only after fourth darkness stage");
  v.addEffect(new EffectInstance(Effects.NIGHT_VISION,1000));NegativeEffects.maintain(v);
  EffectInstance owned=v.getEffect(Effects.NIGHT_VISION);check(owned.getDuration()==Integer.MAX_VALUE,"Unending owned vision has no short timer");
  for(int i=0;i<700;i++){v.tickCount++;v.tickVision();NegativeEffects.maintain(v);}
  check(v.getEffect(Effects.NIGHT_VISION)==owned&&owned.getDuration()==Integer.MAX_VALUE,"Infinite vision neither renews nor expires after 700 ticks");
  net.minecraft.network.play.server.SPlayEntityEffectPacket packet=new net.minecraft.network.play.server.SPlayEntityEffectPacket(v.getId(),owned);
  net.minecraft.network.PacketBuffer buffer=new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
  packet.write(buffer);buffer.readVarInt();buffer.readByte();buffer.readByte();int duration=buffer.readVarInt();buffer.release();
  check(duration==32767,"Client packet activates no-counter display and stays outside flicker window");
  AdaptationEvents.state(v).setNightVisionEnabled(false);NegativeEffects.maintain(v);check(v.getEffect(Effects.NIGHT_VISION).getDuration()==300,"External potion restored with aged remaining time");
  AdaptationEvents.state(v).setNightVisionEnabled(true);NegativeEffects.maintain(v);v.setItemSlot(EquipmentSlotType.HEAD,ItemStack.EMPTY);NegativeEffects.maintain(v);check(v.getEffect(Effects.NIGHT_VISION).getDuration()==300,"Wheel removal preserves external potion");
  NegativeEffects.clear(v);
 }
 private static void moddedDamageAndCatalogue(ServerWorld world){
  TestPlayer p=player(world);ZombieEntity zombie=EntityType.ZOMBIE.create(world);DamageSource beam=new EntityDamageSource("examplemod:light_beam",zombie).setProjectile();p.damage(beam,1);tick(p);
  check(AdaptationEvents.state(p).snapshots().stream().anyMatch(e->e.key().equals("damage:examplemod:light_beam")&&e.profile()==AdaptationProfile.LIGHT),"New mod ray creates individual LIGHT adaptation");
  check(AdaptationEvents.state(p).snapshots().stream().anyMatch(e->e.key().equals("mob:minecraft:zombie")),"Mod attack also teaches species");
  p.damage(new DamageSource("examplemod:radiation"),1);tick(p);check(AdaptationEvents.state(p).snapshots().stream().anyMatch(e->e.key().equals("damage:examplemod:radiation")),"Unknown mechanism gets its own fallback adaptation");
  check(DamageCategories.analyze(DamageSource.OUT_OF_WORLD).isEmpty(),"Void damage remains unadaptable");
  ru.pasha.adaptationwheel.AdaptationConfig.OVERRIDES.set(Arrays.asList("examplemod:opaque=LIGHT"));
  check(DamageCategories.profile(new DamageSource("examplemod:opaque").setIsFire())==AdaptationProfile.LIGHT,"Exact configuration override wins over generic flags");
  ru.pasha.adaptationwheel.AdaptationConfig.OVERRIDES.set(Collections.emptyList());
  List<ru.pasha.adaptationwheel.net.CatalogueEntry> catalog=AdaptationCatalogue.entries(p);
  check(catalog.size()>65,"Full living entity/effect/hazard catalogue");
  check(catalog.stream().anyMatch(e->e.key().equals("damage:examplemod:light_beam")),"Catalogue includes newly detected mod attacks");
  check(catalog.stream().noneMatch(e->e.key().equals("effect:minecraft:speed")),"Catalogue excludes beneficial effects");
  check(catalog.stream().noneMatch(e->e.key().equals("mob:minecraft:husk")),"Zombie family merged");
 }
 private static void lights(ServerWorld world){
  TestPlayer p=player(world);BlockPos center=new BlockPos(100,100,100);
  for(BlockPos pos:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(3,4,3)))world.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
  for(BlockPos pos:BlockPos.betweenClosed(center.offset(-1,0,-1),center.offset(1,2,1)))world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
  p.setPos(100.5,100,100.5);DarknessLight.invalidate(p);check(DarknessLight.isDark(p),"Sealed unlit cave recognized");
  String key=AdaptationProfile.DARK_KEY;AdaptationEvents.state(p).hit(key,false);advance(p,key,1);BlockPos head=p.blockPosition().above();
  check(world.getBlockState(head).is(AdaptationWheel.GLOW.get())&&world.getBlockState(head).getValue(AdaptationGlowBlock.LEVEL)==5,"First cave stage gives actual moving world light: stage="+AdaptationEvents.state(p).stage(key)+" head="+head+" block="+world.getBlockState(head)+" feet="+world.getBlockState(p.blockPosition())+" active="+AdaptationEvents.active(p)+" alive="+p.isAlive());
  DarknessLight.invalidate(p);check(DarknessLight.isDark(p),"Own glow does not stop adaptation");
  world.setBlock(head.east(),Blocks.TORCH.defaultBlockState(),3);DarknessLight.invalidate(p);check(!DarknessLight.isDark(p),"External torch pauses cave adaptation");
  double before=AdaptationEvents.state(p).snapshots().get(0).work();tick(p);check(AdaptationEvents.state(p).snapshots().get(0).work()==before,"Darkness clock paused at torch");
  world.setBlock(head.east(),Blocks.AIR.defaultBlockState(),3);DarknessLight.invalidate(p);advance(p,key,4);
  check(world.getBlockState(head).getValue(AdaptationGlowBlock.LEVEL)==15,"Third/fourth stages brighter than torch");p.setPos(110.5,130,110.5);tick(p);
  check(world.getBlockState(head).isAir(),"Moving light cleans old placement");
  check(world.getBlockState(p.blockPosition().above()).is(AdaptationWheel.GLOW.get()),"Glow places correctly in otherwise empty chunk section");
  p.setItemSlot(EquipmentSlotType.HEAD,ItemStack.EMPTY);tick(p);check(world.getBlockState(p.blockPosition().above()).isAir(),"Removing wheel cleans light");
 }
 private static void prepareCave(ServerWorld world){BlockPos center=new BlockPos(100,100,100);for(BlockPos pos:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(3,4,3)))world.setBlock(pos,Blocks.STONE.defaultBlockState(),3);for(BlockPos pos:BlockPos.betweenClosed(center.offset(-1,0,-1),center.offset(1,2,1)))world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
 private static final class TestPlayer extends FakePlayer{
  TestPlayer(ServerWorld w){super(w,new GameProfile(UUID.randomUUID(),"PortTest"));BlockPos spawn=w.getSharedSpawnPos();setPos(spawn.getX()+.5,250,spawn.getZ()+.5);setHealth(20);setItemSlot(EquipmentSlotType.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));}
  @Override public boolean isInvulnerableTo(DamageSource s){return false;}
  @Override public BlockPos blockPosition(){return new BlockPos(getX(),getY(),getZ());}
  void damage(DamageSource s,float amount){super.actuallyHurt(s,amount);}
  void tickVision(){super.tickEffects();}
 }
}
