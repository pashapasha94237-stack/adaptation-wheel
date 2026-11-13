package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.*;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/**
 * Riptide out of water and Eyes of Ender that shatter. The trident goes through the real TridentItem.use/releaseUsing
 * and the eye through the real EyeOfEnder.tick, both patched by coremods/travel_adaptations.js. EnderEyeItem.use itself
 * needs a stronghold, and the GameTest world generates no structures, so the throw calls the same hook directly.
 */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelGameTests {
    /** Vanilla dash of a Riptide I trident: 3 * (1 + 1) / 4. */
    private static final double DASH=1.5;

    // ---- RIPTIDE ----
    @GameTest(template="empty",timeoutTicks=60)
    public static void dryRiptideRefusesAndExplainsPerStage(GameTestHelper h) {
        var plain=player(h,null);
        check(h,!plain.isInWaterOrRain(),"The test player stands in water or rain");
        check(h,use(plain)==InteractionResult.FAIL,"A Riptide trident started on dry land at stage zero");
        check(h,AdaptationEvents.state(plain).pending().containsKey(MetaMechanic.RIPTIDE.key),"A refused dash did not start the adaptation");
        String[] said={null,"meta.adaptationwheel.dry_riptide.need","meta.adaptationwheel.dry_riptide.dried"};
        for(int stage=1;stage<=2;stage++){
            var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.RIPTIDE,stage);
            check(h,use(p)==InteractionResult.FAIL,"Stage "+stage+" already allowed a dash on dry land");
            check(h,said(sent,said[stage]),"Stage "+stage+" did not explain the refusal");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void riptideStaysWetForFiveSecondsFromStageTwo(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.RIPTIDE,2);
        wet(p,true);tick(p);wet(p,false);
        check(h,use(p)==InteractionResult.CONSUME,"Stage two did not keep the trident wet right after water");
        check(h,dash(p)>DASH-.01,"A dash while still wet was weaker than in water");
        // The window is game time (the same on both sides). Other tests' clocks read it too, so it is moved and put
        // back within this call, before the server ticks again.
        var time=h.getLevel().getServer().getWorldData().overworldData();long now=time.getGameTime();
        try{
            time.setGameTime(now+TravelAdaptations.WET_TICKS-1);
            check(h,use(p)==InteractionResult.CONSUME,"The trident dried before "+TravelAdaptations.WET_TICKS+" ticks");
            time.setGameTime(now+TravelAdaptations.WET_TICKS+1);
            check(h,use(p)==InteractionResult.FAIL,"The trident stayed wet after "+TravelAdaptations.WET_TICKS+" ticks");
            // The client's clock is set back by each time packet: a mark from "the future" still counts.
            time.setGameTime(now-20);
            check(h,use(p)==InteractionResult.CONSUME,"The trident dried when the clock was set back");
        }finally{time.setGameTime(now);}
        // The client builds a new player on a dimension change and so loses its mark; the server must drop it too.
        wet(p,true);tick(p);wet(p,false);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p,Level.OVERWORLD,Level.NETHER));
        check(h,use(p)==InteractionResult.FAIL,"The server kept the wet mark through a dimension change");
        // A respawned player is a new object with the old entity id (and so equal to the old one as a map key).
        var wetOld=player(h,null);learn(wetOld,MetaMechanic.RIPTIDE,2);wet(wetOld,true);tick(wetOld);
        var respawned=player(h,null);learn(respawned,MetaMechanic.RIPTIDE,2);respawned.setId(wetOld.getId());
        check(h,respawned.equals(wetOld),"The test did not give the respawned player the old id");
        check(h,use(respawned)==InteractionResult.FAIL,"A respawned player inherited the wet trident of the old one");
        // Its own mark must be keyed by the new object: an old key would be collected, and the mark with it.
        wet(respawned,true);tick(respawned);wet(respawned,false);
        check(h,use(respawned)==InteractionResult.CONSUME,"A respawned player's own wet trident did not count");
        Map<Player,?> marks=ObfuscationReflectionHelper.getPrivateValue(TravelAdaptations.class,null,"WET_SERVER");
        check(h,marks.keySet().stream().anyMatch(k->k==respawned),"The respawned player's wet mark is still keyed by the old object");
        var first=player(h,null);learn(first,MetaMechanic.RIPTIDE,1);
        wet(first,true);tick(first);wet(first,false);
        check(h,use(first)==InteractionResult.FAIL,"Stage one already kept the trident wet");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void riptideDashesOnDryLandFromStageThree(GameTestHelper h) {
        check(h,MetaConfig.SERVER.isLoaded(),"The server config is not loaded");
        double dry4=MetaConfig.RIPTIDE_DRY_POWER.get()/100D;
        var none=player(h,null);
        check(h,dash(none)==0,"Stage zero dashed on dry land");
        wet(none,true);
        check(h,Math.abs(dash(none)-DASH)<.01,"The vanilla dash in water changed: "+dash(none));
        var p3=player(h,null);learn(p3,MetaMechanic.RIPTIDE,3);
        check(h,use(p3)==InteractionResult.CONSUME,"Stage three did not start a dash on dry land");
        double d3=dash(p3);
        check(h,Math.abs(d3-DASH*TravelAdaptations.STAGE3_POWER/100)<.01,"Stage three dashed "+d3+" on dry land");
        var p4=player(h,null);learn(p4,MetaMechanic.RIPTIDE,4);
        double d4=dash(p4);
        check(h,Math.abs(d4-DASH*dry4)<.01,"Stage four dashed "+d4+" on dry land, wanted "+DASH*dry4);
        wet(p4,true);
        check(h,Math.abs(dash(p4)-DASH)<.01,"Stage four weakened the dash in water");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void riptideStopsWhenSwitchedOffOrUnworn(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.RIPTIDE,4);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.RIPTIDE.key,false);
        check(h,use(p)==InteractionResult.FAIL&&dash(p)==0,"A disabled adaptation still dashed on dry land");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.RIPTIDE.key,true);
        check(h,use(p)==InteractionResult.CONSUME,"Enabling the entry again did not restore the dash");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,use(p)==InteractionResult.FAIL&&dash(p)==0,"A removed wheel still dashed on dry land");
        h.succeed();
    }
    /** Weather is level-wide, so this runs in a batch of its own. */
    @GameTest(template="empty",timeoutTicks=60,batch="travel_snow")
    public static void snowfallCountsAsRainFromStageTwo(GameTestHelper h) {
        var level=h.getLevel();var p1=player(h,null);var p2=player(h,null);
        var at=new BlockPos(h.absolutePos(BlockPos.ZERO).getX(),level.getMaxBuildHeight()-12,h.absolutePos(BlockPos.ZERO).getZ());
        for(var p:List.of(p1,p2))p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
        learn(p1,MetaMechanic.RIPTIDE,1);learn(p2,MetaMechanic.RIPTIDE,2);
        var before=level.getBiome(at);
        var snowy=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        try{
            biome(level,at,snowy);level.setWeatherParameters(0,6000,true,false);level.setRainLevel(1);
            check(h,level.isRaining()&&level.getBiome(at).value().getPrecipitationAt(at)==Biome.Precipitation.SNOW,"The test could not make it snow");
            check(h,!p2.isInWaterOrRain(),"Vanilla counted snowfall as rain");
            check(h,use(p1)==InteractionResult.FAIL,"Stage one dashed in snowfall");
            check(h,use(p2)==InteractionResult.CONSUME,"Stage two did not count snowfall as rain");
            check(h,Math.abs(dash(p2)-DASH)<.01,"A dash in snowfall was weaker than in rain");
            level.setWeatherParameters(6000,0,false,false);level.setRainLevel(0);
            check(h,use(p2)==InteractionResult.FAIL,"Stage two dashed in snow without snowfall");
        }finally{
            level.setWeatherParameters(20000000,20000000,false,false);level.setRainLevel(0);biome(level,at,before);
        }
        h.succeed();
    }

    // ---- ENDER_EYE ----
    @GameTest(template="empty",timeoutTicks=60)
    public static void eyeTellsTheDistanceFromStageOne(GameTestHelper h) {
        var plainSent=new ArrayList<Packet<?>>();var plain=player(h,plainSent);
        var eye=eye(h,plain,plain.blockPosition().offset(300,0,400));
        check(h,plainSent.stream().noneMatch(TravelGameTests::overlay),"Stage zero showed a distance");
        check(h,eye.getPersistentData().hasUUID("adaptationwheel_eye_owner"),"The eye does not know who threw it");
        var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.ENDER_EYE,1);
        eye(h,p,p.blockPosition().offset(300,0,400));
        check(h,said(sent,"meta.adaptationwheel.shattered_eye.distance","500"),"Stage one did not say 500 blocks");
        sent.clear();eye(h,p,p.blockPosition().offset(3,0,4));
        check(h,said(sent,"meta.adaptationwheel.shattered_eye.here"),"Stage one did not say the stronghold is right here");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void eyeBreaksAndCountsAnAttemptUntilStageThree(GameTestHelper h) {
        var p=joined(h,0);
        try{
            var end=end(h,p,false);
            check(h,drops(h,end)==0,"A breaking eye dropped an item");
            check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.ENDER_EYE.key),"A broken eye did not start the adaptation");
            end=end(h,p,true);
            check(h,drops(h,end)==1,"An eye vanilla let survive did not drop");
        }finally{leave(h,p);}
        var two=joined(h,2);
        try{
            int kept=0;for(int i=0;i<60;i++)if(drops(h,end(h,two,false))==1)kept++;
            check(h,kept>=15&&kept<=45,"Stage two kept "+kept+" of 60 eyes vanilla broke, wanted about half");
        }finally{leave(h,two);}
        var three=joined(h,3);
        try{
            check(h,drops(h,end(h,three,false))==1,"Stage three let an eye break");
            check(h,three.getInventory().countItem(Items.ENDER_EYE)==0,"Stage three already handed the eye back");
        }finally{leave(h,three);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void eyeComesBackToTheHandAtStageFour(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.ENDER_EYE,4);place(h,p);h.getLevel().addNewPlayer(p);
        try{
            var end=end(h,p,false);
            check(h,drops(h,end)==0,"Stage four left the eye on the ground");
            check(h,p.getMainHandItem().is(Items.ENDER_EYE)&&p.getMainHandItem().getCount()==1,"The eye did not come back to the empty hand");
            check(h,said(sent,"meta.adaptationwheel.shattered_eye.returned"),"Stage four did not say the eye came back");
            // Another mod that takes the drop first (cancels its spawn) keeps it: the eye is not handed back as well.
            java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> take=e->{if(e.getEntity() instanceof ItemEntity item&&item.getItem().is(Items.ENDER_EYE))e.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,take);
            try{
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);end(h,p,false);
                check(h,p.getInventory().countItem(Items.ENDER_EYE)==0,"The eye came back although another mod had taken the drop");
            }finally{MinecraftForge.EVENT_BUS.unregister(take);}
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
            end=end(h,p,true);
            check(h,drops(h,end)==0&&p.getMainHandItem().is(Items.STONE)&&p.getInventory().countItem(Items.ENDER_EYE)==1,"With a full hand the eye did not go to the inventory");
            // Straight up, so the player stays in the loaded column.
            p.setPos(p.getX(),p.getY()+TravelAdaptations.RETURN_RANGE+16,p.getZ());
            end=end(h,p,false);
            check(h,drops(h,end)==1&&p.getInventory().countItem(Items.ENDER_EYE)==1,"An eye came back to a thrower out of range");
            place(h,p);
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.ENDER_EYE.key,false);
            end=end(h,p,false);
            check(h,drops(h,end)==0&&p.getInventory().countItem(Items.ENDER_EYE)==1,"A disabled adaptation still saved the eye");
            AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.ENDER_EYE.key,true);
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            end=end(h,p,false);
            check(h,drops(h,end)==0&&p.getInventory().countItem(Items.ENDER_EYE)==1,"A removed wheel still saved the eye");
        }finally{leave(h,p);}
        h.succeed();
    }

    // ---- helpers ----
    private static InteractionResult use(ServerPlayer p){
        p.setItemInHand(InteractionHand.MAIN_HAND,trident());
        return p.getMainHandItem().use(p.level(),p,InteractionHand.MAIN_HAND).getResult();
    }
    /** Lets a drawn Riptide trident go in mid-air, looking straight ahead; returns the speed it gave. */
    private static double dash(ServerPlayer p){
        var trident=trident();p.setItemInHand(InteractionHand.MAIN_HAND,trident);
        p.setYRot(0);p.setXRot(0);p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);
        trident.releaseUsing(p.level(),p,trident.getUseDuration()-10);
        double speed=p.getDeltaMovement().length();p.setDeltaMovement(Vec3.ZERO);return speed;
    }
    private static ItemStack trident(){var stack=new ItemStack(Items.TRIDENT);stack.enchant(Enchantments.RIPTIDE,1);return stack;}
    private static void wet(ServerPlayer p,boolean wet){ObfuscationReflectionHelper.setPrivateValue(Entity.class,p,wet,"f_19798_");}
    private static void biome(ServerLevel level,BlockPos at,Holder<Biome> biome){
        var sampler=level.getChunkSource().randomState().sampler();var center=new ChunkPos(at);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var chunk=level.getChunk(center.x+x,center.z+z);chunk.fillBiomesFromNoise((qx,qy,qz,s)->biome,sampler);}
    }
    /** Throws an eye from the player's position at {@code target} through the throw hook. */
    private static EyeOfEnder eye(GameTestHelper h,ServerPlayer p,BlockPos target){return eye(p,p.getX(),p.getY(0.5),p.getZ(),target);}
    private static EyeOfEnder eye(ServerPlayer p,double x,double y,double z,BlockPos target){
        var eye=new EyeOfEnder(p.level(),x,y,z);eye.setItem(new ItemStack(Items.ENDER_EYE));
        TravelAdaptations.signal(eye,target,p);return eye;
    }
    /**
     * An eye of {@code p} thrown in the test area at a point three blocks away, with vanilla's roll forced, ticked
     * through its four seconds; where it ended.
     */
    private static Vec3 end(GameTestHelper h,ServerPlayer p,boolean survives){
        var from=h.absolutePos(new BlockPos(1,2,1));
        var eye=eye(p,from.getX()+.5,from.getY()+1,from.getZ()+.5,from.offset(3,0,0));
        ObfuscationReflectionHelper.setPrivateValue(EyeOfEnder.class,eye,survives,"f_36954_");
        h.getLevel().addFreshEntity(eye);
        for(int i=0;i<90&&!eye.isRemoved();i++)eye.tick();
        if(!eye.isRemoved())throw new GameTestAssertException("The eye did not end in 90 ticks");
        return eye.position();
    }
    /** Ender eye items lying where an eye ended; they are removed. */
    private static int drops(GameTestHelper h,Vec3 at){
        int n=0;for(var item:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at,at).inflate(2),e->e.getItem().is(Items.ENDER_EYE))){n+=item.getItem().getCount();item.discard();}
        return n;
    }
    private static void place(GameTestHelper h,ServerPlayer p){var at=h.absolutePos(new BlockPos(1,2,1));p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);}
    private static ServerPlayer joined(GameTestHelper h,int stage){
        var p=player(h,null);if(stage>0)learn(p,MetaMechanic.ENDER_EYE,stage);place(h,p);h.getLevel().addNewPlayer(p);return p;
    }
    private static void leave(GameTestHelper h,ServerPlayer p){h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    private static boolean overlay(Packet<?> packet){return packet instanceof ClientboundSystemChatPacket chat&&chat.overlay();}
    private static boolean said(List<Packet<?>> sent,String key){return said(sent,key,null);}
    private static boolean said(List<Packet<?>> sent,String key,String firstArgument){
        for(var packet:sent)if(packet instanceof ClientboundSystemChatPacket chat&&chat.overlay()&&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().equals(key)
                &&(firstArgument==null||text.getArgs().length>0&&String.valueOf(text.getArgs()[0]).equals(firstArgument)))return true;
        return false;
    }
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    /** Packets for the player (system chat is what the action bar uses) go to {@code sent} when it is not null. */
    private static ServerPlayer player(GameTestHelper h,List<Packet<?>> sent){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TravelTest"));
        var connection=new Connection(PacketFlow.SERVERBOUND);
        p.connection=sent==null?new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p):new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p){
            @Override public void send(Packet<?> packet){sent.add(packet);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){sent.add(packet);}
        };
        for(int i=0;i<60;i++)p.tick();
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        if(sent!=null)sent.clear();
        return p;
    }
}
