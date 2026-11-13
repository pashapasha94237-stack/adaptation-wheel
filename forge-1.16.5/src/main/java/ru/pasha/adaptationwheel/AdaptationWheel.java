package ru.pasha.adaptationwheel;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.RegistryObject;
import ru.pasha.adaptationwheel.block.AdaptationGlowBlock;
import ru.pasha.adaptationwheel.item.WheelItem;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.*;
@Mod(AdaptationWheel.MOD_ID)
public final class AdaptationWheel {
 public static final String MOD_ID="adaptationwheel";
 private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,MOD_ID);
 private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,MOD_ID);
 private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,MOD_ID);
 public static final RegistryObject<Block> GLOW=BLOCKS.register("glow",AdaptationGlowBlock::new);
 public static final RegistryObject<Item> WHEEL=ITEMS.register("wheel",WheelItem::new);
 public static final RegistryObject<SoundEvent> ADAPTED_SOUND=SOUNDS.register("adapted",()->new SoundEvent(new ResourceLocation(MOD_ID,"adapted")));
 public AdaptationWheel(){
  net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,AdaptationConfig.SPEC);
  IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);SOUNDS.register(bus);
  WheelNetwork.register();MinecraftForge.EVENT_BUS.register(AdaptationEvents.class);MinecraftForge.EVENT_BUS.register(NegativeEffects.class);
 }
}
