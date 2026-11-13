package ru.pasha.adaptationwheel.server;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/** Separate opaque mod abilities by owner type instead of sharing universal generic/magic immunity. */
public final class ObservedAbilities {
    private static Supplier<String> caller=AutonomousHealthGuard::damageMod;
    private ObservedAbilities(){}
    /** Tests stand in for the mod on the call stack; null restores the real lookup. */
    public static void callerForTests(Supplier<String> stand){caller=stand==null?AutonomousHealthGuard::damageMod:stand;}
    public static DamageCategories.Exposure exposure(DamageSource source){
        if(DamageCategories.excluded(source.typeHolder()))return null;
        if(!source.is(DamageTypes.GENERIC)&&!source.is(DamageTypes.MAGIC)&&!source.is(DamageTypes.INDIRECT_MAGIC))return null;
        var type=source.typeHolder().unwrapKey().map(k->k.location()).orElse(null);if(type==null)return null;
        String key;
        if(source.getEntity() instanceof LivingEntity owner){
            // An identified JJC technique is already a distinct ability. Its earned
            // protection must not be replaced by an opaque fallback for the same hit.
            if(JujutsuCompatibility.exposures(source).stream().anyMatch(e->JujutsuCompatibility.techniqueKey(e.key())))return null;
            var entity=BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType());if(entity.getNamespace().equals("minecraft"))return null;
            key="attack:"+entity.getNamespace()+":observed/"+entity.getPath()+"/damage/"+type.getNamespace()+"/"+type.getPath();
        }else{
            // No culprit at all: the mod that called hurt() is the only thing that tells these hits apart.
            // Without this, a stage earned from one mod's anonymous damage would cover every other mod's.
            if(source.getEntity()!=null||source.getDirectEntity()!=null)return null;
            String mod=caller.get();if(mod==null)return null;
            key="attack:"+mod+":unowned/"+type.getNamespace()+"/"+type.getPath();
        }
        if(key.length()>256)return null;return new DamageCategories.Exposure(key,DamageCategories.profile(source),false);
    }
    public static Component name(String key){
        int unowned=key.indexOf(":unowned/");
        if(unowned>0)try{String mod=key.substring(7,unowned),type=key.substring(unowned+9);int slash=type.indexOf('/');if(slash<0)return null;
            return Component.translatable("analysis.adaptationwheel.unowned_ability",DamageCategories.displayName("damage:"+type.substring(0,slash)+":"+type.substring(slash+1)),modName(mod));
        }catch(RuntimeException ignored){return Component.literal(key);}
        int marker=key.indexOf(":observed/");if(marker<0)return null;
        try{String ns=key.substring(7,marker),path=key.substring(marker+10);int damage=path.indexOf("/damage/");if(damage<0)return null;
            String entity=ns+":"+path.substring(0,damage),type=path.substring(damage+8);int slash=type.indexOf('/');if(slash<0)return null;
            return Component.translatable("analysis.adaptationwheel.observed_ability",DamageCategories.displayName("mob:"+entity),DamageCategories.displayName("damage:"+type.substring(0,slash)+":"+type.substring(slash+1)));
        }catch(RuntimeException ignored){return Component.literal(key);}
    }
    private static String modName(String id){return ModList.get().getModContainerById(id).map(m->m.getModInfo().getDisplayName()).orElse(id);}
}
