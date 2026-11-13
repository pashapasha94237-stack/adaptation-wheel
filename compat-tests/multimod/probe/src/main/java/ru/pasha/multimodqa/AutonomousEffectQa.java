package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.*;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** New registered subclasses reach the real MobEffectInstance virtual dispatcher. */
final class AutonomousEffectQa {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"multimodqa");
    private static final RegistryObject<MobEffect> HARM=EFFECTS.register("autonomous_harmful_tick",Harmful::new);
    private static final RegistryObject<MobEffect> BENEFIT=EFFECTS.register("autonomous_beneficial_tick",Beneficial::new);
    private static final String HARM_KEY="effect:multimodqa:autonomous_harmful_tick",BENEFIT_KEY="effect:multimodqa:autonomous_beneficial_tick";
    private static int harmfulCalls,beneficialCalls;
    private AutonomousEffectQa(){}
    static void register(IEventBus bus){EFFECTS.register(bus);}
    private static final class Harmful extends MobEffect {
        Harmful(){super(MobEffectCategory.HARMFUL,0x773399);}
        @Override public boolean isDurationEffectTick(int duration,int amplifier){return true;}
        @Override public void applyEffectTick(LivingEntity victim,int amplifier){
            if(victim.level().isClientSide())return;
            harmfulCalls++;victim.invulnerableTime=0;victim.hurt(victim.damageSources().generic(),4);
        }
    }
    private static final class Beneficial extends MobEffect {
        Beneficial(){super(MobEffectCategory.BENEFICIAL,0x55cc99);}
        @Override public boolean isDurationEffectTick(int duration,int amplifier){return true;}
        @Override public void applyEffectTick(LivingEntity victim,int amplifier){
            if(victim.level().isClientSide())return;
            beneficialCalls++;victim.clearFire();victim.heal(2);
        }
    }
    private static void enabled(ServerPlayer p,String key,boolean value)throws Exception{state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,value);}
    static void run(ServerPlayer p)throws Exception{
        reset(p);harmfulCalls=beneficialCalls=0;
        try{
            new MobEffectInstance(HARM.get(),0,0).applyEffect(p);wheelTick(p);
            check(harmfulCalls==0&&snapshot(p,HARM_KEY)==null,"An expired native instance created a false exposure without running its callback");
            check(p.addEffect(new MobEffectInstance(HARM.get(),200,0)),"Unknown registered harmful effect was rejected before learning");
            MobEffectInstance harmful=p.getEffect(HARM.get());check(harmful!=null,"Real harmful instance is not installed on the player");
            float before=health(p);harmful.tick(p,()->{});
            check(harmfulCalls==1&&health(p)<before,"Unknown subclass's real stage-zero periodic callback failed to remove actual HP/limb health");wheelTick(p);
            check(snapshot(p,HARM_KEY)!=null&&stage(p,HARM_KEY)==0,"Harmful callback did not start its own ordinary adaptation, or earned a free stage");
            check(p.addEffect(new MobEffectInstance(BENEFIT.get(),200,0)),"Unknown beneficial effect was incorrectly rejected");
            MobEffectInstance beneficial=p.getEffect(BENEFIT.get());p.setRemainingFireTicks(80);beneficial.tick(p,()->{});
            check(beneficialCalls==1&&p.getRemainingFireTicks()==0,"Unknown positive callback was blocked instead of actually extinguishing fire");
            check(snapshot(p,BENEFIT_KEY)==null,"Beneficial subclass started an unwanted adaptation");
            learn(p,HARM_KEY,4);
            // A long adaptation can also advance its initial damage source. Disable
            // every other learned protection so a broken effect dispatcher cannot
            // masquerade as success by relying on generic-damage immunity.
            for(Object row:(java.util.List<?>)getter(state(p),"snapshots")){
                String key=getter(row,"key").toString();if(!key.equals(HARM_KEY))enabled(p,key,false);
            }
            before=health(p);int callbacks=harmfulCalls;
            // The instance was really added before learning. Drive its native
            // dispatcher before tick-start maintenance can remove it; this isolates
            // the callback gate from the already-tested application/purge gates.
            harmful.tick(p,()->{});
            check(harmfulCalls==callbacks&&health(p)==before,"Full adaptation did not suppress the actual unknown harmful virtual callback before its damage");
            p.setRemainingFireTicks(80);beneficial.tick(p,()->{});
            check(beneficialCalls==2&&p.getRemainingFireTicks()==0&&snapshot(p,BENEFIT_KEY)==null,"Harmful immunity suppressed the real positive callback or learned its category");
            enabled(p,HARM_KEY,false);before=health(p);harmful.tick(p,()->{});
            check(harmfulCalls==callbacks+1&&health(p)<before,"Disabling effect adaptation failed to restore the real harmful callback and actual HP loss");
            enabled(p,HARM_KEY,true);ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            try{
                before=health(p);harmful.tick(p,()->{});
                check(harmfulCalls==callbacks+2&&health(p)<before,"Unknown callback immunity remained active after unequipping the wheel");
            }finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_EFFECT_DISPATCH PASS: new registered harmful subclass through actual MobEffectInstance.tick loses HP at stage0, callback itself skipped at stage4, disable/unequip restore real damage; positive subclass still extinguishes fire and never starts adaptation");
        }finally{reset(p);}
        instant(p);
    }
    private static void drinkHarm(ServerPlayer p){
        p.invulnerableTime=0;ItemStack potion=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.HARMING);
        potion.finishUsingItem(p.serverLevel(),p);
    }
    public static final class CancelInstant {
        private final ServerPlayer player;
        CancelInstant(ServerPlayer p){player=p;}
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public void cancel(LivingAttackEvent event){if(event.getEntity()==player)event.setCanceled(true);}
    }
    private static void instant(ServerPlayer p)throws Exception{
        String key="effect:minecraft:instant_damage";reset(p);
        try{
            CancelInstant cancel=new CancelInstant(p);MinecraftForge.EVENT_BUS.register(cancel);
            float before=health(p);
            try{drinkHarm(p);}finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
            wheelTick(p);check(health(p)==before&&snapshot(p,key)==null,"A canceled first native instant potion created a fake adaptation");
            before=health(p);drinkHarm(p);float baseline=before-health(p);check(baseline>0,"Native PotionItem instant damage failed");wheelTick(p);
            Object row=required(p,key);check(number(row,"hits")==0&&number(row,"speed")==1,"One native instant potion was counted twice or started at x1.30");
            drinkHarm(p);wheelTick(p);row=required(p,key);check(number(row,"hits")==1&&Math.abs(number(row,"speed")-1.3)<1e-8,"Second native instant potion did not add exactly one x0.30 acceleration");
            learn(p,key,1);before=health(p);drinkHarm(p);float partial=before-health(p);check(Math.abs(partial-baseline*.8)<.025,"Native instant potion was double-mitigated: "+baseline+" -> "+partial);
            learn(p,key,4);before=health(p);drinkHarm(p);check(health(p)==before,"Fully adapted native instant potion still changed actual HP");
            enabled(p,key,false);before=health(p);drinkHarm(p);check(health(p)<before,"Disabled instant protection remained active");
            LogUtils.getLogger().info("MULTIMOD_QA AUTONOMOUS_INSTANT PASS: actual PotionItem canceled-first no fake entry; first shot x1.00, second x1.30, exactly20% real HP reduction, full block and disable native");
        }finally{reset(p);}
    }
}
