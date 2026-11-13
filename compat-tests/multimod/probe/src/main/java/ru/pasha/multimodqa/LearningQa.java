package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.lwjgl.glfw.GLFW;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Actual damage, paced registry inspection, persistence and client-to-server learning controls. */
final class LearningQa {
    static final String CONTROLLER = BASE + "LearningController", MODE = CORE + "LearningMode";
    static final String UNSEEN = "damage:multimodqa:pattern_cold_c";
    static Zombie owner;
    static Entity pulse;
    static ItemStack wheel;
    static int ticks, phase, inspectedBefore, lastInspected;
    static long lastScanTick;
    static float researchHealth;
    static boolean expectedTrained;

    static Object invoke(String method, ServerPlayer p) throws Exception { return call(CONTROLLER, method, new Class<?>[]{ServerPlayer.class}, p); }
    static Object memory(ServerPlayer p) throws Exception { return invoke("memory", p); }
    static Object status(ServerPlayer p) throws Exception { return invoke("status", p); }
    static int count(Object object, String method) throws Exception { return ((Number)getter(object, method)).intValue(); }
    static int observed(ServerPlayer p) throws Exception { return count(memory(p), "observationCount"); }
    static int inspected(ServerPlayer p) throws Exception { return count(status(p), "inspected"); }
    static void mode(ServerPlayer p, String name) throws Exception {
        Class type = Class.forName(MODE);
        call(CONTROLLER, "setMode", new Class<?>[]{ServerPlayer.class, type}, p, Enum.valueOf(type, name));
        check(invoke("mode", p).toString().equals(name), "Server learning mode did not change to " + name);
    }
    static Object fact(ServerPlayer p, String key) throws Exception { return memory(p).getClass().getMethod("fact", String.class).invoke(memory(p), key); }

    /** Wait for actual asynchronous archive writes, without sleeping or changing the model under test. */
    static void flushArchive() throws Exception {
        var io = Class.forName(CONTROLLER).getDeclaredField("IO"); io.setAccessible(true);
        ((ExecutorService)io.get(null)).submit(() -> {}).get(3, TimeUnit.SECONDS);
    }
    static void awaitArchive(ServerPlayer p) throws Exception {
        memory(p);flushArchive();
        check((boolean)invoke("archiveReady",p),"Asynchronous archive load did not finish");
    }

    static void sharedArchive(ServerPlayer p, int learned) throws Exception {
        String flavor = expectedTrained ? "trained" : "untrained";
        CompoundTag learningRoot = p.getPersistentData().getCompound("adaptationwheel_learning");
        CompoundTag oldWorld = learningRoot.getCompound(flavor).copy();
        check(oldWorld.getInt("Schema") == 1, "Shared archive fixture has no real model NBT snapshot");
        flushArchive();
        // Only this isolated player's flavor model is absent, as on entering a world without saved examples.
        // Modes, adaptation stages, HP and all other persistent player tags stay untouched.
        learningRoot.remove(flavor); invoke("forget", p);awaitArchive(p);
        check(observed(p) == learned, "New-world model did not restore archive observations");
        for (String key : new String[]{"damage:multimodqa:pattern_cold_a", "damage:multimodqa:pattern_cold_b"}) {
            Object cold = fact(p, key);
            check(cold != null && getter(cold, "profile").toString().equals("COLD"), "New-world archive restore lost trusted cold fact: " + key);
        }
        // unknown_light_beam has only name evidence. Use the native, registered lightning-tag fixture
        // for a genuinely factual third ability, keeping pattern_cold_c unseen for WorldStudy below.
        String newest = "damage:multimodqa:misleading_fire_name";
        check(fact(p, newest) == null, "Third shared-archive fact was not fresh");
        float hp = health(p);
        check(hit(p, new DamageSource(custom("multimodqa:misleading_fire_name", "multimodqa:burning_flame", DamageTypeTags.IS_LIGHTNING), pulse, owner), 4) > 0,
                "Shared archive fixture did not exercise a real registered tagged impact");
        wheelTick(p);
        Object electric = fact(p, newest);
        check(health(p) < hp && electric != null && getter(electric, "profile").toString().equals("ELECTRIC"),
                "Real third impact failed to learn its authoritative lightning fact");
        int newer = observed(p);
        check(newer > learned, "Third real impact added no empirical archive observation");
        call(CONTROLLER, "checkpoint", new Class<?>[]{ServerPlayer.class, boolean.class}, p, true);
        flushArchive();
        // Reopen an older world's genuine, previously checkpointed model. It must merge newer
        // archive facts/examples rather than letting valid but stale world NBT shadow the archive.
        learningRoot.put(flavor, oldWorld.copy()); invoke("forget", p);awaitArchive(p);
        electric = fact(p, newest);
        check(electric != null && getter(electric, "profile").toString().equals("ELECTRIC") && observed(p) >= newer,
                "Older world NBT shadowed newer cross-world facts or observations");
        check(fact(p, UNSEEN) == null && snapshot(p, UNSEEN) == null, "Archive QA touched the later unseen research fixture");
        LogUtils.getLogger().info("MULTIMOD_QA LEARNING_SHARED_ARCHIVE PASS: real checkpoint/IO restore retained {} observations; native tagged third impact raised to {}; reopening older saved learning NBT merged newer archive fact and observations without changing player HP/stages directly", learned, newer);
    }

