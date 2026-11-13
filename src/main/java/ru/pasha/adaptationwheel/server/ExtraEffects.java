package ru.pasha.adaptationwheel.server;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/** Exact effect identities whose native instant state mutations were inspected in the 616-JAR corpus. */
public final class ExtraEffects {
    private static final Set<String> SIMPLE_MNA_RESOURCES=Set.of(
            "com.mna.capabilities.playerdata.magic.resources.Mana",
            "com.mna.capabilities.playerdata.magic.resources.Souls",
            "com.mna.capabilities.playerdata.magic.resources.SummerFire",
            "com.mna.capabilities.playerdata.magic.resources.WinterIce",
            "com.mna.capabilities.playerdata.magic.resources.CouncilMana");
    private static final Map<String,AdaptationProfile> PROFILES=Map.of(
            "effect:mna:instant_chill",AdaptationProfile.COLD,
            // Mana Burn consumes the victim's casting resource before dealing magical damage;
            // the word "burn" in its name is not evidence of actual fire.
            "effect:mna:mana_burn",AdaptationProfile.MAGIC,
            "effect:garnished:freezing",AdaptationProfile.COLD,
            "effect:yungscavebiomes:frost",AdaptationProfile.COLD,
            // 4.45: harmful effects of the corpus whose id says nothing but whose name does.
            "effect:createaddition:shocking",AdaptationProfile.ELECTRIC,
            "effect:born_in_chaos_v1:jawattack",AdaptationProfile.PHYSICAL,
            "effect:born_in_chaos_v1:curseofthe_boat",AdaptationProfile.MAGIC,
            "effect:soulsweapons:disable_heal",AdaptationProfile.MAGIC,
            "effect:tconstruct:pierce",AdaptationProfile.PHYSICAL);
    private ExtraEffects(){}
    public static AdaptationProfile knownProfile(String key){return key==null?null:PROFILES.get(key);}
    public static AdaptationProfile knownProfile(MobEffect effect){
        return effect==null?null:knownProfile("effect:"+BuiltInRegistries.MOB_EFFECT.getKey(effect));
    }
    /** MNA uses amplifier itself, unlike the other audited effects' amplifier + 1. */
    public static boolean skipInstant(MobEffect effect,LivingEntity victim,int amplifier){
        var id=BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if(amplifier<=0 && id!=null && id.getNamespace().equals("mna")
                && (id.getPath().equals("instant_chill")||id.getPath().equals("mana_burn")))return false;
        if(id!=null && id.getNamespace().equals("mna")){
            if(id.getPath().equals("instant_chill")&&!victim.canFreeze())return false;
            if(id.getPath().equals("mana_burn")&&emptyOrdinaryMana(victim))return false;
        }
        // YUNG's native callback returns before changing cold in these two cases.
        // Preserve that no-op without teaching the wheel an exposure that did not occur.
        if(id!=null && id.getNamespace().equals("yungscavebiomes") && id.getPath().equals("frost")
                && victim instanceof ServerPlayer player
                && (player.isSpectator()||player.serverLevel().getDifficulty()==Difficulty.PEACEFUL))return false;
        return NegativeEffects.skipNativeAction(effect,victim);
    }
    /** Only proven public resource semantics establish a no-op; Brimstone can pay missing mana with HP. */
    private static boolean emptyOrdinaryMana(LivingEntity victim){
        if(!(victim instanceof ServerPlayer player))return false;
        try{
            Capability<?> capability=(Capability<?>)Class.forName("com.mna.capabilities.playerdata.magic.PlayerMagicProvider").getField("MAGIC").get(null);
            var magic=player.getCapability(capability).resolve();
            if(magic.isEmpty())return true; // Native Mana Burn itself uses ifPresent.
            Object resource=Class.forName("com.mna.api.capabilities.IPlayerMagic").getMethod("getCastingResource").invoke(magic.get());
            if(resource==null||!SIMPLE_MNA_RESOURCES.contains(resource.getClass().getName()))return false;
            Class<?> type=resource.getClass();String base="com.mna.api.capabilities.resource.SimpleCastingResource";
            if(!type.getMethod("consume",LivingEntity.class,float.class).getDeclaringClass().getName().equals(base)
                    ||!type.getMethod("setAmount",float.class).getDeclaringClass().getName().equals(base)
                    ||!type.getMethod("getAmount").getDeclaringClass().getName().equals(base))return false;
            float amount=((Number)Class.forName("com.mna.api.capabilities.resource.ICastingResource").getMethod("getAmount").invoke(resource)).floatValue();
            return Float.isFinite(amount)&&amount<=0;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException failure){
            // A changed/new resource is not assumed harmless from its field or method names.
            return false;
        }
    }
}
