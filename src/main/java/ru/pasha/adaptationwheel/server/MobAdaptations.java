package ru.pasha.adaptationwheel.server;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/** Adaptations to mobs: the enderman's gaze, creeper blasts, piglin gold, villager prices and being surrounded. */
public final class MobAdaptations {
    /** Persistent data of a merchant: game time of the last restock done by the wheel. */
    public static final String RESTOCK_KEY="adaptationwheel_trade_restock";
    public static final int RESTOCK_GAP=60*20;
    private static final double[] GAZE={0,48,24,8},BLAST_SAVED={0,.25,.5,.75,1},DEMAND_CUT={0,.25,.5,1,1},SPLASH_RANGE={0,1.5,2,2.5,3},SPLASH_SHARE={0,.25,.5,.75,1};
    private static final Map<ServerPlayer,Integer> PIGLIN_WARNED=new WeakHashMap<>();
    private static final Map<ServerPlayer,Marks> MARKS=new WeakHashMap<>();
    /** Mobs the wearer already struck or that the wheel already splashed in one tick: vanilla sweeping must not chain. */
    private static final class Marks{long tick=-1;final Set<Integer> ids=new HashSet<>();}
    /** One open trade of a player: the villager and, per offer, {demand the cut was computed at, cut applied}. */
    private static final class Session{
        final Villager villager;final MerchantOffers offers;final Map<MerchantOffer,int[]> cuts=new IdentityHashMap<>();
        Session(Villager villager,MerchantOffers offers){this.villager=villager;this.offers=offers;}
    }
    private static final Map<ServerPlayer,Session> TRADES=new WeakHashMap<>();
    private static boolean splashing;
    private MobAdaptations(){}

