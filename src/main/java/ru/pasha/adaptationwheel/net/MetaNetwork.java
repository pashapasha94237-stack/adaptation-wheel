package ru.pasha.adaptationwheel.net;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.server.*;

/** Clients can report only cosmetic conditions, never supply work, stages, inventory or teleport coordinates. */
public final class MetaNetwork {
    private static final SimpleChannel C=NetworkRegistry.newSimpleChannel(new ResourceLocation("adaptationwheel","meta"),()->"1","1"::equals,"1"::equals);
    private MetaNetwork(){}
    public static void register(){
        C.messageBuilder(Action.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Action::write).decoder(Action::read).consumerMainThread(Action::handle).add();
        C.messageBuilder(Warning.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Warning::write).decoder(Warning::read).consumerMainThread(Warning::handle).add();
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
    public static void warning(ServerPlayer p,String dimension,String danger,List<Vec3> trail,boolean alert){C.send(PacketDistributor.PLAYER.with(()->p),new Warning(dimension,danger,List.copyOf(trail),alert));}
    private record Warning(String dimension,String danger,List<Vec3> trail,boolean alert){
        void write(FriendlyByteBuf b){b.writeUtf(dimension,128);b.writeUtf(danger,256);b.writeBoolean(alert);b.writeVarInt(trail.size());for(Vec3 v:trail){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}}
        static Warning read(FriendlyByteBuf b){String dimension=b.readUtf(128),danger=b.readUtf(256);boolean alert=b.readBoolean();int n=b.readVarInt();if(n<0||n>20)throw new IllegalArgumentException("Invalid defeat trail");List<Vec3> points=new ArrayList<>();for(int i=0;i<n;i++){double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>32768)throw new IllegalArgumentException("Invalid trail position");points.add(new Vec3(x,y,z));}return new Warning(dimension,danger,List.copyOf(points),alert);}
        void handle(Supplier<NetworkEvent.Context> ctx){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ru.pasha.adaptationwheel.client.MetaClient.warning(dimension,danger,trail,alert));ctx.get().setPacketHandled(true);}
    }
}
