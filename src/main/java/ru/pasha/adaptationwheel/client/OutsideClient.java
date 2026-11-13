package ru.pasha.adaptationwheel.client;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.net.MetaNetwork;
import ru.pasha.adaptationwheel.server.LogWatch;
import ru.pasha.adaptationwheel.server.NetworkWatch;

/** Client half of the outside adaptations: own inventory, client configs, memory pressure, menu details. */
@Mod.EventBusSubscriber(modid=AdaptationWheel.MOD_ID,value=Dist.CLIENT)
public final class OutsideClient {
    private static boolean inventoryOpen;
    private static int originalDistance=-1,appliedDistance=-1,calmSeconds,pressureSeconds;
    private OutsideClient(){}
    private static boolean ownInventory(Object screen){return screen instanceof InventoryScreen||screen instanceof CreativeModeInventoryScreen;}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){
        boolean open=ownInventory(e.getNewScreen());
        if(open==inventoryOpen||Minecraft.getInstance().player==null)return;
        // Only a wearer learning this adaptation reports; a reported opening is always closed again.
        if(open&&!MetaClient.active(MetaMechanic.INVENTORY))return;
        inventoryOpen=open;MetaNetwork.action(open?"inventory_open":"inventory_close");
    }
    @SubscribeEvent public static void closing(ScreenEvent.Closing e){
        if(inventoryOpen&&ownInventory(e.getScreen())){inventoryOpen=false;MetaNetwork.action("inventory_close");}
    }
    static void chat(Component c){var mc=Minecraft.getInstance();if(mc.player!=null)mc.gui.getChat().addMessage(c);}
    static Component line(String key,Object... args){return Component.translatable("meta.adaptationwheel.outside.prefix").withStyle(ChatFormatting.GOLD).append(Component.translatable(key,args).withStyle(ChatFormatting.YELLOW));}
    /** Called from the config watcher thread for the client-side config files. */
    public static void configChanged(String file,List<String> lines){
        Minecraft.getInstance().execute(()->{
            if(!MetaClient.active(MetaMechanic.CONFIG))return;
            int stage=MetaClient.stage(MetaMechanic.CONFIG);
            chat(line("meta.adaptationwheel.config_edit.event",file,lines.size()));
            if(stage>=4)for(int i=0;i<Math.min(8,lines.size());i++)chat(Component.literal("  "+lines.get(i)).withStyle(ChatFormatting.GRAY));
            MetaClient.report(MetaMechanic.CONFIG);
        });
    }
    /** Once per second from {@link MetaClient#tick()}. */
    static void second(double heapRatio,double threshold){
        var mc=Minecraft.getInstance();
        LogWatch.install();LogWatch.clientWants(MetaClient.full(MetaMechanic.LOGS));
        if(LogWatch.errorsLastMinute()>=LogWatch.ERRORS_PER_MINUTE)MetaClient.report(MetaMechanic.LOGS);
        // Full memory adaptation: draw less of the world before the heap runs out, give it back afterwards.
        var option=mc.options.renderDistance();
        if(MetaClient.full(MetaMechanic.MEMORY)&&heapRatio>=threshold){
            calmSeconds=0;
            if(++pressureSeconds>=10&&option.get()>6&&!SenseClient.distanceCut()){
                if(originalDistance<0||option.get()!=appliedDistance)originalDistance=option.get();
                option.set(Math.max(6,option.get()-2));appliedDistance=option.get();pressureSeconds=0;
                mc.gui.setOverlayMessage(Component.translatable("meta.adaptationwheel.memory_pressure.reduced",option.get()),false);
            }
        }else{
            pressureSeconds=0;
            if(originalDistance>=0&&(heapRatio<threshold-.15||!MetaClient.full(MetaMechanic.MEMORY))&&++calmSeconds>=30)restoreDistance();
        }
    }
    /** The player's render distance while memory pressure holds it down, else -1 (the battery cut stays off meanwhile). */
    static int reducedFrom(){return originalDistance;}
    static void restoreDistance(){
        if(originalDistance<0)return;
        var mc=Minecraft.getInstance();var option=mc.options.renderDistance();
        // A distance the player chose meanwhile is theirs; only our own reduction is undone.
        boolean ours=option.get()==appliedDistance;
        if(ours)option.set(originalDistance);
        originalDistance=appliedDistance=-1;calmSeconds=0;
        if(ours&&mc.player!=null)mc.gui.setOverlayMessage(Component.translatable("meta.adaptationwheel.memory_pressure.restored"),false);
    }
    public static void logout(){restoreDistance();inventoryOpen=false;pressureSeconds=calmSeconds=0;}
    /** Extra lines for the J menu detail panel. */
    static List<Component> details(MetaMechanic m){
        List<Component> out=new ArrayList<>();
        switch(m){
            case LOGS->out.add(Component.translatable("meta.adaptationwheel.log_errors.stats",LogWatch.totalErrors(),LogWatch.errorsLastMinute(),LogWatch.suppressed()).withStyle(ChatFormatting.GRAY));
            case NETWORK->{Boolean online=NetworkWatch.online();if(online!=null)out.add(Component.translatable(online?"meta.adaptationwheel.connection_loss.online":"meta.adaptationwheel.connection_loss.offline").withStyle(ChatFormatting.GRAY));}
            case MEMORY->{if(originalDistance>=0)out.add(Component.translatable("meta.adaptationwheel.memory_pressure.reduced",Minecraft.getInstance().options.renderDistance().get()).withStyle(ChatFormatting.GOLD));}
            default->{}
        }
        return out;
    }
}
