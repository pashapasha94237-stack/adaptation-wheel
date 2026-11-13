package ru.pasha.adaptationwheel.server;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.MetaConfig;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * Travel: a Riptide trident that will not dash out of water and rain, and an Eye of Ender that shatters. The static
 * hooks are called from coremods/travel_adaptations.js. The trident ones run on both sides (the client moves its own
 * player, the server wears the trident down), so both must reach the same verdict; the eye ones run on the server.
 */
public final class TravelAdaptations {
    /** Ticks a trident stays wet after water or rain, from stage II. */
    public static final int WET_TICKS=100;
    /** Dash power on dry land at stage III, in percent; stage IV uses riptideDryPower. */
    public static final int STAGE3_POWER=50;
    /** Horizontal distance to the target below which stage I says "right here" instead of a number. */
    public static final double HERE=12;
    /** Stage IV hands the eye back only to a thrower this close to where it ends. */
    public static final double RETURN_RANGE=64;
    // Entity.equals is the entity id, which the client and server player share in single player: one map per side.
    // A respawned player takes the old id too, so a mark also names the object it was made for. The time is the
    // level's game time, which the server sends to the client, so a lagging server does not stretch its own window.
    private record Wet(int player,long time){}
    private static final Map<Player,Wet> WET_CLIENT=new WeakHashMap<>(),WET_SERVER=new WeakHashMap<>();
    private static final String OWNER="adaptationwheel_eye_owner";
    /** An eye about to drop for a stage IV owner: the item it drops goes to the owner instead (see {@link #join}). */
    private static EyeOfEnder returning;
    private static UUID returningTo;
    private TravelAdaptations(){}

    // ---- RIPTIDE ----------------------------------------------------------------------------------------------

