package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.pasha.adaptationwheel.AdaptationWheel;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/** All client types are isolated here so a dedicated server never loads renderers. */
@Mod.EventBusSubscriber(modid = AdaptationWheel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    public static final KeyMapping MENU = new KeyMapping("key.adaptationwheel.menu", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.adaptationwheel");
    private ClientEvents() {
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){
        e.enqueueWork(LandscapeMemory::setup);
        ru.pasha.adaptationwheel.StackLimits.client=player->player==Minecraft.getInstance().player&&MetaClient.full(ru.pasha.adaptationwheel.core.MetaMechanic.POCKET);
        ru.pasha.adaptationwheel.MetaHooks.client=(player,m)->player==Minecraft.getInstance().player&&MetaClient.active(m)?MetaClient.stage(m):0;
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WheelModel.LAYER, WheelModel::createLayer);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new WheelRenderLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("adaptation", AdaptationOverlay::render);
    }

    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(MENU); }

    /** Configured or a hand edit changed the look: Forge reports it off the main thread. */
    @SubscribeEvent
    public static void configReloaded(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ru.pasha.adaptationwheel.WheelSkinConfig.SPEC)
            Minecraft.getInstance().execute(WheelSkinClient::configChanged);
    }

    @Mod.EventBusSubscriber(modid = AdaptationWheel.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }
        @SubscribeEvent
        public static void openingScreen(net.minecraftforge.client.event.ScreenEvent.Opening event) {ConfiguredMobPicker.opening(event);}

        @SubscribeEvent
        public static void renderHand(net.minecraftforge.client.event.RenderHandEvent event) {ForearmBlade.renderHand(event);}

        /** Slots show big stacks shortened (12k); the tooltip gives the exact number. */
        @SubscribeEvent
        public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            int count=event.getItemStack().getCount();var lines=event.getToolTip();
            if(count>=1000)lines.add(Math.min(1,lines.size()),net.minecraft.network.chat.Component.translatable("gui.adaptationwheel.stack_count",
                    ru.pasha.adaptationwheel.core.StackCount.grouped(count)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if(event.phase==TickEvent.Phase.END)MetaClient.tick();
            if(event.phase==TickEvent.Phase.END && minecraft.level!=null) {FpsAdaptation.tick();AdaptationScreen.observeJournal();}
            if (event.phase == TickEvent.Phase.END && minecraft.level != null && !minecraft.isPaused()) {
                ClientWheelState.tick();
                T2SClientCompatibility.tick();
                while (MENU.consumeClick()) {
                    if (minecraft.screen == null) AdaptationScreen.open();
                    else if (minecraft.screen instanceof AdaptationScreen) minecraft.setScreen(null);
                }
            }
        }

        @SubscribeEvent
        public static void login(ClientPlayerNetworkEvent.LoggingIn event) {WheelSkinClient.login();}

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            FpsAdaptation.logout();
            MetaClient.logout();
            ClientWheelState.clear();
            AdaptationScreen.clearJournal();
            WheelSkinClient.clear();
            ClientVoidText.clear();
            ru.pasha.adaptationwheel.core.SwordSettings.defaults();
        }

        @SubscribeEvent
        public static void frame(TickEvent.RenderTickEvent event) {
            if(event.phase==TickEvent.Phase.END && Minecraft.getInstance().level!=null) {FpsAdaptation.frame();LandscapeMemory.frame();}
        }

        @SubscribeEvent
        public static void leaveLevel(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                ClientWheelState.remove(event.getEntity().getId());
                WheelSkinClient.remove(event.getEntity().getId());
            }
        }
    }
}
