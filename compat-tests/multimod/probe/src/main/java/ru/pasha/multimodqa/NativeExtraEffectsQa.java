package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/** Calls the installed mods' real instant APIs; asserts native cold/mana/HP state before maintenance. */
final class NativeExtraEffectsQa {
    private NativeExtraEffectsQa(){}
    static void run(ServerPlayer p)throws Exception{
        try{
            if(ModList.get().isLoaded("mna")){
                freeze(p,"mna:instant_chill",300);
                mana(p);
            }
            if(ModList.get().isLoaded("yungscavebiomes"))freeze(p,"yungscavebiomes:frost",400);
            if(ModList.get().isLoaded("garnished"))freeze(p,"garnished:freezing",800);
            reset(p);p.setHealth(p.getMaxHealth()-10);float hp=p.getHealth();
            MobEffects.HEAL.applyInstantenousEffect(p,p,p,0,1);
            check(p.getHealth()>hp && snapshot(p,"effect:minecraft:instant_health")==null,"Positive native instant heal was suppressed or created an adaptation");
            LogUtils.getLogger().info("MULTIMOD_QA EXTRA_EFFECTS PASS: native instant callbacks protect actual cold/mana/HP before writes; own-only immunity, disable, unequip and positive native controls");
        }finally{reset(p);}
    }
    private static MobEffect effect(String id){
        MobEffect effect=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(id));
        check(effect!=null,"Missing real registered effect "+id);return effect;
    }
    private static void instant(ServerPlayer p,MobEffect effect){p.invulnerableTime=0;effect.applyInstantenousEffect(p,p,p,1,1);}
    private static void enabled(ServerPlayer p,String key,boolean value)throws Exception{state(p).getClass().getMethod("setCategoryEnabled",String.class,boolean.class).invoke(state(p),key,value);}
    private static void isolate(ServerPlayer p,String key)throws Exception{
        for(Object row:(List<?>)getter(state(p),"snapshots")){
            String candidate=getter(row,"key").toString();if(!candidate.equals(key))enabled(p,candidate,false);
        }
    }
    private static void freeze(ServerPlayer p,String id,int expected)throws Exception{
        reset(p);MobEffect effect=effect(id);String key="effect:"+id;
        check(effect.getCategory()==MobEffectCategory.HARMFUL && effect.isInstantenous(),"Native freezing fixture is not a harmful instant effect: "+id);
        if(id.equals("mna:instant_chill")){
            effect.applyInstantenousEffect(p,p,p,0,1);wheelTick(p);
            check(p.getTicksFrozen()==0 && snapshot(p,key)==null,"MNA's harmless amplifier-zero instant chill created cold or a fake effect adaptation");
            ItemStack boots=p.getItemBySlot(EquipmentSlot.FEET).copy();p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.LEATHER_BOOTS));
            try{
                check(!p.canFreeze(),"Native leather boots fixture failed to prevent freezing");instant(p,effect);wheelTick(p);
                check(p.getTicksFrozen()==0 && snapshot(p,key)==null,"MNA's native leather-protected cold no-op created frozen ticks or a fake effect adaptation");
            }finally{p.setItemSlot(EquipmentSlot.FEET,boots);}
        }
        if(id.equals("yungscavebiomes:frost")){
            Difficulty originalDifficulty=p.serverLevel().getDifficulty();GameType originalMode=p.gameMode.getGameModeForPlayer();
            try{
                p.server.setDifficulty(Difficulty.PEACEFUL,true);instant(p,effect);wheelTick(p);
                check(p.getTicksFrozen()==0 && snapshot(p,key)==null,"YUNG's peaceful native no-op created cold or a fake effect adaptation");
                p.server.setDifficulty(originalDifficulty,true);p.setGameMode(GameType.SPECTATOR);instant(p,effect);wheelTick(p);
                check(p.getTicksFrozen()==0 && snapshot(p,key)==null,"YUNG's spectator native no-op created cold or a fake effect adaptation");
            }finally{p.server.setDifficulty(originalDifficulty,true);p.setGameMode(originalMode);}
        }
        instant(p,effect);int actual=p.getTicksFrozen();
        check(actual==expected,"Real instant callback did not mutate cold as specified: "+id+" "+actual+" expected "+expected);wheelTick(p);
        boolean discovered=snapshot(p,key)!=null;
        LogUtils.getLogger().info("MULTIMOD_QA EXTRA_EFFECT_BASELINE: {} real frozen={} automaticDiscovery={}",id,actual,discovered);
        learn(p,key,4);isolate(p,key);p.setTicksFrozen(7);instant(p,effect);
        check(p.getTicksFrozen()==7,"Fully adapted instant effect still changed actual cold BEFORE tick maintenance: "+id+" frozen="+p.getTicksFrozen());
        check(discovered,"Native instant effect failed to start its own ordinary adaptation automatically: "+id);
        Object info=required(p,key);check(getter(info,"profile").toString().equals("COLD"),"Audited native freezing effect was classified incorrectly: "+id+" "+getter(info,"profile"));
        p.setTicksFrozen(0);enabled(p,key,false);instant(p,effect);
        check(p.getTicksFrozen()==expected,"Disabled instant adaptation still blocked real cold: "+id);enabled(p,key,true);
        ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        try{p.setTicksFrozen(0);instant(p,effect);check(p.getTicksFrozen()==expected,"Instant cold protection remained active without the wheel: "+id);}
        finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
        LogUtils.getLogger().info("MULTIMOD_QA EXTRA_EFFECT_COLD PASS: {} actual freeze {} -> unchanged7 at stage4; disabled/unequip restore {}; profile COLD",id,actual,expected);
    }
    /** Uses MNA's public capability/resource contracts; no field inference or production adapter shortcut. */
    private static void mana(ServerPlayer p)throws Exception{
        reset(p);MobEffect burn=effect("mna:mana_burn"),restore=effect("mna:instant_mana");String key="effect:mna:mana_burn";
        Capability<?> capability=(Capability<?>)Class.forName("com.mna.capabilities.playerdata.magic.PlayerMagicProvider").getField("MAGIC").get(null);
        Object magic=p.getCapability(capability).orElseThrow(()->new AssertionError("Real MNA player magic capability is missing"));
        Object resource=Class.forName("com.mna.api.capabilities.IPlayerMagic").getMethod("getCastingResource").invoke(magic);
        Class<?> api=Class.forName("com.mna.api.capabilities.resource.ICastingResource");
        Method amount=api.getMethod("getAmount"),set=api.getMethod("setAmount",float.class),maximum=api.getMethod("getMaxAmount"),setMaximum=api.getMethod("setMaxAmount",float.class);
        float oldAmount=((Number)amount.invoke(resource)).floatValue(),oldMax=((Number)maximum.invoke(resource)).floatValue();
        try{
            setMaximum.invoke(resource,200F);set.invoke(resource,100F);float hp=health(p);
            p.invulnerableTime=0;burn.applyInstantenousEffect(p,p,p,0,1);wheelTick(p);
            check(((Number)amount.invoke(resource)).floatValue()==100F && health(p)==hp && snapshot(p,key)==null,"MNA's harmless amplifier-zero Mana Burn changed mana/HP or created a fake effect adaptation");
            check(resource.getClass().getName().equals("com.mna.capabilities.playerdata.magic.resources.Mana"),"Empty native mana fixture must use actual ordinary Mana, not an unknown HP-paying resource");
            set.invoke(resource,0F);hp=health(p);instant(p,burn);wheelTick(p);
            check(((Number)amount.invoke(resource)).floatValue()==0F && health(p)==hp && snapshot(p,key)==null,"MNA's empty ordinary Mana no-op changed mana/HP or created a fake effect adaptation");
            set.invoke(resource,100F);hp=health(p);
            instant(p,burn);
            float remaining=((Number)amount.invoke(resource)).floatValue(),loss=hp-health(p);
            check(Math.abs(remaining-75)<.001 && loss>0,"Real Mana Burn failed to consume25 native mana and damage HP: mana="+remaining+" HP loss="+loss);wheelTick(p);
            boolean discovered=snapshot(p,key)!=null;learn(p,key,4);isolate(p,key);set.invoke(resource,100F);p.setHealth(p.getMaxHealth());hp=health(p);instant(p,burn);
            check(((Number)amount.invoke(resource)).floatValue()==100F && health(p)==hp,"Full Mana Burn adaptation failed to protect native casting mana/HP BEFORE the consume callback");
            check(discovered,"Native Mana Burn did not automatically start its own ordinary adaptation");
            check(getter(required(p,key),"profile").toString().equals("MAGIC"),"Mana Burn was guessed as fire from its name instead of the audited MAGIC consequence");
            set.invoke(resource,50F);check((boolean)api.getMethod("artificialRestore").invoke(resource),"MNA fixture's native casting resource does not accept positive potion restoration");
            instant(p,restore);check(((Number)amount.invoke(resource)).floatValue()>50F && snapshot(p,"effect:mna:instant_mana")==null,"Real positive instant mana callback was suppressed or learned as a hostile effect");
            set.invoke(resource,100F);enabled(p,key,false);hp=health(p);instant(p,burn);
            check(((Number)amount.invoke(resource)).floatValue()==75F && health(p)<hp,"Disabled Mana Burn protection still suppressed native mana/HP damage");enabled(p,key,true);
            ItemStack wheel=p.getItemBySlot(EquipmentSlot.HEAD).copy();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            try{set.invoke(resource,100F);hp=health(p);instant(p,burn);check(((Number)amount.invoke(resource)).floatValue()==75F && health(p)<hp,"Unequipped Mana Burn protection still suppressed native mana/HP damage");}
            finally{p.setItemSlot(EquipmentSlot.HEAD,wheel);}
            LogUtils.getLogger().info("MULTIMOD_QA EXTRA_EFFECT_MANA PASS: native Mana Burn consumes25 mana and HP at stage0, neither changes at stage4; disabled/unequip restore both; real positive mana restore and MAGIC catalogue category");
        }finally{setMaximum.invoke(resource,oldMax);set.invoke(resource,oldAmount);}
    }
}
