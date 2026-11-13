package ru.pasha.adaptationwheel.server;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.boss.dragon.*;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.WheelNetwork;

public final class AdaptationEvents {
 private static final Map<ServerPlayerEntity,AdaptationState> STATES=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,List<Hit>> QUEUED=new WeakHashMap<>();
 private static final class Hit {
  final DamageCategories.Exposure exposure;final DamageSource source;boolean awaiting;
  Hit(DamageCategories.Exposure e,DamageSource s,boolean a){exposure=e;source=s;awaiting=a;}
 }
 public static boolean equipped(PlayerEntity p){return p.getItemBySlot(EquipmentSlotType.HEAD).getItem()==AdaptationWheel.WHEEL.get();}
 public static boolean active(ServerPlayerEntity p){return equipped(p)&&state(p).enabled();}
 public static AdaptationState state(ServerPlayerEntity p){return STATES.computeIfAbsent(p,AdaptationEvents::loadState);}
 public static AdaptationState loadState(PlayerEntity p){
  CompoundNBT tag=p.getPersistentData().getCompound(AdaptationWheel.MOD_ID);List<AdaptationState.Snapshot> entries=new ArrayList<>();
  ListNBT list=tag.getList("Entries",10);
  for(int i=0;i<list.size();i++){CompoundNBT e=list.getCompound(i);String key=DamageCategories.canonical(e.getString("Key"));
   AdaptationProfile profile=AdaptationProfile.forKey(key,e.getBoolean("Boss"));try{profile=AdaptationProfile.valueOf(e.getString("Profile"));}catch(IllegalArgumentException ignored){}
   entries.add(new AdaptationState.Snapshot(key,e.getBoolean("Enabled"),e.getBoolean("Boss"),e.getInt("Stage"),e.getDouble("Work"),e.getInt("Hits"),e.getBoolean("Running"),profile));
  }
  AdaptationState result=AdaptationState.restore(!tag.contains("Enabled")||tag.getBoolean("Enabled"),entries,tag.getLong("Revision"));
  result.setNightVisionEnabled(!tag.contains("NightVisionEnabled")||tag.getBoolean("NightVisionEnabled"));return result;
 }
 public static void save(ServerPlayerEntity p){
  AdaptationState s=state(p);CompoundNBT tag=new CompoundNBT();tag.putInt("Version",4);tag.putBoolean("Enabled",s.enabled());tag.putLong("Revision",s.revision());tag.putBoolean("NightVisionEnabled",s.nightVisionEnabled());
  ListNBT entries=new ListNBT();
  for(AdaptationState.Snapshot v:s.snapshots()){CompoundNBT e=new CompoundNBT();e.putString("Key",v.key());e.putString("Profile",v.profile().name());e.putBoolean("Enabled",v.enabled());e.putBoolean("Boss",v.boss());e.putInt("Stage",v.stage());e.putDouble("Work",v.work());e.putInt("Hits",v.hits());e.putBoolean("Running",v.running());entries.add(e);}
  tag.put("Entries",entries);p.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
 }
 public static void saveAndSync(ServerPlayerEntity p){save(p);WheelNetwork.sync(p);}
 public static void discardQueuedDamage(ServerPlayerEntity p){QUEUED.remove(p);}
 public static void recordEffect(ServerPlayerEntity p,net.minecraft.potion.Effect effect){
  if(active(p)&&DamageCategories.harmful(effect)){String k=DamageCategories.effectKey(effect);if(state(p).categoryEnabled(k))QUEUED.computeIfAbsent(p,x->new ArrayList<>()).add(new Hit(new DamageCategories.Exposure(k,DamageCategories.effectProfile(effect),false),null,false));}
 }
 private static List<DamageCategories.Exposure> exposures(ServerPlayerEntity p,DamageSource source){
  List<DamageCategories.Exposure> all=new ArrayList<>(DamageCategories.analyze(source));
  if(source==DamageSource.WITHER&&source.getEntity()==null&&state(p).stage("mob:minecraft:wither")>0){
   AdaptationState.Snapshot saved=state(p).snapshots().stream().filter(s->s.key().equals("mob:minecraft:wither")).findFirst().get();
   all.add(new DamageCategories.Exposure(saved.key(),saved.profile(),saved.boss()));
  }
  if(source==DamageSource.MAGIC&&p.hasEffect(net.minecraft.potion.Effects.POISON)){
   for(int i=0;i<all.size();i++)if(all.get(i).key.equals("effect:minecraft:instant_damage"))all.set(i,new DamageCategories.Exposure(DamageCategories.effectKey(net.minecraft.potion.Effects.POISON),AdaptationProfile.POISON,false));
  }
  return all;
 }
 private static boolean contactEligible(ServerPlayerEntity p,DamageCategories.Exposure e,List<Hit> hits){
  return state(p).snapshots().stream().anyMatch(v->v.key().equals(e.key)&&(v.running()&&!v.finished()||e.boss&&v.stage()==4))||hits.stream().anyMatch(h->h.exposure.key.equals(e.key));
 }
 private static void queue(ServerPlayerEntity p,DamageSource source,boolean contact){
  if(!active(p))return;List<Hit> hits=QUEUED.computeIfAbsent(p,x->new ArrayList<>());
  for(DamageCategories.Exposure e:exposures(p,source)){
   if(!state(p).categoryEnabled(e.key)||contact&&!contactEligible(p,e,hits))continue;
   boolean matched=false;
   if(!contact)for(Hit h:hits)if(h.exposure.key.equals(e.key)&&h.source==source&&h.awaiting){h.awaiting=false;matched=true;break;}
   if(!matched)hits.add(new Hit(e,source,contact));
  }
 }
 private static double multiplier(ServerPlayerEntity p,DamageSource source){return exposures(p,source).stream().mapToDouble(e->state(p).damageMultiplier(e.key)).min().orElse(1);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent e){
  if(e.getAmount()>0&&e.getEntityLiving() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getEntityLiving();if(active(p)&&multiplier(p,e.getSource())==0){queue(p,e.getSource(),true);e.setCanceled(true);}}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void contact(LivingAttackEvent e){
  if(e.getAmount()>0&&e.getEntityLiving() instanceof ServerPlayerEntity&&e.getEntityLiving().isAlive())queue((ServerPlayerEntity)e.getEntityLiving(),e.getSource(),true);
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingHurtEvent e){
  if(e.getEntityLiving() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getEntityLiving();if(active(p)){double m=multiplier(p,e.getSource());if(m==0)e.setCanceled(true);else e.setAmount((float)(e.getAmount()*m));}}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damaged(LivingDamageEvent e){if(e.getAmount()>0&&e.getEntityLiving() instanceof ServerPlayerEntity)queue((ServerPlayerEntity)e.getEntityLiving(),e.getSource(),false);}
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
  if(!(event.player instanceof ServerPlayerEntity))return;ServerPlayerEntity p=(ServerPlayerEntity)event.player;
  if(event.phase==TickEvent.Phase.START){NegativeEffects.sustainHunger(p);return;}
  AdaptationState s=state(p);NegativeEffects.observe(p);
  List<Hit> hits=QUEUED.remove(p);Set<String> skip=new HashSet<>();boolean changed=false;
  if(p.isAlive()){
   if(hits!=null&&equipped(p))for(Hit h:hits){if(s.hit(h.exposure.key,h.exposure.boss,h.exposure.profile)){skip.add(h.exposure.key);p.displayClientMessage(new TranslationTextComponent("message.adaptationwheel.started",DamageCategories.name(h.exposure.key)),false);}changed=true;}
   if(!DarknessLight.isDark(p))skip.add(AdaptationProfile.DARK_KEY);
   List<String> completed=s.tick(equipped(p),skip);NegativeEffects.completed(p,completed);
   if(!completed.isEmpty()){p.setHealth(p.getMaxHealth());p.getLevel().playSound(null,p.getX(),p.getY(),p.getZ(),AdaptationWheel.ADAPTED_SOUND.get(),SoundCategory.PLAYERS,1,1);for(String k:completed)p.displayClientMessage(new TranslationTextComponent(s.stage(k)==5?"message.adaptationwheel.existence":"message.adaptationwheel.stage",DamageCategories.name(k),AdaptationState.reductionPercent(s.stage(k))),false);changed=true;}
  }else if(!s.pending().isEmpty()){s.clearPending();changed=true;}
  NegativeEffects.maintain(p);DarknessLight.update(p);if(changed||!s.pending().isEmpty())save(p);if(changed||p.tickCount%10==0)WheelNetwork.sync(p);
 }
 public static boolean executeBoss(ServerPlayerEntity p,Entity entity){
  LivingEntity target=entity instanceof EnderDragonPartEntity?((EnderDragonPartEntity)entity).parentMob:entity instanceof LivingEntity?(LivingEntity)entity:null;
  if(target==null||!target.isAlive()||!active(p)||!DamageCategories.boss(target)||!state(p).existenceAdapted(DamageCategories.forEntity(target)))return false;
  DamageSource source=DamageSource.playerAttack(p);
  if(target instanceof EnderDragonEntity){EnderDragonEntity d=(EnderDragonEntity)target;d.hurt(d.head,source,Float.MAX_VALUE);if(d.getHealth()>0)d.setHealth(0);d.getPhaseManager().setPhase(PhaseType.DYING);}
  else{target.hurt(source,Float.MAX_VALUE);if(target.isAlive()){target.setHealth(0);target.die(source);}}return true;
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void playerAttack(AttackEntityEvent e){if(e.getPlayer() instanceof ServerPlayerEntity&&executeBoss((ServerPlayerEntity)e.getPlayer(),e.getTarget()))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(e.getEntityLiving() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getEntityLiving();state(p).clearPending();discardQueuedDamage(p);NegativeEffects.clear(p);DarknessLight.clear(p);saveAndSync(p);}}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){
  if(e.getPlayer() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getPlayer();AdaptationState old=e.getOriginal() instanceof ServerPlayerEntity?state((ServerPlayerEntity)e.getOriginal()):loadState(e.getOriginal());AdaptationState copy=AdaptationState.restore(old.enabled(),old.snapshots(),old.revision());copy.setNightVisionEnabled(old.nightVisionEnabled());if(e.isWasDeath())copy.clearPending();STATES.put(p,copy);save(p);}
 }
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getPlayer() instanceof ServerPlayerEntity)saveAndSync((ServerPlayerEntity)e.getPlayer());}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getPlayer() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getPlayer();NegativeEffects.clear(p);DarknessLight.clear(p);save(p);STATES.remove(p);QUEUED.remove(p);WheelNetwork.forget(p);}}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getPlayer() instanceof ServerPlayerEntity){ServerPlayerEntity p=(ServerPlayerEntity)e.getPlayer();DarknessLight.clear(p);saveAndSync(p);}}
 @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getPlayer() instanceof ServerPlayerEntity)saveAndSync((ServerPlayerEntity)e.getPlayer());}
 @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getPlayer() instanceof ServerPlayerEntity&&e.getTarget() instanceof ServerPlayerEntity)WheelNetwork.sendTo((ServerPlayerEntity)e.getTarget(),(ServerPlayerEntity)e.getPlayer());}
 @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent e){if(e.getSlot()==EquipmentSlotType.HEAD&&e.getEntityLiving() instanceof ServerPlayerEntity)saveAndSync((ServerPlayerEntity)e.getEntityLiving());}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){AdaptationCommands.register(e.getDispatcher());}
 @SubscribeEvent public static void stopped(FMLServerStoppedEvent e){DarknessLight.clearAll();NegativeEffects.clearAll();STATES.clear();QUEUED.clear();WheelNetwork.clear();}
}