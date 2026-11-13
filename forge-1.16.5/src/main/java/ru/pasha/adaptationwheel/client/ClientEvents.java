package ru.pasha.adaptationwheel.client;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.*;
import org.lwjgl.glfw.GLFW;
@Mod.EventBusSubscriber(modid="adaptationwheel",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
 public static final KeyBinding MENU=new KeyBinding("key.adaptationwheel.menu",InputMappings.Type.KEYSYM,GLFW.GLFW_KEY_J,"key.categories.adaptationwheel");
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->ClientRegistry.registerKeyBinding(MENU));}
 @Mod.EventBusSubscriber(modid="adaptationwheel",value=Dist.CLIENT)
 public static final class GameEvents {
  private static final Set<PlayerRenderer> ADDED=Collections.newSetFromMap(new WeakHashMap<PlayerRenderer,Boolean>());
  @SubscribeEvent public static void player(RenderPlayerEvent.Pre e){PlayerRenderer p=e.getRenderer();if(ADDED.add(p))p.addLayer(new WheelRenderLayer(p));}
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){Minecraft m=Minecraft.getInstance();if(e.phase==TickEvent.Phase.END&&m.level!=null&&!m.isPaused()){ClientWheelState.tick();while(MENU.consumeClick()){if(m.screen==null)AdaptationScreen.open();else if(m.screen instanceof AdaptationScreen)m.setScreen(null);}}}
  @SubscribeEvent public static void hud(RenderGameOverlayEvent.Post e){if(e.getType()==RenderGameOverlayEvent.ElementType.ALL)AdaptationOverlay.render(new GuiGraphics(e.getMatrixStack(),null),e.getPartialTicks(),e.getWindow().getGuiScaledWidth(),e.getWindow().getGuiScaledHeight());}
  @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggedOutEvent e){ClientWheelState.clear();ADDED.clear();}
 }
}