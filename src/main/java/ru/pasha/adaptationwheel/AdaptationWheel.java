package ru.pasha.adaptationwheel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import ru.pasha.adaptationwheel.item.WheelItem;
import ru.pasha.adaptationwheel.item.ExterminationSwordItem;
import ru.pasha.adaptationwheel.server.ExterminationSwordEvents;
import net.minecraft.world.level.block.Block;
import ru.pasha.adaptationwheel.block.AdaptationGlowBlock;
import ru.pasha.adaptationwheel.server.NegativeEffects;

@Mod(AdaptationWheel.MOD_ID)
public final class AdaptationWheel {
    public static final String MOD_ID = "adaptationwheel";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final RegistryObject<Block> GLOW = BLOCKS.register("glow", AdaptationGlowBlock::new);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> WHEEL = ITEMS.register("wheel", WheelItem::new);
    public static final RegistryObject<Item> EXTERMINATION_SWORD = ITEMS.register("extermination_sword", ExterminationSwordItem::new);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    public static final RegistryObject<SoundEvent> ADAPTED_SOUND = SOUNDS.register("adapted",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "adapted")));

    public AdaptationWheel() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,AdaptationConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,FpsAdaptationConfig.SPEC,"adaptationwheel-fps-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,MetaConfig.SERVER,"adaptationwheel-utility-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,LearningConfig.SPEC,"adaptationwheel-learning-server.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,MetaConfig.CLIENT,"adaptationwheel-utility-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,WheelSkinConfig.SPEC,"adaptationwheel-appearance-client.toml");
        if(NeuralConfig.ONLINE)net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,NeuralConfig.SPEC,"adaptationwheel-neural-common.toml");
        modBus.addListener(AdaptationConfig::changed);
        modBus.addListener(ConfigWatch::changed);
        ru.pasha.adaptationwheel.server.LogWatch.install();
        SOUNDS.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(WHEEL);
            if (event.getTabKey().equals(CreativeModeTabs.COMBAT)) event.accept(EXTERMINATION_SWORD);
        });
        WheelNetwork.register();
        ru.pasha.adaptationwheel.net.MetaNetwork.register();
        ru.pasha.adaptationwheel.net.LearningNetwork.register();
        MinecraftForge.EVENT_BUS.register(AdaptationEvents.class);
        MinecraftForge.EVENT_BUS.register(NegativeEffects.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.T2SCompatibility.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.JujutsuCompatibility.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.InfinityAdaptation.class);
        MinecraftForge.EVENT_BUS.register(ExterminationSwordEvents.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.WorldSlash.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.FirstAidCompatibility.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.MetaServer.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.VoidTextAdaptation.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.OutsideServer.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.DefeatMemory.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.LandscapeServerPolicy.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.CombatObserver.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.GuidedShots.class);
        ru.pasha.adaptationwheel.server.GuidedShots.registerGunEvents();
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.LearningController.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.WorldStudy.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.RestAdaptations.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.MovementAdaptations.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.ItemAdaptations.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.MobAdaptations.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.SenseAdaptations.class);
        MinecraftForge.EVENT_BUS.register(ru.pasha.adaptationwheel.server.TravelAdaptations.class);
    }
}