    // ---------- enderman: WheelItem.isEnderMask is what EnderMan.isLookingAtMe asks through ForgeHooks ----------
    private static boolean provoked(EnderMan e,Player p){return e.getLastHurtByMob()==p||e.isAngryAt(p);}
    /** True when this enderman does not take the wearer's look as a stare (distance by stage, never for a provoked one). */
    public static boolean isEnderMask(Player player,EnderMan enderman){
        int stage=MetaHooks.stage(player,MetaMechanic.ENDERMAN);
        if(stage<=0||provoked(enderman,player))return false;
        return stage>=4||enderman.distanceToSqr(player)>GAZE[stage]*GAZE[stage];
    }
    /** Targets of endermen and piglins are chosen on mob AI; the event is the one place that sees both. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent e){
        if(!(e.getNewTarget() instanceof ServerPlayer p)||p.level().isClientSide)return;
        var mob=e.getEntity();
        if(mob instanceof EnderMan em){if(!provoked(em,p))MetaServer.attempt(p,MetaMechanic.ENDERMAN);}
        else if(mob instanceof Piglin pg)piglin(pg,p);
    }

    // ---------- piglin: WheelItem.makesPiglinsNeutral is what PiglinAi.isWearingGold asks ----------
    public static boolean makesPiglinsNeutral(LivingEntity wearer){return wearer instanceof ServerPlayer p&&MetaHooks.stage(p,MetaMechanic.PIGLIN)>=4;}
    private static void piglin(Piglin pg,ServerPlayer p){
        // Already angry (hit, chest opened) is not about gold; a brute is not a Piglin and never reaches here.
        if(PiglinAi.isWearingGold(p)||pg.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(p.getUUID()::equals).isPresent())return;
        int stage=MetaHooks.stage(p,MetaMechanic.PIGLIN);
        MetaServer.attempt(p,MetaMechanic.PIGLIN);
        if(stage>=1&&stage<4&&p.tickCount-PIGLIN_WARNED.getOrDefault(p,-1000)>=100){
            PIGLIN_WARNED.put(p,p.tickCount);p.displayClientMessage(Component.translatable("meta.adaptationwheel.piglin_gold.angry"),true);
        }
    }

    // ---------- creeper: the list of blocks is cut before vanilla destroys them (and before it is sent to clients) ----------
    @SubscribeEvent public static void blast(ExplosionEvent.Detonate e){
        var ex=e.getExplosion();var blocks=e.getAffectedBlocks();
        if(!(e.getLevel() instanceof ServerLevel level)||!(ex.getDirectSourceEntity() instanceof Creeper)||!ex.interactsWithBlocks())return;
        // Explosion lists every ray position, air included; only real blocks are destroyed, so only they count and are cut.
        blocks.removeIf(pos->level.getBlockState(pos).isAir());
        if(blocks.isEmpty())return;
        var centre=ex.getPosition();int best=0;List<ServerPlayer> wearers=new ArrayList<>();
        for(var p:level.players())if(p.distanceToSqr(centre)<=256&&MetaServer.active(p,MetaMechanic.CREEPER)){
            wearers.add(p);best=Math.max(best,MetaHooks.stage(p,MetaMechanic.CREEPER));
        }
        for(var p:wearers)MetaServer.attempt(p,MetaMechanic.CREEPER);
        if(best<=0)return;
        int left=(int)Math.round(blocks.size()*(1-BLAST_SAVED[best]));
        Collections.shuffle(blocks,new Random(level.random.nextLong())); // which blocks survive is random
        while(blocks.size()>left)blocks.remove(blocks.size()-1);
    }

    // ---------- villager: prices are changed when the menu opens (Villager.startTrading already set the trader and the
    // reputation prices; sendMerchantOffers runs after the Open event) and vanilla resets them in stopTrading ----------
    /** The demand surcharge vanilla adds to the first price of an offer. */
    private static int surcharge(MerchantOffer o){return Math.max(0,Mth.floor((float)(o.getBaseCostA().getCount()*o.getDemand())*o.getPriceMultiplier()));}
    private static int cut(MerchantOffer o,int stage){return (int)Math.round(surcharge(o)*DEMAND_CUT[stage]);}
    @SubscribeEvent public static void trade(PlayerContainerEvent.Open e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getContainer() instanceof MerchantMenu menu))return;
        TRADES.remove(p);
        var offers=menu.getOffers();
        if(offers==null||offers.isEmpty())return;
        AbstractVillager merchant=null;
        for(var v:p.serverLevel().getEntitiesOfClass(AbstractVillager.class,p.getBoundingBox().inflate(16),a->a.getTradingPlayer()==p&&a.getOffers()==offers)){merchant=v;break;}
        if(merchant==null)return;
        int stage=MetaHooks.stage(p,MetaMechanic.TRADE);boolean trouble=false;
        for(var o:offers)trouble|=o.isOutOfStock()||surcharge(o)>0;
        if(trouble)MetaServer.attempt(p,MetaMechanic.TRADE);
        if(stage>=4)restock(p,merchant);
        // A wandering trader has no demand, and only Villager clears special prices when the trade ends.
        if(stage>=1&&merchant instanceof Villager v){
            var s=new Session(v,offers);
            for(var o:offers){int cut=cut(o,stage);if(cut>0)o.addToSpecialPriceDiff(-cut);s.cuts.put(o,new int[]{o.getDemand(),cut});}
            TRADES.put(p,s);
        }
    }
    @SubscribeEvent public static void tradeEnd(PlayerContainerEvent.Close e){if(e.getEntity() instanceof ServerPlayer p)TRADES.remove(p);}
    /** A vanilla restock while the menu is open changes the demand (updateDemand); the stored cut must follow it, or the price falls below base. */
    @SubscribeEvent public static void tradeRestocked(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||TRADES.isEmpty()||!(e.player instanceof ServerPlayer p)||p.tickCount%10!=0)return;
        var s=TRADES.get(p);
        if(s==null)return;
        if(!(p.containerMenu instanceof MerchantMenu menu)||menu.getOffers()!=s.offers||s.villager.getTradingPlayer()!=p){TRADES.remove(p);return;}
        int stage=MetaHooks.stage(p,MetaMechanic.TRADE);boolean changed=false;
        for(var en:s.cuts.entrySet()){
            var o=en.getKey();var r=en.getValue();
            if(o.getDemand()==r[0])continue;
            int cut=cut(o,stage);o.addToSpecialPriceDiff(r[1]-cut);r[0]=o.getDemand();r[1]=cut;changed=true;
        }
        // Villager.resendOffersToTradingPlayer already ran with the old prices; send them again after the correction.
        if(changed)p.sendMerchantOffers(menu.containerId,s.offers,s.villager.getVillagerData().getLevel(),s.villager.getVillagerXp(),s.villager.showProgressBar(),s.villager.canRestock());
    }
    private static void restock(ServerPlayer p,AbstractVillager merchant){
        long now=p.serverLevel().getGameTime(),last=merchant.getPersistentData().contains(RESTOCK_KEY)?merchant.getPersistentData().getLong(RESTOCK_KEY):Long.MIN_VALUE/2;
        if(now>=last&&now-last<RESTOCK_GAP)return;
        boolean any=false;
        for(var o:merchant.getOffers())if(o.isOutOfStock()){o.resetUses();any=true;}
        if(any){merchant.getPersistentData().putLong(RESTOCK_KEY,now);p.displayClientMessage(Component.translatable("meta.adaptationwheel.villager_trade.restocked"),true);}
    }

    // ---------- surrounded ----------
    /** Brain mobs (hoglins, zoglins) keep their target in ATTACK_TARGET and never call Mob.setTarget; isMemoryValue is safe for unregistered memories. */
    static boolean targets(Mob m,Player p){return m.getTarget()==p||m.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET,p);}
    @SubscribeEvent public static void surroundedCheck(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0||!p.isAlive()||!MetaServer.active(p,MetaMechanic.SURROUNDED)||MetaServer.full(p,MetaMechanic.SURROUNDED))return;
        // One bounded box query a second, and only until the adaptation is complete.
        var around=p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(8),m->m instanceof Enemy&&targets(m,p)&&m.distanceToSqr(p)<=64);
        if(around.size()>=4)MetaServer.expose(p,MetaMechanic.SURROUNDED);
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void struck(LivingHurtEvent e){
        var source=e.getSource();
        if(splashing||!(source.getDirectEntity() instanceof ServerPlayer p)||source.getEntity()!=p||!source.is(DamageTypes.PLAYER_ATTACK)||!(e.getEntity() instanceof Mob victim))return;
        int stage=MetaHooks.stage(p,MetaMechanic.SURROUNDED);
        if(stage<=0)return;
        float share=(float)(e.getAmount()*SPLASH_SHARE[stage]);
        if(share<=0)return;
        var level=p.serverLevel();var marks=MARKS.computeIfAbsent(p,x->new Marks());long now=level.getGameTime();
        if(marks.tick!=now){marks.tick=now;marks.ids.clear();}
        marks.ids.add(victim.getId());
        double r=SPLASH_RANGE[stage];
        var others=level.getEntitiesOfClass(Mob.class,victim.getBoundingBox().inflate(r,r/2,r),m->m!=victim&&m.isAlive()&&m instanceof Enemy&&targets(m,p)&&!marks.ids.contains(m.getId())&&!m.isAlliedTo(p)&&!(m instanceof OwnableEntity o&&o.getOwnerUUID()!=null));
        if(others.isEmpty())return;
        splashing=true;
        try{for(var m:others){marks.ids.add(m.getId());m.hurt(p.damageSources().playerAttack(p),share);}}
        finally{splashing=false;}
        level.playSound(null,victim.getX(),victim.getY(),victim.getZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1,1);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,victim.getX(),victim.getY(.5),victim.getZ(),0,0,0,0,0);
    }
}