    static void run(ServerPlayer p) throws Exception {
        expectedTrained = group.get("expectedTrained").getAsBoolean();
        reset(p);
        damageTypes = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, p.server);
        check(invoke("mode", p).toString().equals("FROZEN"), "Default-config fixture failed to start in FROZEN mode");
        Object initial = status(p);
        check((boolean)getter(initial, "pretrained") == expectedTrained, "Wrong prepared/untrained capability marker");
        check(expectedTrained ? count(initial, "seedFacts") > 0 : count(initial, "seedFacts") == 0,
                "Prepared/untrained variant has incorrect seed facts: " + count(initial, "seedFacts"));
        check(inspected(p) == 0, "World research ran while learning was frozen");
        owner = EntityType.ZOMBIE.create(p.serverLevel()); owner.setNoAi(true); owner.moveTo(p.getX() + 3, p.getY(), p.getZ());
        p.serverLevel().addFreshEntity(owner);
        pulse = UNKNOWN_PULSE.get().create(p.serverLevel());
        int before = observed(p); float hp = health(p); p.invulnerableTime = 0;
        owner.doHurtTarget(p); wheelTick(p);
        check(health(p) < hp && stage(p, "mob:minecraft:zombie") == 0 && snapshot(p, "mob:minecraft:zombie") != null,
                "Frozen research incorrectly disabled actual ordinary damage/adaptation");
        check(observed(p) == before, "Frozen mode accumulated a new empirical example");
        LogUtils.getLogger().info("MULTIMOD_QA LEARNING_FROZEN PASS: variant={} seeds={}, native zombie removes actual HP and starts stage zero while empirical learning stays frozen",
                expectedTrained ? "trained" : "untrained", count(initial, "seedFacts"));
        mode(p, "ADAPTIVE");
        for (String name : new String[]{"pattern_cold_a", "pattern_cold_b"}) {
            hp = health(p);
            check(hit(p, new DamageSource(custom("multimodqa:" + name, "multimodqa:" + name), pulse, owner), 4) > 0,
                    "Real tagged cold impact failed to remove HP");
            wheelTick(p);
            check(health(p) < hp, "A category example granted unearned immunity");
        }
        check(observed(p) > before, "ADAPTIVE mode did not learn from actual registered damage");
        int learned = observed(p);
        call(BASE + "AdaptationEvents", "save", new Class<?>[]{ServerPlayer.class}, p);
        call(CONTROLLER, "checkpoint", new Class<?>[]{ServerPlayer.class, boolean.class}, p, true);
        invoke("forget", p);
        check(observed(p) == learned && invoke("mode", p).toString().equals("ADAPTIVE"), "Learning observations or player mode lost after checkpoint/reload");
        if (group.has("sharedArchive") && group.get("sharedArchive").getAsBoolean()) sharedArchive(p, learned);
        check(fact(p, UNSEEN) == null && snapshot(p, UNSEEN) == null, "Unseen QA damage definition was already learned before research");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2000, 0));
        // Isolate inspection from completion healing of the ordinary adaptation timers exercised above.
        state(p).getClass().getMethod("reset").invoke(state(p));
        wheel = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        inspectedBefore = inspected(p);
        LogUtils.getLogger().info("MULTIMOD_QA LEARNING_ADAPTIVE PASS: actual cold impacts add empirical observations without free immunity; checkpoint/reload preserves {} observations", learned);
    }
    interface ServerAction { void run(ServerPlayer p) throws Exception; }
    static void server(ServerAction action) throws Exception {
        var mc = Minecraft.getInstance(); var integrated = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        integrated.submit(() -> { try { action.run(integrated.getPlayerList().getPlayer(uuid)); } catch (Exception e) { throw new RuntimeException(e); } }).get();
    }
    static void press(String mode) throws Exception {
        var mc = Minecraft.getInstance(); check(mc.screen != null && mc.screen.getClass().getName().endsWith("LearningScreen"), "Learning settings screen not open");
        var buttons = (Map<?, ?>)ScreensQa.field(mc.screen, "modes");
        for (var row : buttons.entrySet()) if (row.getKey().toString().equals(mode)) {
            Button button = (Button)row.getValue(); check(button.active, "Learning mode control unexpectedly disabled: " + mode); button.onPress(); return;
        }
        throw new AssertionError("Missing learning mode button: " + mode);
    }
    static void checkMode(String expected) throws Exception {
        server(p -> check(invoke("mode", p).toString().equals(expected), "Mode button changed only the UI, server stayed " + invoke("mode", p)));
        Object client = call("ru.pasha.adaptationwheel.client.LearningClient", "state", new Class<?>[]{});
        check(client != null && getter(client, "mode").toString().equals(expected), "Server learning mode not reflected by client status: " + expected);
    }
    static void screenshot(String suffix) {
        var mc = Minecraft.getInstance(); String gui = mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
        if (group.has("gui")) check(gui.equals(group.get("gui").getAsString()), "Wrong learning GUI dimensions: " + gui);
        Screenshot.grab(mc.gameDirectory, "learning-" + suffix + "-" + gui + ".png", mc.getMainRenderTarget(), c -> {});
    }
    static boolean clientTick() throws Exception {
        if (++ticks > 2400) throw new AssertionError("Learning QA timed out at phase " + phase);
        if (ticks % 20 != 0) return false;
        var mc = Minecraft.getInstance();
        switch (phase) {
            case 0 -> server(p -> {
                check(inspected(p) == inspectedBefore, "ADAPTIVE mode unexpectedly performed registry research");
                p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); mode(p, "RESEARCH");
            });
            case 1 -> server(p -> {
                check(inspected(p) == inspectedBefore, "Research ran after physical wheel removal");
                p.setItemSlot(EquipmentSlot.HEAD, wheel); researchHealth = health(p);
                lastInspected = inspected(p); lastScanTick = p.serverLevel().getGameTime();
            });
            case 2 -> {
                final boolean[] complete = {false};
                server(p -> {
                    Object s = status(p); int seen = count(s, "inspected"), total = count(s, "total");
                    long now = p.serverLevel().getGameTime(), batches = (Math.max(0, now - lastScanTick) + 19) / 20 + 1;
                    check(seen - lastInspected <= batches * 8, "Research exceeded the configured definitions-per-second budget");
                    lastInspected = seen; lastScanTick = now;
                    complete[0] = total > 0 && seen >= total;
                    if (!complete[0]) return;
                    Object cold = fact(p, UNSEEN);
                    check(cold != null && getter(cold, "profile").toString().equals("COLD"), "Inspection did not learn the unseen registered freezing tag");
                    check(snapshot(p, UNSEEN) == null && health(p) == researchHealth, "Research fabricated adaptation progress or mutated health");
                    check(fact(p, "effect:minecraft:speed") == null && snapshot(p, "effect:minecraft:speed") == null && p.hasEffect(MobEffects.MOVEMENT_SPEED),
                            "Beneficial speed was turned into a threat or removed");
                    call(BASE + "AdaptationEvents", "saveAndSync", new Class<?>[]{ServerPlayer.class}, p);
                    call("ru.pasha.adaptationwheel.net.LearningNetwork", "sendStatus", new Class<?>[]{ServerPlayer.class}, p);
                    LogUtils.getLogger().info("MULTIMOD_QA LEARNING_RESEARCH PASS: {} paced registry definitions inspected, unseen freezing fact learned without stages or HP changes; positive speed preserved; wheel removal and ADAPTIVE stop scanning", seen);
                });
                if (!complete[0]) return false;
            }
            case 3 -> Class.forName("ru.pasha.adaptationwheel.client.AdaptationScreen").getMethod("open").invoke(null);
            case 4 -> {
                check(mc.screen != null && mc.screen.getClass().getName().endsWith("AdaptationScreen"), "Native wheel menu missing");
                mc.screen.keyPressed(GLFW.GLFW_KEY_L, 0, GLFW.GLFW_MOD_CONTROL);
                check(mc.screen != null && mc.screen.getClass().getName().endsWith("LearningScreen"), "Ctrl+L did not open native learning settings");
            }
            case 5 -> {
                checkMode("RESEARCH");
                Object client = call("ru.pasha.adaptationwheel.client.LearningClient", "state", new Class<?>[]{});
                server(p -> {
                    Object s = status(p);
                    for (String n : new String[]{"seedFacts", "learnedFacts", "observations", "contradictions", "inspected", "total"})
                        check(count(client, n) == count(s, n), "Displayed learning counter differs from server: " + n);
                });
                screenshot("research"); press("ADAPTIVE");
            }
            case 6 -> { checkMode("ADAPTIVE"); press("FROZEN"); }
            case 7 -> {
                checkMode("FROZEN");
                server(p -> {
                    int before = observed(p); float hp = health(p);
                    check(hit(p, new DamageSource(custom("multimodqa:pattern_cold_c", "multimodqa:pattern_cold_c"), pulse, owner), 4) > 0, "Frozen mode unexpectedly granted learned-database immunity");
                    wheelTick(p);
                    check(health(p) < hp && stage(p, UNSEEN) == 0 && observed(p) == before, "Freezing learning altered ordinary adaptation or still trained examples");
                });
            }
            case 8 -> { screenshot("frozen"); press("RESEARCH"); }
            case 9 -> { checkMode("RESEARCH"); screenshot("resumed"); }
            case 10 -> {
                mc.screen.onClose();
                check(mc.screen != null && mc.screen.getClass().getName().endsWith("AdaptationScreen"), "Learning Back lost the wheel menu");
                server(p -> { owner.discard(); pulse.discard(); });
                LogUtils.getLogger().info("MULTIMOD_QA LEARNING_UI PASS: native Ctrl+L and fixed mode controls, actual C2S server mode changes and returned S2C counters, frozen ordinary damage retained, compact/wide screenshots, back preserves wheel menu");
                return true;
            }
        }
        phase++;
        return false;
    }
}
