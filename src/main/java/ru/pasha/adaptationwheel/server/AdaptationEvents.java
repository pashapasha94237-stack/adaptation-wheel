package ru.pasha.adaptationwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationState;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.AdaptationTimings;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageTypes;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import java.util.*;

/** Server-only equipment, mitigation, exposure, persistence and boss execution. */
public final class AdaptationEvents {
    private static final Map<ServerPlayer,AdaptationState> STATES = new WeakHashMap<>();
    private static final Map<ServerPlayer,List<Hit>> RECEIVED_DAMAGE = new WeakHashMap<>();
    private record Hit(String key,boolean boss,AdaptationProfile profile,DamageSource source,boolean awaitingDamage,int receipts,double raw,double actual) {}
    private AdaptationEvents() {}
    public static boolean equipped(Player player) { return player.getItemBySlot(EquipmentSlot.HEAD).is(AdaptationWheel.WHEEL.get()); }
    public static boolean active(ServerPlayer player) { return equipped(player) && state(player).enabled(); }
    public static AdaptationState state(ServerPlayer player) { return STATES.computeIfAbsent(player,AdaptationEvents::loadState); }

    public static AdaptationState loadState(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(AdaptationWheel.MOD_ID);
        boolean enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        if (tag.getInt("Version") >= 2) {
            Map<String,AdaptationState.Snapshot> entries = new LinkedHashMap<>();
            ListTag list = tag.getList("Entries",Tag.TAG_COMPOUND);
            for (int i=0;i<list.size();i++) {
                CompoundTag entry = list.getCompound(i);
                String key=migrateKey(entry.getString("Key"));
                if(RETIRED.contains(key))continue;
                AdaptationProfile profile=AdaptationProfile.forKey(key,entry.getBoolean("Boss"));
                if(entry.contains("Profile")) try { profile=AdaptationProfile.valueOf(entry.getString("Profile")); }
                    catch(IllegalArgumentException ignored) { }
                // Two saved rows can fold into one key (player_explosion into explosion): the earned stage survives.
                entries.merge(key,new AdaptationState.Snapshot(key,entry.getBoolean("Enabled"),
                        entry.getBoolean("Boss"),entry.getInt("Stage"),entry.getDouble("Work"),
                        entry.getInt("Hits"),entry.getBoolean("Running"),profile,
                        readTimings(entry),entry.getInt("AnalysisActiveDuration"),entry.getInt("AnalysisConfidence"),entry.getString("AnalysisReason"),entry.getInt("AnalysisObservations"),readManual(entry)),
                        (a,b) -> b.stage()>a.stage()||b.stage()==a.stage()&&b.work()>a.work() ? b : a);
            }
            AdaptationState restored=AdaptationState.restore(enabled,List.copyOf(entries.values()),tag.getLong("Revision"));
            restored.setNightVisionEnabled(!tag.contains("NightVisionEnabled") || tag.getBoolean("NightVisionEnabled"));
            return restored;
        }
        Set<String> complete = new LinkedHashSet<>();
        ListTag completed = tag.getList("Completed",Tag.TAG_STRING);
        for (int i=0;i<completed.size();i++) { String key=migrateKey(completed.getString(i)); if(!RETIRED.contains(key))complete.add(key); }
        Map<String,Integer> pending = new LinkedHashMap<>();
        ListTag list = tag.getList("Pending",Tag.TAG_COMPOUND);
        for (int i=0;i<list.size();i++) {
            CompoundTag e = list.getCompound(i);
            String key=migrateKey(e.getString("Category"));
            if(!RETIRED.contains(key))pending.put(key,e.getInt("Elapsed"));
        }
        return AdaptationState.restore(enabled,complete,pending,tag.getLong("Revision"));
    }
    public static void save(ServerPlayer player) {
        AdaptationState state = state(player);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version",5); tag.putBoolean("Enabled",state.enabled()); tag.putLong("Revision",state.revision());
        tag.putBoolean("NightVisionEnabled",state.nightVisionEnabled());
        ListTag list = new ListTag();
        for (AdaptationState.Snapshot p : state.snapshots()) {
            CompoundTag e = new CompoundTag();
            e.putString("Key",p.key()); e.putBoolean("Enabled",p.enabled()); e.putBoolean("Boss",p.boss());
            e.putString("Profile",p.profile().name());
            if(p.manualProfile()!=null)e.putString("ManualProfile",p.manualProfile().name());
            e.putInt("Stage",p.stage()); e.putDouble("Work",p.work()); e.putInt("Hits",p.hits()); e.putBoolean("Running",p.running());
            if(p.analyzedDurations()!=null){int[] timings=new int[5];for(int n=0;n<5;n++)timings[n]=p.analyzedDurations().ticks(n);e.putIntArray("AnalysisTimings",timings);}
            e.putInt("AnalysisActiveDuration",p.activeDuration());e.putInt("AnalysisConfidence",p.analysisConfidence());e.putString("AnalysisReason",p.analysisReason());e.putInt("AnalysisObservations",p.analysisObservations());
            list.add(e);
        }
        tag.put("Entries",list);
        SmartAttackAnalyzer.write(player,tag);
        CombatObserver.write(player,tag);
        player.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
    }
    private static AdaptationProfile readManual(CompoundTag entry){
        try{return entry.contains("ManualProfile")?AdaptationProfile.valueOf(entry.getString("ManualProfile")):null;}catch(IllegalArgumentException unknown){return null;}
    }
    private static ru.pasha.adaptationwheel.core.StageDurations readTimings(CompoundTag entry){
        int[] t=entry.getIntArray("AnalysisTimings");if(t.length!=5)return null;
        try{return new ru.pasha.adaptationwheel.core.StageDurations(t[0],t[1],t[2],t[3],t[4]);}catch(IllegalArgumentException invalid){return null;}
    }
    /** Adaptations that no longer exist; their saved rows are dropped on load (landscape memory left with Distant Horizons in 4.49.0). */
    static final Set<String> RETIRED=Set.of("environment:landscape_memory","environment:silent_world","environment:console_commands","environment:wheel_update","environment:foreign_mods");
    static String migrateKey(String key) {
        return switch(key) {
            case "damage:minecraft:starve" -> AdaptationProfile.HUNGER_KEY;
            case "damage:minecraft:freeze" -> AdaptationProfile.COLD_KEY;
            case "damage:minecraft:magic" -> NegativeEffects.key(MobEffects.HARM);
            case "damage:minecraft:wither" -> NegativeEffects.key(MobEffects.WITHER);
            case "damage:minecraft:player_explosion", "damage:minecraft:unattributed_fireball" -> DamageCategories.canonicalKey(key);
            default -> key;
        };
    }
    public static void saveAndSync(ServerPlayer player) { save(player); WheelNetwork.sync(player); }
    /** The chat line for a stage just reached, by time or (for a higher stage) by command. */
    static void announceStage(ServerPlayer player,String key) {
        int stage = state(player).stage(key);
        WheelAdvancements.stage(player,stage);
        if(ru.pasha.adaptationwheel.core.MetaMechanic.fromKey(key)!=null){player.displayClientMessage(Component.translatable("meta.adaptationwheel.stage_complete",DamageCategories.displayName(key),stage).withStyle(ChatFormatting.GOLD),false);return;}
        if(stage==5&&key.startsWith("mob:")&&WorldSlash.unlocked(player))
            player.displayClientMessage(Component.translatable("message.adaptationwheel.world_slash_unlocked").withStyle(ChatFormatting.LIGHT_PURPLE),false);
        player.displayClientMessage(Component.translatable(stage == 5
                ? "message.adaptationwheel.existence" : "message.adaptationwheel.stage",
                // «Отравление: этап 4/4 пройден — защита 100%»: the stage number says what was completed; a boss has a fifth.
                DamageCategories.displayName(key),AdaptationState.reductionPercent(stage),stage,
                state(player).snapshots().stream().anyMatch(s->s.key().equals(key)&&s.boss())&&ru.pasha.adaptationwheel.core.AdaptationTimings.existenceEnabled()?5:4)
                .withStyle(ChatFormatting.GOLD),false);
    }
    /** A command raised a stage: what a completion does, except the optional heal. */
    static void stageGiven(ServerPlayer player,String key) {
        NegativeEffects.onCompletions(player,List.of(key));
        player.serverLevel().playSound(null,player.getX(),player.getY(),player.getZ(),AdaptationWheel.ADAPTED_SOUND.get(),SoundSource.PLAYERS,1,1);
        announceStage(player,key);
    }
    public static void discardQueuedDamage(ServerPlayer player) { RECEIVED_DAMAGE.remove(player); }
    /** A persistent condition is one exposure, not an extra hit on every native tick. */
    public static void continuousExposure(ServerPlayer player,String key) {
        if(!active(player)||!state(player).categoryEnabled(key))return;
        if(state(player).snapshots().stream().anyMatch(s->s.key().equals(key)&&s.running())
                ||RECEIVED_DAMAGE.getOrDefault(player,List.of()).stream().anyMatch(h->h.key().equals(key)))return;
        SmartAttackAnalyzer.continuous(player,key);
        state(player).hit(key,false);
    }
    public static void acceptedDamage(ServerPlayer player,DamageSource source) {
        acceptedDamage(player,source,0);
    }
    public static void acceptedDamage(ServerPlayer player,DamageSource source,double amount) {
        if(!ModAttackCompatibility.directHealthWrite(player))queue(player,source,false,2,amount);
    }
    public static void recordExposure(ServerPlayer player,String key,boolean boss) {
        recordExposure(player,key,boss,null,0,0);
    }
    public static void recordExposure(ServerPlayer player,String key,boolean boss,DamageSource source,double raw,double actual) {
        AdaptationProfile profile=JujutsuCompatibility.techniqueKey(key)?JujutsuCompatibility.profile(key):AdaptationProfile.forKey(key,boss);
        if(key.startsWith("effect:")) {
            var id=net.minecraft.resources.ResourceLocation.tryParse(key.substring(7));
            var effect=id==null ? null : net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);
            if(effect!=null) {
                if(!DamageCategories.isHarmful(effect)) return;
                profile=DamageCategories.effectProfile(effect);
            }
        }
        if(JujutsuCompatibility.techniqueKey(key) && RECEIVED_DAMAGE.getOrDefault(player,List.of()).stream().anyMatch(h->h.key().equals(key)))return;
        if(active(player) && state(player).categoryEnabled(key))
            RECEIVED_DAMAGE.computeIfAbsent(player,ignored -> new ArrayList<>()).add(new Hit(key,boss,profile,source,false,0,raw,actual));
    }
    private static void queue(ServerPlayer player,DamageSource source) {
        queue(player,source,false);
    }
    private static void queue(ServerPlayer player,DamageSource source,boolean attack) {
        queue(player,source,attack,attack?0:1);
    }
    private static void queue(ServerPlayer player,DamageSource source,boolean attack,int receipt) {
        queue(player,source,attack,receipt,0);
    }
    private static void queue(ServerPlayer player,DamageSource source,boolean attack,int receipt,double amount) {
        if(!active(player)) return;
        if(attack)DefeatMemory.contact(player,source);
        List<Hit> hits=RECEIVED_DAMAGE.computeIfAbsent(player,ignored -> new ArrayList<>());
        // An attack and its downstream health damage are one exposure. Separate attack
        // events still count separately, even when Minecraft reuses the same DamageSource.
        var observed=exposures(player,source);
        source=ModAttackCompatibility.impactSource(player,source);
        double raw=ModAttackCompatibility.rawAmount(player,amount);
        for(var exposure:observed) {
            String key=exposure.key();
            if(!state(player).categoryEnabled(key)) continue;
            if(!attack&&ModAttackCompatibility.repeatedAccepted(player,key))continue;
            if(JujutsuCompatibility.techniqueKey(key) && hits.stream().anyMatch(h->h.key().equals(key)))continue;
            // Contact/cancelled hits accelerate only an already encountered source.
            if(attack && !contactEligible(player,exposure,hits)) continue;
            boolean matched=false;
            if (!attack) for (int i=0;i<hits.size();i++) {
                Hit hit=hits.get(i);
                if (hit.key().equals(key) && sameImpactSource(hit.source(),source) && hit.awaitingDamage()) {
                    hits.set(i,new Hit(hit.key(),hit.boss(),hit.profile(),source,false,hit.receipts()|receipt,Math.max(raw,hit.raw()),Math.max(amount,hit.actual())));
                    matched=true;break;
                }
            }
            // First Aid's committed limb damage and vanilla's HP notification can
            // describe the same hit. Pair the two APIs, after matching the current
            // attack contact, so separate real hits using the same source still count.
            if(!attack&&!matched)for(int i=0;i<hits.size();i++){
                Hit hit=hits.get(i);
                if(hit.key().equals(key)&&sameImpactSource(hit.source(),source)&&!hit.awaitingDamage()
                        &&hit.receipts()!=0&&(hit.receipts()&receipt)==0){
                    hits.set(i,new Hit(hit.key(),hit.boss(),hit.profile(),source,false,hit.receipts()|receipt,Math.max(raw,hit.raw()),Math.max(amount,hit.actual())));matched=true;break;
                }
            }
            if(!matched) hits.add(new Hit(key,exposure.boss(),exposure.profile(),source,attack,receipt,raw,attack?0:amount));
        }
    }
    private static boolean sameImpactSource(DamageSource contact,DamageSource damage) {
        // Damage hooks may rebuild a DamageSource. Object identity alone then
        // mistakes the downstream health event for another attack.
        return contact==damage || contact!=null && contact.typeHolder().equals(damage.typeHolder())
            && contact.getEntity()==damage.getEntity() && contact.getDirectEntity()==damage.getDirectEntity();
    }
    private static boolean contactEligible(ServerPlayer player,DamageCategories.Exposure exposure,List<Hit> queued) {
        String key=exposure.key();
        return state(player).snapshots().stream().anyMatch(p -> p.key().equals(key)
                && AdaptationState.canProgress(p.boss(),p.stage())
                && (p.running() && !p.finished() || exposure.boss() && p.stage()==4))
                || queued.stream().anyMatch(hit -> hit.key().equals(key));
    }
    private static List<DamageCategories.Exposure> exposures(ServerPlayer player,DamageSource source) {
        // Rebuilt JJC/First Aid notifications belong to the original physical
        // contact. Classify and correlate them using that same source.
        source=ModAttackCompatibility.impactSource(player,source);
        List<DamageCategories.Exposure> result=new ArrayList<>();
        var analyzed=DamageCategories.analyze(source,player);
        if(analyzed.isEmpty()) return List.of();
        String disease=T2SCompatibility.damageCause();
        if(disease!=null && source.getEntity()==null && source.getDirectEntity()==null)
            return List.of(new DamageCategories.Exposure(disease,AdaptationProfile.forKey(disease,false),false));
        // Lingering unattributed wither damage also respects a learned Wither species,
        // without inventing a boss before actual exposure to that opponent.
        if(source.is(DamageTypes.WITHER) && source.getEntity()==null && source.getDirectEntity()==null) {
            state(player).snapshots().stream().filter(p -> p.key().equals("mob:minecraft:wither"))
                .findFirst().ifPresent(p -> result.add(new DamageCategories.Exposure(p.key(),p.profile(),p.boss())));
        }
        result.addAll(JujutsuCompatibility.exposures(source));
        var precise=ModAttackCompatibility.exposure(player,source);
        if(precise==null)precise=ObservedAbilities.exposure(source);
        // A plain blow of a mod creature: its own row already says everything, a second one would only split the stages.
        if(precise!=null&&!DamageCategories.plainMelee(source))result.add(precise);
        for(var exposure:analyzed) {
            boolean harm=exposure.key().equals(NegativeEffects.key(MobEffects.HARM));
            if(harm && source.is(DamageTypes.MAGIC) && player.hasEffect(MobEffects.POISON))
                result.add(new DamageCategories.Exposure(NegativeEffects.key(MobEffects.POISON),DamageCategories.effectProfile(MobEffects.POISON),false));
            // A mod's effect ticking through plain magic damage already has its own key: it is not Instant Damage.
            // The precise row is this hit; beside it a shared type like arrow or generic would start a second adaptation.
            else if(precise==null || exposure.key().startsWith("mob:")) result.add(exposure);
        }
        return List.copyOf(result);
    }
    private static double damageMultiplier(ServerPlayer player,DamageSource source) {
        source=ModAttackCompatibility.impactSource(player,source);
        var exposures=exposures(player,source);
        boolean precise=exposures.stream().anyMatch(e->e.key().startsWith("attack:"));
        boolean basicPhysical=DamageCategories.profile(source)==AdaptationProfile.PHYSICAL
                &&!(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile);
        boolean distinct=!precise&&!basicPhysical&&(source.getEntity() instanceof LivingEntity||source.getDirectEntity() instanceof LivingEntity)
                &&exposures.stream().anyMatch(e->!e.key().startsWith("mob:"));
        // Learning a creature's ordinary bite must not silently unlock immunity to its breath.
        // Species progress is still earned for its separate fifth existence stage.
        double multiplier=exposures.stream().filter(e->precise?e.key().startsWith("attack:")||e.key().startsWith("mob:")&&state(player).existenceAdapted(e.key())
                :!distinct||!e.key().startsWith("mob:")||state(player).existenceAdapted(e.key()))
            .mapToDouble(e -> state(player).damageMultiplier(e.key())).min().orElse(1);
        // Plain blows stopped starting a row of their own in 4.43; what was earned on one before that still protects.
        var earned=DamageCategories.plainMelee(source)?ModAttackCompatibility.exposure(player,source):null;
        return earned==null?multiplier:Math.min(multiplier,state(player).damageMultiplier(earned.key()));
    }
    static double expectedMultiplier(ServerPlayer player,DamageSource source){return damageMultiplier(player,source);}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer player) || !active(player)) return;
        if (damageMultiplier(player,event.getSource())==0) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void onContact(LivingAttackEvent event) {
        if (event.getAmount()<=0 || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isAlive() || !active(player)) return;
        if(ModAttackCompatibility.repeatedContact(player))return;
        // First exposure requires actual damage. Once started, contact hits during
        // vanilla hurt cooldown accelerate the current stage without needing lost HP.
        // Observe canceled events too: our immunity and other mods can block the
        // health path. This is the single contact counter, including boss immunity.
        queue(player,event.getSource(),true,0,event.getAmount());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active(player)) return;
        double multiplier = damageMultiplier(player,event.getSource());
        if (multiplier == 0) event.setCanceled(true);
        else event.setAmount((float)(event.getAmount()*multiplier));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof ServerPlayer player&&!ModAttackCompatibility.directHealthWrite(player)) queue(player,event.getSource(),false,1,event.getAmount());
    }

    /** Public for event integration tests; the actual attack callback invokes this same path. */
    public static boolean executeAdaptedBoss(ServerPlayer player,Entity entity) {
        LivingEntity target = entity instanceof EnderDragonPart part ? part.parentMob
                : entity instanceof LivingEntity living ? living : null;
        if (target == null || !target.isAlive() || !DamageCategories.boss(target) || !active(player) || !AdaptationTimings.oneHitKillEnabled()
                || !state(player).existenceAdapted(DamageCategories.forEntity(target))) return false;
        DamageSource source = player.damageSources().playerAttack(player);
        if (target instanceof EnderDragon dragon) {
            dragon.hurt(dragon.head,source,Float.MAX_VALUE);
            // Dragon phases can clamp or reject damage: retain the vanilla dying animation and loot.
            if (dragon.getHealth() > 0) dragon.setHealth(0);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.DYING);
        } else {
            target.hurt(source,Float.MAX_VALUE);
            if (target.isAlive()) { target.setHealth(0); target.die(source); }
        }
        return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && executeAdaptedBoss(player,event.getTarget()))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void onTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        if(event.phase==TickEvent.Phase.START) { CombatObserver.start(player);NegativeEffects.beforeTick(player);NegativeEffects.sustainHunger(player); return; }
        CombatObserver.finish(player);
        NegativeEffects.observeEnvironment(player);
        AdaptationState state = state(player);
        long beforeRevision=state.revision();
        List<Hit> received = RECEIVED_DAMAGE.remove(player);
        boolean changed = false;
        if (player.isAlive()) {
            Set<String> newlyStarted=new HashSet<>();
            if (received != null && equipped(player)) for (Hit hit : received) {
                SmartAttackAnalyzer.observe(player,hit.key(),hit.boss(),hit.profile(),hit.source(),hit.raw(),hit.actual());
                if (state.hit(hit.key(),hit.boss(),hit.profile())) {
                    newlyStarted.add(hit.key());
                    player.displayClientMessage(Component.translatable("message.adaptationwheel.started",
                            DamageCategories.displayName(hit.key())).withStyle(ChatFormatting.YELLOW),false);
                }
                changed = true;
            }
            // Apply this tick's contact bonuses to the current stage, not to the
            // next stage when a completion and another hit coincide.
            if(!DarknessLight.isDark(player)) newlyStarted.add(AdaptationProfile.DARK_KEY);
            List<String> completed = state.tick(equipped(player),newlyStarted);
            NegativeEffects.onCompletions(player,completed);
            if (!completed.isEmpty()) {
                if(AdaptationTimings.healAfterStage()) {
                    player.setHealth(player.getMaxHealth());
                    FirstAidCompatibility.healAll(player);
                }
                player.serverLevel().playSound(null,player.getX(),player.getY(),player.getZ(),
                        AdaptationWheel.ADAPTED_SOUND.get(),SoundSource.PLAYERS,1,1);
                for (String key: completed) announceStage(player,key);
                changed = true;
            }
        } else if (!state.pending().isEmpty()) { state.clearPending(); changed = true; }
        NegativeEffects.maintain(player);
        MobControlCompatibility.maintain(player);
        DarknessLight.update(player);
        boolean batched=MetaServer.tpsProfile(player);
        if ((changed || !state.pending().isEmpty()) && (!batched || player.tickCount%20==0 || state.revision()!=beforeRevision)) save(player);
        if ((!batched&&changed) || player.tickCount%(batched?20:10)==0 || state.revision()!=beforeRevision) WheelNetwork.sync(player);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            state(player).clearPending(); discardQueuedDamage(player); saveAndSync(player);
            NegativeEffects.clear(player); DarknessLight.clear(player);
        }
    }
    @SubscribeEvent public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer replacement) {
            AdaptationState old = event.getOriginal() instanceof ServerPlayer original ? state(original) : loadState(event.getOriginal());
            AdaptationState copy = AdaptationState.restore(old.enabled(),old.snapshots(),old.revision());
            copy.setNightVisionEnabled(old.nightVisionEnabled());
            if (event.isWasDeath()) copy.clearPending();
            STATES.put(replacement,copy);
            if(event.getOriginal() instanceof ServerPlayer original)SmartAttackAnalyzer.copy(original,replacement);
            if(event.getOriginal() instanceof ServerPlayer original)CombatObserver.copy(original,replacement);
            if (event.getOriginal() instanceof ServerPlayer original) { STATES.remove(original); RECEIVED_DAMAGE.remove(original); }
            save(replacement);
        }
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { saveAndSync(player); WheelAdvancements.catchUp(player); }
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NegativeEffects.clear(player); DarknessLight.clear(player);
            save(player); STATES.remove(player); RECEIVED_DAMAGE.remove(player);
            WheelNetwork.forget(player);
            SmartAttackAnalyzer.forget(player);
            CombatObserver.forget(player);
            NeuralAdvisor.forget(player);
        }
    }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) saveAndSync(player);
    }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { DarknessLight.clear(player); saveAndSync(player); }
    }
    @SubscribeEvent public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer subject && event.getEntity() instanceof ServerPlayer viewer)
            WheelNetwork.sendTo(subject,viewer);
    }
    @SubscribeEvent public static void onEquipment(LivingEquipmentChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.HEAD && event.getEntity() instanceof ServerPlayer player) {
            if (event.getTo().is(AdaptationWheel.WHEEL.get())) WheelAdvancements.award(player,WheelAdvancements.WORN);
            saveAndSync(player);
        }
    }
    @SubscribeEvent public static void onCommands(RegisterCommandsEvent event) { AdaptationCommands.register(event.getDispatcher()); }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        CombatObserver.clearAll();
        NeuralAdvisor.reset();
        DarknessLight.clearAll(); NegativeEffects.clearAll(); STATES.clear(); RECEIVED_DAMAGE.clear();
        WheelNetwork.clearServerState();
        SmartAttackAnalyzer.clearAll();
    }
}

