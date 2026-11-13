package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.server.ItemAdaptations;

/** Client half of the item adaptations: who owns the local cooldowns, the anvil screen and the "on cooldown" click. */
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ItemAdaptationsClient {
    private ItemAdaptationsClient(){}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){
        ItemAdaptations.clientOwner=cooldowns->{var p=Minecraft.getInstance().player;return p!=null&&p.getCooldowns()==cooldowns?p:null;};
    }
    /** AnvilScreen.renderLabels: "Too Expensive!" becomes the real price from stage I. */
    public static Component label(Component plain){
        var p=Minecraft.getInstance().player;
        return p!=null&&p.containerMenu instanceof AnvilMenu menu?label(plain,menu.getCost()):plain;
    }
    /** AnvilScreen.renderLabels: the same verdict the menu reaches, so a raised limit shows the price, not the refusal. */
    public static boolean screenAllows(boolean instabuild){
        if(instabuild)return true;
        var p=Minecraft.getInstance().player;
        return p!=null&&p.containerMenu instanceof AnvilMenu menu&&screenAllows(false,menu.getCost());
    }
    /** Both checks for a price read elsewhere: the anvil upgrade tab of Sophisticated Backpacks wraps its own AnvilMenu. */
    public static Component label(Component plain,int cost){
        var p=Minecraft.getInstance().player;
        if(p==null||MetaHooks.stage(p,MetaMechanic.ANVIL)<1)return plain;
        return Component.translatable("meta.adaptationwheel.too_expensive.price",cost);
    }
    public static boolean screenAllows(boolean instabuild,int cost){
        if(instabuild)return true;
        var p=Minecraft.getInstance().player;
        return p!=null&&cost<ItemAdaptations.anvilLimit(p);
    }

    @Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT)
    public static final class Events {
        private Events(){}
        /** Vanilla drops a use click on an item on cooldown without any event, so the click itself is observed; the server verifies the claim. */
        @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered e){
            if(!e.isUseItem()||!MetaClient.active(MetaMechanic.COOLDOWN))return;
            var p=Minecraft.getInstance().player;if(p==null)return;
            var item=p.getItemInHand(e.getHand()).getItem();
            if(item==Items.AIR||!p.getCooldowns().isOnCooldown(item))return;
            var id=BuiltInRegistries.ITEM.getKey(item);
            if(id!=null&&"minecraft".equals(id.getNamespace()))MetaClient.report(MetaMechanic.COOLDOWN);
        }
    }
}
