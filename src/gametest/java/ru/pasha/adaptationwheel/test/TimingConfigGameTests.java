package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.*;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.*;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.WheelEntry;
import ru.pasha.adaptationwheel.server.*;
import java.util.*;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimingConfigGameTests {
    @GameTest(template="empty",timeoutTicks=60)
    public static void bossTogglesPauseProgressAndIndependentlyGateExecution(GameTestHelper h) {
        var config=serverConfig();
        boolean oldExistence=AdaptationConfig.EXISTENCE_ENABLED.get(),oldKill=AdaptationConfig.ONE_HIT_KILL_ENABLED.get();
        int oldFourth=AdaptationConfig.PROTECTION_PERCENT.get(3).get();
        try {
            AdaptationConfig.EXISTENCE_ENABLED.set(true);AdaptationConfig.ONE_HIT_KILL_ENABLED.set(true);
            AdaptationConfig.PROTECTION_PERCENT.get(3).set(80);reload(config);
            TestPlayer p=atCompletion(h.getLevel(),5);var state=AdaptationEvents.state(p);String key="mob:minecraft:warden";
            var boss=EntityType.WARDEN.create(h.getLevel());var source=p.damageSources().mobAttack(boss);
            double work=state.snapshots().get(0).work();int hits=state.snapshots().get(0).hits();long revision=state.revision();
            AdaptationConfig.EXISTENCE_ENABLED.set(false);reload(config);
            var contact=new net.minecraftforge.event.entity.living.LivingAttackEvent(p,source,2);MinecraftForge.EVENT_BUS.post(contact);
            for(int i=0;i<10;i++)tick(p);
            check(h,state.stage(key)==4 && state.snapshots().get(0).work()==work && state.snapshots().get(0).hits()==hits,"Disabled existence cannot progress or collect contact bonuses");
            check(h,!state.pending().containsKey(key) && state.revision()==revision && p.getHealth()==7,"Paused fifth stage cannot heal or produce completion turns");
            AdaptationEvents.save(p);var restored=AdaptationEvents.loadState(p);
            check(h,restored.snapshots().get(0).work()==work && restored.snapshots().get(0).running(),"Saving while disabled retains resumable fifth-stage progress");
            AdaptationConfig.EXISTENCE_ENABLED.set(true);AdaptationConfig.ONE_HIT_KILL_ENABLED.set(false);reload(config);tick(p);
            check(h,state.stage(key)==5 && state.isImmune(key),"Re-enabled fifth stage resumes from saved progress and grants immunity");
            check(h,!AdaptationEvents.executeAdaptedBoss(p,boss) && boss.isAlive(),"Execution switch independently blocks one-hit kills");
            long completedRevision=state.revision();AdaptationConfig.EXISTENCE_ENABLED.set(false);reload(config);p.setHealth(20);p.damage(source,10);
            check(h,p.getHealth()==18 && !state.existenceAdapted(key),"Disabling an earned fifth stage falls back to configured fourth-stage protection");
            check(h,state.stage(key)==5 && state.revision()==completedRevision,"Earned fifth degree is retained without replaying a completion");
            AdaptationConfig.ONE_HIT_KILL_ENABLED.set(true);reload(config);
            check(h,!AdaptationEvents.executeAdaptedBoss(p,boss),"Execution also requires existence adaptation to be enabled");
            AdaptationConfig.EXISTENCE_ENABLED.set(true);reload(config);
            check(h,state.isImmune(key) && state.stage(key)==5,"Re-enabling restores earned powers without relearning");
            check(h,AdaptationEvents.executeAdaptedBoss(p,boss) && !boss.isAlive(),"Both switches on restore one-hit boss execution");
        }finally{
            AdaptationConfig.EXISTENCE_ENABLED.set(oldExistence);AdaptationConfig.ONE_HIT_KILL_ENABLED.set(oldKill);
            AdaptationConfig.PROTECTION_PERCENT.get(3).set(oldFourth);reload(config);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredHealingToggleControlsAllFiveCompletions(GameTestHelper h) {
        var config=serverConfig();
        boolean previous=AdaptationConfig.FULL_HEAL_AFTER_STAGE.get();String key="mob:minecraft:warden";
        try {
            for(boolean enabled:new boolean[]{false,true}) {
                AdaptationConfig.FULL_HEAL_AFTER_STAGE.set(enabled);reload(config);
                for(int degree=1;degree<=5;degree++) {
                    TestPlayer p=atCompletion(h.getLevel(),degree);var state=AdaptationEvents.state(p);long revision=state.revision();
                    tick(p);
                    check(h,state.stage(key)==degree && state.revision()==revision+1,"Healing toggle leaves stage completion and wheel-turn revision intact");
                    check(h,p.getHealth()==(enabled?30:7),"Degree "+degree+" full healing is "+enabled);
                    if(degree==5)check(h,state.existenceAdapted(key),"Disabled healing does not disable existence adaptation");
                }
            }
            AdaptationConfig.FULL_HEAL_AFTER_STAGE.set(false);reload(config);TestPlayer p=atCompletion(h.getLevel(),1);
            double before=AdaptationEvents.state(p).snapshots().get(0).work();
            AdaptationConfig.FULL_HEAL_AFTER_STAGE.set(true);reload(config);
            check(h,AdaptationEvents.state(p).snapshots().get(0).work()==before && p.getHealth()==7,"Reload does not reset progress or heal before completion");
            tick(p);check(h,p.getHealth()==30,"Re-enabling healing applies to an already running adaptation");
        }finally{AdaptationConfig.FULL_HEAL_AFTER_STAGE.set(previous);reload(config);}
        h.succeed();
    }
    private static TestPlayer atCompletion(ServerLevel level,int degree) {
        TestPlayer p=new TestPlayer(level);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(30);
        var state=AdaptationEvents.state(p);String key="mob:minecraft:warden";state.hit(key,true);
        for(int stage=0;stage<degree;stage++) {
            int ticks=AdaptationTimings.duration(key,AdaptationProfile.MAGIC,stage)-(stage==degree-1?1:0);
            for(int tick=0;tick<ticks;tick++)state.tick();
        }
        p.setHealth(7);return p;
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void customTimingsSurviveBothNetworkPackets(GameTestHelper h) throws Exception {
        var times=StageDurations.seconds(5,7,9,11,13);
        var entry=new WheelEntry("mob:minecraft:zombie","entity.minecraft.zombie",true,false,1,13.5,2,true,true,AdaptationProfile.PHYSICAL,times,50,new StageProtections(30,55,75,80),false,false);
        checkRoundTrip(h,"StatePacket",new Object[]{42,true,true,true,List.of(entry),8L,100L});
        var catalog=new ru.pasha.adaptationwheel.net.CatalogueEntry(entry.key(),entry.translationKey(),"Zombie",entry.profile(),times);
        checkRoundTrip(h,"CataloguePacket",new Object[]{8L,0,1,List.of(catalog)});
        checkRoundTrip(h,"SwordSettingsPacket",new Object[]{new SwordSettings.Values(5.5,1.5,3.25,List.of("minecraft:creeper","examplemod:cursed_spirit"),Map.of("minecraft:creeper",6.25,"examplemod:cursed_spirit",0.0))});
        checkRoundTrip(h,"SwordSettingsPacket",new Object[]{new SwordSettings.Values(10,2,4,List.of())});
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredProtectionChangesActualDamageAtEveryDegree(GameTestHelper h) {
        var config=serverConfig();
        var previous=AdaptationConfig.PROTECTION_PERCENT.stream().map(v->v.get()).toList();
        try {
            int[] reductions={30,55,75,80};for(int i=0;i<4;i++)AdaptationConfig.PROTECTION_PERCENT.get(i).set(reductions[i]);reload(config);
            TestPlayer p=new TestPlayer(h.getLevel());var source=p.damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));String key="mob:minecraft:zombie";
            p.damage(source,1);tick(p);var state=AdaptationEvents.state(p);
            for(int stage=1;stage<=4;stage++) {
                int guard=20000;while(state.stage(key)<stage&&guard-->0)tick(p);check(h,guard>0,"Configured stage completes");
                p.setHealth(20);p.damage(source,10);
                check(h,Math.abs(p.getHealth()-(20-10*(1-reductions[stage-1]/100.0)))<1e-4,"Stage "+stage+" actually reduces damage by "+reductions[stage-1]+" percent");
                tick(p);
            }
            check(h,!state.isImmune(key),"Fourth stage at 80 percent must not silently grant full immunity");
            AdaptationConfig.PROTECTION_PERCENT.get(3).set(100);reload(config);p.setHealth(20);p.damage(source,10);
            check(h,state.stage(key)==4 && state.isImmune(key) && p.getHealth()==20,"Reload updates protection of an already earned degree without resetting it");
            AdaptationConfig.PROTECTION_PERCENT.get(3).set(0);reload(config);p.damage(source,5);
            check(h,p.getHealth()==15,"Zero percent protection leaves full damage");
            var boss=EntityType.WARDEN.create(h.getLevel());String bossKey=DamageCategories.forEntity(boss);state.hit(bossKey,true);
            while(state.stage(bossKey)<5)state.tick();check(h,state.isImmune(bossKey),"Existence stage retains guaranteed 100 percent protection");
            check(h,AdaptationEvents.executeAdaptedBoss(p,boss),"Existence one-hit execution remains available");
            AdaptationConfig.PROTECTION_PERCENT.get(0).set(100);reload(config);
            TestPlayer dark=new TestPlayer(h.getLevel());var darkState=AdaptationEvents.state(dark);darkState.hit(AdaptationProfile.DARK_KEY,false);
            while(darkState.stage(AdaptationProfile.DARK_KEY)<1)darkState.tick();NegativeEffects.maintain(dark);
            check(h,!dark.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION),"Early 100 percent reduction does not unlock night vision before darkness degree four");
            while(darkState.stage(AdaptationProfile.DARK_KEY)<4)darkState.tick();NegativeEffects.maintain(dark);
            check(h,dark.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION),"Completed darkness still unlocks vision when fourth-stage damage reduction is zero");
            NegativeEffects.clear(dark);
        }finally{for(int i=0;i<4;i++)AdaptationConfig.PROTECTION_PERCENT.get(i).set(previous.get(i));reload(config);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredHitAccelerationChangesRealProgressWithoutJumping(GameTestHelper h) {
        var config=serverConfig();
        int previous=AdaptationConfig.HIT_ACCELERATION_PERCENT.get();
        try {
            AdaptationConfig.HIT_ACCELERATION_PERCENT.set(50);reload(config);
            TestPlayer p=new TestPlayer(h.getLevel());var source=p.damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));
            p.damage(source,1);tick(p);String key="mob:minecraft:zombie";var state=AdaptationEvents.state(p);
            check(h,state.snapshots().get(0).speed()==1,"First exposure still starts at x1.00");
            p.damage(source,1);tick(p);var first=state.snapshots().get(0);
            check(h,first.hits()==1 && first.speed()==1.5,"50 percent makes the next hit x1.50");
            double before=first.work();for(int i=0;i<20;i++)tick(p);
            check(h,Math.abs(state.snapshots().get(0).work()-before-30)<1e-8,"One real second advances 1.50 adaptation seconds");
            p.damage(source,1);tick(p);check(h,state.snapshots().get(0).speed()==2,"Two repeat hits at 50 percent produce x2.00");
            before=state.snapshots().get(0).work();AdaptationConfig.HIT_ACCELERATION_PERCENT.set(25);reload(config);
            check(h,state.snapshots().get(0).work()==before && state.snapshots().get(0).hits()==2,"Reload preserves elapsed work and repeat-hit count");
            check(h,state.snapshots().get(0).speed()==1.5,"New percentage applies to the current stage immediately");
            tick(p);check(h,state.snapshots().get(0).work()==before+1.5,"Server applies the reloaded rate");
            AdaptationConfig.HIT_ACCELERATION_PERCENT.set(0);reload(config);before=state.snapshots().get(0).work();tick(p);
            check(h,state.snapshots().get(0).speed()==1 && state.snapshots().get(0).work()==before+1,"Zero disables repeat-hit acceleration");
            AdaptationConfig.HIT_ACCELERATION_PERCENT.set(50);reload(config);
            int remaining=(int)Math.ceil((state.snapshots().get(0).duration()-state.snapshots().get(0).work())/2);
            for(int i=0;i<remaining-1;i++)tick(p);check(h,state.stage(key)==0,"No early completion with configurable acceleration");
            tick(p);check(h,state.stage(key)==1 && state.snapshots().get(0).speed()==1 && p.getHealth()==20,"Correct completion heals and resets next-stage speed");
        }finally{AdaptationConfig.HIT_ACCELERATION_PERCENT.set(previous);reload(config);}
        h.succeed();
    }
    private static void checkRoundTrip(GameTestHelper h,String name,Object[] args) throws Exception {
        Class<?> type=Class.forName("ru.pasha.adaptationwheel.net.WheelNetwork$"+name);
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object packet=ctor.newInstance(args);
        var encode=type.getDeclaredMethod("encode",net.minecraft.network.FriendlyByteBuf.class);encode.setAccessible(true);
        var decode=type.getDeclaredMethod("decode",net.minecraft.network.FriendlyByteBuf.class);decode.setAccessible(true);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {encode.invoke(packet,buffer);check(h,packet.equals(decode.invoke(null,buffer)),name+" preserves all authoritative stage durations");}
        finally{buffer.release();}
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void configuredTimingsChangeActualProgressAndCatalogue(GameTestHelper h) throws Exception {
        check(h,AdaptationConfig.SPEC.isLoaded(),"Forge loaded the world server configuration");
        var config=serverConfig();
        var physical=AdaptationConfig.CATEGORIES.get(AdaptationProfile.PHYSICAL).get(0);
        var zombie=AdaptationConfig.MOBS.get("mob:minecraft:zombie").get(0);
        int oldPhysical=physical.get(),oldZombie=zombie.get();var oldRules=AdaptationConfig.SOURCE_OVERRIDES.get();
        try {
            physical.set(40);reload(config);
            check(h,AdaptationTimings.duration("mob:minecraft:zombie",AdaptationProfile.PHYSICAL,0)==800,"Category edit sets zombie duration to forty seconds");
            zombie.set(5);reload(config);
            TestPlayer p=new TestPlayer(h.getLevel());var source=p.damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));
            p.damage(source,1);tick(p);
            check(h,AdaptationEvents.state(p).snapshots().stream().anyMatch(e->e.key().equals("mob:minecraft:zombie")&&e.duration()==100),"Zombie edit changes the actual server timer to five seconds");
            for(int i=0;i<99;i++)tick(p);
            check(h,AdaptationEvents.state(p).stage("mob:minecraft:zombie")==0 && p.getHealth()==19,"No early completion or healing");
            tick(p);check(h,AdaptationEvents.state(p).stage("mob:minecraft:zombie")==1 && p.getHealth()==20,"Five seconds actually earn resistance and heal the player");
            var catalog=AdaptationCatalogue.entries(p).stream().filter(e->e.key().equals("mob:minecraft:zombie")).findFirst().orElseThrow();
            // Unset later stages follow the analyzer's suggestion while analysis is on, the category otherwise.
            check(h,catalog.timings().first()==100 && catalog.timings().equals(AdaptationEvents.state(p).snapshot("mob:minecraft:zombie").timings()),"Catalogue receives actual per-mob times and the server's later stages");
            boolean analysis=AdaptationTimings.analysisEnabled();AdaptationTimings.setAnalysisEnabled(false);
            try{check(h,catalog.timings().first()==100 && AdaptationEvents.state(p).snapshot("mob:minecraft:zombie").timings().second()==1000,"Without analysis later stages inherit the category");}
            finally{AdaptationTimings.setAnalysisEnabled(analysis);}
            var client=new WheelEntry(catalog.key(),catalog.translationKey(),true,false,0,0,0,true,true,catalog.profile(),catalog.timings());
            check(h,client.duration()==100,"Client timer uses the authoritative server duration");
            check(h,AdaptationTimings.duration("mob:minecraft:skeleton",AdaptationProfile.PHYSICAL,0)==800,"Per-zombie settings do not change the skeleton's category duration");
            AdaptationConfig.SOURCE_OVERRIDES.set(List.of("damage:examplemod:beam=2,3,4,5,6","mob:minecraft:husk=7,0,0,0,0"));reload(config);
            check(h,AdaptationTimings.duration("damage:examplemod:beam",AdaptationProfile.LIGHT,0)==40,"Exact mod attack overrides apply without requiring that mod");
            check(h,AdaptationTimings.duration("mob:minecraft:zombie",AdaptationProfile.PHYSICAL,0)==140,"Zombie aliases canonicalize to the same configurable source");
            check(h,!AdaptationConfig.validOverride("mob:minecraft:zombie=-1,2,3,4,5")&&!AdaptationConfig.validOverride("mob:minecraft:zombie=5"),"Malformed timing overrides are rejected");
            boolean configuredPresent;
            try {Class.forName("com.mrcrayfish.configured.api.ModContext");configuredPresent=true;}
            catch(ClassNotFoundException absent){configuredPresent=false;}
            if(configuredPresent)verifyConfigured(h);
        } catch(Exception failure){failure.printStackTrace();throw failure;} finally {
            physical.set(oldPhysical);zombie.set(oldZombie);AdaptationConfig.SOURCE_OVERRIDES.set(oldRules);reload(config);
        }
        h.succeed();
    }
    private static void verifyConfigured(GameTestHelper h) throws Exception {
        Class<?> context=Class.forName("com.mrcrayfish.configured.api.ModContext");
        Object provider=Class.forName("com.mrcrayfish.configured.impl.forge.ForgeConfigProvider").getConstructor().newInstance();
        var configs=(Set<?>)provider.getClass().getMethod("getConfigurationsForMod",context).invoke(provider,context.getConstructor(String.class).newInstance(AdaptationWheel.MOD_ID));
        check(h,!configs.isEmpty(),"Configured 2.2.3 discovers the registered server spec");
        Class<?> configApi=Class.forName("com.mrcrayfish.configured.api.IModConfig");
        Object registered=configs.stream().filter(c->{try{return "adaptationwheel-server.toml".equals(configApi.getMethod("getFileName").invoke(c));}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        Object root=configApi.getMethod("getRoot").invoke(registered);
        Object value=configuredValue(root,List.of("mobs","zombie","stage1Seconds"));
        Class<?> valueApi=Class.forName("com.mrcrayfish.configured.api.IConfigValue");
        check(h,valueApi.getMethod("get").invoke(value).equals(5),"Configured sees zombie stage one as an editable integer");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(value,5),"Configured accepts five seconds");
        check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(value,-1),"Configured rejects negative durations");
        Object acceleration=configuredValue(root,List.of("acceleration","bonusPerHitPercent"));
        check(h,valueApi.getMethod("get").invoke(acceleration).equals(AdaptationConfig.HIT_ACCELERATION_PERCENT.get()),"Configured discovers the acceleration percentage");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(acceleration,50),"Configured accepts 50 percent acceleration");
        check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(acceleration,-1),"Configured rejects negative acceleration");
        for(int stage=1;stage<=4;stage++) {
            Object protection=configuredValue(root,List.of("protection","stage"+stage+"Percent"));
            check(h,valueApi.getMethod("get").invoke(protection).equals(AdaptationConfig.PROTECTION_PERCENT.get(stage-1).get()),"Configured discovers protection for degree "+stage);
            check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(protection,30),"Configured accepts thirty percent protection");
            check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(protection,101),"Configured rejects protection above one hundred percent");
        }
        Object healing=configuredValue(root,List.of("healing","fullHealAfterStage"));
        check(h,valueApi.getMethod("get").invoke(healing).equals(AdaptationConfig.FULL_HEAL_AFTER_STAGE.get()),"Configured discovers the healing boolean");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(healing,Boolean.FALSE),"Configured accepts disabling completion healing");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(healing,Boolean.TRUE),"Configured accepts enabling completion healing");
        for(String flag:List.of("existenceAdaptationEnabled","oneHitKillEnabled")) {
            Object bossControl=configuredValue(root,List.of("bosses",flag));
            check(h,valueApi.getMethod("get").invoke(bossControl) instanceof Boolean,"Configured discovers boss switch "+flag);
            check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(bossControl,Boolean.FALSE),"Configured accepts disabling "+flag);
            check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(bossControl,Boolean.TRUE),"Configured accepts enabling "+flag);
        }
        for(String setting:List.of("baseDamage","undeadMultiplier","cursedSpiritMultiplier")) {
            Object sword=configuredValue(root,List.of("sword",setting));
            check(h,valueApi.getMethod("get").invoke(sword) instanceof Double,"Configured discovers decimal sword setting "+setting);
            check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(sword,5.5),"Configured accepts fractional sword setting "+setting);
            check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(sword,0.0),"Configured accepts zero sword setting "+setting);
            check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(sword,-1.0),"Configured rejects negative sword setting "+setting);
            check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(sword,Double.NaN),"Configured rejects nonfinite sword setting "+setting);
        }
        Object targets=configuredValue(root,List.of("sword","specialMobs"));
        check(h,valueApi.getMethod("get").invoke(targets).equals(AdaptationConfig.SWORD_SPECIAL_MOBS.get()),"Configured discovers the editable mob list");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(targets,List.of("minecraft:wither","examplemod:cursed_spirit")),"Configured accepts vanilla and mod mob IDs");
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(targets,List.of()),"Configured accepts an explicitly empty target list");
        check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(targets,List.of("not an entity id")),"Configured rejects malformed mob IDs");
        Object overrideValue=configuredValue(root,List.of("sword","mobMultipliers"));
        check(h,(Boolean)valueApi.getMethod("isValid",Object.class).invoke(overrideValue,List.of("minecraft:wither=4","minecraft:warden=6.25")),"Configured accepts individual multiplier rules");
        check(h,!(Boolean)valueApi.getMethod("isValid",Object.class).invoke(overrideValue,List.of("minecraft:warden=NaN")),"Configured rejects nonfinite individual multipliers");
        Object marker=new ConfiguredParentFixture(root);var fixture=new ConfiguredEditorFixture(registered,targets,marker);
        var session=ru.pasha.adaptationwheel.integration.ConfiguredMobSelectionAccess.read(fixture).orElseThrow();
        var original=session.initial();
        try {
            check(h,session.parent()==marker,"Picker preserves the exact Configured parent screen");
            session.stage(List.of("minecraft:creeper","examplemod:spirit"));
            check(h,valueApi.getMethod("get").invoke(targets).equals(List.of("minecraft:creeper","examplemod:spirit")),"Picker stages selected names as IDs in Configured's actual value holder");
            check(h,AdaptationConfig.SWORD_SPECIAL_MOBS.get().equals(original),"Done does not bypass Configured's Save or change live server config prematurely");
            session.stage(List.of("minecraft:wither","minecraft:warden"),Map.of("minecraft:wither",4.0,"minecraft:warden",6.0));
            var stagedRules=((List<?>)valueApi.getMethod("get").invoke(overrideValue)).stream().map(String.class::cast).toList();
            check(h,SwordSettings.parseMultipliers(stagedRules).equals(Map.of("minecraft:wither",4.0,"minecraft:warden",6.0)),"Picker stages per-mob multipliers in the exact parent's cached Configured holder");
            check(h,AdaptationConfig.SWORD_MOB_MULTIPLIERS.get().isEmpty(),"Per-mob changes also wait for Configured Save instead of modifying the live server immediately");
            var reopened=ru.pasha.adaptationwheel.integration.ConfiguredMobSelectionAccess.read(new ConfiguredEditorFixture(registered,overrideValue,marker)).orElseThrow();
            check(h,reopened.initialMultipliers().get("minecraft:warden")==6,"Reopening either list uses the parent's unsaved multiplier draft");
            session.stage(List.of());check(h,((List<?>)valueApi.getMethod("get").invoke(targets)).isEmpty(),"Picker can stage an empty selection");
        }finally{session.stage(original);}
        check(h,ru.pasha.adaptationwheel.integration.ConfiguredMobSelectionAccess.read(new ConfiguredEditorFixture(registered,acceleration,marker)).isEmpty(),"Other Configured fields are never replaced by the mob picker");
        try(var bytes=context.getClassLoader().getResourceAsStream("com/mrcrayfish/configured/client/screen/EditListScreen.class")) {
            check(h,bytes!=null,"Configured list editor class bytes are available for optional bridge verification");
            Set<String> fields=new HashSet<>();
            new org.objectweb.asm.ClassReader(bytes).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value) {
                    fields.add(name+":"+descriptor);return null;
                }
            },org.objectweb.asm.ClassReader.SKIP_CODE|org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
            check(h,fields.contains("holder:Lcom/mrcrayfish/configured/api/IConfigValue;") && fields.contains("parent:Lnet/minecraft/client/gui/screens/Screen;"),"Installed Configured 2.2.3 editor has the exact bridge fields");
        }
        com.mojang.logging.LogUtils.getLogger().info("CONFIGURED_COMPAT:PASS Configured 2.2.3 mob picker stages selection and individual multipliers in the parent's cached holders; Save remains authoritative");
    }
    public static final class ConfiguredParentFixture {
        private final Object folderEntry;
        public ConfiguredParentFixture(Object entry){folderEntry=entry;}
    }
    public static final class ConfiguredEditorFixture {
        private final Object config,holder,parent;
        public ConfiguredEditorFixture(Object config,Object holder,Object parent){this.config=config;this.holder=holder;this.parent=parent;}
        public Object getActiveConfig(){return config;}
    }
    private static Object configuredValue(Object root,List<String> path) throws Exception {
        Class<?> entryApi=Class.forName("com.mrcrayfish.configured.api.IConfigEntry");
        Object leaf=root;
        for(String part:path) {
            var children=(List<?>)entryApi.getMethod("getChildren").invoke(leaf);
            leaf=children.stream().filter(c->{try{
                if((Boolean)entryApi.getMethod("isLeaf").invoke(c)) {
                    Object v=entryApi.getMethod("getValue").invoke(c);
                    return part.equals(Class.forName("com.mrcrayfish.configured.api.IConfigValue").getMethod("getName").invoke(v));
                }
                return part.equals(entryApi.getMethod("getEntryName").invoke(c));
            }catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow(()->new IllegalStateException("Configured did not expose "+part));
        }
        return entryApi.getMethod("getValue").invoke(leaf);
    }
    private static ModConfig serverConfig() {
        var config=ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
            .filter(c->c.getModId().equals(AdaptationWheel.MOD_ID)).findFirst().orElseThrow();
        // Tests reload synchronously after each mutation; avoid the file watcher reading partial fixture writes.
        try {com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().setWatch(config.getFullPath(),()->{});}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        return config;
    }
    private static void reload(ModConfig config){AdaptationConfig.SPEC.afterReload();AdaptationConfig.changed(new ModConfigEvent.Reloading(config));}
    private static void tick(TestPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void check(GameTestHelper h,boolean ok,String message){if(!ok)h.fail(message);}
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level){super(level,new GameProfile(UUID.randomUUID(),"ConfigWheelTest"));setPos(0,100,0);setHealth(20);setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
        void damage(net.minecraft.world.damagesource.DamageSource source,float amount){super.actuallyHurt(source,amount);}
    }
}
