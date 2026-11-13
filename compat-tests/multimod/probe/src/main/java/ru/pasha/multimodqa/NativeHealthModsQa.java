package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Supplied content-mod callbacks hit the real vanilla health sink; never calls its guard. */
final class NativeHealthModsQa {
    private static final String MNA="com.mna.effects.harmful.EffectLacerate";
    private static final String BLOOD="com.aizistral.enigmaticlegacy.effects.GrowingBloodlustEffect";
    private static final String INFINITY="committee.nova.mods.avaritia.common.item.tools.infinity.InfinitySwordItem";
    @FunctionalInterface private interface Action { void run(ServerPlayer player) throws Exception; }
    private record Attack(String mod,String label,Action action,MobEffect effect) {}
    private NativeHealthModsQa() {}

    static void run(ServerPlayer p) throws Exception {
        String system=group.get("healthSystem").getAsString();
        boolean regen=p.serverLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,p.server);
        try {
            switch(system) {
                case "mna" -> source(p,effect("mna",MNA));
                case "enigmaticlegacy" -> {Attack blood=effect("enigmaticlegacy",BLOOD);source(p,blood);bloodFloor(p,blood);}
                case "avaritia" -> source(p,infinity(p));
                case "hexcasting" -> source(p,hex());
                case "trio" -> {
                    Attack mna=effect("mna",MNA),blood=effect("enigmaticlegacy",BLOOD),infinity=infinity(p);
                    source(p,mna);source(p,blood);bloodFloor(p,blood);source(p,infinity);isolation(p,mna,blood,infinity);
                }
                default -> throw new AssertionError("Unknown native health corpus system: "+system);
            }
            LogUtils.getLogger().info("MULTIMOD_QA CORPUS_HEALTH PASS: {} actual supplied native callbacks; real HP stage0/1/4, one receipt per hit, disable/unequip and unrelated source controls",system);
        } finally {
            reset(p);p.serverLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(regen,p.server);
        }
    }

    private static Attack effect(String mod,String type) {
        check(ModList.get().isLoaded(mod),"Native fixture mod missing: "+mod);
        List<MobEffect> found=new ArrayList<>();
        for(MobEffect candidate:ForgeRegistries.MOB_EFFECTS)if(candidate.getClass().getName().equals(type))found.add(candidate);
        check(found.size()==1,"Expected exactly one real registered "+type+", got "+found.size());
        MobEffect effect=found.get(0);int period=0;
        for(int n=1;n<=10000;n++)if(effect.isDurationEffectTick(n,0)){period=n;break;}
        check(period>0,"Native periodic callback has no executable amplifier-zero tick: "+type);
        int duration=period;
        // Each detached instance still follows the actual vanilla virtual dispatcher.
        // Not installing a harmful effect avoids tick-maintenance modifying the fixture;
        // every non-target effect protection is also disabled before measured calls.
        return new Attack(mod,ForgeRegistries.MOB_EFFECTS.getKey(effect).toString(),player->{
            NativeDamageEvent event=type.equals(MNA)?new NativeDamageEvent(player):null;
            if(event!=null)MinecraftForge.EVENT_BUS.register(event);
            try {
                new MobEffectInstance(effect,duration,0).tick(player,()->{});
                // Lacerate emits its native ENTITY_DAMAGE event after setHealth,
                // even when that one HP write is fully protected. This proves
                // the real callback ran instead of being skipped by effect immunity.
                if(event!=null)check(event.calls==1,"Native MNA callback did not emit its one post-setHealth damage game event");
            } finally {if(event!=null)MinecraftForge.EVENT_BUS.unregister(event);}
        },effect);
    }

    @SuppressWarnings("unchecked") private static Attack infinity(ServerPlayer p) throws Exception {
        check(ModList.get().isLoaded("avaritia"),"Native Re-Avaritia mod missing");
        Item sword=null;for(Item item:ForgeRegistries.ITEMS)if(item.getClass().getName().equals(INFINITY)){check(sword==null,"Ambiguous native InfinitySwordItem");sword=item;}
        check(sword!=null,"Registered Infinity Sword missing");
        Method hurt=sword.getClass().getMethod("hurt",LivingEntity.class,DamageSource.class,float.class);
        ResourceKey<DamageType> key=(ResourceKey<DamageType>)Class.forName("committee.nova.mods.avaritia.init.registry.ModDamageTypes").getField("INFINITY").get(null);
        DamageSource source=new DamageSource(p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
        Object nativeSword=sword;
        // This is the sword's real public HP-writing combat method. Use 4 HP to
        // measure fractional mitigation instead of the item's lethal 900+ attack.
        return new Attack("avaritia","InfinitySwordItem.hurt",player->{
            player.hurtTime=0;invoke(hurt,nativeSword,player,source,4F);
            check(player.hurtTime==10,"Native Infinity Sword callback did not execute its post-HP combat animation state");
        },null);
    }

    private static Attack hex() throws Exception {
        check(ModList.get().isLoaded("hexcasting"),"Native Hex Casting mod missing");
        Object companion=Class.forName("at.petrak.hexcasting.api.casting.mishaps.Mishap").getField("Companion").get(null);
        Method hurt=companion.getClass().getMethod("trulyHurt",LivingEntity.class,DamageSource.class,float.class);
        return new Attack("hexcasting","Mishap.Companion.trulyHurt fallback",player->{
            CancelHurt cancel=new CancelHurt(player);MinecraftForge.EVENT_BUS.register(cancel);
            try {invoke(hurt,companion,player,player.damageSources().generic(),4F);check(cancel.calls==1,"Hex fixture did not traverse the real canceled Forge hurt before fallback");}
            finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
        },null);
    }

    public static final class CancelHurt {
        private final ServerPlayer player;int calls;
        CancelHurt(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
        public void cancel(LivingAttackEvent event){if(event.getEntity()==player){calls++;event.setCanceled(true);}}
    }

    public static final class NativeDamageEvent {
        private final ServerPlayer player;int calls;
        NativeDamageEvent(ServerPlayer player){this.player=player;}
        @SubscribeEvent(receiveCanceled=true)
        public void observe(VanillaGameEvent event){
            if(event.getLevel()==player.serverLevel()&&event.getVanillaEvent()==GameEvent.ENTITY_DAMAGE)calls++;
        }
    }

    private static void invoke(Method method,Object target,Object...args) throws Exception {
        try {method.invoke(target,args);}catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw new RuntimeException(cause);
        }
    }
    private static List<String> keys(ServerPlayer p,String mod) throws Exception {
        List<String> result=new ArrayList<>();for(Object row:(List<?>)getter(state(p),"snapshots")) {
            String key=getter(row,"key").toString();if(key.startsWith("attack:"+mod+":health_write/"))result.add(key);
        }return result;
    }
    private static String onlyKey(ServerPlayer p,Attack attack) throws Exception {
        List<String> keys=keys(p,attack.mod);check(keys.size()==1,"Expected one discovered actual "+attack.label+" writer, got "+keys);return keys.get(0);
    }
    private static void enabled(ServerPlayer p,String key,boolean enabled) throws Exception {
        state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,enabled);
    }
    private static void disableOther(ServerPlayer p,String target) throws Exception {
        for(Object row:(List<?>)getter(state(p),"snapshots")) {
            String key=getter(row,"key").toString();if(!key.equals(target))enabled(p,key,false);
        }
    }
    private static float loss(ServerPlayer p,Attack attack) throws Exception {
        p.invulnerableTime=0;float before=p.getHealth();attack.action.run(p);return before-p.getHealth();
    }
    private static void heal(ServerPlayer p){p.setHealth(p.getMaxHealth());p.invulnerableTime=0;}

    private static void source(ServerPlayer p,Attack attack) throws Exception {
        reset(p);double attribute=p.getAttributeValue(Attributes.ATTACK_DAMAGE);boolean beneficial=attack.effect!=null&&attack.effect.getCategory()==MobEffectCategory.BENEFICIAL;
        if(beneficial) {
            check(p.addEffect(new MobEffectInstance(attack.effect,12000,0)),"Native beneficial effect was rejected");
            check(p.getAttributeValue(Attributes.ATTACK_DAMAGE)>attribute,"Native beneficial attack bonus was not actually applied");
            attribute=p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        float baseline=loss(p,attack);check(baseline>0&&p.isAlive(),attack.label+" native baseline did not remove real HP");wheelTick(p);
        String key=onlyKey(p,attack);check(stage(p,key)==0,attack.label+" got a free adaptation stage");
        Object first=required(p,key);double work=number(first,"work");int frozen=duration(first);
        check(number(first,"hits")==0&&number(first,"speed")==1,attack.label+" first native callback was counted twice");
        disableOther(p,key);check(loss(p,attack)>0,attack.label+" repeated native baseline lost no HP");
        check(duration(required(p,key))==frozen&&number(required(p,key),"work")==work,attack.label+" native receipt changed timer work before tick");
        wheelTick(p);Object second=required(p,key);
        check(number(second,"hits")==1&&Math.abs(number(second,"speed")-1.3)<1E-8&&Math.abs(number(second,"work")-work-1.3)<1E-8,attack.label+" native second callback did not produce exactly one x1.30 receipt");
        learn(p,key,1);disableOther(p,key);heal(p);float partial=loss(p,attack);
        check(Math.abs(partial-baseline*.8F)<.025F,attack.label+" native stage-one HP loss was not exactly 20% smaller: "+baseline+" -> "+partial);
        wheelTick(p);learn(p,key,4);disableOther(p,key);heal(p);float before=p.getHealth();
        check(loss(p,attack)==0&&p.getHealth()==before&&p.isAlive(),attack.label+" native full protection still lost real HP");
        if(beneficial)check(p.hasEffect(attack.effect)&&p.getAttributeValue(Attributes.ATTACK_DAMAGE)==attribute,
                "Blocking the actual bloodlust HP loss removed its real beneficial attack modifier");
        wheelTick(p);check(keys(p,attack.mod).equals(List.of(key)),attack.label+" repeat callbacks changed the native source identity");

        // A learned HP writer cannot make an unrelated native creature harmless.
        Zombie zombie=new Zombie(p.serverLevel());zombie.setNoAi(true);zombie.moveTo(p.getX()+3,p.getY(),p.getZ());p.serverLevel().addFreshEntity(zombie);
        try {
            disableOther(p,key);check(hit(p,p.damageSources().mobAttack(zombie),4)>0,attack.label+" incorrectly protected unrelated zombie melee");wheelTick(p);
            check(snapshot(p,"mob:minecraft:zombie")!=null,attack.label+" ordinary zombie receipt disappeared");
        } finally {zombie.discard();}

        disableOther(p,key);controlDisable(p,key);heal(p);
        check(Math.abs(loss(p,attack)-baseline)<.025F,attack.label+" category disable did not restore native HP loss");
        wheelTick(p);enabled(p,key,true);disableOther(p,key);heal(p);
        ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        try {check(Math.abs(loss(p,attack)-baseline)<.025F,attack.label+" immunity survived unequipping the wheel");}
        finally {p.setItemSlot(EquipmentSlot.HEAD,wheel);}
        wheelTick(p);double observations=number(required(p,key),"analysisObservations");float damaged=p.getHealth();
        p.heal(1F);check(p.getHealth()>damaged,"Ordinary healing was blocked by native HP-writer immunity");wheelTick(p);
        check(number(required(p,key),"analysisObservations")==observations,"Ordinary healing manufactured a native hostile receipt");
        LogUtils.getLogger().info("MULTIMOD_QA CORPUS_HEALTH_SOURCE PASS: {} key={} baseline={} partial={} stage4=0; exact x1.30; other protections disabled; zombie/heal/disable/unequip native",attack.label,key,baseline,partial);
        reset(p);
    }

    private static void bloodFloor(ServerPlayer p,Attack blood) throws Exception {
        reset(p);ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        try {p.setHealth(p.getMaxHealth()*.1F);}finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
        check(loss(p,blood)==0,"Native bloodlust did not honor its own low-health floor");wheelTick(p);
        check(keys(p,blood.mod).isEmpty(),"A native no-op below bloodlust's HP floor created a false hostile adaptation");
        LogUtils.getLogger().info("MULTIMOD_QA CORPUS_HEALTH_NOOP PASS: actual GrowingBloodlust below its native health-loss floor loses no HP and creates no health-write adaptation");reset(p);
    }

    private static void isolation(ServerPlayer p,Attack first,Attack second,Attack third) throws Exception {
        reset(p);check(loss(p,first)>0,"First cross-mod isolation baseline failed");wheelTick(p);String a=onlyKey(p,first);learn(p,a,4);disableOther(p,a);heal(p);
        check(loss(p,first)==0,"First native writer immunity failed before cross-mod control");
        check(loss(p,second)>0,"Learning MNA improperly blocked Enigmatic native HP writer");wheelTick(p);String b=onlyKey(p,second);
        check(loss(p,third)>0,"Learning MNA improperly blocked Re-Avaritia native HP writer");wheelTick(p);String c=onlyKey(p,third);
        check(new HashSet<>(List.of(a,b,c)).size()==3&&stage(p,b)==0&&stage(p,c)==0,"Native writers from three different content mods shared earned protection");
        LogUtils.getLogger().info("MULTIMOD_QA CORPUS_HEALTH_ISOLATION PASS: MNA stage4 leaves real Enigmatic/Re-Avaritia HP loss and three independent keys {}, {}, {}",a,b,c);reset(p);
    }
}
