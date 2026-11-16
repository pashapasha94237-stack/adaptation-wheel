package ru.pasha.adaptationwheel.net;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.client.ClientWheelState;
import ru.pasha.adaptationwheel.client.AdaptationScreen;
import ru.pasha.adaptationwheel.server.*;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.StageDurations;
import ru.pasha.adaptationwheel.core.AdaptationTimings;
import ru.pasha.adaptationwheel.core.StageProtections;
import ru.pasha.adaptationwheel.core.SwordSettings;
import ru.pasha.adaptationwheel.core.WheelSkin;

public final class WheelNetwork {
    private static final com.google.gson.Gson ANALYSIS_JSON=new com.google.gson.Gson();
    private static final String PROTOCOL="25";
    /** Each player's wheel look, as their client chose it (cosmetic, so the client decides). Server thread only. */
    private static final Map<UUID,WheelSkin> SKINS=new HashMap<>();
    private static final Map<ServerPlayer,SwordSettings.Values> SWORD_SIGNATURES=new WeakHashMap<>();
    private static final Map<ServerPlayer,Integer> CATALOGUE_SIGNATURES=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> CATALOGUE_REQUESTS=new WeakHashMap<>();
    private static long catalogueBatch;
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AdaptationWheel.MOD_ID,"state"),() -> PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private WheelNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(StatePacket.class,0,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(StatePacket::encode).decoder(StatePacket::decode).consumerMainThread(StatePacket::handle).add();
        CHANNEL.messageBuilder(TogglePacket.class,1,NetworkDirection.PLAY_TO_SERVER)
            .encoder(TogglePacket::encode).decoder(TogglePacket::decode).consumerMainThread(TogglePacket::handle).add();
        CHANNEL.messageBuilder(OpenPacket.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b) -> {}).decoder(b -> new OpenPacket()).consumerMainThread(OpenPacket::handle).add();
        CHANNEL.messageBuilder(CataloguePacket.class,3,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(CataloguePacket::encode).decoder(CataloguePacket::decode).consumerMainThread(CataloguePacket::handle).add();
        CHANNEL.messageBuilder(CatalogueRequest.class,4,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b) -> {}).decoder(b -> new CatalogueRequest()).consumerMainThread(CatalogueRequest::handle).add();
        CHANNEL.messageBuilder(SwordSettingsPacket.class,5,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SwordSettingsPacket::encode).decoder(SwordSettingsPacket::decode).consumerMainThread(SwordSettingsPacket::handle).add();
        CHANNEL.messageBuilder(SkinChoicePacket.class,6,NetworkDirection.PLAY_TO_SERVER)
            .encoder(SkinChoicePacket::encode).decoder(SkinChoicePacket::decode).consumerMainThread(SkinChoicePacket::handle).add();
        CHANNEL.messageBuilder(SkinPacket.class,7,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SkinPacket::encode).decoder(SkinPacket::decode).consumerMainThread(SkinPacket::handle).add();
        CHANNEL.messageBuilder(VoidTextPacket.class,8,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(VoidTextPacket::encode).decoder(VoidTextPacket::decode).consumerMainThread(VoidTextPacket::handle).add();
        CHANNEL.messageBuilder(VoidTextWatch.class,9,NetworkDirection.PLAY_TO_SERVER)
            .encoder(VoidTextWatch::encode).decoder(VoidTextWatch::decode).consumerMainThread(VoidTextWatch::handle).add();
        CHANNEL.messageBuilder(CategoryFixPacket.class,10,NetworkDirection.PLAY_TO_SERVER)
            .encoder(CategoryFixPacket::encode).decoder(CategoryFixPacket::decode).consumerMainThread(CategoryFixPacket::handle).add();
    }
    public static void sync(ServerPlayer player) {
        syncSwordSettings(player);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),snapshot(player));
        syncCatalogue(player,false);
    }
    public static void sendTo(ServerPlayer subject,ServerPlayer viewer) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),snapshot(subject));
        if(subject==viewer) {syncSwordSettings(viewer);syncCatalogue(subject,false);}
        else CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),new SkinPacket(subject.getId(),skin(subject).id));
    }
    /** Client side: tells the server this computer's look; false when the server has no wheel channel. */
    public static boolean chooseSkin(net.minecraft.network.Connection connection,String id) {
        if(!CHANNEL.isRemotePresent(connection)) return false;
        CHANNEL.sendToServer(new SkinChoicePacket(id));
        return true;
    }
    /** A player's look changed: everyone who sees that player is told (the player's own client already knows). */
    public static void chooseSkin(ServerPlayer player,WheelSkin skin) {
        WheelSkin old=skin(player);
        if(skin==WheelSkin.CLASSIC) SKINS.remove(player.getUUID()); else SKINS.put(player.getUUID(),skin);
        if(old!=skin) CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),new SkinPacket(player.getId(),skin.id));
    }
    public static WheelSkin skin(ServerPlayer player) { return SKINS.getOrDefault(player.getUUID(),WheelSkin.CLASSIC); }
    private record SkinChoicePacket(String id) {
        void encode(FriendlyByteBuf b) { b.writeUtf(id,64); }
        static SkinChoicePacket decode(FriendlyByteBuf b) { return new SkinChoicePacket(b.readUtf(64)); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player=ctx.get().getSender();
            // an unknown id (a newer mod's look) is the classic wheel here, as on every client of this version
            if(player!=null) chooseSkin(player,WheelSkin.byId(id));
            ctx.get().setPacketHandled(true);
        }
    }
    private record SkinPacket(int entityId,String id) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeUtf(id,64); }
        static SkinPacket decode(FriendlyByteBuf b) { return new SkinPacket(b.readVarInt(),b.readUtf(64)); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> ClientReceiver.skin(this));
            ctx.get().setPacketHandled(true);
        }
    }
    /** Client side: the look menu started or stopped showing the Infinite Void; false when the server has no wheel channel. */
    public static boolean watchVoidText(net.minecraft.network.Connection connection,boolean watching) {
        if(!CHANNEL.isRemotePresent(connection)) return false;
        CHANNEL.sendToServer(new VoidTextWatch(watching));
        return true;
    }
    public static void voidText(ServerPlayer player,int ticks,boolean read) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new VoidTextPacket(ticks,read));
    }
    private record VoidTextWatch(boolean watching) {
        void encode(FriendlyByteBuf b) { b.writeBoolean(watching); }
        static VoidTextWatch decode(FriendlyByteBuf b) { return new VoidTextWatch(b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player=ctx.get().getSender();
            if(player!=null) VoidTextAdaptation.watch(player,watching);
            ctx.get().setPacketHandled(true);
        }
    }
    private record VoidTextPacket(int ticks,boolean read) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(ticks); b.writeBoolean(read); }
        static VoidTextPacket decode(FriendlyByteBuf b) { return new VoidTextPacket(b.readVarInt(),b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> ClientReceiver.voidText(this));
            ctx.get().setPacketHandled(true);
        }
    }
    public static void openMenu(ServerPlayer player) {
        sendTo(player,player);
        syncCatalogue(player,true);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new OpenPacket());
    }
    public static void setEnabled(String key,boolean enabled) { CHANNEL.sendToServer(new TogglePacket(key,enabled)); }
    /** Client side: the player's category for a row, or null to give it back to the analyzer. */
    public static void fixCategory(String key,AdaptationProfile profile) { CHANNEL.sendToServer(new CategoryFixPacket(key,profile==null?"":profile.name())); }
    /** Server side: only the sender's own rows the analyzer has an opinion on; stages and protection never come from this. */
    public static boolean fixCategory(ServerPlayer player,String key,AdaptationProfile profile) {
        var state=AdaptationEvents.state(player);
        if(!SmartAttackAnalyzer.fixable(player,state.snapshot(key)) || !state.setManualCategory(key,profile))return false;
        AdaptationEvents.saveAndSync(player);
        return true;
    }
    public static void requestCatalogue() { CHANNEL.sendToServer(new CatalogueRequest()); }
    public static void forget(ServerPlayer player) { CATALOGUE_SIGNATURES.remove(player);CATALOGUE_REQUESTS.remove(player);SWORD_SIGNATURES.remove(player);SKINS.remove(player.getUUID()); }
    public static void clearServerState() { CATALOGUE_SIGNATURES.clear();CATALOGUE_REQUESTS.clear();SWORD_SIGNATURES.clear();SKINS.clear(); }
    private static void syncSwordSettings(ServerPlayer player) {
        var values=SwordSettings.current();
        if(values.equals(SWORD_SIGNATURES.get(player)))return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new SwordSettingsPacket(values));
        SWORD_SIGNATURES.put(player,values);
    }
    private record SwordSettingsPacket(SwordSettings.Values values) {
        void encode(FriendlyByteBuf b){
            b.writeDouble(values.baseDamage());b.writeDouble(values.undeadMultiplier());b.writeDouble(values.cursedSpiritMultiplier());
            b.writeVarInt(values.specialMobs().size());for(String id:values.specialMobs())b.writeUtf(id,256);
            b.writeVarInt(values.mobMultipliers().size());values.mobMultipliers().forEach((id,multiplier)->{b.writeUtf(id,256);b.writeDouble(multiplier);});
        }
        static SwordSettingsPacket decode(FriendlyByteBuf b){
            double base=b.readDouble(),undead=b.readDouble(),cursed=b.readDouble();int count=b.readVarInt();
            if(count<0 || count>65536)throw new IllegalArgumentException("Invalid special mob count");
            List<String> mobs=new ArrayList<>(count);for(int i=0;i<count;i++)mobs.add(b.readUtf(256));
            int overrides=b.readVarInt();if(overrides<0 || overrides>65536)throw new IllegalArgumentException("Invalid multiplier count");
            Map<String,Double> multipliers=new LinkedHashMap<>();for(int i=0;i<overrides;i++)multipliers.put(b.readUtf(256),b.readDouble());
            return new SwordSettingsPacket(new SwordSettings.Values(base,undead,cursed,mobs,multipliers));
        }
        void handle(Supplier<NetworkEvent.Context> ctx){SwordSettings.replace(values);ctx.get().setPacketHandled(true);}
    }
    private static void syncCatalogue(ServerPlayer player,boolean requested) {
        int signature=Objects.hash(AdaptationTimings.revision(),AdaptationEvents.state(player).snapshots().stream()
            .map(p -> Objects.hash(p.key(),p.category())).toList().hashCode());
        boolean changed=!Objects.equals(CATALOGUE_SIGNATURES.get(player),signature);
        long now=player.serverLevel().getGameTime();
        if(!changed && (!requested || now-CATALOGUE_REQUESTS.getOrDefault(player,Long.MIN_VALUE/2)<20)) return;
        var catalogue=AdaptationCatalogue.entries(player);
        CATALOGUE_SIGNATURES.put(player,signature);CATALOGUE_REQUESTS.put(player,now);
        long batch=++catalogueBatch;
        int count=Math.max(1,(catalogue.size()+255)/256);
        for(int i=0;i<count;i++) {
            var chunk=catalogue.subList(i*256,Math.min(catalogue.size(),(i+1)*256));
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new CataloguePacket(batch,i,count,chunk));
        }
    }
    private static StatePacket snapshot(ServerPlayer player) {
        var state=AdaptationEvents.state(player);var experience=state.experiencePercents();
        List<WheelEntry> entries=state.snapshots().stream().map(p -> new WheelEntry(p.key(),DamageCategories.translationKey(p.key()),
            p.enabled(),p.boss(),p.stage(),p.work(),p.hits(),p.running(),state.enabled() && AdaptationEvents.equipped(player)
            && p.enabled() && p.running() && ru.pasha.adaptationwheel.core.AdaptationState.canProgress(p.boss(),p.stage()) && (!p.key().equals(ru.pasha.adaptationwheel.core.AdaptationProfile.DARK_KEY)
            || DarknessLight.isDark(player)),p.category(),p.timings(),AdaptationTimings.hitAccelerationPercent(),
            ru.pasha.adaptationwheel.core.MetaMechanic.fromKey(p.key())==null?AdaptationTimings.protections():ru.pasha.adaptationwheel.core.MetaMechanic.NO_DAMAGE,falseIfUtility(p.key(),AdaptationTimings.existenceEnabled()),falseIfUtility(p.key(),AdaptationTimings.oneHitKillEnabled())).withAnalysis(SmartAttackAnalyzer.info(player,p)).withCategory(p.manualProfile()==null?null:p.profile(),SmartAttackAnalyzer.fixable(player,p)).withExperience(experience.getOrDefault(p.key(),0))).toList();
        return new StatePacket(player.getId(),state.enabled(),AdaptationEvents.equipped(player),state.nightVisionEnabled(),entries,state.revision(),player.serverLevel().getGameTime());
    }
    private static boolean falseIfUtility(String key,boolean value){return ru.pasha.adaptationwheel.core.MetaMechanic.fromKey(key)==null&&value;}
    private record StatePacket(int entityId,boolean enabled,boolean equipped,boolean nightVision,List<WheelEntry> entries,long revision,long serverTick) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(entityId); b.writeBoolean(enabled); b.writeBoolean(equipped); b.writeBoolean(nightVision); b.writeVarInt(entries.size());
            for (WheelEntry p:entries) {
                b.writeUtf(p.key(),256); b.writeUtf(p.translationKey(),256); b.writeBoolean(p.enabled()); b.writeBoolean(p.boss());
                b.writeVarInt(p.stage()); b.writeDouble(p.work()); b.writeVarInt(p.hits()); b.writeBoolean(p.running()); b.writeBoolean(p.progressing());
                b.writeUtf(p.profile().name(),32);
                writeTimings(b,p.timings());
                b.writeVarInt(p.hitAccelerationPercent());
                for(int stage=1;stage<=4;stage++)b.writeVarInt(p.protections().percent(stage));
                b.writeBoolean(p.existenceEnabled());b.writeBoolean(p.oneHitKillEnabled());
                writeAnalysis(b,p.analysis());
                b.writeUtf(p.analyzerProfile()==null?"":p.analyzerProfile().name(),32);b.writeBoolean(p.fixable());
                b.writeVarInt(p.experiencePercent());
            }
            b.writeLong(revision);b.writeLong(serverTick);
        }
        static StatePacket decode(FriendlyByteBuf b) {
            int id=b.readVarInt(); boolean enabled=b.readBoolean(),equipped=b.readBoolean(),nightVision=b.readBoolean(); int size=b.readVarInt();
            if (size<0) throw new IllegalArgumentException("Invalid category count");
            List<WheelEntry> entries=new ArrayList<>();
            for (int i=0;i<size;i++) entries.add(new WheelEntry(b.readUtf(256),b.readUtf(256),b.readBoolean(),b.readBoolean(),
                Math.max(0,Math.min(5,b.readVarInt())),b.readDouble(),Math.max(0,Math.min(ru.pasha.adaptationwheel.core.AdaptationState.MAX_BONUS_HITS,b.readVarInt())),b.readBoolean(),b.readBoolean(),readProfile(b),readTimings(b),b.readVarInt(),new StageProtections(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()),b.readBoolean(),b.readBoolean(),readAnalysis(b)).withCategory(readOptionalProfile(b),b.readBoolean()).withExperience(b.readVarInt()));
            return new StatePacket(id,enabled,equipped,nightVision,List.copyOf(entries),b.readLong(),b.readLong());
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> ClientReceiver.accept(this));
            ctx.get().setPacketHandled(true);
        }
    }
    private record CategoryFixPacket(String key,String profile) {
        void encode(FriendlyByteBuf b) { b.writeUtf(key,256); b.writeUtf(profile,32); }
        static CategoryFixPacket decode(FriendlyByteBuf b) { return new CategoryFixPacket(b.readUtf(256),b.readUtf(32)); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player=ctx.get().getSender();
            try { if(player!=null) fixCategory(player,key,profile.isEmpty()?null:AdaptationProfile.valueOf(profile)); }
            catch(IllegalArgumentException unknown) { } // a newer client's category means nothing here
            ctx.get().setPacketHandled(true);
        }
    }
    private record TogglePacket(String key,boolean enabled) {
        void encode(FriendlyByteBuf b) { b.writeUtf(key,256); b.writeBoolean(enabled); }
        static TogglePacket decode(FriendlyByteBuf b) { return new TogglePacket(b.readUtf(256),b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player=ctx.get().getSender();
            if (player!=null) {
                // Only the sender's own existing categories can be changed. No progression comes from clients.
                if (key.isEmpty()) AdaptationEvents.state(player).setEnabled(enabled);
                else if(key.equals("@nightvision") && AdaptationEvents.state(player).nightVisionUnlocked()) AdaptationEvents.state(player).setNightVisionEnabled(enabled);
                else AdaptationEvents.state(player).setCategoryEnabled(key,enabled);
                AdaptationEvents.discardQueuedDamage(player);
                AdaptationEvents.saveAndSync(player);
            }
            ctx.get().setPacketHandled(true);
        }
    }
    private record OpenPacket() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> ClientReceiver::open);
            ctx.get().setPacketHandled(true);
        }
    }
    private static AdaptationProfile readProfile(FriendlyByteBuf b) {
        try { return AdaptationProfile.valueOf(b.readUtf(32)); }
        catch(IllegalArgumentException ex) { return AdaptationProfile.ENVIRONMENT; }
    }
    private static AdaptationProfile readOptionalProfile(FriendlyByteBuf b) {
        String name=b.readUtf(32);
        try { return name.isEmpty()?null:AdaptationProfile.valueOf(name); }
        catch(IllegalArgumentException ex) { return AdaptationProfile.ENVIRONMENT; }
    }
    /** The menu is opening (every way of opening it sends this). The advancement is cosmetic, so a client may report it. */
    public static void catalogueRequested(ServerPlayer player) {
        WheelAdvancements.award(player,WheelAdvancements.MENU);
        syncCatalogue(player,true);
    }
    private record CatalogueRequest() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            var player=ctx.get().getSender();
            if(player!=null) catalogueRequested(player);
            ctx.get().setPacketHandled(true);
        }
    }
    private record CataloguePacket(long batch,int index,int count,List<CatalogueEntry> entries) {
        void encode(FriendlyByteBuf b) {
            b.writeLong(batch);b.writeVarInt(index);b.writeVarInt(count);b.writeVarInt(entries.size());
            for(var e:entries) {
                b.writeUtf(e.key(),256);b.writeUtf(e.translationKey(),256);b.writeUtf(e.fallbackName(),512);b.writeUtf(e.profile().name(),32);
                writeTimings(b,e.timings());
            }
        }
        static CataloguePacket decode(FriendlyByteBuf b) {
            long batch=b.readLong();int index=b.readVarInt(),count=b.readVarInt(),size=b.readVarInt();
            if(count<1 || count>512 || index<0 || index>=count || size<0 || size>256)
                throw new IllegalArgumentException("Invalid catalogue chunk");
            List<CatalogueEntry> entries=new ArrayList<>();
            for(int i=0;i<size;i++) entries.add(new CatalogueEntry(b.readUtf(256),b.readUtf(256),b.readUtf(512),readProfile(b),readTimings(b)));
            return new CataloguePacket(batch,index,count,List.copyOf(entries));
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> ClientReceiver.catalogue(this));
            ctx.get().setPacketHandled(true);
        }
    }
    private static void writeTimings(FriendlyByteBuf b,StageDurations timings) {for(int stage=0;stage<5;stage++)b.writeVarInt(timings.ticks(stage));}
    private static StageDurations readTimings(FriendlyByteBuf b) {return new StageDurations(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());}
    private static void writeAnalysis(FriendlyByteBuf b,AnalysisInfo info) {
        b.writeBoolean(info.present());
        if(!info.present())return;
        b.writeUtf(info.kind(),16);b.writeVarInt(info.confidence());b.writeUtf(info.reason(),128);
        b.writeVarInt(info.observations());writeTimings(b,info.recommendedTimings());
        boolean detailed=!info.insight().equals(ru.pasha.adaptationwheel.core.CombatKnowledge.Summary.EMPTY);
        b.writeBoolean(detailed);if(detailed)b.writeUtf(ANALYSIS_JSON.toJson(info.insight()),4096);
        b.writeUtf(info.originMod(),64);b.writeUtf(info.originWithout(),32);
    }
    private static AnalysisInfo readAnalysis(FriendlyByteBuf b) {
        if(!b.readBoolean())return AnalysisInfo.NONE;
        String kind=b.readUtf(16);int confidence=b.readVarInt();String reason=b.readUtf(128);int count=b.readVarInt();StageDurations timings=readTimings(b);
        var insight=b.readBoolean()?ANALYSIS_JSON.fromJson(b.readUtf(4096),ru.pasha.adaptationwheel.core.CombatKnowledge.Summary.class):ru.pasha.adaptationwheel.core.CombatKnowledge.Summary.EMPTY;
        return new AnalysisInfo(kind,confidence,reason,count,timings,insight,b.readUtf(64),b.readUtf(32));
    }
    private static final class ClientReceiver {
        static void accept(StatePacket p) { ClientWheelState.accept(p.entityId,p.enabled,p.equipped,p.nightVision,p.entries,p.revision,p.serverTick); }
        static void open() { AdaptationScreen.open(); }
        static void catalogue(CataloguePacket p) { ClientWheelState.acceptCatalogue(p.batch,p.index,p.count,p.entries); }
        static void skin(SkinPacket p) { ru.pasha.adaptationwheel.client.WheelSkinClient.accept(p.entityId,p.id); }
        static void voidText(VoidTextPacket p) { ru.pasha.adaptationwheel.client.ClientVoidText.accept(p.ticks,p.read); }
    }
}
