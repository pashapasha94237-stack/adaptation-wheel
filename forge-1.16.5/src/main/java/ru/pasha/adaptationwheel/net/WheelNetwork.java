package ru.pasha.adaptationwheel.net;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.common.util.FakePlayer;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.client.*;
import ru.pasha.adaptationwheel.server.*;
public final class WheelNetwork {
 private static final String PROTOCOL="1165-1";
 private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("adaptationwheel","state"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
 private static final Map<ServerPlayerEntity,Integer> SIGNATURES=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,Long> REQUESTS=new WeakHashMap<>();
 private static long batch;
 public static void register(){
  CHANNEL.registerMessage(0,StatePacket.class,StatePacket::encode,StatePacket::decode,StatePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(1,TogglePacket.class,TogglePacket::encode,TogglePacket::decode,TogglePacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(2,OpenPacket.class,(p,b)->{},b->new OpenPacket(),OpenPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(3,CataloguePacket.class,CataloguePacket::encode,CataloguePacket::decode,CataloguePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(4,CatalogueRequest.class,(p,b)->{},b->new CatalogueRequest(),CatalogueRequest::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
 }
 private static boolean real(ServerPlayerEntity p){return p.connection!=null&&!(p instanceof FakePlayer);}
 public static void sync(ServerPlayerEntity p){if(!real(p))return;CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),snapshot(p));catalogue(p,false);}
 public static void sendTo(ServerPlayerEntity subject,ServerPlayerEntity viewer){if(!real(viewer))return;CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),snapshot(subject));if(subject==viewer)catalogue(subject,false);}
 public static void openMenu(ServerPlayerEntity p){if(!real(p))return;sendTo(p,p);catalogue(p,true);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenPacket());}
 public static void setEnabled(String key,boolean on){CHANNEL.sendToServer(new TogglePacket(key,on));}
 public static void requestCatalogue(){CHANNEL.sendToServer(new CatalogueRequest());}
 public static void forget(ServerPlayerEntity p){SIGNATURES.remove(p);REQUESTS.remove(p);}
 public static void clear(){SIGNATURES.clear();REQUESTS.clear();}
 private static void catalogue(ServerPlayerEntity p,boolean requested){
  int sig=AdaptationEvents.state(p).snapshots().stream().map(v->Objects.hash(v.key(),v.profile())).collect(java.util.stream.Collectors.toList()).hashCode();
  long now=p.getLevel().getGameTime();boolean changed=!Objects.equals(SIGNATURES.get(p),sig);
  if(!changed&&(!requested||now-REQUESTS.getOrDefault(p,Long.MIN_VALUE/2)<20))return;
  List<CatalogueEntry> entries=AdaptationCatalogue.entries(p);SIGNATURES.put(p,sig);REQUESTS.put(p,now);long id=++batch;int count=Math.max(1,(entries.size()+255)/256);
  for(int i=0;i<count;i++)CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new CataloguePacket(id,i,count,entries.subList(i*256,Math.min(entries.size(),(i+1)*256))));
 }
 private static StatePacket snapshot(ServerPlayerEntity p){
  AdaptationState s=AdaptationEvents.state(p);List<WheelEntry> entries=new ArrayList<>();
  for(AdaptationState.Snapshot e:s.snapshots())entries.add(new WheelEntry(e.key(),DamageCategories.translationKey(e.key()),e.enabled(),e.boss(),e.stage(),e.work(),e.hits(),e.running(),s.enabled()&&AdaptationEvents.equipped(p)&&e.enabled()&&e.running()&&(!e.key().equals(AdaptationProfile.DARK_KEY)||DarknessLight.isDark(p)),e.profile()));
  return new StatePacket(p.getId(),s.enabled(),AdaptationEvents.equipped(p),s.nightVisionEnabled(),entries,s.revision());
 }
 private static AdaptationProfile profile(PacketBuffer b){try{return AdaptationProfile.valueOf(b.readUtf(32));}catch(IllegalArgumentException e){return AdaptationProfile.ENVIRONMENT;}}
 private static final class StatePacket {
  final int id;final boolean enabled,equipped,vision;final List<WheelEntry> entries;final long revision;
  StatePacket(int id,boolean on,boolean eq,boolean nv,List<WheelEntry> entries,long r){this.id=id;enabled=on;equipped=eq;vision=nv;this.entries=entries;revision=r;}
  void encode(PacketBuffer b){b.writeVarInt(id);b.writeBoolean(enabled);b.writeBoolean(equipped);b.writeBoolean(vision);b.writeVarInt(entries.size());for(WheelEntry e:entries){b.writeUtf(e.key(),256);b.writeUtf(e.translationKey(),256);b.writeBoolean(e.enabled());b.writeBoolean(e.boss());b.writeVarInt(e.stage());b.writeDouble(e.work());b.writeVarInt(e.hits());b.writeBoolean(e.running());b.writeBoolean(e.progressing());b.writeUtf(e.profile().name(),32);}b.writeLong(revision);}
  static StatePacket decode(PacketBuffer b){int id=b.readVarInt();boolean on=b.readBoolean(),eq=b.readBoolean(),nv=b.readBoolean();int count=b.readVarInt();if(count<0||count>16384)throw new IllegalArgumentException("Invalid entries");List<WheelEntry> entries=new ArrayList<>();for(int i=0;i<count;i++)entries.add(new WheelEntry(b.readUtf(256),b.readUtf(256),b.readBoolean(),b.readBoolean(),Math.max(0,Math.min(5,b.readVarInt())),b.readDouble(),Math.max(0,Math.min(AdaptationState.MAX_BONUS_HITS,b.readVarInt())),b.readBoolean(),b.readBoolean(),profile(b)));return new StatePacket(id,on,eq,nv,entries,b.readLong());}
  void handle(Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientReceiver.state(this)));c.get().setPacketHandled(true);}
 }
 private static final class TogglePacket {
  final String key;final boolean enabled;TogglePacket(String k,boolean e){key=k;enabled=e;}
  void encode(PacketBuffer b){b.writeUtf(key,256);b.writeBoolean(enabled);}
  static TogglePacket decode(PacketBuffer b){return new TogglePacket(b.readUtf(256),b.readBoolean());}
  void handle(Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->{ServerPlayerEntity p=c.get().getSender();if(p==null)return;AdaptationState s=AdaptationEvents.state(p);if(key.isEmpty())s.setEnabled(enabled);else if(key.equals("@nightvision")){if(s.nightVisionUnlocked())s.setNightVisionEnabled(enabled);}else s.setCategoryEnabled(key,enabled);AdaptationEvents.discardQueuedDamage(p);AdaptationEvents.saveAndSync(p);});c.get().setPacketHandled(true);}
 }
 private static final class OpenPacket{void handle(Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->ClientReceiver::open));c.get().setPacketHandled(true);}}
 private static final class CatalogueRequest{void handle(Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->{ServerPlayerEntity p=c.get().getSender();if(p!=null)catalogue(p,true);});c.get().setPacketHandled(true);}}
 private static final class CataloguePacket{
  final long batch;final int index,count;final List<CatalogueEntry> entries;
  CataloguePacket(long batch,int i,int count,List<CatalogueEntry> e){this.batch=batch;index=i;this.count=count;entries=e;}
  void encode(PacketBuffer b){b.writeLong(batch);b.writeVarInt(index);b.writeVarInt(count);b.writeVarInt(entries.size());for(CatalogueEntry e:entries){b.writeUtf(e.key(),256);b.writeUtf(e.translationKey(),256);b.writeUtf(e.fallbackName(),512);b.writeUtf(e.profile().name(),32);}}
  static CataloguePacket decode(PacketBuffer b){long id=b.readLong();int i=b.readVarInt(),count=b.readVarInt(),size=b.readVarInt();if(count<1||count>512||i<0||i>=count||size<0||size>256)throw new IllegalArgumentException("Invalid chunk");List<CatalogueEntry> entries=new ArrayList<>();for(int n=0;n<size;n++)entries.add(new CatalogueEntry(b.readUtf(256),b.readUtf(256),b.readUtf(512),profile(b)));return new CataloguePacket(id,i,count,entries);}
  void handle(Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientReceiver.catalogue(this)));c.get().setPacketHandled(true);}
 }
 private static final class ClientReceiver{
  static void state(StatePacket p){ClientWheelState.accept(p.id,p.enabled,p.equipped,p.vision,p.entries,p.revision);}
  static void catalogue(CataloguePacket p){ClientWheelState.acceptCatalogue(p.batch,p.index,p.count,p.entries);}
  static void open(){AdaptationScreen.open();}
 }
}