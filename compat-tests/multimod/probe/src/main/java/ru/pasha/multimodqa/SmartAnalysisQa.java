package ru.pasha.multimodqa;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;

/** Real server damage/effect integration checks for the offline analyzer. */
final class SmartAnalysisQa {
 static final String CORE="ru.pasha.adaptationwheel.core.";
 static final net.minecraftforge.registries.DeferredRegister<MobEffect> SMART_EFFECTS=net.minecraftforge.registries.DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"multimodqa");
 static final net.minecraftforge.registries.RegistryObject<MobEffect> NEUTRAL_SLOW=SMART_EFFECTS.register("neutral_slow",()->new NeutralSpeed(-.25,"f17166bb-960b-4eea-84c7-30f00d9965ad"));
 static final net.minecraftforge.registries.RegistryObject<MobEffect> NEUTRAL_FAST=SMART_EFFECTS.register("neutral_fast",()->new NeutralSpeed(.25,"c4d13baf-f827-452f-9ac2-f714341b6914"));
 static final net.minecraftforge.registries.DeferredRegister<EntityType<?>> SMART_ENTITIES=net.minecraftforge.registries.DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"multimodqa");
 static final net.minecraftforge.registries.RegistryObject<EntityType<UnknownPulse>> UNKNOWN_PULSE=SMART_ENTITIES.register("unknown_pulse",()->EntityType.Builder.<UnknownPulse>of(UnknownPulse::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(4).build("multimodqa:unknown_pulse"));
 static final net.minecraftforge.registries.RegistryObject<EntityType<net.minecraft.world.entity.monster.Zombie>> UNKNOWN_CASTER=SMART_ENTITIES.register("unknown_caster",()->EntityType.Builder.<net.minecraft.world.entity.monster.Zombie>of(net.minecraft.world.entity.monster.Zombie::new,MobCategory.MONSTER).sized(.6F,1.95F).build("multimodqa:unknown_caster"));
 static Registry<DamageType> damageTypes;
 static final String CLIENT_KEY="damage:multimodqa:unknown_light_beam";
 static volatile Object expectedClientAnalysis;
 static volatile int expectedClientDuration;
 static int clientWait,clientFrames;
 static Screen clientScreen;
 static final class NeutralSpeed extends MobEffect {
  NeutralSpeed(double multiplier,String uuid){super(MobEffectCategory.NEUTRAL,0x776655);addAttributeModifier(Attributes.MOVEMENT_SPEED,uuid,multiplier,AttributeModifier.Operation.MULTIPLY_TOTAL);}
 }
 /** Deliberately supplies no custom DamageType or attacker to its hurt call. */
 static final class UnknownPulse extends ThrowableItemProjectile {
  UnknownPulse(EntityType<? extends ThrowableItemProjectile> type,Level level){super(type,level);}
  @Override protected net.minecraft.world.item.Item getDefaultItem(){return Items.SNOWBALL;}
  @Override protected void onHitEntity(EntityHitResult hit){super.onHitEntity(hit);hit.getEntity().hurt(damageSources().generic(),4F);}
 }
 static void register(net.minecraftforge.eventbus.api.IEventBus bus){SMART_EFFECTS.register(bus);SMART_ENTITIES.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(UNKNOWN_CASTER.get(),net.minecraft.world.entity.monster.Zombie.createAttributes().build()));bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(UNKNOWN_CASTER.get(),net.minecraft.client.renderer.entity.ZombieRenderer::new));}
 static Object getter(Object obj,String name)throws Exception{return obj.getClass().getMethod(name).invoke(obj);}
 static Object snapshot(Object s,String key)throws Exception{
  for(Object row:(List<?>)getter(s,"snapshots"))if(getter(row,"key").equals(key))return row;
  return null;
 }
 static Object snapshot(ServerPlayer p,String key)throws Exception{return snapshot(state(p),key);}
 static Object required(ServerPlayer p,String key)throws Exception{
  Object row=snapshot(p,key);check(row!=null,"Smart analyzer failed to create encountered entry "+key);return row;
 }
 static double number(Object obj,String name)throws Exception{return ((Number)getter(obj,name)).doubleValue();}
 static int duration(Object row)throws Exception{return ((Number)getter(row,"duration")).intValue();}
 static Object info(ServerPlayer p,String key)throws Exception{return call(BASE+"SmartAttackAnalyzer","info",new Class<?>[]{ServerPlayer.class,String.class},p,key);}
 static void classified(ServerPlayer p,String key,String profile,String reasonPrefix)throws Exception{
  Object row=required(p,key);
  check(getter(row,"profile").toString().equals(profile),"Wrong analyzed category for "+key+": "+getter(row,"profile"));
  check(number(row,"analysisObservations")>0&&getter(row,"analyzedDurations")!=null,"No real observation/timing for "+key);
  check(getter(row,"analysisReason").toString().startsWith(reasonPrefix),"Wrong evidence explanation for "+key+": "+getter(row,"analysisReason"));
  Object explanation=info(p,key);check((boolean)getter(explanation,"present")&&number(explanation,"observations")>0,"Missing authoritative analysis explanation "+key);
 }
 @SafeVarargs static Holder<DamageType> custom(String id,String message,TagKey<DamageType>...tags)throws Exception{
  // Registered by this QA mod's own datapack, absent from production manifests.
  // Real synchronized holders also exercise the native damage packet path.
  Holder.Reference<DamageType> holder=damageTypes.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(id)));
  check(holder.value().msgId().equals(message),"Unexpected custom fixture message id: "+id);
  for(TagKey<DamageType> tag:tags)check(holder.is(tag),"Custom fixture tag missing: "+id+" / "+tag);
  return holder;
 }
 static float hit(ServerPlayer p,DamageSource source,float amount)throws Exception{
  p.invulnerableTime=0;float before=health(p);p.hurt(source,amount);return before-health(p);
 }
 static void liveHpProtection(ServerPlayer p,String label,String key,DamageSource source,float amount,float baseline)throws Exception{
  learn(p,key,1);p.setHealth(p.getMaxHealth());call(BASE+"FirstAidCompatibility","healAll",new Class<?>[]{ServerPlayer.class},p);
  float partial=hit(p,source,amount);check(Math.abs(partial-baseline*.8F)<.025F,label+" stage one did not reduce real HP loss by 20%: "+baseline+" -> "+partial);
  learn(p,key,4);float before=health(p);hit(p,source,amount);check(health(p)==before,label+" analyzed full protection still loses HP");
  controlDisable(p,key);check(hit(p,source,amount)>0,label+" disabled analyzed protection still blocks HP loss");
  state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,true);
  ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
  try{check(hit(p,source,amount)>0,label+" analyzed immunity stayed active without the wheel");}
  finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
  LogUtils.getLogger().info("MULTIMOD_QA SMART_HP PASS: {} actual HP loss {} -> stage 1 exactly 20% less -> stage 4 zero -> disable/unequip native HP loss",label,baseline);
 }
 static void run(ServerPlayer p)throws Exception{
  damageTypes=p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
  check((boolean)call(CORE+"AdaptationTimings","analysisEnabled",new Class<?>[]{}),"Smart analyzer must be enabled for this fixture");
  unknownAndClock(p);registryFacts(p);sameOwnerIsolation(p);effects(p);neutralEffects(p);manualPolicy(p);dynamicProjectile(p);
  if(ModList.get().isLoaded("iceandfire"))nativeDragon(p);
  nativeArrow(p);reset(p);clientFixture(p);
  LogUtils.getLogger().info("MULTIMOD_QA SMART_ANALYSIS SERVER PASS: actual server mitigation, tag/name evidence, bounded recommendations, frozen work clock, persistence, manual policy and effect eligibility");
 }
 static void clientFixture(ServerPlayer p)throws Exception{
  DamageSource source=new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"));
  check(hit(p,source,4)>0,"Final client sync fixture did not exercise real HP loss");wheelTick(p);
  expectedClientAnalysis=info(p,CLIENT_KEY);expectedClientDuration=duration(required(p,CLIENT_KEY));
  call(BASE+"AdaptationEvents","saveAndSync",new Class<?>[]{ServerPlayer.class},p);
 }
 /** Called on the render thread after the server suite has finished. */
 static boolean clientTick()throws Exception{
  Minecraft mc=Minecraft.getInstance();check(mc.player!=null,"No player during native analyzer client check");
  if(++clientWait>200)throw new AssertionError("Native smart analyzer packet/GUI check timed out");
  if(clientScreen==null){
   Object client=call("ru.pasha.adaptationwheel.client.ClientWheelState","get",new Class<?>[]{int.class},mc.player.getId());
   if(client==null)return false;Object row=null;
   for(Object entry:(List<?>)getter(client,"entries"))if(getter(entry,"key").equals(CLIENT_KEY)){row=entry;break;}
   if(row==null||!(boolean)getter(getter(row,"analysis"),"present"))return false;
   if(number(getter(row,"analysis"),"observations")<number(expectedClientAnalysis,"observations"))return false;
   check(getter(row,"analysis").equals(expectedClientAnalysis),"Real server packet lost/changed analyzer explanation, confidence, observation count or recommendations");
   check(getter(row,"profile").toString().equals("LIGHT")&&duration(row)==expectedClientDuration,"Real server packet changed analyzed category or frozen current duration");
   Class<?> outerClass=Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen");Object outer=outerClass.getConstructor().newInstance();
   Class<?> detailClass=Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen$DetailScreen");Constructor<?> constructor=detailClass.getDeclaredConstructor(outerClass,String.class);constructor.setAccessible(true);
   clientScreen=(Screen)constructor.newInstance(outer,CLIENT_KEY);mc.setScreen(clientScreen);
   String translated=net.minecraft.client.resources.language.I18n.get("gui.adaptationwheel.analysis.title");check(!translated.equals("gui.adaptationwheel.analysis.title"),"Analyzer GUI explanation lacks real Russian translation");
   LogUtils.getLogger().info("MULTIMOD_QA SMART_NETWORK PASS: actual received WheelNetwork packet retains category, confidence, explanation, observations, recommendations and frozen current duration {} ticks",expectedClientDuration);
  }
  if(++clientFrames==8)Screenshot.grab(mc.gameDirectory,"smart-analysis-details-top.png",mc.getMainRenderTarget(),message->{});
  // Since 4.23 the details are drawn by a DetailView that the screen keeps in "view"; scroll it to the bottom.
  if(clientFrames==9){Field viewField=clientScreen.getClass().getDeclaredField("view");viewField.setAccessible(true);Object view=viewField.get(clientScreen);Field limit=view.getClass().getDeclaredField("maxScroll"),position=view.getClass().getDeclaredField("scrollOffset");limit.setAccessible(true);position.setAccessible(true);position.setInt(view,limit.getInt(view));}
  if(clientFrames==17)Screenshot.grab(mc.gameDirectory,"smart-analysis-details-bottom.png",mc.getMainRenderTarget(),message->{});
  if(clientFrames<21)return false;
  mc.setScreen(null);LogUtils.getLogger().info("MULTIMOD_QA SMART_GUI PASS: actual adaptation detail screen rendered Russian smart analysis and effective timings; top/bottom screenshots saved");return true;
 }
 static void unknownAndClock(ServerPlayer p)throws Exception{
  reset(p);String key="damage:multimodqa:unknown_light_beam";
  DamageSource source=new DamageSource(custom("multimodqa:unknown_light_beam","multimodqa:unknown_light_beam"));
  float baseline=hit(p,source,4);wheelTick(p);check(baseline>0,"Unknown native damage baseline did not remove HP");
  classified(p,key,"LIGHT","name.");Object first=required(p,key);int frozen=duration(first);
  double work=number(first,"work"),observations=number(first,"analysisObservations");
  int oldRecommendation=((Number)getter(getter(first,"analyzedDurations"),"first")).intValue();
  check(number(first,"hits")==0&&Math.abs(number(first,"speed")-1)<1E-8,"First unknown hit counted a duplicate acceleration bonus");
  check(hit(p,source,ModList.get().isLoaded("firstaid")?4:30)>0,"Repeated stronger native damage fixture did not remove HP");
  Object beforeTick=required(p,key);check(duration(beforeTick)==frozen&&number(beforeTick,"work")==work,"Receiving a hit changed current timer/work before the server tick");
  wheelTick(p);Object second=required(p,key);
  check(duration(second)==frozen,"New severity recommendation changed the running stage duration");
  check(number(second,"hits")==1&&Math.abs(number(second,"speed")-1.3)<1E-8,"Two actual unknown hits must yield exactly x1.30");
  check(Math.abs(number(second,"work")-work-1.3)<1E-8,"Actual server work did not advance by x1.30 per tick");
  check(number(second,"analysisObservations")>observations,"Repeated native exposure did not update analyzer memory");
  int recommendation=((Number)getter(getter(second,"analyzedDurations"),"first")).intValue();
  if(!ModList.get().isLoaded("firstaid"))check(recommendation>oldRecommendation,"Stronger observed attack did not increase future recommendation");
  call(BASE+"AdaptationEvents","save",new Class<?>[]{ServerPlayer.class},p);
  Object restored=call(BASE+"AdaptationEvents","loadState",new Class<?>[]{net.minecraft.world.entity.player.Player.class},p);
  Object loaded=snapshot(restored,key);check(loaded!=null&&duration(loaded)==frozen&&Math.abs(number(loaded,"work")-number(second,"work"))<1E-8,"Save/load changed frozen current work clock");
  check(getter(loaded,"analyzedDurations").equals(getter(second,"analyzedDurations"))&&getter(loaded,"analysisReason").equals(getter(second,"analysisReason"))&&number(loaded,"analysisObservations")==number(second,"analysisObservations"),"Save/load lost native analysis metadata");
  int realTicks=((Number)getter(second,"estimatedTicks")).intValue();Object liveState=state(p);
  for(int tick=1;tick<realTicks;tick++)liveState.getClass().getMethod("tick").invoke(liveState);
  check(stage(p,key)==0,"Accelerated work completed earlier than its authoritative estimated tick count");
  liveState.getClass().getMethod("tick").invoke(liveState);check(stage(p,key)==1,"Accelerated server work did not actually complete its stage at the predicted tick");
  call(BASE+"SmartAttackAnalyzer","forget",new Class<?>[]{ServerPlayer.class},p);
  check(hit(p,source,4)>0,"Restored-memory continuation did not exercise real damage");wheelTick(p);
  check(number(required(p,key),"analysisObservations")==number(second,"analysisObservations")+1,"Analyzer memory did not restore its observation count from saved NBT");
  LogUtils.getLogger().info("MULTIMOD_QA SMART_CLOCK PASS: unknown light source baseline {}; two actual hits x1.30; current {} ticks frozen while future recommendation {} -> {}; saved work and analysis restored",baseline,frozen,oldRecommendation,recommendation);
  liveHpProtection(p,"unknown light beam",key,source,4,baseline);reset(p);
 }
 static void registryFacts(ServerPlayer p)throws Exception{
  reset(p);String key="damage:multimodqa:misleading_fire_name";
  DamageSource source=new DamageSource(custom("multimodqa:misleading_fire_name","multimodqa:burning_flame",DamageTypeTags.IS_LIGHTNING));
  float baseline=hit(p,source,4);wheelTick(p);check(baseline>0,"Unknown tagged lightning baseline did not remove HP");
  classified(p,key,"ELECTRIC","registry.");check(number(required(p,key),"analysisConfidence")>=90,"Registry evidence must outrank a name guess");
  liveHpProtection(p,"unknown tag-defined lightning",key,source,4,baseline);
  reset(p);String override="damage:multimodqa:explicit_override";
  TagKey<DamageType> coldOverride=TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("adaptationwheel:adaptation/cold"));
  DamageSource overridden=new DamageSource(custom("multimodqa:explicit_override","multimodqa:fire_beam",DamageTypeTags.IS_LIGHTNING,coldOverride));
  check(hit(p,overridden,4)>0,"Explicit profile override baseline absent");wheelTick(p);classified(p,override,"COLD","registry.");
  reset(p);String effectOverride="damage:multimodqa:explicit_effect_override";
  TagKey<DamageType> explicitEffect=TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("adaptationwheel:adaptation/effect"));
  DamageSource effectSource=new DamageSource(custom("multimodqa:explicit_effect_override","multimodqa:fire_arrow",DamageTypeTags.IS_PROJECTILE,explicitEffect));
  check(hit(p,effectSource,4)>0,"Explicit EFFECT override baseline absent");wheelTick(p);classified(p,effectOverride,"EFFECT","registry.effect");
  learn(p,effectOverride,4);check(hit(p,effectSource,4)==0,"Explicit EFFECT override lost real full damage protection");
  LogUtils.getLogger().info("MULTIMOD_QA SMART_TAG PASS: misleading fire name classified ELECTRIC from lightning tag; explicit COLD outranks lightning; explicit EFFECT outranks native projectile and fire-arrow name while retaining real HP immunity");reset(p);
 }
 static void sameOwnerIsolation(ServerPlayer p)throws Exception{
  reset(p);LivingEntity owner=spawn(p,"minecraft:zombie",3);
  try{
   String mob="mob:minecraft:zombie",light="damage:multimodqa:unseen_owner_light_beam",fire="damage:multimodqa:unseen_owner_fire";
   DamageSource physicalSource=new DamageSource(custom("multimodqa:ordinary_melee","multimodqa:ordinary_melee"),owner);
   DamageSource lightSource=new DamageSource(custom("multimodqa:unseen_owner_light_beam","multimodqa:light_beam"),owner);
   DamageSource fireSource=new DamageSource(custom("multimodqa:unseen_owner_fire","multimodqa:fire",DamageTypeTags.IS_FIRE),owner);
   learn(p,mob,4);check(hit(p,physicalSource,4)==0,"Learned species physical protection was bypassed by an ordinary custom melee DamageType");
   float baseline=hit(p,lightSource,4);wheelTick(p);check(baseline>0,"Learned basic zombie melee incorrectly protected from a new unknown beam");classified(p,light,"LIGHT","name.");
   learn(p,light,4);check(hit(p,lightSource,4)==0,"Unknown same-owner beam was not blocked after its own adaptation");
   check(hit(p,fireSource,4)>0,"Light adaptation or ordinary mob immunity incorrectly blocked distinct unknown fire");wheelTick(p);classified(p,fire,"FIRE","registry.");
   check(stage(p,fire)==0,"Unknown separate attack began already adapted");
   DamageSource canonicalMagic=new DamageSource(p.damageSources().magic().typeHolder(),owner,owner);String magicKey="effect:minecraft:instant_damage";
   Object scope=call(BASE+"ModAttackCompatibility","beginHurt",new Class<?>[]{Entity.class,DamageSource.class,float.class},p,canonicalMagic,4F);float magicLoss;
   try{magicLoss=hit(p,p.damageSources().generic(),4);}finally{call(BASE+"ModAttackCompatibility","endHurt",new Class<?>[]{Object.class},scope);}
   check(magicLoss>0,"Rebuilt downstream generic damage incorrectly inherited learned ordinary mob immunity instead of original unlearned magic");wheelTick(p);classified(p,magicKey,"MAGIC","registry.magic");
   learn(p,magicKey,4);scope=call(BASE+"ModAttackCompatibility","beginHurt",new Class<?>[]{Entity.class,DamageSource.class,float.class},p,canonicalMagic,4F);
   try{check(hit(p,p.damageSources().generic(),4)==0,"Canonical magic full adaptation failed to block actual rebuilt generic HP damage");}finally{call(BASE+"ModAttackCompatibility","endHurt",new Class<?>[]{Object.class},scope);}
   controlDisable(p,magicKey);scope=call(BASE+"ModAttackCompatibility","beginHurt",new Class<?>[]{Entity.class,DamageSource.class,float.class},p,canonicalMagic,4F);
   try{check(hit(p,p.damageSources().generic(),4)>0,"Disabled canonical magic adaptation still blocks downstream HP damage");}finally{call(BASE+"ModAttackCompatibility","endHurt",new Class<?>[]{Object.class},scope);}
   LogUtils.getLogger().info("MULTIMOD_QA SMART_CANONICAL PASS: original zombie-owned magic rebuilt as source-less generic damage removes real HP despite learned zombie melee; own magic stage 4 blocks actual HP, disabling magic restores HP");
   LogUtils.getLogger().info("MULTIMOD_QA SMART_SEPARATION PASS: learned species blocks ordinary custom melee but does not block unknown light beam; light full immunity does not block distinct same-owner fire");
  }finally{owner.discard();reset(p);}
 }
 static void effects(ServerPlayer p)throws Exception{
  reset(p);String good="effect:minecraft:speed",bad="effect:multimodqa:lifecycle";
  check(p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,400,0)),"Beneficial effect fixture was rejected");wheelTick(p);
  check(snapshot(p,good)==null,"Beneficial effect created an adaptation entry");check(!(boolean)getter(info(p,good),"present"),"Beneficial effect generated analysis memory");
  MobEffect negative=LIFECYCLE.get();check(p.addEffect(new MobEffectInstance(negative,1000,0)),"Unknown harmful effect fixture was rejected before learning");wheelTick(p);
  classified(p,bad,"EFFECT","native.");Object row=required(p,bad);check(number(row,"hits")==0,"First harmful effect application generated a fake speed bonus");
  learn(p,bad,1);p.removeEffect(negative);p.addEffect(new MobEffectInstance(negative,1000,0));call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
  check(p.getEffect(negative)!=null&&p.getEffect(negative).getDuration()==800,"Analyzed harmful effect stage one did not shorten its real native duration");
  learn(p,bad,4);call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);check(!p.hasEffect(negative),"Analyzed harmful effect survived full purge");
  check(!p.addEffect(new MobEffectInstance(negative,1000,0)),"Analyzed full harmful effect immunity accepted reapplication");
  controlDisable(p,bad);check(p.addEffect(new MobEffectInstance(negative,1000,0)),"Disabling analyzed effect did not restore actual application");
  check(p.hasEffect(MobEffects.MOVEMENT_SPEED),"Harmful effect adaptation removed a beneficial effect");
  LogUtils.getLogger().info("MULTIMOD_QA SMART_EFFECT PASS: unknown registered negative effect learned; actual 1000 -> 800 tick duration, full purge and reapplication denied; disable restored; beneficial speed preserved and never analyzed");reset(p);
 }
 static void neutralEffects(ServerPlayer p)throws Exception{
  reset(p);String slow="effect:multimodqa:neutral_slow",fast="effect:multimodqa:neutral_fast";
  MobEffect negative=NEUTRAL_SLOW.get(),positive=NEUTRAL_FAST.get();
  double baseline=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
  check(p.addEffect(new MobEffectInstance(negative,1000,0)),"Neutral slowing effect baseline rejected");
  check(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-baseline*.75)<1E-8,"Unknown neutral slowing effect did not actually lower movement speed");wheelTick(p);
  classified(p,slow,"EFFECT","native.");learn(p,slow,4);call(BASE+"NegativeEffects","maintain",new Class<?>[]{ServerPlayer.class},p);
  check(!p.hasEffect(negative)&&Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-baseline)<1E-8,"Full neutral-debuff adaptation failed to restore actual movement speed");
  check(!p.addEffect(new MobEffectInstance(negative,1000,0)),"Full neutral-debuff adaptation accepted reapplication");
  controlDisable(p,slow);check(p.addEffect(new MobEffectInstance(negative,1000,0))&&p.getAttributeValue(Attributes.MOVEMENT_SPEED)<baseline,"Disabling neutral-debuff adaptation did not restore actual speed loss");p.removeEffect(negative);
  check(p.addEffect(new MobEffectInstance(positive,1000,0)),"Neutral positive effect rejected");wheelTick(p);
  check(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-baseline*1.25)<1E-8,"Neutral positive effect lost its real speed increase");
  check(snapshot(p,fast)==null&&!(boolean)getter(info(p,fast),"present"),"Neutral positive attribute modifier incorrectly started an adaptation");
  LogUtils.getLogger().info("MULTIMOD_QA SMART_NEUTRAL PASS: unknown neutral -25% speed modifier detected and full adaptation restores real movement attribute; disable restores slowdown; neutral +25% speed preserved and ignored");reset(p);
 }
 static void manualPolicy(ServerPlayer p)throws Exception{
  reset(p);String key="damage:multimodqa:manual_light_beam";
  try{
   call(CORE+"AdaptationTimings","replace",new Class<?>[]{Map.class,Map.class},Map.of(),Map.of(key,List.of(5,0,0,0,0)));
   DamageSource source=new DamageSource(custom("multimodqa:manual_light_beam","multimodqa:light_beam"));check(hit(p,source,4)>0,"Manual timing unknown source baseline absent");wheelTick(p);
   check(duration(required(p,key))==100,"Manual Configured source duration did not outrank analyzer recommendation");
   call(CORE+"AdaptationTimings","setAnalysisEnabled",new Class<?>[]{boolean.class},false);
   String disabled="damage:multimodqa:analysis_disabled_fire";DamageSource fire=new DamageSource(custom("multimodqa:analysis_disabled_fire","multimodqa:fire",DamageTypeTags.IS_FIRE));
   check(hit(p,fire,4)>0,"Disabled analyzer baseline absent");wheelTick(p);Object row=required(p,disabled);
   check(number(row,"analysisObservations")==0&&getter(row,"analyzedDurations")==null,"Disabled analyzer still learned new recommendations");
   learn(p,disabled,4);check(hit(p,fire,4)==0,"Turning off intelligent timing also disabled real conventional protection");
   LogUtils.getLogger().info("MULTIMOD_QA SMART_POLICY PASS: configured 5-second source override wins; analyzer disabled stops learning but learned stage-four HP protection remains functional");
  }finally{call(CORE+"AdaptationTimings","defaults",new Class<?>[]{});reset(p);}
 }
 static UnknownPulse pulse(ServerPlayer p,LivingEntity owner){
  UnknownPulse projectile=UNKNOWN_PULSE.get().create(p.level());check(projectile!=null,"Unknown QA projectile factory failed");
  projectile.setOwner(owner);projectile.moveTo(p.position().add(0,1,-1));projectile.setDeltaMovement(0,0,1);return projectile;
 }
 static void dynamicProjectile(ServerPlayer p)throws Exception{
  reset(p);String key="attack:multimodqa:dynamic_projectile/unknown_pulse";LivingEntity owner=spawn(p,"minecraft:zombie",3);
  try{
   try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){
    JsonObject entries=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonObject("projectiles");
    check(!entries.has("multimodqa:unknown_pulse"),"Unknown projectile is already listed in production compatibility manifest");
   }
   UnknownPulse shot=pulse(p,owner);float hp=health(p);try{projectileHit(shot.getClass()).invoke(shot,new EntityHitResult(p));}finally{shot.discard();}
   check(health(p)<hp,"True unknown native projectile did not remove HP");wheelTick(p);Object row=required(p,key);
   check(number(row,"analysisObservations")>0,"Unknown generic-damage projectile was not analyzed");
   check(snapshot(p,"mob:minecraft:zombie")!=null,"Unknown generic-damage projectile lost its actual owner identity");
   learn(p,key,4);hp=health(p);p.invulnerableTime=0;shot=pulse(p,owner);
   try{projectileHit(shot.getClass()).invoke(shot,new EntityHitResult(p));}finally{shot.discard();}
   check(health(p)==hp,"True unknown generic-damage projectile bypassed specific full adaptation");
   controlDisable(p,key);p.invulnerableTime=0;shot=pulse(p,owner);
   try{projectileHit(shot.getClass()).invoke(shot,new EntityHitResult(p));}finally{shot.discard();}
   check(health(p)<hp,"Disabled true unknown projectile protection did not restore native HP loss");
   LogUtils.getLogger().info("MULTIMOD_QA SMART_DYNAMIC_PROJECTILE PASS: new registered projectile absent production manifest; inherited native onHit with source-less generic hurt learns precise key and restores owner; specific full blocks HP, disable restores HP loss");
  }finally{owner.discard();reset(p);}
 }
 static void nativeDragon(ServerPlayer p)throws Exception{
  reset(p);LivingEntity lightning=spawn(p,"iceandfire:lightning_dragon",2),fire=spawn(p,"iceandfire:fire_dragon",2);
  try{
   for(LivingEntity dragon:List.of(lightning,fire))dragon.getClass().getMethod("setAgeInDays",int.class).invoke(dragon,50);
   String lightningKey="attack:iceandfire:dragon_breath/lightning",fireKey="attack:iceandfire:dragon_breath/fire";
   float hp=health(p);breath(p,lightning);wheelTick(p);check(health(p)<hp,"Actual mod lightning breath baseline absent");classified(p,lightningKey,"ELECTRIC","");
   learn(p,lightningKey,4);p.invulnerableTime=0;hp=health(p);breath(p,lightning);check(health(p)==hp,"Analyzed native lightning breath bypassed full protection");
   p.invulnerableTime=0;breath(p,fire);wheelTick(p);check(health(p)<hp,"Analyzed lightning immunity wrongly covered native fire breath");classified(p,fireKey,"FIRE","");
   controlDisable(p,lightningKey);p.invulnerableTime=0;hp=health(p);breath(p,lightning);check(health(p)<hp,"Disabling analyzed native lightning did not restore real damage");
   LogUtils.getLogger().info("MULTIMOD_QA SMART_NATIVE PASS: real Ice and Fire lightning breath recognized ELECTRIC and blocked after learning, fire breath remains independently harmful, disable restores lightning HP loss");
  }finally{lightning.discard();fire.discard();reset(p);}
 }
 static void nativeArrow(ServerPlayer p)throws Exception{
  JsonObject entries;try(var in=Class.forName(BASE+"ModAttackCompatibility").getResourceAsStream("/compat/mod-attacks.json")){entries=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonObject("projectiles");}
  LivingEntity owner=spawn(p,"minecraft:zombie",3);
  try{
   for(var entry:entries.entrySet()){
    ResourceLocation id=new ResourceLocation(entry.getKey());if(id.getNamespace().equals("minecraft")||!ForgeRegistries.ENTITY_TYPES.containsKey(id))continue;
    reset(p);Projectile candidate=testProjectile(p,id,owner);if(candidate==null)continue;
    if(!(candidate instanceof AbstractArrow)){candidate.discard();continue;}
    String key=entry.getValue().getAsJsonObject().get("key").getAsString();
    float hp=health(p);try{projectileHit(candidate.getClass()).invoke(candidate,new EntityHitResult(p));}finally{candidate.discard();}
    if(health(p)>=hp)continue;wheelTick(p);Object row=required(p,key);check(number(row,"analysisObservations")>0&&getter(row,"analyzedDurations")!=null,"Actual unknown/mod projectile produced no analysis");
    learn(p,key,4);p.invulnerableTime=0;hp=health(p);candidate=testProjectile(p,id,owner);
    try{projectileHit(candidate.getClass()).invoke(candidate,new EntityHitResult(p));}finally{candidate.discard();}
    check(health(p)==hp,"Analyzed real native projectile bypassed full protection "+id);
    controlDisable(p,key);p.invulnerableTime=0;candidate=testProjectile(p,id,owner);
    try{projectileHit(candidate.getClass()).invoke(candidate,new EntityHitResult(p));}finally{candidate.discard();}
    check(health(p)<hp,"Disabled analyzed native projectile did not restore HP loss "+id);
    LogUtils.getLogger().info("MULTIMOD_QA SMART_PROJECTILE PASS: {} actual callback learned native analysis; full blocks HP and disable restores HP loss",id);return;
   }
   check(group.getAsJsonArray("mods").size()==0,"No installed real native arrow fixture was exercised");
  }finally{owner.discard();reset(p);}
 }
}
