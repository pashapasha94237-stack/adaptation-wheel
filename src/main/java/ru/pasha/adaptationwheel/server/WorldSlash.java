package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.pasha.adaptationwheel.AdaptationConfig;
import ru.pasha.adaptationwheel.core.SlashPlane;
import ru.pasha.adaptationwheel.item.ExterminationSwordItem;

/**
 * Instant server-side cut along a huge diagonal plane: it passes through walls and hits every
 * creature the plane crosses. The visible tear travels outward over a few ticks; blocks are cut
 * only when the server option allows it. Infinity bypass exists only inside this attack's damage callback.
 */
public final class WorldSlash {
    public static final String MODE="AdaptationWorldSlash";
    public static final double LENGTH=64,SPAN=10,THICKNESS=3,TILT=35;
    public static final int COOLDOWN=45*20;
    /** 150 hearts. */
    public static final float DAMAGE=300;
    private static final int TRAVEL_TICKS=6,MAX_CUT_BLOCKS=4096;
    public static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("adaptationwheel","world_slash"));
    private static final ThreadLocal<UUID> ATTACKER=new ThreadLocal<>();
    private record Travel(ServerLevel level,ServerPlayer caster,SlashPlane plane,boolean cutBlocks,int[] cut,int[] tick){}
    private static final List<Travel> TRAVELLING=new ArrayList<>();
    private static final ParticleOptions TEAR=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.02F,0.0F,0.03F),3.0F);
    private WorldSlash() {}

    public static boolean unlocked(ServerPlayer player) {
        var state=AdaptationEvents.state(player);
        return AdaptationEvents.active(player)&&state.snapshots().stream()
            .anyMatch(entry->entry.boss()&&entry.key().startsWith("mob:")&&state.existenceAdapted(entry.key()));
    }
    public static boolean selected(ItemStack stack) {
        return stack.hasTag()&&stack.getTag().getBoolean(MODE);
    }
    public static void toggle(ServerPlayer player,ItemStack stack) {
        if(!selected(stack)&&!unlocked(player)) {
            player.displayClientMessage(Component.translatable("message.adaptationwheel.world_slash_locked"),true);
            return;
        }
        boolean selected=!selected(stack);
        stack.getOrCreateTag().putBoolean(MODE,selected);
        player.displayClientMessage(Component.translatable(selected?"message.adaptationwheel.world_slash_on":"message.adaptationwheel.world_slash_off"),true);
    }
    public static boolean bypassing(ServerPlayer player) {
        return player.getUUID().equals(ATTACKER.get())&&unlocked(player);
    }
    public static boolean isAttack(DamageSource source) {
        return source.is(TYPE)&&source.getEntity() instanceof ServerPlayer player&&player.getUUID().equals(ATTACKER.get());
    }
    /** Final HP damage, after armor, magic protection and absorption calculations. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fixedHealthDamage(LivingDamageEvent event) {
        if(!event.getEntity().level().isClientSide&&isAttack(event.getSource()))event.setAmount(DAMAGE);
    }
    public static SlashPlane plane(ServerPlayer player){
        var eye=player.getEyePosition();var look=player.getLookAngle();
        return SlashPlane.of(eye.x,eye.y,eye.z,look.x,look.y,look.z,TILT,LENGTH,SPAN,THICKNESS);
    }
    public static void cast(ServerPlayer player,ItemStack stack,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND||!player.getMainHandItem().equals(stack)
                ||!(stack.getItem() instanceof ExterminationSwordItem))return;
        if(!unlocked(player)) {
            player.displayClientMessage(Component.translatable("message.adaptationwheel.world_slash_locked"),true);
            return;
        }
        if(player.getCooldowns().isOnCooldown(stack.getItem()))return;
        player.getCooldowns().addCooldown(stack.getItem(),COOLDOWN);
        var level=player.serverLevel();SlashPlane plane=plane(player);
        int hit=cut(player,level,plane);
        boolean blocks=AdaptationConfig.SPEC.isLoaded()&&AdaptationConfig.WORLD_SLASH_CUTS_BLOCKS.get();
        travel(player,plane,blocks);
        level.playSound(null,player.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,3.0F,0.5F);
        level.playSound(null,player.blockPosition(),SoundEvents.WARDEN_SONIC_BOOM,SoundSource.PLAYERS,1.2F,1.7F);
        player.swing(hand,true);
        stack.hurtAndBreak(3,player,p->p.broadcastBreakEvent(hand));
        if(hit>0)player.displayClientMessage(Component.translatable("message.adaptationwheel.world_slash_hit",hit),true);
        WheelAdvancements.award(player,WheelAdvancements.WORLD_SLASH);
    }
    /** Damages every creature the plane crosses, through walls. Returns how many were cut. */
    public static int cut(ServerPlayer player,ServerLevel level,SlashPlane plane){
        double[] r=plane.bounds();
        Set<LivingEntity> targets=new LinkedHashSet<>();
        for(Entity e:level.getEntities(player,new AABB(r[0],r[1],r[2],r[3],r[4],r[5]),e->e.isAlive()&&!e.isSpectator())){
            var box=e.getBoundingBox();
            if(!plane.hits((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2,box.getXsize()/2,box.getYsize()/2,box.getZsize()/2))continue;
            if(e instanceof EnderDragonPart part)targets.add(part.parentMob);
            else if(e instanceof LivingEntity living)targets.add(living);
        }
        int hit=0;
        UUID previous=ATTACKER.get();ATTACKER.set(player.getUUID());
        try {
            for(LivingEntity target:targets) {
                if(target==player||!target.isAlive())continue;
                // Keep vanilla PvP/team checks and spare the caster's own pets. Never force health or remove the opponent's Infinity.
                if(target instanceof net.minecraft.world.entity.player.Player other&&!player.canHarmPlayer(other))continue;
                if(target instanceof OwnableEntity pet&&player.getUUID().equals(pet.getOwnerUUID()))continue;
                var source=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),player);
                // Absorption otherwise returns before Forge's final health-damage callback.
                // Temporarily exclude its buffer from this cut and retain any newly granted hearts.
                float absorption=target.getAbsorptionAmount();
                target.setAbsorptionAmount(0);
                try {
                    boolean damaged=target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                        ?dragon.hurt(dragon.head,source,DAMAGE):target.hurt(source,DAMAGE);
                    if(damaged)hit++;
                } finally { target.setAbsorptionAmount(target.getAbsorptionAmount()+absorption); }
            }
        } finally {
            if(previous==null)ATTACKER.remove();else ATTACKER.set(previous);
        }
        return hit;
    }
    /** Starts the visible tear (and, if allowed, the block cut) along the plane. */
    public static void travel(ServerPlayer player,SlashPlane plane,boolean cutBlocks){
        TRAVELLING.add(new Travel(player.serverLevel(),player,plane,cutBlocks,new int[1],new int[1]));
    }
    /** The visible tear runs from the blade to the far end over {@link #TRAVEL_TICKS} ticks. */
    @SubscribeEvent public static void travel(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||TRAVELLING.isEmpty())return;
        for(Iterator<Travel> it=TRAVELLING.iterator();it.hasNext();){
            Travel t=it.next();int step=t.tick[0]++;
            if(step>=TRAVEL_TICKS||!t.level.getServer().isRunning()){it.remove();continue;}
            double from=LENGTH*step/TRAVEL_TICKS,to=LENGTH*(step+1)/TRAVEL_TICKS;
            List<ServerPlayer> viewers=new ArrayList<>();
            for(ServerPlayer p:t.level.players())if(p.distanceToSqr(t.plane.ox,t.plane.oy,t.plane.oz)<192*192)viewers.add(p);
            ServerPlayer caster=t.caster.isRemoved()?null:t.caster;
            // Half-block steps keep the cut through blocks continuous; particles use every other step and
            // nothing is drawn in the first blocks, where particles at the eye would only blind the caster.
            int row=(int)Math.round(Math.max(1,from)*2);
            for(double d=row/2.0;d<to;d+=.5,row++){
                boolean draw=d>=4&&row%2==0;
                int n=0;
                for(double s=-SPAN;s<=SPAN;s+=.35,n++){
                    double[] p=t.plane.point(d,s);
                    BlockPos pos=BlockPos.containing(p[0],p[1],p[2]);BlockState state=t.level.getBlockState(pos);
                    boolean solid=!state.isAir();
                    if(solid&&t.cutBlocks&&caster!=null&&t.cut[0]<MAX_CUT_BLOCKS&&cuttable(t.level,pos,state,caster)){
                        t.level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);t.cut[0]++;
                    }
                    if(!draw)continue;
                    // The tear itself: a thin black line of dust along the plane, ink here and there, a white sweep at the blade's edges.
                    send(viewers,TEAR,p,0,0,0,1,0);
                    if(n%8==0&&d>=8)send(viewers,ParticleTypes.SQUID_INK,p,0,0,0,1,0);
                    if(Math.abs(s)>SPAN-.5||n%15==0)send(viewers,ParticleTypes.SWEEP_ATTACK,p,0,0,0,1,0);
                    if(solid&&n%5==0)send(viewers,ParticleTypes.CAMPFIRE_COSY_SMOKE,p,0,1,0,0,.07);
                }
            }
        }
    }
    private static boolean cuttable(ServerLevel level,BlockPos pos,BlockState state,ServerPlayer caster){
        if(state.getDestroySpeed(level,pos)<0||state.hasBlockEntity()||!state.getFluidState().isEmpty())return false;
        if(!level.mayInteract(caster,pos))return false;
        // Claim/protection mods veto block breaking through this event.
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,caster));
    }
    /** Forced particles so the whole length is visible, not only the vanilla 32 blocks; count 0 makes (dx,dy,dz)*speed a velocity. */
    private static void send(List<ServerPlayer> viewers,ParticleOptions type,double[] p,double dx,double dy,double dz,int count,double speed){
        for(ServerPlayer v:viewers)v.serverLevel().sendParticles(v,type,true,p[0],p[1],p[2],count,dx,dy,dz,speed);
    }
}
