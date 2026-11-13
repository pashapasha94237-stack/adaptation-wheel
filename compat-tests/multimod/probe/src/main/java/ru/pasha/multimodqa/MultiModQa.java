package ru.pasha.multimodqa;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

@Mod("multimodqa")
public final class MultiModQa {
 static final String BASE="ru.pasha.adaptationwheel.server.";
 private boolean requested,testing;private volatile boolean finished;private volatile Throwable error;private int ticks,after;
 static JsonObject group;
 static final net.minecraftforge.registries.DeferredRegister<MobEffect> QA_EFFECTS=net.minecraftforge.registries.DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"multimodqa");
 static final net.minecraftforge.registries.RegistryObject<MobEffect> LIFECYCLE=QA_EFFECTS.register("lifecycle",LifecycleEffect::new);
 public static final class LifecycleEffect extends MobEffect {
  int added,removed;
  LifecycleEffect(){super(MobEffectCategory.HARMFUL,0x332211);}
  @Override public void addAttributeModifiers(LivingEntity e,net.minecraft.world.entity.ai.attributes.AttributeMap a,int n){added++;super.addAttributeModifiers(e,a,n);}
  @Override public void removeAttributeModifiers(LivingEntity e,net.minecraft.world.entity.ai.attributes.AttributeMap a,int n){removed++;super.removeAttributeModifiers(e,a,n);}
 }
 public MultiModQa(){QA_EFFECTS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());SmartAnalysisQa.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());AutonomousEffectQa.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);MinecraftForge.EVENT_BUS.addListener(IngameQa::sound);MinecraftForge.EVENT_BUS.addListener(PerfQa::frame);}
 private void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.END&&group!=null&&group.has("utility")&&group.get("utility").getAsString().equals("landscape"))LandscapeQa.frame();}
 private void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(++ticks>18000)throw new IllegalStateException("Compatibility QA timeout");
  if(mc.getOverlay()!=null)return;GLFW.glfwHideWindow(mc.getWindow().getWindow());
  try{
   if(error!=null)throw new IllegalStateException("Integration tests failed",error);
   if(!requested&&mc.screen!=null){requested=true;group=JsonParser.parseString(Files.readString(Path.of(System.getProperty("multimodqa.group")))).getAsJsonObject();
    for(var id:group.getAsJsonArray("mods"))check(ModList.get().isLoaded(id.getAsString()),"Required test mod missing "+id);
    if(group.has("resume")){try(var saves=Files.list(mc.gameDirectory.toPath().resolve("saves"))){String name=saves.filter(Files::isDirectory).filter(p->p.getFileName().toString().startsWith("Adaptation-QA-")).findFirst().orElseThrow().getFileName().toString();mc.createWorldOpenFlows().loadLevel(mc.screen,name);}return;}
    GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_MOBGRIEFING).set(false,null);
    var preset=ModList.get().isLoaded("eternal_tales")?WorldPresets.NORMAL:WorldPresets.FLAT;
    mc.createWorldOpenFlows().createFreshLevel("Adaptation-QA-"+System.currentTimeMillis(),new LevelSettings("Compatibility tests",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(7781,group.has("structures")&&group.get("structures").getAsBoolean(),false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions());return;
   }
   if(!testing&&mc.player!=null&&mc.player.tickCount>80&&mc.getSingleplayerServer()!=null){testing=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{run(server.getPlayerList().getPlayer(uuid));}catch(Throwable failure){error=failure;LogUtils.getLogger().error("MULTIMOD_QA FAIL",failure);}finally{finished=true;}});return;}
   if(finished&&++after>30){if(group.has("neuralMock")&&!NeuralNativeQa.clientTick())return;if(group.has("analysisDeep")&&!DeepAnalysisQa.clientTick())return;if(group.has("smartAnalysis")&&!SmartAnalysisQa.clientTick())return;if(group.has("utility")&&!UtilityQa.clientTick())return;LogUtils.getLogger().info("MULTIMOD_QA PASS: {}",group.get("name").getAsString());mc.stop();}
  }catch(Exception failure){throw new IllegalStateException("Compatibility QA",failure);}
 }
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static Object call(String cls,String method,Class<?>[] types,Object...args)throws Exception{
  try{return Class.forName(cls).getMethod(method,types).invoke(null,args);}catch(InvocationTargetException failure){throw new RuntimeException(failure.getCause());}
 }
 static Object state(ServerPlayer p)throws Exception{return call(BASE+"AdaptationEvents","state",new Class<?>[]{ServerPlayer.class},p);}
 static int stage(ServerPlayer p,String key)throws Exception{return (int)state(p).getClass().getMethod("stage",String.class).invoke(state(p),key);}
 static void learn(ServerPlayer p,String key,int stage)throws Exception{
  Object s=state(p);s.getClass().getMethod("hit",String.class,boolean.class).invoke(s,key,false);
  for(int n=0;n<70000&&stage(p,key)<stage;n++)s.getClass().getMethod("tick").invoke(s);
  check(stage(p,key)==stage,"Progress failed "+key);
 }
 static void wheelTick(ServerPlayer p)throws Exception{p.tickCount++;call(BASE+"AdaptationEvents","onTick",new Class<?>[]{TickEvent.PlayerTickEvent.class},new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
 static void reset(ServerPlayer p)throws Exception{
  p.removeAllEffects();p.clearFire();p.setTicksFrozen(0);p.setAbsorptionAmount(0);p.invulnerableTime=0;
  state(p).getClass().getMethod("reset").invoke(state(p));
  try{call(BASE+"SmartAttackAnalyzer","clear",new Class<?>[]{ServerPlayer.class},p);}catch(ClassNotFoundException ignored){}
  call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);
  p.setHealth(p.getMaxHealth());
  if(ModList.get().isLoaded("firstaid")){
   Object model=call("ichttt.mods.firstaid.common.util.CommonUtils","getDamageModel",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);
   for(Object part:(Iterable<?>)model)part.getClass().getMethod("setMaxHealth",int.class).invoke(part,1000);
  }
  call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);
 }
 static float health(ServerPlayer p)throws Exception{
  if(!ModList.get().isLoaded("firstaid"))return p.getHealth();
  Object model=call("ichttt.mods.firstaid.common.util.CommonUtils","getDamageModel",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);
  float sum=0;for(Object part:(Iterable<?>)model)sum+=part.getClass().getField("currentHealth").getFloat(part);return sum;
 }
 static void prepare(ServerPlayer p)throws Exception{
  p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;
  p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
  p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","wheel"))));
  p.teleportTo(p.getX(),-59,p.getZ());
  // Eternal Tales uses a normal-world fixture: dragon breath requires actual
  // line of sight, so carve a small arena instead of testing inside solid stone.
  BlockPos center=p.blockPosition();
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=7;y++)
   p.serverLevel().setBlock(center.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  reset(p);
 }
 static String family(String owner,String method)throws Exception{
  try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){
   var root=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
   for(var e:root.getAsJsonArray("entries")){var row=e.getAsJsonObject();if(row.get("owner").getAsString().endsWith(owner)&&row.get("method").getAsString().contains(method))return row.get("key").getAsString();}
  }throw new AssertionError("Missing family "+owner+" / "+method);
 }
 static void run(ServerPlayer p)throws Exception{
  if(group.has("neuralMock")){prepare(p);NeuralNativeQa.run(p);return;}
  if(group.has("analysisDeep")){prepare(p);DeepAnalysisQa.run(p);return;}
  // Part B continues a saved player: preparing would reset the adaptations learned in part A.
  if(group.has("utility")&&group.get("utility").getAsString().equals("outside-b"))return;
  if(group.has("utility")){prepare(p);UtilityQa.run(p);return;}
  if(group.has("smartAnalysis")){prepare(p);SmartAnalysisQa.run(p);return;}
  if(group.has("bossphases")){prepare(p);BossPhaseQa.run(p);return;}
  if(group.has("projectiles")){prepare(p);verifyClasses();projectileMatrix(p);return;}
  if(group.has("focus")){prepare(p);effectDuration(p);nested(p);sun(p);directHealth(p);lifesteal(p);zombie(p);return;}
  prepare(p);verifyClasses();effectDuration(p);verifyEffects(p);verifyRegistryDamage(p);
  if(ModList.get().isLoaded("aoa3"))extendedEffects(p);
  inheritedArrow(p);
  if(ModList.get().isLoaded("vampirism"))sun(p);
  if(ModList.get().isLoaded("botania"))directHealth(p);
  if(ModList.get().isLoaded("botania")&&ModList.get().isLoaded("vampirism"))nested(p);
  if(ModList.get().isLoaded("born_in_chaos_v1"))lifesteal(p);
  if(ModList.get().isLoaded("iceandfire"))ice(p);
  if(ModList.get().isLoaded("mowziesmobs"))mowzie(p);
  if(ModList.get().isLoaded("ad_astra"))space(p);
  if(ModList.get().isLoaded("weather2"))wind(p);
  if(ModList.get().isLoaded("iceandfire"))siren(p);
  if(ModList.get().isLoaded("born_in_chaos_v1")){cursedMark(p);shellMace(p);}
  controls(p);
  BossPhaseQa.run(p);
  nativeMelee(p);zombie(p);
  if(ModList.get().isLoaded("alexsmobs"))projectileException(p);
  // Native execution deliberately kills the vulnerable test player at the end.
  if(ModList.get().isLoaded("anomaly_rephased"))anomaly(p);
  LogUtils.getLogger().info("MULTIMOD_QA SERVER PASS: {}",group.get("name"));
 }
 static net.minecraft.world.entity.projectile.Projectile testProjectile(ServerPlayer p,ResourceLocation id,LivingEntity owner)throws Exception{
  var entity=ForgeRegistries.ENTITY_TYPES.getValue(id).create(p.level());
  if(!(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile)){if(entity!=null)entity.discard();return null;}
  projectile.setOwner(owner);projectile.setPos(p.getX(),p.getY()+1,p.getZ()-1);projectile.setDeltaMovement(0,0,1);
  projectile.tickCount=5;
  if(projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)arrow.setBaseDamage(1);
  try{projectile.getClass().getMethod("setDamage",float.class).invoke(projectile,1F);}catch(NoSuchMethodException ignored){}
  try{projectile.getClass().getMethod("setShadowArrowDamage",float.class).invoke(projectile,1F);}catch(NoSuchMethodException ignored){}
  if(projectile instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity){
   ItemStack rocket=new ItemStack(Items.FIREWORK_ROCKET);var fireworks=new net.minecraft.nbt.CompoundTag();var explosions=new net.minecraft.nbt.ListTag();var star=new net.minecraft.nbt.CompoundTag();star.putByte("Type",(byte)0);star.putIntArray("Colors",new int[]{0xFFAA33});explosions.add(star);fireworks.put("Explosions",explosions);fireworks.putByte("Flight",(byte)1);rocket.getOrCreateTag().put("Fireworks",fireworks);
   var saved=projectile.saveWithoutId(new net.minecraft.nbt.CompoundTag());saved.put("FireworksItem",rocket.save(new net.minecraft.nbt.CompoundTag()));projectile.load(saved);
  }
  p.serverLevel().addFreshEntity(projectile);return projectile;
 }
 static Method projectileHit(Class<?> cls)throws Exception{
  for(Class<?> c=cls;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod("m_6532_",net.minecraft.world.phys.HitResult.class);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException("Native projectile onHit");
 }
 static void projectileMatrix(ServerPlayer p)throws Exception{
  JsonObject entries;try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){entries=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonObject("projectiles");}
  // A QA-only cap prevents a baseline projectile from killing the fixture. It
  // changes test strength, never source identity, callbacks, or production code.
  java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> cap=e->{if(e.getEntity()==p)e.setAmount(Math.min(e.getAmount(),1F));};
  MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,cap);
  LivingEntity owner=spawn(p,"minecraft:zombie",4);LivingEntity monkey=ModList.get().isLoaded("alexsmobs")?spawn(p,"alexsmobs:capuchin_monkey",4):null;Vec3 position=p.position();int passed=0,unexercised=0;Set<String> namespaces=new TreeSet<>();List<String> failures=new ArrayList<>();
  owner.setInvulnerable(true);if(monkey!=null)monkey.setInvulnerable(true);
  try{
   for(var row:entries.entrySet()){
    ResourceLocation id=new ResourceLocation(row.getKey());if(!ForgeRegistries.ENTITY_TYPES.containsKey(id))continue;
    LivingEntity shooter=id.toString().equals("alexsmobs:tossed_item")?monkey:owner;
    reset(p);p.teleportTo(position.x,position.y,position.z);p.setDeltaMovement(Vec3.ZERO);
    var projectile=testProjectile(p,id,shooter);if(projectile==null){unexercised++;LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE_UNEXERCISED: {} is not a Projectile",id);continue;}
    Method hit=projectileHit(projectile.getClass());float before=health(p);String fixtureFailure=null;
    try{hit.invoke(projectile,new net.minecraft.world.phys.EntityHitResult(p));}catch(InvocationTargetException e){fixtureFailure=e.getCause().toString();}finally{projectile.discard();}
    check(p.isAlive(),"Projectile fixture died before protection test: "+id);
    if(fixtureFailure!=null||health(p)>=before){unexercised++;LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE_UNEXERCISED: {} no immediate HP baseline; {}",id,fixtureFailure);continue;}
    wheelTick(p);List<String> keys=new ArrayList<>();
    for(Object snapshot:(List<?>)state(p).getClass().getMethod("snapshots").invoke(state(p))){String key=(String)snapshot.getClass().getMethod("key").invoke(snapshot);if(key.startsWith("attack:"))keys.add(key);}
    check(!keys.isEmpty(),"Real projectile HP loss did not start an attack adaptation: "+id);
    // Learn only its observed ability keys; mob-wide and generic damage immunity
    // must not hide a missing specific integration.
    state(p).getClass().getMethod("reset").invoke(state(p));call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);
    for(String key:keys)learn(p,key,4);
    p.removeAllEffects();p.clearFire();p.setDeltaMovement(Vec3.ZERO);p.setHealth(p.getMaxHealth());p.invulnerableTime=0;p.teleportTo(position.x,position.y,position.z);
    projectile=testProjectile(p,id,shooter);before=health(p);
    boolean protectedHp;
    try{projectileHit(projectile.getClass()).invoke(projectile,new net.minecraft.world.phys.EntityHitResult(p));protectedHp=p.isAlive()&&health(p)==before;}finally{projectile.discard();}
    if(!protectedHp){failures.add(id+" immunity "+keys);LogUtils.getLogger().error("MULTIMOD_QA PROJECTILE_MATRIX FAILURE: {} specific immunity left HP loss; {}",id,keys);continue;}
    if(id.toString().startsWith("sculkhorde:soul_"))check(p.getDeltaMovement().lengthSqr()==0&&!p.isOnFire()&&p.getActiveEffects().isEmpty(),"Full Soul projectile immunity left fire, forces or secondary effects: "+id);
    for(String key:keys)controlDisable(p,key);
    p.removeAllEffects();p.clearFire();p.invulnerableTime=0;projectile=testProjectile(p,id,shooter);before=health(p);
    boolean disabledHurt;
    try{projectileHit(projectile.getClass()).invoke(projectile,new net.minecraft.world.phys.EntityHitResult(p));disabledHurt=health(p)<before;}finally{projectile.discard();}
    if(!disabledHurt){failures.add(id+" disable "+keys);LogUtils.getLogger().error("MULTIMOD_QA PROJECTILE_MATRIX FAILURE: {} disabled protection did not reproduce damage",id);continue;}
    passed++;namespaces.add(id.getNamespace());LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE_MATRIX PASS: {} actual HP loss -> specific full immunity -> disabled HP loss; {}",id,keys);
   }
  }finally{MinecraftForge.EVENT_BUS.unregister(cap);owner.discard();if(monkey!=null)monkey.discard();reset(p);}
  check(failures.isEmpty(),"Projectile matrix actual failures: "+failures);check(passed>0,"No real projectiles exercised");LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE_MATRIX SUMMARY PASS: {} native projectile types; {} namespaces {}; {} require ability-specific fixtures",passed,namespaces.size(),namespaces,unexercised);
 }
 static void animation(Entity mob,String field,int tick)throws Exception{
  Class<?> cls=Class.forName("com.github.alexthe666.citadel.animation.Animation");
  mob.getClass().getMethod("setAnimation",cls).invoke(mob,mob.getClass().getField(field).get(null));
  mob.getClass().getMethod("setAnimationTick",int.class).invoke(mob,tick);
 }
 static void controlDisable(ServerPlayer p,String key)throws Exception{state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,false);}
 static void release(ServerPlayer p)throws Exception{
  if(p.getVehicle()!=null&&ModList.get().isLoaded("twilightforest"))call("twilightforest.events.HostileMountEvents","hostileDismount",new Class<?>[]{Entity.class},p);
  else p.stopRiding();
 }
 static void controlCaptureAttack(ServerPlayer p,Mob mob)throws Exception{
  mob.setTarget(p);
  String id=ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString();
  if(id.equals("alexsmobs:crocodile")){animation(mob,"ANIMATION_LUNGE",6);mob.tick();}
  else if(id.equals("twilightforest:pinch_beetle"))mob.doHurtTarget(p);
  else{
   Class<?> cls=Class.forName("twilightforest.entity.ai.goal.ThrowRiderGoal");Object goal=cls.getConstructor(PathfinderMob.class,double.class,boolean.class).newInstance(mob,1D,false);
   Method attack=cls.getDeclaredMethod("m_6739_",LivingEntity.class,double.class);attack.setAccessible(true);attack.invoke(goal,p,0D);
  }
 }
 static void controls(ServerPlayer p)throws Exception{
  Vec3 origin=p.position();
  for(String id:List.of("alexsmobs:crocodile","twilightforest:pinch_beetle","twilightforest:yeti","twilightforest:alpha_yeti")){
   if(!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(id)))continue;
   reset(p);p.setDeltaMovement(Vec3.ZERO);Mob mob=(Mob)spawn(p,id,1.5);
   if(mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null)mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);
   try{
    String key="condition:"+id.replace(":",":capture/");controlCaptureAttack(p,mob);
    check(p.getVehicle()==mob,"Native grab absent "+id);wheelTick(p);check(stage(p,key)==0&&((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native capture failed to start adaptation "+id);
    learn(p,key,4);wheelTick(p);check(p.getVehicle()==null,"Full capture immunity did not release existing hold "+id);
    p.teleportTo(origin.x,origin.y,origin.z);p.invulnerableTime=0;controlCaptureAttack(p,mob);check(p.getVehicle()==null,"Immune player recaptured "+id);
    // The prior snatching dismount must also preserve an ordinary voluntary mount.
    var boat=new net.minecraft.world.entity.vehicle.Boat(p.level(),p.getX(),p.getY(),p.getZ());p.serverLevel().addFreshEntity(boat);p.startRiding(boat,true);
    controlCaptureAttack(p,mob);check(p.getVehicle()==boat,"Blocked grab dismounted ordinary boat "+id);release(p);boat.discard();
    controlDisable(p,key);p.teleportTo(origin.x,origin.y,origin.z);controlCaptureAttack(p,mob);check(p.getVehicle()==mob,"Disabled capture immunity still active "+id);
   }finally{release(p);mob.discard();p.teleportTo(origin.x,origin.y,origin.z);reset(p);}
   LogUtils.getLogger().info("MULTIMOD_QA CAPTURE PASS: {} native grab starts adaptation, existing hold released, recapture blocked, boat retained and disable restored",id);
  }
  if(ModList.get().isLoaded("alexsmobs")){
   reset(p);Mob snake=(Mob)spawn(p,"alexsmobs:anaconda",2);String key="condition:alexsmobs:constriction";
   try{
    snake.setTarget(p);Method set=snake.getClass().getMethod("setStrangling",boolean.class),get=snake.getClass().getMethod("isStrangling");
    set.invoke(snake,true);p.setOnGround(false);p.setDeltaMovement(new Vec3(1,0,0));snake.tick();check(p.getDeltaMovement().x==0,"Native constriction did not clamp motion");
    learn(p,key,1);p.setOnGround(false);p.setDeltaMovement(new Vec3(1,0,0));snake.tick();check(Math.abs(p.getDeltaMovement().x-.2)<1E-6,"Partial constriction did not weaken actual motion");
    learn(p,key,4);p.setDeltaMovement(new Vec3(1,0,0));snake.tick();check(!(boolean)get.invoke(snake)&&p.getDeltaMovement().x==1,"Existing full constriction did not release");
    set.invoke(snake,true);check(!(boolean)get.invoke(snake),"Immune constriction reapplied");controlDisable(p,key);set.invoke(snake,true);check((boolean)get.invoke(snake),"Disabled constriction still blocked");
   }finally{snake.discard();reset(p);}
   LogUtils.getLogger().info("MULTIMOD_QA CONSTRICTION PASS: native snake tick clamps motion, first stage weakens 20%, full releases and blocks reapplication, disable restored");
  }
  if(ModList.get().isLoaded("alexscaves")){
   reset(p);Mob forsaken=(Mob)spawn(p,"alexscaves:forsaken",2);String key="condition:alexscaves:forsaken_hold";
   try{
    forsaken.setTarget(p);Method set=forsaken.getClass().getMethod("setHeldMobId",int.class),get=forsaken.getClass().getMethod("getHeldMobId");
    animation(forsaken,"ANIMATION_RIGHT_PICKUP",20);set.invoke(forsaken,p.getId());p.setDeltaMovement(Vec3.ZERO);forsaken.tick();double force=p.getDeltaMovement().length();check(force>0,"Native Forsaken hold did not pull");
    learn(p,key,1);animation(forsaken,"ANIMATION_RIGHT_PICKUP",20);p.setDeltaMovement(Vec3.ZERO);forsaken.tick();check(Math.abs(p.getDeltaMovement().length()-force*.8)<1E-5,"Partial Forsaken hold wrong");
    learn(p,key,4);p.setDeltaMovement(Vec3.ZERO);forsaken.tick();check((int)get.invoke(forsaken)==-1&&p.getDeltaMovement().lengthSqr()==0,"Full Forsaken hold not cleared");
    set.invoke(forsaken,p.getId());check((int)get.invoke(forsaken)==-1,"Immune Forsaken hold reapplied");controlDisable(p,key);set.invoke(forsaken,p.getId());check((int)get.invoke(forsaken)==p.getId(),"Disabled Forsaken hold blocked");
   }finally{forsaken.discard();reset(p);}
   LogUtils.getLogger().info("MULTIMOD_QA FORSAKEN PASS: native pickup pulls player, first stage weakens 20%, full clears native held ID and prevents reapplication, disable restored");
   wave(p);
  }
  if(ModList.get().isLoaded("iceandfire"))gorgon(p);
  if(ModList.get().isLoaded("born_in_chaos_v1"))krampus(p);
  statusProjectiles(p);
  specialProjectiles(p);
 }
 static boolean specialImpact(ServerPlayer p,String id,LivingEntity owner)throws Exception{
  Entity shot=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)).create(p.level());shot.setPos(p.position());p.serverLevel().addFreshEntity(shot);
  try{
   if(id.equals("alexscaves:candy_cane_hook")){((net.minecraft.world.entity.projectile.Projectile)shot).setOwner(owner);shot.getClass().getMethod("setDamage",float.class).invoke(shot,1F);shot.tick();}
   else{
    shot.getClass().getMethod("setShooter",Entity.class).invoke(shot,owner);
    Object resolved=shot.getClass().getMethod("getShooter").invoke(shot);
    if(!(resolved instanceof LivingEntity)){LogUtils.getLogger().info("MULTIMOD_QA SPECIAL_PROJECTILE_UNEXERCISED: {} native getShooter cannot resolve freshly spawned owner UUID; no original damage callback is possible in this fixture",id);return false;}
    Method hit=null;for(Class<?> c=shot.getClass();c!=null&&hit==null;c=c.getSuperclass())try{hit=c.getDeclaredMethod("onEntityHit",net.minecraft.world.phys.EntityHitResult.class);}catch(NoSuchMethodException ignored){}
    check(hit!=null,"Custom native collision missing "+id);hit.setAccessible(true);hit.invoke(shot,new net.minecraft.world.phys.EntityHitResult(p));
   }
   return true;
  }finally{shot.discard();}
 }
 static void specialProjectiles(ServerPlayer p)throws Exception{
  for(String id:List.of("alexsmobs:pollen_ball","alexsmobs:mud_ball","alexscaves:candy_cane_hook")){
   if(!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(id)))continue;
   LivingEntity owner=spawn(p,id.equals("alexsmobs:pollen_ball")?"alexsmobs:flutter":id.equals("alexsmobs:mud_ball")?"alexsmobs:mudskipper":"minecraft:zombie",4);owner.setInvulnerable(true);
   try{reset(p);float hp=health(p);if(!specialImpact(p,id,owner)){reset(p);continue;}
   if(health(p)>=hp&&ModList.get().isLoaded("firstaid")){
    ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD);float without=health(p);p.invulnerableTime=0;
    try{p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(specialImpact(p,id,owner),"Native bare-head control lost shooter "+id);}finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
    check(health(p)==without,"Wheel suppressed unlearned special projectile while native bare-head hit hurts: "+id);
    LogUtils.getLogger().info("MULTIMOD_QA SPECIAL_PROJECTILE_UNEXERCISED: {} native First Aid fixture has no HP loss both with and without wheel; no immunity success claimed",id);reset(p);continue;
   }
   check(health(p)<hp,"Special projectile baseline did not hurt "+id+" limbHP="+hp+" -> "+health(p)+" raw="+p.getHealth()+" absorption="+p.getAbsorptionAmount()+" invul="+p.invulnerableTime);wheelTick(p);
   String key="attack:"+id.replace(":",":projectile/");check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Special projectile source missing "+id);
   state(p).getClass().getMethod("reset").invoke(state(p));call(BASE+"AdaptationEvents","discardQueuedDamage",new Class<?>[]{ServerPlayer.class},p);learn(p,key,4);
   p.removeAllEffects();p.invulnerableTime=0;hp=health(p);check(specialImpact(p,id,owner),"Immune shot lost its native shooter "+id);check(health(p)==hp,"Full special projectile immunity failed "+id);
   controlDisable(p,key);p.invulnerableTime=0;check(specialImpact(p,id,owner),"Disabled shot lost native shooter "+id);check(health(p)<hp,"Disabled special projectile protection blocked damage "+id);reset(p);
   LogUtils.getLogger().info("MULTIMOD_QA SPECIAL_PROJECTILE PASS: {} actual native custom collision/tick HP loss, specific full immunity, disabled HP loss",id);
   }finally{owner.discard();reset(p);}
  }
 }
 static void statusImpact(ServerPlayer p,String id)throws Exception{
  Entity entity=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)).create(p.level());entity.setPos(p.position());p.serverLevel().addFreshEntity(entity);
  try{
   if(id.equals("alexsmobs:mud_ball")){Method m=entity.getClass().getDeclaredMethod("onEntityHit",net.minecraft.world.phys.EntityHitResult.class);m.setAccessible(true);m.invoke(entity,new net.minecraft.world.phys.EntityHitResult(p));}
   else projectileHit(entity.getClass()).invoke(entity,new net.minecraft.world.phys.EntityHitResult(p));
  }finally{entity.discard();}
 }
 static void statusProjectiles(ServerPlayer p)throws Exception{
  for(String id:List.of("alexscaves:ink_bomb","undergarden:goo_ball","alexsmobs:mud_ball")){
   if(!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(id)))continue;
   reset(p);statusImpact(p,id);List<MobEffect> effects=p.getActiveEffects().stream().map(MobEffectInstance::getEffect).filter(e->{try{return (boolean)call(BASE+"NegativeEffects","harmful",new Class<?>[]{MobEffect.class},e);}catch(Exception failure){throw new RuntimeException(failure);}}).toList();
   check(!effects.isEmpty(),"Native status projectile did not apply harmful effect "+id);wheelTick(p);
   for(MobEffect effect:effects)learn(p,"effect:"+ForgeRegistries.MOB_EFFECTS.getKey(effect),4);p.removeAllEffects();p.invulnerableTime=0;statusImpact(p,id);
   for(MobEffect effect:effects)check(!p.hasEffect(effect),"Native status projectile bypassed effect immunity "+id);
   for(MobEffect effect:effects)controlDisable(p,"effect:"+ForgeRegistries.MOB_EFFECTS.getKey(effect));p.invulnerableTime=0;statusImpact(p,id);
   for(MobEffect effect:effects)check(p.hasEffect(effect),"Disabled effect immunity still blocked native projectile "+id);reset(p);
   LogUtils.getLogger().info("MULTIMOD_QA STATUS_PROJECTILE PASS: {} actual harmful effect application -> immunity -> disabled application; {}",id,effects.stream().map(e->ForgeRegistries.MOB_EFFECTS.getKey(e).toString()).toList());
  }
 }
 static void wave(ServerPlayer p)throws Exception{
  reset(p);LivingEntity owner=spawn(p,"minecraft:zombie",4);owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(150);owner.setHealth(150);String key="attack:alexscaves:projectile/wave";
  try{
   float hp=health(p);p.setDeltaMovement(Vec3.ZERO);nativeWave(p,owner);double force=p.getDeltaMovement().horizontalDistance();
   check(health(p)<hp&&force>0,"Native wave did not damage and knock back");wheelTick(p);check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native wave source attribution missing");
   // Isolate the wave's explicit force from ordinary hurt knockback. The native
   // hurt is already in its normal invulnerability window for both measurements.
   p.invulnerableTime=20;p.setDeltaMovement(Vec3.ZERO);nativeWave(p,owner);force=p.getDeltaMovement().horizontalDistance();
   learn(p,key,1);p.invulnerableTime=20;p.setDeltaMovement(Vec3.ZERO);nativeWave(p,owner);check(Math.abs(p.getDeltaMovement().horizontalDistance()-force*.8)<1E-5,"Partial wave knockback wrong: "+force+" -> "+p.getDeltaMovement().horizontalDistance());
   learn(p,key,4);p.invulnerableTime=0;p.setDeltaMovement(Vec3.ZERO);hp=health(p);nativeWave(p,owner);check(health(p)==hp&&p.getDeltaMovement().lengthSqr()==0,"Full wave immunity left HP damage or knockback");
   controlDisable(p,key);p.invulnerableTime=0;hp=health(p);nativeWave(p,owner);check(health(p)<hp&&p.getDeltaMovement().horizontalDistance()>0,"Disabled wave protection still active");
   wheelTick(p);learn(p,"mob:minecraft:zombie",5);p.invulnerableTime=0;p.setDeltaMovement(Vec3.ZERO);hp=health(p);nativeWave(p,owner);check(health(p)==hp&&p.getDeltaMovement().lengthSqr()==0,"Boss existence immunity left wave damage or force");
  }finally{owner.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA WAVE PASS: native tick damages and knocks back, partial force reduced 20%, full HP/force immunity, disable restored, boss existence also blocks force");
 }
 static void nativeWave(ServerPlayer p,LivingEntity owner)throws Exception{
  Entity wave=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("alexscaves:wave")).create(p.level());wave.setPos(p.position());wave.setYRot(0);p.serverLevel().addFreshEntity(wave);
  try{wave.getClass().getMethod("setOwner",LivingEntity.class).invoke(wave,owner);wave.getClass().getMethod("setWaitingTicks",int.class).invoke(wave,0);wave.tick();}finally{wave.discard();}
 }
 static void projectileException(ServerPlayer p)throws Exception{
  reset(p);LivingEntity owner=spawn(p,"minecraft:zombie",3);var arrow=testProjectile(p,new ResourceLocation("alexsmobs:shark_tooth_arrow"),owner);
  java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingAttackEvent> fail=e->{if(e.getEntity()==p)throw new IllegalStateException("QA expected projectile-scope exit");};
  MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,fail);boolean expected=false;
  try{projectileHit(arrow.getClass()).invoke(arrow,new net.minecraft.world.phys.EntityHitResult(p));}
  catch(InvocationTargetException e){for(Throwable cause=e;cause!=null;cause=cause.getCause())if(String.valueOf(cause.getMessage()).contains("QA expected projectile-scope exit"))expected=true;}
  finally{MinecraftForge.EVENT_BUS.unregister(fail);arrow.discard();owner.discard();}
  check(expected,"Native projectile exception fixture not exercised");zombie(p);
  for(Object snapshot:(List<?>)state(p).getClass().getMethod("snapshots").invoke(state(p)))check(!((String)snapshot.getClass().getMethod("key").invoke(snapshot)).startsWith("attack:"),"Projectile callback context leaked into next ordinary melee");
  reset(p);LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE_EXIT PASS: deliberately thrown native event closes collision and hurt scopes; subsequent ordinary zombie retains its own source, x1.30 and 20% protection");
 }
 static void krampusNative(ServerPlayer p,Entity attacker)throws Exception{
  call("net.mcreator.borninchaosv.procedures.KrampusAttackProcedure","execute",new Class<?>[]{LevelAccessor.class,double.class,double.class,double.class,Entity.class,Entity.class},p.level(),p.getX(),p.getY(),p.getZ(),p,attacker);
 }
 static void krampus(ServerPlayer p)throws Exception{
  reset(p);var mob=spawn(p,"born_in_chaos_v1:krampus",2);String key="condition:born_in_chaos_v1:krampus_disarm";
  try{
   p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));for(int n=0;n<64&&!p.getMainHandItem().isEmpty();n++)krampusNative(p,mob);
   check(p.getMainHandItem().isEmpty(),"Native Krampus failed to disarm baseline");wheelTick(p);check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Disarm did not start its own adaptation");
   learn(p,key,4);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));for(int n=0;n<64;n++)krampusNative(p,mob);
   check(p.getMainHandItem().is(Items.DIAMOND_SWORD)&&p.getMainHandItem().getCount()==1,"Full Krampus immunity lost held weapon");
   controlDisable(p,key);for(int n=0;n<64&&!p.getMainHandItem().isEmpty();n++)krampusNative(p,mob);check(p.getMainHandItem().isEmpty(),"Disabled Krampus immunity still blocks native theft");
  }finally{p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);mob.discard();for(var item:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(5)))item.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA KRAMPUS PASS: actual native procedure removes weapon, starts separate adaptation, full protection preserves weapon across 64 attempts, disable restores theft");
 }
 static void gazeAt(LivingEntity a,LivingEntity b){a.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,b.getEyePosition());a.setYHeadRot(a.getYRot());a.yBodyRot=a.getYRot();}
 static void gorgon(ServerPlayer p)throws Exception{
  reset(p);Mob g=(Mob)spawn(p,"iceandfire:gorgon",3);String key=family("EntityGorgon","m_8107_");
  java.util.function.LongSupplier statues=()->p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(12)).stream().filter(e->ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals("iceandfire:stone_statue")).count();
  try{
   g.setTarget(p);gazeAt(g,p);gazeAt(p,g);animation(g,"ANIMATION_SCARE",0);g.aiStep();
   check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native preparatory gaze did not start Gorgon adaptation");
   learn(p,key,4);gazeAt(g,p);gazeAt(p,g);animation(g,"ANIMATION_SCARE",11);long before=statues.getAsLong();float hp=health(p);g.aiStep();
   check(p.isAlive()&&health(p)==hp&&statues.getAsLong()==before,"Immune Gorgon gaze hurt or created fake statue");
   controlDisable(p,key);gazeAt(g,p);gazeAt(p,g);check((boolean)call(BASE+"MobControlCompatibility","gorgonLooking",new Class<?>[]{LivingEntity.class,LivingEntity.class,double.class},p,g,.4D),"Disabled Gorgon gaze remained blocked");
   // Exercise the original lethal statue procedure on a nonplayer fixture.
   LivingEntity cow=spawn(p,"minecraft:cow",2);cow.setPos(g.getX()-3,g.getY(),g.getZ());g.setTarget(cow);gazeAt(g,cow);gazeAt(cow,g);animation(g,"ANIMATION_SCARE",11);g.aiStep();
   check(cow.isRemoved()&&statues.getAsLong()>before,"Native vulnerable Gorgon petrification was not exercised");cow.discard();
  }finally{g.discard();for(var e:p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(12)))if(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals("iceandfire:stone_statue"))e.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA GORGON PASS: actual early gaze starts learning, full immunity prevents real death and statue creation, disable restored; native cow petrification verified");
 }
 static void effectDuration(ServerPlayer p)throws Exception {
  reset(p);var effect=(LifecycleEffect)LIFECYCLE.get();String key="effect:multimodqa:lifecycle";learn(p,key,1);
  p.addEffect(new MobEffectInstance(effect,1000,0));p.addEffect(new MobEffectInstance(effect,200,1));
  MobEffectInstance original=p.getEffect(effect);int added=effect.added,removed=effect.removed;
  call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
  check(p.getEffect(effect)==original,"Partial adaptation replaced native instance");
  check(original.getDuration()==160,"Expected 200 ticks ->160: "+original.getDuration());
  check(original.save(new net.minecraft.nbt.CompoundTag()).getCompound("HiddenEffect").getInt("Duration")==800,"Hidden weaker effect escaped shortening");
  check(effect.added==added&&effect.removed==removed,"Duration adjustment fired native start/expiry callbacks again");
  for(int n=0;n<8;n++)call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
  check(original.getDuration()==160,"Duration shrank repeatedly without a new application");
  reset(p);p.addEffect(new MobEffectInstance(effect,100,0));effect.applyEffectTick(p,0);wheelTick(p);
  for(Object snapshot:(List<?>)state(p).getClass().getMethod("snapshots").invoke(state(p)))if(snapshot.getClass().getMethod("key").invoke(snapshot).equals(key))
   check((int)snapshot.getClass().getMethod("hits").invoke(snapshot)==0,"First effect tick added a fake acceleration bonus");
  reset(p);LogUtils.getLogger().info("MULTIMOD_QA LIFECYCLE PASS: original instance retained; 200->160, hidden 1000->800; no extra start/expiry or per-tick acceleration");
 }
 static void siren(ServerPlayer p)throws Exception {
  reset(p);String key="condition:iceandfire:siren_charm";var siren=spawn(p,"iceandfire:siren",10);
  Class<?> cls=Class.forName("com.github.alexthe666.iceandfire.entity.props.SirenData");Object data=cls.getConstructor().newInstance();
  Method tick=cls.getMethod("tickCharmed",LivingEntity.class);tick.invoke(data,p);
  siren.getClass().getMethod("setSinging",boolean.class).invoke(siren,true);
  try {
   cls.getMethod("setCharmed",Entity.class).invoke(data,siren);p.setDeltaMovement(Vec3.ZERO);tick.invoke(data,p);
   double force=p.getDeltaMovement().length();check(force>0,"Native siren charm did not pull player");check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Siren exposure missing");
   learn(p,key,1);p.setDeltaMovement(Vec3.ZERO);tick.invoke(data,p);check(Math.abs(p.getDeltaMovement().length()-force*0.8)<1E-7,"Native charm partial pull wrong");
   learn(p,key,4);p.setDeltaMovement(Vec3.ZERO);tick.invoke(data,p);check(!cls.getField("isCharmed").getBoolean(data)&&cls.getField("charmedBy").get(data)==null&&p.getDeltaMovement().lengthSqr()==0,"Siren native charm/controller survived immunity");
   cls.getMethod("setCharmed",Entity.class).invoke(data,siren);tick.invoke(data,p);check(!cls.getField("isCharmed").getBoolean(data),"Siren charm returned after immunity");
   state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,false);cls.getMethod("setCharmed",Entity.class).invoke(data,siren);p.setDeltaMovement(Vec3.ZERO);tick.invoke(data,p);check(p.getDeltaMovement().length()>0,"Disabled siren adaptation still blocks pull");
  }finally{siren.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA SIREN PASS: native pull starts learning; 20% partial reduction; full native cleanup; reapplication and disable tested");
 }
 static List<Entity> persecutors(ServerPlayer p){return p.serverLevel().getEntities(p,p.getBoundingBox().inflate(12),e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("born_in_chaos_v1:scarlet_persecutor"));}
 static void cursedMark(ServerPlayer p)throws Exception {
  reset(p);var effect=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("born_in_chaos_v1","cursed_mark"));check(effect!=null,"Cursed mark fixture absent");String key="effect:born_in_chaos_v1:cursed_mark";
  // A real native expiry must spawn persecutors, proving this test reaches that code.
  p.addEffect(new MobEffectInstance(effect,200,0));p.removeEffect(effect);check(!persecutors(p).isEmpty(),"Native cursed-mark expiry did not summon fixture mobs");persecutors(p).forEach(Entity::discard);
  reset(p);learn(p,key,1);p.addEffect(new MobEffectInstance(effect,200,0));MobEffectInstance original=p.getEffect(effect);call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
  check(p.getEffect(effect)==original&&original.getDuration()==160&&persecutors(p).isEmpty(),"Shortening cursed mark triggered hostile expiry");
  learn(p,key,4);call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);check(!p.hasEffect(effect)&&persecutors(p).isEmpty(),"Adaptation purge summoned native cursed-mark enemies");
  reset(p);LogUtils.getLogger().info("MULTIMOD_QA CURSEDMARK PASS: native expiry summons verified; partial duration and full purge do not summon enemies");
 }
 static void shellMace(ServerPlayer p)throws Exception {
  reset(p);String owner="net.mcreator.borninchaosv.procedures.ShellMacePriUdariePoSushchnostiInstrumientomProcedure",key=family("ShellMacePriUdariePoSushchnostiInstrumientomProcedure","execute");
  var attacker=EntityType.ZOMBIE.create(p.level());attacker.moveTo(p.position());
  // The audited native attack requires both entities actually submerged.
  var water=net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();BlockPos at=p.blockPosition();
  for(int y=0;y<3;y++)p.serverLevel().setBlock(at.above(y),water,2);
  Method fluid=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Entity.class,"m_20073_");fluid.invoke(p);fluid.invoke(attacker);
  try {
   check(p.isInWater()&&attacker.isInWater(),"Shell mace water fixture invalid");
   Method attack=Arrays.stream(Class.forName(owner).getMethods()).filter(m->m.getName().equals("execute")).findFirst().orElseThrow();
   Object[] args=attack.getParameterCount()==5?new Object[]{p.level(),p.getX(),p.getY(),p.getZ(),p}:new Object[]{p.level(),p.getX(),p.getY(),p.getZ(),p,attacker};
   var marker=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("born_in_chaos_v1","barbedattack"));check(marker!=null,"Native mace cooldown marker absent");
   float baseline=p.getHealth(),before=health(p);attack.invoke(null,args);wheelTick(p);float loss=before-health(p);check(loss>0,"Native shell mace did not remove HP/limb health");
   check(!((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey("damage:minecraft:generic"),"First Aid mislabeled native direct HP as generic damage");
   learn(p,key,1);if(ModList.get().isLoaded("firstaid"))call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);else p.setHealth(baseline);
   p.removeEffect(marker);before=health(p);attack.invoke(null,args);float partial=before-health(p);check(Math.abs(partial-loss*0.8F)<0.02,"Shell mace direct HP partial reduction wrong: "+loss+" -> "+partial);
   learn(p,key,4);p.removeEffect(marker);before=health(p);attack.invoke(null,args);check(health(p)==before,"Shell mace direct HP bypassed full adaptation");
  }finally{for(int y=0;y<3;y++)p.serverLevel().setBlock(at.above(y),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);attacker.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA SHELLMACE PASS: real submerged native attack loses HP, reduced 20%, then blocked completely");
 }
 static void anomaly(ServerPlayer p)throws Exception {
  reset(p);String key="condition:anomaly_rephased:execution";
  var attacker=EntityType.ZOMBIE.create(p.level());attacker.moveTo(p.position());
  Class<?> procedure=Class.forName("net.mcreator.anomalyrephased.procedures.AHuntOnEntityTickUpdateProcedure");
  Method kill=procedure.getDeclaredMethod("lambda$execute$23",Entity.class,LevelAccessor.class,double.class,double.class,double.class);kill.setAccessible(true);
  // The preceding native preparation lambda applies paralysis and queues execution.
  Method prepare=procedure.getDeclaredMethod("lambda$execute$24",Entity.class,LevelAccessor.class,double.class,double.class,double.class);prepare.setAccessible(true);
  prepare.invoke(null,attacker,p.level(),p.getX(),p.getY(),p.getZ());wheelTick(p);
  check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native anomaly preparation did not start adaptation");
  learn(p,key,4);kill.invoke(null,attacker,p.level(),p.getX(),p.getY(),p.getZ());check(p.isAlive(),"Native anomaly execution bypassed full adaptation");
  state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,false);
  kill.invoke(null,attacker,p.level(),p.getX(),p.getY(),p.getZ());check(!p.isAlive(),"Disabled anomaly adaptation still prevented native execution");
  attacker.discard();LogUtils.getLogger().info("MULTIMOD_QA ANOMALY PASS: native preparation starts learning; actual execution blocked at full adaptation; disabled execution really kills");
 }
 static void extendedEffects(ServerPlayer p)throws Exception {
  reset(p);var bleed=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("aoa3","bleeding"));check(bleed!=null,"AoA bleeding fixture absent");
  Method tick=bleed.getClass().getMethod("tick",LivingEntity.class,MobEffectInstance.class,int.class);
  MobEffectInstance instance=new MobEffectInstance(bleed,300,1);p.addEffect(instance);float before=health(p);tick.invoke(bleed,p,instance,1);wheelTick(p);
  check(health(p)<before,"Native ExtendedMobEffect bleeding did not hurt");String key="effect:aoa3:bleeding";
  check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native extended bleeding exposure missing");
  learn(p,key,4);before=health(p);float exhaustion=p.getFoodData().getExhaustionLevel();p.invulnerableTime=0;tick.invoke(bleed,p,instance,1);
  check(health(p)==before&&p.getFoodData().getExhaustionLevel()==exhaustion,"Immune extended bleeding damaged/exhausted player before effect cleanup");
  reset(p);var burned=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("aoa3","burned"));check(burned!=null,"AoA burned fixture absent");
  Method modify=burned.getClass().getMethod("modifyIncomingAttackDamage",LivingEntity.class,MobEffectInstance.class,DamageSource.class,float.class);
  MobEffectInstance burn=new MobEffectInstance(burned,300,0);float nativeAmount=(float)modify.invoke(burned,p,burn,p.damageSources().fall(),10F);check(nativeAmount>10,"Native burn debuff fixture absent");
  learn(p,"effect:aoa3:burned",4);check((float)modify.invoke(burned,p,burn,p.damageSources().fall(),10F)==10F,"Immune burn still amplifies damage");
  state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),"effect:aoa3:burned",false);check((float)modify.invoke(burned,p,burn,p.damageSources().fall(),10F)==nativeAmount,"Disabled burn adaptation still changes native multiplier");
  reset(p);LogUtils.getLogger().info("MULTIMOD_QA EXTENDEDEFFECTS PASS: actual Tslat bleeding HP/exhaustion and burn damage multiplier blocked; native tick, application and disable preserved");
 }
 static void nativeMelee(ServerPlayer p)throws Exception {
  Set<String> wanted=new LinkedHashSet<>();for(var mod:group.getAsJsonArray("mods"))wanted.add(mod.getAsString());
  var types=new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getKeys());types.sort(Comparator.comparing(ResourceLocation::toString));Set<String> tested=new LinkedHashSet<>();
  for(var id:types){
   if(!wanted.contains(id.getNamespace())||tested.contains(id.getNamespace()))continue;
   Entity entity=ForgeRegistries.ENTITY_TYPES.getValue(id).create(p.level());if(entity==null)continue;
   try {
    if(!(entity instanceof Mob mob)||mob.getMaxHealth()>100||mob.getAttribute(Attributes.ATTACK_DAMAGE)==null)continue;
    // Test the exact vanilla melee implementation inherited by mod creatures;
    // special overrides have their own manifest/native ability tests.
    String attackName=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Mob.class,"m_7327_",Entity.class).getName();
    var implementation=mob.getClass().getMethod(attackName,Entity.class).getDeclaringClass().getName();
    if(!implementation.startsWith("net.minecraft."))continue;
    reset(p);mob.moveTo(p.position());mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);
    float before=health(p);mob.doHurtTarget(p);wheelTick(p);float loss=before-health(p);if(loss<=0)continue;
    String key=(String)call(BASE+"DamageCategories","forEntity",new Class<?>[]{LivingEntity.class},mob);
    check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Real mod melee did not start species adaptation: "+id);
    // Learning via fast state ticks advances every pending source. Isolate the
    // species under test so a separately learned damage-type entry cannot mask disable.
    state(p).getClass().getMethod("reset").invoke(state(p));
    learn(p,key,4);p.invulnerableTime=0;before=health(p);mob.doHurtTarget(p);check(health(p)==before,"Real inherited mod melee bypassed full adaptation: "+id);
    state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,false);p.invulnerableTime=0;mob.doHurtTarget(p);check(health(p)<before,"Disabled mod melee adaptation still protects: "+id);
    tested.add(id.getNamespace());LogUtils.getLogger().info("MULTIMOD_QA MODMELEE PASS: {} native inherited melee; first HP/limb loss, full immunity, disable",id);
   }finally{entity.discard();reset(p);}
  }
  LogUtils.getLogger().info("MULTIMOD_QA MODMELEE MATRIX: {} namespaces with native inherited attackers tested: {}",tested.size(),tested);
 }
 static void zombie(ServerPlayer p)throws Exception{
  reset(p);var zombie=EntityType.ZOMBIE.create(p.level());check(zombie!=null,"Zombie fixture missing");
  float loss=0;
  try{
   for(int n=0;n<2;n++){p.invulnerableTime=0;float before=health(p);zombie.doHurtTarget(p);wheelTick(p);check(health(p)<before,"Real zombie hit did not damage");loss=before-health(p);}
   boolean found=false;
   for(Object snapshot:(List<?>)state(p).getClass().getMethod("snapshots").invoke(state(p)))if(snapshot.getClass().getMethod("key").invoke(snapshot).equals("mob:minecraft:zombie")){
    found=true;check((int)snapshot.getClass().getMethod("hits").invoke(snapshot)==1,"Real zombie hit counted duplicate bonuses: "+snapshot);
    check(Math.abs((double)snapshot.getClass().getMethod("speed").invoke(snapshot)-1.3)<1E-8,"Real zombie multiplier is not 1.30");
   }check(found,"Real zombie adaptation missing");
   state(p).getClass().getMethod("reset").invoke(state(p));learn(p,"mob:minecraft:zombie",1);
   call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);p.invulnerableTime=0;float before=health(p);zombie.doHurtTarget(p);
   check(Math.abs((before-health(p))-loss*0.8F)<0.02F,"Native zombie stage-one reduction is not exactly 20%: "+loss+" -> "+(before-health(p)));
  }finally{zombie.discard();reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA ZOMBIE PASS: two actual vanilla zombie melee hits with First Aid yield exactly x1.30; native stage one reduces actual HP/limb loss exactly 20%");
 }
 static void nested(ServerPlayer p)throws Exception{
  reset(p);String root=family("VampirePlayer","handleSunDamage"),inner=family("BottledManaItem","effectSetRandomHp");
  DamageSource original=p.damageSources().fall();
  java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingAttackEvent> rewrite=event->{
   if(event.getEntity()!=p||event.getSource()!=original)return;
   try{call(BASE+"ModAttackCompatibility","hurt",new Class<?>[]{Entity.class,DamageSource.class,float.class,String.class},p,p.damageSources().magic(),2F,inner);event.setCanceled(true);}
   catch(Exception failure){throw new RuntimeException(failure);}
  };
  MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH,false,rewrite);
  try{
   for(int n=0;n<2;n++){p.invulnerableTime=0;call(BASE+"ModAttackCompatibility","hurt",new Class<?>[]{Entity.class,DamageSource.class,float.class,String.class},p,original,2F,root);wheelTick(p);}
   check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(root),"Rebuilt damage lost root ability");
   check(!((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(inner),"Rebuilt damage incorrectly learned downstream rewrite");
   for(Object snapshot:(List<?>)state(p).getClass().getMethod("snapshots").invoke(state(p)))if(snapshot.getClass().getMethod("key").invoke(snapshot).equals(root))
    check((int)snapshot.getClass().getMethod("hits").invoke(snapshot)==1,"One rewritten attack counted multiple acceleration bonuses: "+snapshot);
  }finally{MinecraftForge.EVENT_BUS.unregister(rewrite);reset(p);}
  LogUtils.getLogger().info("MULTIMOD_QA NESTED PASS: rebuilt native hurt retains original ability; two attacks produce exactly one acceleration bonus");
 }
 static void inheritedArrow(ServerPlayer p)throws Exception{
  Class<?> arrows=net.minecraft.world.entity.projectile.AbstractArrow.class;
  Method hit=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(arrows,"m_5790_",net.minecraft.world.phys.EntityHitResult.class);
  try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){
   var projectiles=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonObject("projectiles");
   for(var row:projectiles.entrySet()){
    var id=new ResourceLocation(row.getKey());if(!ForgeRegistries.ENTITY_TYPES.containsKey(id))continue;
    Entity entity=ForgeRegistries.ENTITY_TYPES.getValue(id).create(p.level());if(entity==null)continue;
    if(!(entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)){entity.discard();continue;}
    String key=row.getValue().getAsJsonObject().get("key").getAsString();
    reset(p);arrow.setBaseDamage(2);arrow.setDeltaMovement(new Vec3(1,0,0));float before=health(p);hit.invoke(arrow,new net.minecraft.world.phys.EntityHitResult(p));wheelTick(p);float loss=before-health(p);
    check(loss>0,"Native inherited arrow did not damage "+id);check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Inherited arrow attribution missing "+id);
    learn(p,key,1);p.invulnerableTime=0;before=health(p);hit.invoke(arrow,new net.minecraft.world.phys.EntityHitResult(p));float partial=before-health(p);
    if(!ModList.get().isLoaded("firstaid"))check(Math.abs(partial-loss*0.8F)<0.02F,"Arrow stage-one protection wrong: "+loss+" -> "+partial);
    learn(p,key,4);p.invulnerableTime=0;before=health(p);hit.invoke(arrow,new net.minecraft.world.phys.EntityHitResult(p));check(health(p)==before,"Native inherited arrow penetrated immunity");
    state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,false);p.invulnerableTime=0;hit.invoke(arrow,new net.minecraft.world.phys.EntityHitResult(p));check(health(p)<before,"Disabled arrow adaptation still protects");
    arrow.discard();reset(p);LogUtils.getLogger().info("MULTIMOD_QA PROJECTILE PASS: {} native inherited arrow hit; first exposure, 20% reduction, full immunity, per-entry disable",id);return;
   }
  }
 }
 static void sun(ServerPlayer p)throws Exception{
  reset(p);Class<?> cls=Class.forName("de.teamlapen.vampirism.entity.player.vampire.VampirePlayer");
  Object vampire=cls.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,p);
  Object attributes=call("de.teamlapen.vampirism.entity.player.VampirismPlayerAttributes","get",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);
  Field level=attributes.getClass().getField("vampireLevel");int previous=level.getInt(attributes);level.setInt(attributes,5);
  Method apply=cls.getDeclaredMethod("applyEntityAttributes");apply.setAccessible(true);apply.invoke(vampire);
  Field sun=cls.getDeclaredField("ticksInSun");sun.setAccessible(true);sun.setInt(vampire,99);p.tickCount=805;
  Method burn=cls.getDeclaredMethod("handleSunDamage",boolean.class);burn.setAccessible(true);
  float before=health(p);burn.invoke(vampire,false);wheelTick(p);check(health(p)<before,"Native vampire sun did not damage");
  String key=family("VampirePlayer","handleSunDamage");check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Shared helper lost native sunlight caller: wanted "+key+"; actual "+state(p).getClass().getMethod("pending").invoke(state(p)));
  learn(p,key,4);p.invulnerableTime=0;p.tickCount=845;before=health(p);burn.invoke(vampire,false);check(health(p)==before,"Native vampire sunlight immunity failed");
  level.setInt(attributes,previous);reset(p);LogUtils.getLogger().info("MULTIMOD_QA VAMPIRISM PASS: real native sunlight caller retained through shared helper; damage then full immunity");
 }
 static void directHealth(ServerPlayer p)throws Exception{
  reset(p);Class<?> cls=Class.forName("vazkii.botania.common.item.BottledManaItem");
  Method effect=cls.getDeclaredMethod("effectSetRandomHp",Level.class,LivingEntity.class);effect.setAccessible(true);
  if(ModList.get().isLoaded("firstaid")){
   // The original random HP loss can kill a vital limb. Baseline/partial direct
   // losses are checked with native life drain below, and Botania without First Aid.
   learn(p,family("BottledManaItem","effectSetRandomHp"),4);float before=health(p);
   for(int n=0;n<8;n++)effect.invoke(null,p.level(),p);
   check(p.isAlive()&&health(p)==before,"Fully adapted Botania random HP changed First Aid model");
   reset(p);LogUtils.getLogger().info("MULTIMOD_QA HEALTHSET PASS: actual Botania random HP protected with First Aid; baseline tested separately without vital-limb lethality");return;
  }
  // First Aid caps individual limb HP. Use a small, survivable native loss and
  // its raw display setter only for fixture setup, leaving every limb healed.
  double max=p.getAttribute(Attributes.MAX_HEALTH).getBaseValue();p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4);
  if(ModList.get().isLoaded("firstaid")){
   Object accessor=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(LivingEntity.class,"f_20961_").get(null);
   p.getEntityData().getClass().getMethod("set_impl",net.minecraft.network.syncher.EntityDataAccessor.class,Object.class).invoke(p.getEntityData(),accessor,4F);
  }else p.setHealth(4);
  float before=p.getHealth(),limbs=health(p);effect.invoke(null,p.level(),p);float immediate=p.getHealth(),limbsNow=health(p);wheelTick(p);
  LogUtils.getLogger().info("MULTIMOD_QA BOTANIA OBSERVE: HP {} -> {} -> {}; limb sum {} -> {}; max {}; alive {}",before,immediate,p.getHealth(),limbs,limbsNow,p.getMaxHealth(),p.isAlive());
  check(immediate<before||limbsNow<limbs,"Native Botania health loss absent");
  String key=family("BottledManaItem","effectSetRandomHp");check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Direct health attack did not start adaptation");
  learn(p,key,4);p.setHealth(p.getMaxHealth());before=p.getHealth();
  for(int n=0;n<8;n++)effect.invoke(null,p.level(),p);check(p.getHealth()==before,"Adapted direct-health attack still removes HP");
  p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);reset(p);LogUtils.getLogger().info("MULTIMOD_QA HEALTHSET PASS: actual Botania direct-health loss starts adaptation; completed protection retains HP");
 }
 static void lifesteal(ServerPlayer p)throws Exception{
  reset(p);Class<?> cls=Class.forName("net.mcreator.borninchaosv.procedures.LifestealPriNalozhieniiEffiektaProcedure");
  Method effect=Arrays.stream(cls.getMethods()).filter(m->m.getName().equals("execute")).findFirst().orElseThrow();
  Object[] args=effect.getParameterCount()==1?new Object[]{p}:new Object[]{p.level(),p};
  float before=health(p);effect.invoke(null,args);wheelTick(p);float loss=before-health(p);
  check(loss>0,"Native Born in Chaos life drain did not remove HP");String key=family("LifestealPriNalozhieniiEffiektaProcedure","execute");
  check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Native life drain attribution missing");
  learn(p,key,1);before=health(p);effect.invoke(null,args);check(Math.abs((before-health(p))-loss*0.8F)<0.02F,"Partial life-drain protection failed");
  learn(p,key,4);before=health(p);effect.invoke(null,args);check(health(p)==before,"Native life drain bypasses full protection");
  reset(p);LogUtils.getLogger().info("MULTIMOD_QA LIFESTEAL PASS: native Born in Chaos direct-health drain starts learning, stage one scales actual HP loss, full stage blocks HP loss");
 }
 static void verifyClasses()throws Exception{
  int loaded=0,optional=0;
  try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){
   var root=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
   for(var item:root.getAsJsonObject("classes").entrySet()){
    if(!ModList.get().isLoaded(item.getValue().getAsJsonObject().get("mod").getAsString()))continue;
    if(MultiModQa.class.getClassLoader().getResource(item.getKey()+".class")==null){optional++;continue;}
    try{Class.forName(item.getKey().replace('/','.'),false,MultiModQa.class.getClassLoader()).getDeclaredMethods();loaded++;}
    catch(ClassNotFoundException|NoClassDefFoundError failure){if(!item.getKey().contains("compat"))throw failure;optional++;}
   }
  }
  LogUtils.getLogger().info("MULTIMOD_QA CLASSES PASS: {} transformed classes loaded and linked; {} absent optional integrations",loaded,optional);
 }
 static void verifyEffects(ServerPlayer p)throws Exception{
  reset(p);int denied=0,positive=0,nativeTicks=0;var harmful=new ArrayList<MobEffect>();
  for(var effect:BuiltInRegistries.MOB_EFFECT){
   if(BuiltInRegistries.MOB_EFFECT.getKey(effect).getNamespace().equals("multimodqa"))continue;
   boolean negative=(boolean)call(BASE+"DamageCategories","isHarmful",new Class<?>[]{MobEffect.class},effect);
   if(negative)harmful.add(effect);else if(effect.getCategory()==MobEffectCategory.BENEFICIAL)positive++;
  }
  // Pre-learn independently; do not execute harmful start/expiry commands in bulk.
  for(var effect:harmful){
   String key="effect:"+BuiltInRegistries.MOB_EFFECT.getKey(effect);learn(p,key,4);check(!p.addEffect(new MobEffectInstance(effect,100,0)),"Immune player accepted "+key);denied++;
   if(!BuiltInRegistries.MOB_EFFECT.getKey(effect).getNamespace().equals("minecraft")){
    float hp=health(p);int air=p.getAirSupply(),food=p.getFoodData().getFoodLevel();Vec3 motion=p.getDeltaMovement(),position=p.position();
    effect.applyEffectTick(p,1);
    check(p.isAlive()&&health(p)==hp&&p.getAirSupply()==air&&p.getFoodData().getFoodLevel()==food&&p.getDeltaMovement().equals(motion)&&p.position().equals(position),"Learned native effect callback still acts: "+key);
    nativeTicks++;
   }
  }
  for(var effect:BuiltInRegistries.MOB_EFFECT)if(effect.getCategory()==MobEffectCategory.BENEFICIAL)
   check(!(boolean)call(BASE+"DamageCategories","isHarmful",new Class<?>[]{MobEffect.class},effect),"Beneficial effect classified harmful");
  reset(p);float before=health(p);p.hurt(p.damageSources().fall(),2);wheelTick(p);check(health(p)<before,"Initial real fall did not damage player");
  String key="damage:minecraft:fall";check(stage(p,key)==0,"Unfinished fall stage");
  learn(p,key,4);before=health(p);p.invulnerableTime=0;p.hurt(p.damageSources().fall(),2);check(health(p)==before,"Full fall protection failed");
  if(ModList.get().isLoaded("firstaid")){
   float injured=health(p);call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);check(health(p)>injured,"Full heal did not restore First Aid limbs");
   LogUtils.getLogger().info("MULTIMOD_QA FIRSTAID PASS: limb damage starts adaptation; learned hits blocked; body parts really heal");
  }
  LogUtils.getLogger().info("MULTIMOD_QA EFFECTS PASS: {} real negative-effect applications denied, {} positive effects retained",denied,positive);
  LogUtils.getLogger().info("MULTIMOD_QA NATIVEEFFECTS PASS: {} direct native negative-effect tick callbacks preserve actual HP, air, food, motion and position",nativeTicks);
 }
 static void verifyRegistryDamage(ServerPlayer p)throws Exception{
  int tested=0;var registry=p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
  for(var holder:registry.holders().toList()){
   String ns=holder.key().location().getNamespace();if(ns.equals("minecraft")||ns.equals("adaptationwheel"))continue;
   if((boolean)call(BASE+"DamageCategories","excluded",new Class<?>[]{Holder.class},holder))continue;
   reset(p);var source=new DamageSource(holder);String key=(String)call(BASE+"DamageCategories","from",new Class<?>[]{DamageSource.class},source);
   learn(p,key,4);float before=health(p);p.hurt(source,1);check(health(p)==before,"Registry immunity failed "+holder.key());tested++;
  }
  LogUtils.getLogger().info("MULTIMOD_QA DAMAGE PASS: {} registered custom damage sources blocked through actual hurt",tested);
 }
 static LivingEntity spawn(ServerPlayer p,String id,double x)throws Exception{
  var entity=(LivingEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)).create(p.level());
  entity.moveTo(p.getX()+x,p.getY(),p.getZ());if(entity instanceof Mob mob)mob.setNoAi(true);p.serverLevel().addFreshEntity(entity);return entity;
 }
 static void breath(ServerPlayer p,Entity dragon)throws Exception{
  Class<?> dragonType=Class.forName("com.github.alexthe666.iceandfire.entity.EntityDragonBase");
  call("com.github.alexthe666.iceandfire.entity.IafDragonDestructionManager","destroyAreaBreath",new Class<?>[]{Level.class,BlockPos.class,dragonType},p.level(),p.blockPosition(),dragon);
 }
 static void ice(ServerPlayer p)throws Exception{
  reset(p);var lightning=spawn(p,"iceandfire:lightning_dragon",2);var fire=spawn(p,"iceandfire:fire_dragon",2);
  try{
   for(var dragon:List.of(lightning,fire))dragon.getClass().getMethod("setAgeInDays",int.class).invoke(dragon,50);
   float before=health(p);breath(p,lightning);wheelTick(p);check(health(p)<before,"Lightning dragon breath did not damage");
   String key="attack:iceandfire:dragon_breath/lightning";check(((Map<?,?>)state(p).getClass().getMethod("pending").invoke(state(p))).containsKey(key),"Lightning breath attribution missing");
   learn(p,key,4);before=health(p);p.invulnerableTime=0;breath(p,lightning);check(health(p)==before,"Learned lightning breath still hurts");
   call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);before=health(p);
   p.invulnerableTime=0;breath(p,fire);check(health(p)<before,"Lightning learning wrongly blocks distinct fire breath");
   reset(p);Class<?> frozen=Class.forName("com.github.alexthe666.iceandfire.entity.props.FrozenData");Object data=frozen.getConstructor().newInstance();
   frozen.getMethod("setFrozen",LivingEntity.class,int.class).invoke(data,p,100);wheelTick(p);check(frozen.getField("isFrozen").getBoolean(data),"Native dragon freezing absent");
   learn(p,"condition:iceandfire:frozen",4);frozen.getMethod("tickFrozen",LivingEntity.class).invoke(data,p);check(!frozen.getField("isFrozen").getBoolean(data),"Completed dragon freezing did not thaw");
   frozen.getMethod("setFrozen",LivingEntity.class,int.class).invoke(data,p,100);check(!frozen.getField("isFrozen").getBoolean(data),"Immune player refrozen");
  }finally{lightning.discard();fire.discard();}
  LogUtils.getLogger().info("MULTIMOD_QA ICEANDFIRE PASS: native lightning/fire breath separated; real health/limb protection; native FrozenData thaw and reapplication");
 }
 static void mowzie(ServerPlayer p)throws Exception{
  reset(p);Class<?> cls=Class.forName("com.bobmowzie.mowziesmobs.server.capability.FrozenCapability$FrozenCapabilityImp");Object data=cls.getConstructor().newInstance();
  cls.getMethod("addFreezeProgress",LivingEntity.class,float.class).invoke(data,p,0.4F);check(cls.getField("freezeProgress").getFloat(data)>0,"Native Frostmaw buildup absent");
  cls.getMethod("onFreeze",LivingEntity.class).invoke(data,p);check(cls.getField("frozen").getBoolean(data),"Native freezing controller absent");
  learn(p,"effect:mowziesmobs:frozen",4);cls.getMethod("tick",LivingEntity.class).invoke(data,p);check(!cls.getField("frozen").getBoolean(data)&&p.getVehicle()==null,"Native freezing controller remains after immunity");
  cls.getMethod("addFreezeProgress",LivingEntity.class,float.class).invoke(data,p,0.4F);check(cls.getField("freezeProgress").getFloat(data)==0,"Immune freeze buildup returned");
  LogUtils.getLogger().info("MULTIMOD_QA MOWZIE PASS: real capability buildup, controller thaw and immunity");
 }
 static void space(ServerPlayer p)throws Exception{
  Class<?> cls=Class.forName("earth.terrarium.adastra.common.systems.TemperatureApiImpl");Object api=cls.getConstructor().newInstance();
  for(String method:List.of("burnEntity","freezeEntity")){
   reset(p);Method invoke=method.equals("burnEntity")?cls.getDeclaredMethod(method,LivingEntity.class):cls.getDeclaredMethod(method,LivingEntity.class,ServerLevel.class);invoke.setAccessible(true);
   Object[] args=method.equals("burnEntity")?new Object[]{p}:new Object[]{p,p.serverLevel()};float before=health(p);invoke.invoke(api,args);wheelTick(p);check(health(p)<before,"Native planetary "+method+" did not hurt");
   learn(p,family("TemperatureApiImpl",method),4);before=health(p);p.invulnerableTime=0;invoke.invoke(api,args);check(health(p)==before,"Planetary immunity failed "+method);
   check(method.equals("burnEntity")?!p.isOnFire():p.getTicksFrozen()==0,"Planetary status continued after immunity "+method+" alive="+p.isAlive());
  }
  reset(p);ServerLevel origin=p.serverLevel();Vec3 pos=p.position();var mars=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("ad_astra","mars")));check(mars!=null,"Mars dimension missing");
  p.teleportTo(mars,0,90,0,0,0);mars.getChunkAt(p.blockPosition());Class<?> oxygen=Class.forName("earth.terrarium.adastra.common.systems.OxygenApiImpl");Object o=oxygen.getConstructor().newInstance();
  try{float before=health(p);oxygen.getMethod("entityTick",ServerLevel.class,LivingEntity.class).invoke(o,mars,p);wheelTick(p);check(health(p)<before,"Mars vacuum did not hurt");learn(p,family("OxygenApiImpl","entityTick"),4);before=health(p);p.invulnerableTime=0;oxygen.getMethod("entityTick",ServerLevel.class,LivingEntity.class).invoke(o,mars,p);check(health(p)==before&&p.getAirSupply()>0,"Vacuum immunity/air restoration failed");}
  finally{p.teleportTo(origin,pos.x,pos.y,pos.z,0,0);}
  LogUtils.getLogger().info("MULTIMOD_QA ADASTRA PASS: native heat, cold and actual Mars oxygen tick protected, fire/frozen/air status restored");
 }
 static void wind(ServerPlayer p)throws Exception{
  reset(p);String key="condition:weather2:tornado";
  Class<?> manager=Class.forName("weather2.weathersystem.WeatherManager"),storm=Class.forName("weather2.weathersystem.storm.StormObject");
  Object weather=Class.forName("weather2.weathersystem.WeatherManagerServer").getConstructor(ServerLevel.class).newInstance(p.serverLevel());
  Object tornado=storm.getConstructor(manager).newInstance(weather);
  storm.getField("pos").set(tornado,p.position().add(3,10,3));storm.getField("posGround").set(tornado,p.position().add(3,0,3));storm.getField("posBaseFormationPos").set(tornado,p.position().add(3,-2,3));
  storm.getField("levelCurIntensityStage").setInt(tornado,storm.getField("STATE_STAGE3").getInt(null));storm.getMethod("setupTornado").invoke(tornado);
  for(String name:List.of("spinEntityv2","spinEntity")){
   reset(p);Method spin=storm.getMethod(name,name.equals("spinEntityv2")?Entity.class:Object.class);
   p.setDeltaMovement(Vec3.ZERO);spin.invoke(tornado,p);check(p.getDeltaMovement().lengthSqr()>0,"Native unlearned tornado force absent "+name);learn(p,key,4);
   p.setDeltaMovement(Vec3.ZERO);spin.invoke(tornado,p);check(p.getDeltaMovement().lengthSqr()==0,"Immune native tornado still lifts player "+name);
  }
  LogUtils.getLogger().info("MULTIMOD_QA WEATHER PASS: actual StormObject spinEntity and spinEntityv2 move unlearned player; completed adaptation blocks both native paths");
 }
}
