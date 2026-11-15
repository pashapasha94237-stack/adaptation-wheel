package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.AdaptationTimings;
import ru.pasha.adaptationwheel.core.ResearchMemory;
import ru.pasha.adaptationwheel.server.AdaptationCatalogue;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import ru.pasha.adaptationwheel.server.DamageCategories;
import ru.pasha.adaptationwheel.server.LearningController;
import ru.pasha.adaptationwheel.server.ModAttackCompatibility;
import ru.pasha.adaptationwheel.server.ObservedAbilities;
import ru.pasha.adaptationwheel.server.SmartAttackAnalyzer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClassifierGameTests {
    private static final ResourceLocation HARMFUL=new ResourceLocation(AdaptationWheel.MOD_ID,"test_toxic_mist");
    private static final ResourceLocation BENEFICIAL=new ResourceLocation(AdaptationWheel.MOD_ID,"test_holy_blessing");

    private static EntityType<Zombie> modZombie,fireOrb;
    @SubscribeEvent public static void registerTestEntities(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES,helper -> {
            modZombie=EntityType.Builder.<Zombie>of(Zombie::new,MobCategory.MONSTER).sized(.6F,1.95F).build("test_mod_zombie");
            fireOrb=EntityType.Builder.<Zombie>of(Zombie::new,MobCategory.MONSTER).sized(.6F,1.95F).build("test_fire_orb");
            helper.register(new ResourceLocation(AdaptationWheel.MOD_ID,"test_mod_zombie"),modZombie);
            helper.register(new ResourceLocation(AdaptationWheel.MOD_ID,"test_fire_orb"),fireOrb);
        });
    }
    @SubscribeEvent public static void testEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(modZombie,Zombie.createAttributes().build());event.put(fireOrb,Zombie.createAttributes().build());
    }
    @SubscribeEvent public static void registerTestEffects(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.MOB_EFFECTS,helper -> {
            helper.register(HARMFUL,new TestEffect(MobEffectCategory.HARMFUL));
            helper.register(BENEFICIAL,new TestEffect(MobEffectCategory.BENEFICIAL));
        });
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void modLightBeamRetainsMobAndSeparateLightAdaptation(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        DamageSource beam=new DamageSource(holder(h,"light_beam"),zombie);
        var exposures=DamageCategories.analyze(beam);
        check(h,exposures.size()==2,"A specific mod attack discovers both species and attack type");
        check(h,exposures.get(0).key().equals("mob:minecraft:zombie"),"Primary species key remains stable");
        check(h,exposures.get(1).key().equals("damage:adaptationwheel:light_beam")
                && exposures.get(1).profile()==AdaptationProfile.LIGHT,"Named light remains LIGHT even with generic projectile tag");
        TestPlayer p=new TestPlayer(h.getLevel());p.damage(beam,1);tick(p);
        var saved=AdaptationEvents.loadState(p).snapshots();
        check(h,saved.stream().anyMatch(e -> e.key().equals("damage:adaptationwheel:light_beam") && e.profile()==AdaptationProfile.LIGHT),
                "Actual mod damage persists its inferred LIGHT profile");
        check(h,saved.stream().anyMatch(e -> e.key().equals("mob:minecraft:zombie")),"Actual mod damage also discovers attacker immunity");
        check(h,DamageCategories.analyze(h.getLevel().damageSources().mobAttack(zombie)).size()==1,"Ordinary melee must not duplicate a species exposure");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void tagsAndMessageIdRefineUnknownDamage(GameTestHelper h) {
        check(h,DamageCategories.profile(holder(h,"opaque_override"))==AdaptationProfile.MAGIC,"Datapack profile override classifies an opaque mod type without name hints");
        var arrow=EntityType.ARROW.create(h.getLevel());
        var unnamed=net.minecraft.core.Holder.direct(new DamageType("unclassified",0.1F));
        check(h,DamageCategories.profile(new DamageSource(unnamed,arrow))==AdaptationProfile.PROJECTILE,"Direct projectile entity refines an otherwise unknown damage type");
        var unregisteredSource=new DamageSource(unnamed,arrow);
        TestPlayer direct=new TestPlayer(h.getLevel());direct.damage(unregisteredSource,1);tick(direct);
        check(h,AdaptationEvents.state(direct).snapshots().stream().anyMatch(e ->
            e.key().equals("damage:adaptationwheel:unregistered/unclassified") && e.profile()==AdaptationProfile.PROJECTILE),
            "An unregistered direct-holder attack still creates an adaptation on actual damage");
        check(h,DamageCategories.profile(new DamageSource(holder(h,"laser_suppressed"),arrow))==AdaptationProfile.ENVIRONMENT,"Explicit ENVIRONMENT overrides both laser naming and projectile class");
        check(h,DamageCategories.profile(holder(h,"frost_named_fire"))==AdaptationProfile.FIRE,"Specific fire tag wins over misleading cold name");
        check(h,DamageCategories.profile(holder(h,"opaque_impact"))==AdaptationProfile.MAGIC,"Opaque registry id can use arcane messageId");
        check(h,DamageCategories.profile(h.getLevel().damageSources().lightningBolt())==AdaptationProfile.ELECTRIC,"Vanilla lightning tag");
        check(h,DamageCategories.profile(h.getLevel().damageSources().drown())==AdaptationProfile.SUFFOCATION,"Vanilla drowning tag");
        check(h,DamageCategories.profile(h.getLevel().damageSources().freeze())==AdaptationProfile.COLD,"Vanilla freezing tag");
        var registry=h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        check(h,DamageCategories.profile(registry.getHolderOrThrow(DamageTypes.ARROW))==AdaptationProfile.PROJECTILE,"Vanilla projectile tag");
        check(h,DamageCategories.profile(registry.getHolderOrThrow(DamageTypes.EXPLOSION))==AdaptationProfile.EXPLOSION,"Vanilla explosion tag");
        check(h,DamageCategories.analyze(h.getLevel().damageSources().starve()).get(0).key().equals(AdaptationProfile.HUNGER_KEY)
                && DamageCategories.analyze(h.getLevel().damageSources().freeze()).get(0).key().equals(AdaptationProfile.COLD_KEY),"Environmental damage uses canonical learned keys");
        var magicOwner=EntityType.ZOMBIE.create(h.getLevel());
        var magic=DamageCategories.analyze(new DamageSource(registry.getHolderOrThrow(DamageTypes.INDIRECT_MAGIC),magicOwner));
        check(h,magic.size()==2 && magic.get(0).key().equals("mob:minecraft:zombie")
                && magic.get(1).key().equals("damage:minecraft:indirect_magic") && magic.get(1).profile()==AdaptationProfile.MAGIC,
                "Attributed magic retains the species and its own type: no potion delivered it");
        check(h,DamageCategories.analyze(h.getLevel().damageSources().genericKill()).isEmpty()
                && DamageCategories.analyze(h.getLevel().damageSources().fellOutOfWorld()).isEmpty(),"Administrative damage is unadaptable");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void untaggedVanillaTypesModTagsAndCompatibilityTables(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        var bee=EntityType.BEE.create(h.getLevel());
        p.damage(h.getLevel().damageSources().sting(bee),1);tick(p);
        check(h,analyzed(p,"damage:minecraft:sting",AdaptationProfile.PHYSICAL,"registry.physical"),"A bee's sting is physical by vanilla's own definition, not a projectile by its name");
        check(h,analyzed(p,"mob:minecraft:bee",AdaptationProfile.PHYSICAL,"registry.physical"),"The bee itself is learned from its sting");
        p.damage(h.getLevel().damageSources().inWall(),1);tick(p);
        check(h,analyzed(p,"damage:minecraft:in_wall",AdaptationProfile.SUFFOCATION,"registry.suffocation"),"Suffocating in a wall is known, not a name guess");
        var tagged=holder(h,"tagged_unknown");
        check(h,DamageCategories.tagProfiles(tagged).equals(java.util.EnumSet.of(AdaptationProfile.MAGIC)),"forge:curse names magic; testmod:fire_resistant_to is a relation and is ignored");
        check(h,DamageCategories.profile(tagged)==AdaptationProfile.MAGIC,"The catalogue uses the tag's category");
        p.damage(new DamageSource(tagged),1);tick(p);
        check(h,analyzed(p,"damage:adaptationwheel:tagged_unknown",AdaptationProfile.MAGIC,"tag.category"),"A mod type with an opaque name is classified by its mod tag");
        AdaptationEvents.continuousExposure(p,"condition:iceandfire:frozen");
        check(h,analyzed(p,"condition:iceandfire:frozen",AdaptationProfile.COLD,"compat.known"),"A condition from a compatibility table is known, not a 30% fallback");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void harmfulMetadataAndCompleteCanonicalCatalogue(GameTestHelper h) {
        MobEffect harmful=BuiltInRegistries.MOB_EFFECT.get(HARMFUL),beneficial=BuiltInRegistries.MOB_EFFECT.get(BENEFICIAL);
        check(h,harmful!=null && beneficial!=null,"Synthetic mod effects are registered");
        check(h,DamageCategories.isHarmful(harmful) && !DamageCategories.isHarmful(beneficial),"Effect metadata overrides attractive or frightening names");
        check(h,DamageCategories.effectProfile(harmful)==AdaptationProfile.POISON,"Toxic mod effect receives POISON profile");
        check(h,DamageCategories.isHarmful(MobEffects.BAD_OMEN) && DamageCategories.isHarmful(MobEffects.GLOWING),"Negative neutral effects are included");
        TestPlayer p=new TestPlayer(h.getLevel());
        check(h,p.addEffect(new net.minecraft.world.effect.MobEffectInstance(harmful,1000)),"A new mod harmful effect can be applied before adaptation");tick(p);
        check(h,AdaptationEvents.state(p).snapshots().stream().anyMatch(e -> e.key().equals("effect:"+HARMFUL)
                && e.running() && e.profile()==AdaptationProfile.POISON),"Applying an unknown HARMFUL mod effect automatically starts its own correctly classified adaptation");
        check(h,p.addEffect(new net.minecraft.world.effect.MobEffectInstance(beneficial,1000)),"A beneficial mod effect stays applicable");tick(p);
        check(h,AdaptationEvents.state(p).snapshots().stream().noneMatch(e -> e.key().equals("effect:"+BENEFICIAL)),"A positive mod effect never creates progression despite a light-themed name");
        AdaptationEvents.state(p).hit("damage:adaptationwheel:opaque_impact",false,AdaptationProfile.LIGHT);
        var entries=AdaptationCatalogue.entries(p);
        var byKey=entries.stream().collect(Collectors.toMap(e -> e.key(),e -> e));
        check(h,entries.size()>80,"Catalogue includes unencountered registered mobs and hazards");
        check(h,byKey.containsKey("mob:minecraft:cow") && byKey.containsKey("mob:minecraft:ender_dragon"),"Both passive mobs and bosses are catalogued");
        check(h,byKey.containsKey("damage:adaptationwheel:light_beam") && byKey.containsKey("effect:"+HARMFUL),"Mod damage and harmful effects are catalogued before exposure");
        check(h,!byKey.containsKey("effect:"+BENEFICIAL) && !byKey.containsKey("effect:minecraft:instant_health"),"Beneficial effects never appear in negative catalogue");
        check(h,!byKey.containsKey("damage:minecraft:generic_kill") && !byKey.containsKey("damage:minecraft:fell_out_of_world"),"Administrative damage stays hidden");
        check(h,byKey.containsKey(AdaptationProfile.HUNGER_KEY) && byKey.containsKey(AdaptationProfile.COLD_KEY)
                && !byKey.containsKey("damage:minecraft:starve") && !byKey.containsKey("damage:minecraft:freeze"),"Cold and hunger aliases are merged");
        check(h,byKey.containsKey("effect:minecraft:instant_damage") && !byKey.containsKey("damage:minecraft:magic")
                && !byKey.containsKey("damage:minecraft:wither"),"Effect damage aliases are merged");
        check(h,byKey.containsKey("damage:minecraft:explosion") && !byKey.containsKey("damage:minecraft:player_explosion")
                && byKey.containsKey("damage:minecraft:fireball") && !byKey.containsKey("damage:minecraft:unattributed_fireball"),"Blasts and fireballs have one row each");
        check(h,byKey.containsKey("damage:minecraft:indirect_magic"),"Indirect magic has its own row");
        check(h,byKey.get("mob:minecraft:ghast").profile()==AdaptationProfile.FIRE && byKey.get("mob:minecraft:creeper").profile()==AdaptationProfile.EXPLOSION,"The catalogue knows how a ghast and a creeper fight");
        check(h,byKey.containsKey("mob:minecraft:zombie") && !byKey.containsKey("mob:minecraft:husk")
                && !byKey.containsKey("mob:minecraft:drowned"),"Zombie family has one learnable row");
        check(h,byKey.get("damage:adaptationwheel:opaque_impact").profile()==AdaptationProfile.LIGHT,"Saved exposure profile takes priority over registry inference");
        h.succeed();
    }

    /** The sources are built by vanilla itself: a fireball's blast and a creeper's blast arrive as "player_explosion". */
    @GameTest(template="empty",timeoutTicks=40)
    public static void ghastAndCreeperAreNotPlayerExplosions(GameTestHelper h) {
        var level=h.getLevel();
        var ghast=EntityType.GHAST.create(level);var ball=new LargeFireball(level,ghast,0,0,0,1);
        DamageSource blast=blast(level,ball),burn=level.damageSources().fireball(ball,ghast);
        check(h,blast.is(DamageTypes.PLAYER_EXPLOSION),"Vanilla still types a ghast's blast this way, or this test proves nothing");
        check(h,keys(blast,null).equals(List.of("mob:minecraft:ghast","damage:minecraft:explosion")),"A ghast's blast is a blast: "+keys(blast,null));
        check(h,keys(burn,null).equals(List.of("mob:minecraft:ghast","damage:minecraft:fireball")),"A ghast's fireball: "+keys(burn,null));
        check(h,keys(level.damageSources().fireball(ball,null),null).equals(List.of("damage:minecraft:fireball")),"A fireball without an owner is the same fireball");
        var creeper=EntityType.CREEPER.create(level);
        check(h,keys(blast(level,creeper),null).equals(List.of("mob:minecraft:creeper","damage:minecraft:explosion")),"A creeper's blast: "+keys(blast(level,creeper),null));
        TestPlayer p=new TestPlayer(level);
        p.damage(burn,1);tick(p);p.damage(blast,1);tick(p);
        check(h,AdaptationEvents.state(p).snapshot("damage:minecraft:player_explosion")==null,"No «player explosions» row appears");
        check(h,analyzed(p,"damage:minecraft:explosion",AdaptationProfile.EXPLOSION,"registry.explosion"),"The blast teaches explosions");
        check(h,analyzed(p,"damage:minecraft:fireball",AdaptationProfile.FIRE,"registry.fire"),"The fireball teaches fireballs");
        check(h,analyzed(p,"mob:minecraft:ghast",AdaptationProfile.FIRE,"species.main"),"The ghast's row stays fire after its blast");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void earnedExplosionProtectsFromEveryBlast(GameTestHelper h) {
        var level=h.getLevel();TestPlayer p=new TestPlayer(level);
        AdaptationEvents.state(p).setStage("damage:minecraft:explosion",4,null);
        var ghast=EntityType.GHAST.create(level);
        p.damage(blast(level,new LargeFireball(level,ghast,0,0,0,1)),6);
        check(h,p.getHealth()==20,"Stage IV of explosions covers a ghast's blast, health "+p.getHealth());
        p.damage(blast(level,EntityType.CREEPER.create(level)),6);
        check(h,p.getHealth()==20,"Stage IV of explosions covers a creeper's blast, health "+p.getHealth());
        p.damage(level.damageSources().explosion(null,null),6);
        check(h,p.getHealth()==20,"And a blast without a culprit");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void ownBlastTeachesTheBlastNotAPlayer(GameTestHelper h) {
        var level=h.getLevel();TestPlayer p=new TestPlayer(level),other=new TestPlayer(level);
        DamageSource own=blast(level,p);
        check(h,keys(own,p).equals(List.of("damage:minecraft:explosion")),"One's own blast has no opponent: "+keys(own,p));
        check(h,keys(own,other).equals(List.of("mob:minecraft:player","damage:minecraft:explosion")),"For another player it has one: "+keys(own,other));
        var deflected=new LargeFireball(level,p,0,0,0,1);
        check(h,keys(level.damageSources().fireball(deflected,p),p).equals(List.of("damage:minecraft:fireball")),"A deflected fireball is a fireball");
        p.damage(own,1);tick(p);
        check(h,AdaptationEvents.state(p).snapshot("mob:minecraft:player")==null,"Hitting oneself does not start «Игрок»");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void magicIsAPotionOnlyWhenAPotionDeliveredIt(GameTestHelper h) {
        var level=h.getLevel();var sources=level.damageSources();
        var guardian=EntityType.GUARDIAN.create(level);var fangs=EntityType.EVOKER_FANGS.create(level);var evoker=EntityType.EVOKER.create(level);
        check(h,keys(sources.indirectMagic(guardian,guardian),null).equals(List.of("mob:minecraft:guardian","damage:minecraft:indirect_magic")),"A guardian's beam");
        check(h,keys(sources.indirectMagic(fangs,evoker),null).equals(List.of("mob:minecraft:evoker","damage:minecraft:indirect_magic")),"Evoker fangs");
        var witch=EntityType.WITCH.create(level);var potion=EntityType.POTION.create(level);var cloud=EntityType.AREA_EFFECT_CLOUD.create(level);
        check(h,keys(sources.indirectMagic(potion,witch),null).equals(List.of("mob:minecraft:witch","effect:minecraft:instant_damage")),"A witch's potion");
        check(h,keys(sources.indirectMagic(potion,null),null).equals(List.of("effect:minecraft:instant_damage")),"A potion from a dispenser");
        check(h,keys(sources.indirectMagic(cloud,null),null).equals(List.of("effect:minecraft:instant_damage")),"A lingering cloud");
        check(h,keys(sources.magic(),null).equals(List.of("effect:minecraft:instant_damage")),"Drinking Harming");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void oldSavesFoldWithoutLosingTheStage(GameTestHelper h) {
        for(boolean oldRowFirst:new boolean[]{true,false}) {
            TestPlayer p=new TestPlayer(h.getLevel());
            var entries=new ListTag();var analysis=new ListTag();
            CompoundTag oldRow=row("damage:minecraft:player_explosion",3),plain=row("damage:minecraft:explosion",1);
            entries.add(oldRowFirst?oldRow:plain);entries.add(oldRowFirst?plain:oldRow);
            entries.add(row("damage:minecraft:unattributed_fireball",2));
            analysis.add(memory("damage:minecraft:player_explosion",9));analysis.add(memory("damage:minecraft:explosion",2));
            var tag=new CompoundTag();tag.putInt("Version",5);tag.putBoolean("Enabled",true);tag.put("Entries",entries);tag.put("Analysis",analysis);
            p.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
            var state=AdaptationEvents.loadState(p);
            check(h,state.stage("damage:minecraft:explosion")==3,"The further row wins, whichever came first");
            check(h,state.stage("damage:minecraft:fireball")==2,"Fireballs fold too");
            check(h,state.snapshots().stream().noneMatch(s->s.key().equals("damage:minecraft:player_explosion")),"The old row is gone");
            check(h,SmartAttackAnalyzer.memory(p,"damage:minecraft:explosion").observations()==9&&SmartAttackAnalyzer.memory(p,"damage:minecraft:player_explosion")==null,"So is its analysis");
        }
        h.succeed();
    }

    /** Old saves lose retired utility rows and retain active adaptation progress. */
    @GameTest(template="empty",timeoutTicks=40)
    public static void retiredUtilityRowsAreDropped(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());
        var retired=java.util.List.of("environment:landscape_memory","environment:silent_world",
                "environment:console_commands","environment:wheel_update","environment:foreign_mods");
        var entries=new ListTag();
        for(String key:retired)entries.add(row(key,4));
        entries.add(row("environment:memory_pressure",2));
        var tag=new CompoundTag();tag.putInt("Version",5);tag.putBoolean("Enabled",true);tag.put("Entries",entries);
        p.getPersistentData().put(AdaptationWheel.MOD_ID,tag);
        var state=AdaptationEvents.loadState(p);
        for(String key:retired)check(h,state.snapshot(key)==null,"Retired row survived: "+key);
        check(h,state.stage("environment:memory_pressure")==2,"Other utility adaptations keep their stage");
        var old=new CompoundTag();var done=new ListTag();done.add(net.minecraft.nbt.StringTag.valueOf("environment:landscape_memory"));old.put("Completed",done);
        p.getPersistentData().put(AdaptationWheel.MOD_ID,old);
        check(h,AdaptationEvents.loadState(p).snapshot("environment:landscape_memory")==null,"The oldest save format drops it too");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void analyzerKeepsEveryKeyAndCreatureVotes(GameTestHelper h) {
        TestPlayer p=new TestPlayer(h.getLevel());int rows=9000;
        for(int i=0;i<rows;i++)SmartAttackAnalyzer.observe(p,"attack:testmod:move_"+i,false,AdaptationProfile.PHYSICAL,null,1,1);
        var ghast=EntityType.GHAST.create(h.getLevel());var ball=new LargeFireball(h.getLevel(),ghast,0,0,0,1);
        SmartAttackAnalyzer.observe(p,"mob:minecraft:ghast",false,AdaptationProfile.FIRE,h.getLevel().damageSources().fireball(ball,ghast),6,6);
        AdaptationEvents.save(p);SmartAttackAnalyzer.forget(p);
        check(h,SmartAttackAnalyzer.memory(p,"attack:testmod:move_"+(rows-1))!=null,"The 9000th attack is learned and saved past the old 8192");
        check(h,SmartAttackAnalyzer.memory(p,"attack:testmod:move_0")!=null,"The first one stays too");
        check(h,SmartAttackAnalyzer.memory(p,"mob:minecraft:ghast").votes().equals(java.util.Map.of(AdaptationProfile.FIRE,1)),"A creature's votes survive a save");
        h.succeed();
    }

    private static DamageSource blast(ServerLevel level,net.minecraft.world.entity.Entity source) {
        return new Explosion(level,source,0,100,0,1F,false,Explosion.BlockInteraction.KEEP).getDamageSource();
    }
    private static List<String> keys(DamageSource source,net.minecraft.world.entity.LivingEntity victim) {
        return DamageCategories.analyze(source,victim).stream().map(DamageCategories.Exposure::key).toList();
    }
    private static CompoundTag row(String key,int stage) {
        var row=new CompoundTag();row.putString("Key",key);row.putBoolean("Enabled",true);row.putInt("Stage",stage);row.putString("Profile","EXPLOSION");return row;
    }
    private static CompoundTag memory(String key,int count) {
        var row=new CompoundTag();row.putString("Key",key);row.putInt("Count",count);return row;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void onlyVanillaZombiesShareARow(GameTestHelper h) {
        var mod=modZombie.create(h.getLevel());
        check(h,DamageCategories.forEntity(mod).equals("mob:adaptationwheel:test_mod_zombie"),"A mod creature built on the Zombie class keeps its own row");
        check(h,DamageCategories.analyze(h.getLevel().damageSources().mobAttack(mod)).get(0).key().equals("mob:adaptationwheel:test_mod_zombie"),"Its blow teaches its own row");
        check(h,DamageCategories.forEntity(EntityType.HUSK.create(h.getLevel())).equals("mob:minecraft:zombie"),"A husk is still a zombie");
        check(h,DamageCategories.analyze(h.getLevel().damageSources().mobAttack(EntityType.DROWNED.create(h.getLevel()))).get(0).key().equals("mob:minecraft:zombie"),"A drowned's blow still teaches the zombie");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void attacksWithALotOfHealthAreNotBosses(GameTestHelper h) {
        var creature=modZombie.create(h.getLevel());var orb=fireOrb.create(h.getLevel());
        creature.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);orb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        check(h,DamageCategories.boss(creature),"A creature with 100+ HP is a boss");
        check(h,!DamageCategories.boss(orb),"A flying attack with 100+ HP is not");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void oneHitStartsOneAdaptation(GameTestHelper h) {
        var mod=modZombie.create(h.getLevel());var sources=h.getLevel().damageSources();
        var generic=new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC),mod);
        TestPlayer p=new TestPlayer(h.getLevel());p.damage(generic,1);tick(p);
        var keys=keys(p);
        check(h,keys.contains("attack:adaptationwheel:observed/test_mod_zombie/damage/minecraft/generic"),"The precise row is missing: "+keys);
        check(h,keys.contains("mob:adaptationwheel:test_mod_zombie"),"The creature's row is missing: "+keys);
        check(h,!keys.contains("damage:minecraft:generic"),"A shared type started a second adaptation: "+keys);
        check(h,DamageCategories.plainMelee(sources.mobAttack(mod)),"A creature's own blow is plain melee");
        check(h,!DamageCategories.plainMelee(sources.arrow(EntityType.ARROW.create(h.getLevel()),mod))&&!DamageCategories.plainMelee(generic),"Anything else is not");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void anonymousDamageIsSplitByMod(GameTestHelper h) {
        var generic=h.getLevel().damageSources().generic();
        try{
            TestPlayer p=new TestPlayer(h.getLevel());
            ObservedAbilities.callerForTests(() -> "moda");p.damage(generic,1);tick(p);
            ObservedAbilities.callerForTests(() -> "modb");p.damage(generic,1);tick(p);
            var keys=keys(p);
            check(h,keys.contains("attack:moda:unowned/minecraft/generic")&&keys.contains("attack:modb:unowned/minecraft/generic"),"Each mod needs its own row: "+keys);
            check(h,!keys.contains("damage:minecraft:generic"),"The shared row must not start beside them: "+keys);
            var name=DamageCategories.displayName("attack:moda:unowned/minecraft/generic").getContents();
            check(h,name instanceof net.minecraft.network.chat.contents.TranslatableContents row&&row.getKey().equals("analysis.adaptationwheel.unowned_ability"),"The row has a readable name");
            ObservedAbilities.callerForTests(() -> null);
            TestPlayer plain=new TestPlayer(h.getLevel());plain.damage(generic,1);tick(plain);
            check(h,keys(plain).contains("damage:minecraft:generic"),"With no mod on the stack the shared row stays");
        }finally{ObservedAbilities.callerForTests(null);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void aModsDeathLineNamesItsDamage(GameTestHelper h) {
        check(h,DamageCategories.profile(net.minecraft.core.Holder.direct(new DamageType("opaque_death_line",0.1F)))==AdaptationProfile.COLD,"The death line says frozen");
        check(h,DamageCategories.profile(net.minecraft.core.Holder.direct(new DamageType("qqq_no_line",0.1F)))==AdaptationProfile.ENVIRONMENT,"No line, no hint");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void aBlowAtArmsLengthIsMelee(GameTestHelper h) {
        var unnamed=net.minecraft.core.Holder.direct(new DamageType("qqq_zzz",0.1F));String key="damage:adaptationwheel:unregistered/qqq_zzz";
        var near=modZombie.create(h.getLevel());near.setPos(0,100,1);
        TestPlayer p=new TestPlayer(h.getLevel());p.damage(new DamageSource(unnamed,near),1);tick(p);
        check(h,analyzed(p,key,AdaptationProfile.PHYSICAL,"native.physical"),"A creature's blow from next to the player is melee");
        var far=modZombie.create(h.getLevel());far.setPos(0,100,40);
        TestPlayer distant=new TestPlayer(h.getLevel());distant.damage(new DamageSource(unnamed,far),1);tick(distant);
        check(h,analyzed(distant,key,AdaptationProfile.ENVIRONMENT,"fallback.profile"),"From 40 blocks away it is no melee");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void paceAndExperienceShapeTheSchedule(GameTestHelper h) {
        var lava=h.getLevel().damageSources().lava();String key="damage:minecraft:lava";
        TestPlayer calm=new TestPlayer(h.getLevel()),hot=new TestPlayer(h.getLevel());
        SmartAttackAnalyzer.observe(calm,key,false,AdaptationProfile.FIRE,lava,1,1);
        for(int i=0;i<10;i++)SmartAttackAnalyzer.observe(hot,key,false,AdaptationProfile.FIRE,lava,1,1);
        check(h,SmartAttackAnalyzer.memory(hot,key).peakRisk()>SmartAttackAnalyzer.memory(calm,key).peakRisk()*3,"Ten quick small hits are a pace, not ten harmless hits");
        TestPlayer master=new TestPlayer(h.getLevel());var state=AdaptationEvents.state(master);
        state.setStage("damage:minecraft:in_fire",4,AdaptationProfile.FIRE);state.setStage("damage:minecraft:on_fire",4,AdaptationProfile.FIRE);
        SmartAttackAnalyzer.observe(master,key,false,AdaptationProfile.FIRE,lava,1,1);
        int plain=AdaptationEvents.state(calm).snapshot(key).analyzedDurations().first(),same=state.snapshot(key).analyzedDurations().first();
        check(h,same==plain,"Experience never shortens a stage: "+plain+" -> "+same);
        check(h,state.experiencePercent(key)==10,"Two mastered fire sources start the next one 10% faster: "+state.experiencePercent(key));
        check(h,AdaptationEvents.state(calm).experiencePercent(key)==0,"No mastered source, no experience");
        check(h,state.damageMultiplier(key)==1,"Experience gives speed, never protection");
        AdaptationTimings.setElementExperience(false);
        try{
            TestPlayer off=new TestPlayer(h.getLevel());var offState=AdaptationEvents.state(off);
            offState.setStage("damage:minecraft:in_fire",4,AdaptationProfile.FIRE);offState.setStage("damage:minecraft:on_fire",4,AdaptationProfile.FIRE);
            SmartAttackAnalyzer.observe(off,key,false,AdaptationProfile.FIRE,lava,1,1);
            check(h,offState.experiencePercent(key)==0&&offState.snapshot(key).analyzedDurations().first()==plain,"The switch turns experience off");
        }finally{AdaptationTimings.setElementExperience(true);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void learningLosesOnlyWhatChanged(GameTestHelper h) {
        var model=new ResearchMemory();String world="world:registry/vanilla-category";
        model.learnFact(new ResearchMemory.Fact("damage:mod:a",AdaptationProfile.FIRE,90,world));
        model.learnFact(new ResearchMemory.Fact("damage:mod:b",AdaptationProfile.COLD,90,world));
        model.learnFact(new ResearchMemory.Fact("effect:mod:c",AdaptationProfile.EFFECT,85,"live:native.harmful_effect"));
        var changed=LearningController.changed(Map.of("damage:mod:a","1","damage:mod:b","2","damage:mod:gone","3"),Map.of("damage:mod:a","9","damage:mod:b","2","damage:mod:new","4"));
        check(h,changed.equals(Set.of("damage:mod:a","damage:mod:gone","damage:mod:new")),"Only different rows are changed: "+changed);
        var kept=LearningController.withoutStaleDefinitions(model,Set.of(),changed);
        check(h,kept.fact("damage:mod:a")==null&&kept.fact("damage:mod:b")!=null&&kept.fact("effect:mod:c")!=null,"Only the changed definition is forgotten");
        check(h,LearningController.changed(null,Map.of("damage:mod:a","1"))==null,"An old save has nothing to compare with");
        var legacy=LearningController.withoutStaleDefinitions(model,Set.of(),null);
        check(h,legacy.fact("damage:mod:b")==null&&legacy.fact("effect:mod:c")==null,"The last full reset also clears plain harmful-effect facts");
        check(h,LearningController.changed(Map.of("damage:minecraft:starve","1"),Map.of("damage:minecraft:starve","2")).contains(AdaptationProfile.HUNGER_KEY),"A folded key is matched under its row's name");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void aPlainBlowKeepsOneRowAndWhatWasEarned(GameTestHelper h) {
        var blow=h.getLevel().damageSources().mobAttack(modZombie.create(h.getLevel()));
        String family="attack:iceandfire:dragon_charge",row=family+"/fire";
        TestPlayer p=new TestPlayer(h.getLevel());ModAttackCompatibility.hurt(p,blow,2,family);tick(p);
        var keys=keys(p);
        check(h,keys.contains("mob:adaptationwheel:test_mod_zombie")&&!keys.contains(row),"A plain blow teaches the creature's row only: "+keys);
        check(h,p.getHealth()<20,"The blow must have landed");
        TestPlayer veteran=new TestPlayer(h.getLevel());AdaptationEvents.state(veteran).setStage(row,4,AdaptationProfile.PHYSICAL);
        ModAttackCompatibility.hurt(veteran,blow,10,family);
        check(h,veteran.getHealth()==20,"A stage earned on the old row still protects: "+veteran.getHealth());
        h.succeed();
    }

    private static Set<String> keys(TestPlayer p) { return AdaptationEvents.state(p).snapshots().stream().map(e -> e.key()).collect(Collectors.toSet()); }
    private static net.minecraft.core.Holder<DamageType> holder(GameTestHelper h,String path) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(AdaptationWheel.MOD_ID,path)));
    }
    private static boolean analyzed(TestPlayer p,String key,AdaptationProfile profile,String reason) {
        var entry=AdaptationEvents.state(p).snapshot(key);
        return entry!=null && entry.profile()==profile && reason.equals(entry.analysisReason());
    }
    private static void check(GameTestHelper h,boolean condition,String message) { if(!condition) h.fail(message); }
    private static void tick(TestPlayer p) { p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p)); }
    private static final class TestEffect extends MobEffect { TestEffect(MobEffectCategory category) { super(category,0xAABBCC); } }
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) { super(level,new GameProfile(UUID.randomUUID(),"ClassifierTest"));setPos(0,100,0);setHealth(20);setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get())); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        void damage(DamageSource source,float amount) { super.actuallyHurt(source,amount); }
        // A fake player never ticks, so spawn protection would swallow a real hurt() forever.
        @Override public boolean hurt(DamageSource source,float amount) { damage(source,amount);return true; }
    }
}