    /** TridentItem.use asks "in water or rain?" for a Riptide trident: a refusal starts the adaptation and is explained. */
    public static boolean riptideUse(Player p){
        if(p.isInWaterOrRain())return true;
        int stage=MetaHooks.stage(p,MetaMechanic.RIPTIDE);
        if(stage>=3||stage==2&&damp(p))return true;
        if(p instanceof ServerPlayer server){
            MetaServer.attempt(server,MetaMechanic.RIPTIDE);
            if(stage>=1)server.displayClientMessage(Component.translatable(stage>=2?"meta.adaptationwheel.dry_riptide.dried":"meta.adaptationwheel.dry_riptide.need").withStyle(ChatFormatting.GOLD),true);
        }
        return false;
    }
    /** TridentItem.releaseUsing asks the same when the trident is let go. */
    public static boolean riptideRelease(Player p){
        if(p.isInWaterOrRain())return true;
        int stage=MetaHooks.stage(p,MetaMechanic.RIPTIDE);
        return stage>=3||stage==2&&damp(p);
    }
    /** TridentItem.releaseUsing: the dash itself (Player.push); out of water, rain, snow and the wet time it is weaker. */
    public static void riptidePush(Player p,double x,double y,double z){
        double k=power(p)/100D;
        p.push(x*k,y*k,z*k);
    }
    /** Dash power in percent: 100 where vanilla would dash, less on dry land at stages III and IV. */
    public static int power(Player p){
        if(p.isInWaterOrRain())return 100;
        int stage=MetaHooks.stage(p,MetaMechanic.RIPTIDE);
        if(stage<3||damp(p))return 100;
        return stage>=4?(MetaConfig.SERVER.isLoaded()?MetaConfig.RIPTIDE_DRY_POWER.get():75):STAGE3_POWER;
    }
    /** From stage II snowfall counts as rain, and the trident stays wet for a while after water or rain. */
    static boolean damp(Player p){
        var wet=(p.level().isClientSide?WET_CLIENT:WET_SERVER).get(p);
        // No lower bound: each time packet sets the client's clock back to the server's, so it can read a moment
        // before the mark was made.
        return wet!=null&&wet.player()==System.identityHashCode(p)&&p.level().getGameTime()-wet.time()<=WET_TICKS||snowing(p);
    }
    /** Like Entity.isInRain, for snow: at the feet or the top of the player, under open sky where it snows. */
    static boolean snowing(Player p){
        var level=p.level();if(!level.isRaining())return false;
        return snowsAt(level,p.blockPosition())||snowsAt(level,BlockPos.containing(p.getX(),p.getBoundingBox().maxY,p.getZ()));
    }
    private static boolean snowsAt(Level level,BlockPos pos){
        return level.canSeeSky(pos)&&level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,pos).getY()<=pos.getY()
            &&level.getBiome(pos).value().getPrecipitationAt(pos)==Biome.Precipitation.SNOW;
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var p=event.player;
        if(!p.isInWaterOrRain()||MetaHooks.stage(p,MetaMechanic.RIPTIDE)<2)return;
        var marks=p.level().isClientSide?WET_CLIENT:WET_SERVER;
        // put keeps an equal key that is already there: after a respawn that is the old object, and the GC would take the mark with it
        marks.remove(p);marks.put(p,new Wet(System.identityHashCode(p),p.level().getGameTime()));
    }
    /** The client builds a new player on every dimension change, which drops its mark; the server keeps its player, so it drops the mark itself. */
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event){WET_SERVER.remove(event.getEntity());}

    // ---- ENDER_EYE --------------------------------------------------------------------------------------------

    /** EnderEyeItem.use: replaces eye.signalTo(target). Remembers the thrower and, from stage I, says how far the target is. */
    public static void signal(EyeOfEnder eye,BlockPos target,Player p){
        eye.signalTo(target);
        if(!(p instanceof ServerPlayer server))return;
        try{
            eye.getPersistentData().putUUID(OWNER,server.getUUID());
            if(MetaHooks.stage(server,MetaMechanic.ENDER_EYE)<1)return;
            double dx=target.getX()+.5-server.getX(),dz=target.getZ()+.5-server.getZ();
            double distance=Math.sqrt(dx*dx+dz*dz);
            // Within 12 blocks vanilla's eye flies straight to the target and dives into the ground.
            server.displayClientMessage((distance<HERE?Component.translatable("meta.adaptationwheel.shattered_eye.here"):Component.translatable("meta.adaptationwheel.shattered_eye.distance",Math.round(distance/10)*10)).withStyle(ChatFormatting.AQUA),true);
        }catch(RuntimeException e){}
    }
    /**
     * EyeOfEnder.tick reads surviveAfterDeath once, when the eye ends: vanilla's roll breaks one eye in five. From stage
     * II the wearer's eye breaks half as often, from III never; at IV the item it drops goes back to the thrower.
     */
    public static boolean eyeSurvives(boolean survives,EyeOfEnder eye){
        try{
            returning=null;
            var owner=owner(eye);if(owner==null)return survives;
            int stage=MetaHooks.stage(owner,MetaMechanic.ENDER_EYE);
            if(!survives)survives=stage>=3||stage==2&&eye.level().getRandom().nextBoolean();
            if(!survives){MetaServer.attempt(owner,MetaMechanic.ENDER_EYE);return false;}
            if(stage>=4&&owner.isAlive()&&owner.level()==eye.level()&&owner.distanceToSqr(eye)<=RETURN_RANGE*RETURN_RANGE){returning=eye;returningTo=owner.getUUID();}
            return true;
        }catch(RuntimeException e){return survives;}
    }
    /** The thrower, when it is a player in the eye's own level (the eye lives four seconds, so that is where it is). */
    private static ServerPlayer owner(EyeOfEnder eye){
        var data=eye.getPersistentData();
        return data.hasUUID(OWNER)&&eye.level().getPlayerByUUID(data.getUUID(OWNER)) instanceof ServerPlayer p?p:null;
    }
    /** The item vanilla drops right after eyeSurvives said yes: at IV it is not added to the world but handed back. */
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void join(EntityJoinLevelEvent event){
        var eye=returning;if(eye==null||!(event.getEntity() instanceof ItemEntity item)||event.getLevel().isClientSide())return;
        returning=null;
        if(event.isCanceled())return; // another mod already took the drop: handing it back as well would duplicate it
        if(event.getLevel()!=eye.level()||!item.position().equals(eye.position())||!ItemStack.isSameItemSameTags(item.getItem(),eye.getItem()))return;
        var owner=eye.level().getPlayerByUUID(returningTo) instanceof ServerPlayer p?p:null;
        if(owner==null||!owner.isAlive()||owner.level()!=eye.level())return;
        event.setCanceled(true);
        give(owner,item.getItem().copy());
    }
    /** Into the main hand when it is empty, else wherever it fits (the stack in hand first); the rest lands at the feet. */
    private static void give(ServerPlayer p,ItemStack stack){
        var inventory=p.getInventory();
        if(inventory.getSelected().isEmpty()){inventory.setItem(inventory.selected,stack);stack=ItemStack.EMPTY;}
        else inventory.add(stack);
        if(!stack.isEmpty()){var drop=new ItemEntity(p.level(),p.getX(),p.getY(),p.getZ(),stack);drop.setNoPickUpDelay();p.level().addFreshEntity(drop);}
        p.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ITEM_PICKUP,SoundSource.PLAYERS,.3F,1.4F);
        p.displayClientMessage(Component.translatable("meta.adaptationwheel.shattered_eye.returned").withStyle(ChatFormatting.AQUA),true);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){returning=null;returningTo=null;WET_SERVER.clear();}
}
