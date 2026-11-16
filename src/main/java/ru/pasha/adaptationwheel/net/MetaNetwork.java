package ru.pasha.adaptationwheel.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.server.*;

/** Clients can report only cosmetic conditions, never supply work, stages, inventory or teleport coordinates. */
public final class MetaNetwork {
    private static final SimpleChannel C=NetworkRegistry.newSimpleChannel(new ResourceLocation("adaptationwheel","meta"),()->"2","2"::equals,"2"::equals);
    private MetaNetwork(){}
    public static void register(){
        C.messageBuilder(Action.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Action::write).decoder(Action::read).consumerMainThread(Action::handle).add();
    }
    public static void action(String action){C.sendToServer(new Action(action));}
    private record Action(String action){
        void write(FriendlyByteBuf b){b.writeUtf(action,80);}static Action read(FriendlyByteBuf b){return new Action(b.readUtf(80));}
        void handle(Supplier<NetworkEvent.Context> ctx){
            ServerPlayer p=ctx.get().getSender();if(p!=null&&p.isAlive()&&!p.isSpectator()&&AdaptationEvents.active(p)){
                switch(action){case "compact"->InventoryPressure.compact(p);case "border"->MetaServer.cross(p);
                    case "inventory_open"->OutsideServer.inventory(p,true);case "inventory_close"->OutsideServer.inventory(p,false);default->{
                    MetaMechanic m=MetaMechanic.fromKey(action);
                    if(m==MetaMechanic.NOISE||m==MetaMechanic.MEMORY||m==MetaMechanic.LOGS||m==MetaMechanic.CONFIG||m==MetaMechanic.BATTERY)MetaServer.expose(p,m);
                    // Vanilla blocks using an item on cooldown on the client; the server checks the claim itself.
                    if(m==MetaMechanic.COOLDOWN)ItemAdaptations.reportedCooldown(p);
                }}
            }ctx.get().setPacketHandled(true);
        }
    }
}
