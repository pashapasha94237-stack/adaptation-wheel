package ru.pasha.adaptationwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * Server triggers of the sense adaptations (murky water, lava sight, unseen foes). The effects are drawn by the
 * client ({@code client/SenseClient}); the battery adaptation is reported by the client and routed in MetaNetwork.
 */
public final class SenseAdaptations {
    /** Keys the wheel files lava under: the lava block itself and the burning it causes. */
    public static final String LAVA_KEY="damage:minecraft:lava",FIRE_KEY="damage:minecraft:on_fire";
    private SenseAdaptations(){}
    /** Lava that cannot hurt this wearer: Fire Resistance, or the wheel is fully adapted to lava and to burning. */
    public static boolean lavaIsHarmless(ServerPlayer p){
        var state=AdaptationEvents.state(p);
        return p.hasEffect(MobEffects.FIRE_RESISTANCE)||state.isImmune(LAVA_KEY)&&state.isImmune(FIRE_KEY);
    }
    /** Pure parser of the {@code adaptationwheel.fakeBattery} test property: a charge 0..100, or -1 when absent or invalid. */
    public static int fakeCharge(String property){
        if(property==null)return -1;
        try{var value=Double.parseDouble(property.trim());return Double.isNaN(value)?-1:(int)Math.round(Math.max(0,Math.min(100,value)));}
        catch(NumberFormatException invalid){return -1;}
    }
    /** Windows reports a battery that is not there as "not discharging", so only a real discharge off the mains counts. */
    public static boolean runsOnBattery(boolean powerOnLine,boolean discharging){return discharging&&!powerOnLine;}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=7||!p.isAlive()||p.isSpectator()||!AdaptationEvents.active(p))return;
        // Two fluid-tag reads a second per wearer; MetaServer.expose rate-limits and never accelerates.
        if(p.isEyeInFluid(FluidTags.WATER))MetaServer.expose(p,MetaMechanic.MURKY);
        else if(p.isEyeInFluid(FluidTags.LAVA)&&lavaIsHarmless(p))MetaServer.expose(p,MetaMechanic.LAVA_SIGHT);
    }
    /** Observed after the wheel's own immunity (which may cancel the hit): an invisible mob still found the wearer. */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void attacked(LivingAttackEvent e){
        if(e.getAmount()<=0||!(e.getEntity() instanceof ServerPlayer p)||!(e.getSource().getEntity() instanceof Mob mob)||!mob.isInvisibleTo(p))return;
        MetaServer.attempt(p,MetaMechanic.UNSEEN);
    }
}
