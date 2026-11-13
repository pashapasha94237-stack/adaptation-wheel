package ru.pasha.adaptationwheel.server;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** This server's actual mod/data-pack registries, without creating or spawning entities. */
public final class AdaptationCatalogue {
    private AdaptationCatalogue() {}

    public static List<CatalogueEntry> entries(ServerPlayer player) {
        Map<String,CatalogueEntry> entries=new LinkedHashMap<>();
        for (EntityType<?> type:BuiltInRegistries.ENTITY_TYPE) {
            if (type==EntityType.PLAYER || type==EntityType.ARMOR_STAND || !DefaultAttributes.hasSupplier(type)) continue;
            String key=canonicalKey("mob:"+BuiltInRegistries.ENTITY_TYPE.getKey(type));
            put(entries,key,AdaptationProfile.forKey(key,isBossType(type)));
        }
        for (MobEffect effect:BuiltInRegistries.MOB_EFFECT) {
            if (DamageCategories.isHarmful(effect)) put(entries,"effect:"+BuiltInRegistries.MOB_EFFECT.getKey(effect),DamageCategories.effectProfile(effect));
        }
        Registry<DamageType> damageTypes=player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        damageTypes.holders().forEach(holder -> {
            if (!DamageCategories.excluded(holder)) {
                String original="damage:"+holder.key().location(),key=canonicalKey(original);
                // Aliases use the existing real adaptation's profile and key.
                put(entries,key,DamageCategories.profile(holder));
            }
        });
        put(entries,AdaptationProfile.COLD_KEY,AdaptationProfile.COLD);
        put(entries,AdaptationProfile.DARK_KEY,AdaptationProfile.DARKNESS);
        for(var m:ru.pasha.adaptationwheel.core.MetaMechanic.values())entries.put(m.key,new CatalogueEntry(m.key,m.nameKey(),m.id,AdaptationProfile.ENVIRONMENT,ru.pasha.adaptationwheel.core.AdaptationTimings.forSource(m.key,AdaptationProfile.ENVIRONMENT)));
        T2SCompatibility.addCatalogue(entries);
        JujutsuCompatibility.addCatalogue(entries);
        for(var entry:ModAttackCompatibility.catalogue())entries.put(entry.key(),entry);
        // Actual exposure can refine an entity's profile (e.g. changed boss health).
        // Its saved profile must beat catalogue guesses.
        for (var snapshot:AdaptationEvents.state(player).snapshots()) {
            if (allowed(snapshot.key(),damageTypes)){
                String key=canonicalKey(snapshot.key());put(entries,key,snapshot.category());
                var value=entries.get(key);entries.put(key,new CatalogueEntry(key,value.translationKey(),value.fallbackName(),snapshot.category(),snapshot.timings()));
            }
        }
        return entries.values().stream().sorted(Comparator.comparing(CatalogueEntry::key)).toList();
    }

    private static void put(Map<String,CatalogueEntry> entries,String key,AdaptationProfile profile) {
        entries.put(key,key.equals("mob:minecraft:zombie")
                ? new CatalogueEntry(key,"entity.adaptationwheel.zombie_family","All zombie variants",profile)
                : new CatalogueEntry(key,DamageCategories.translationKey(key),DamageCategories.fallbackName(key),profile));
    }

    public static String canonicalKey(String key) {
        return DamageCategories.canonicalKey(key);
    }

    @SuppressWarnings("unchecked")
    private static boolean isBossType(EntityType<?> type) {
        var attributes=DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>)type);
        return attributes!=null && attributes.hasAttribute(Attributes.MAX_HEALTH)
                && attributes.getValue(Attributes.MAX_HEALTH)>=100.0;
    }

    private static boolean allowed(String key,Registry<DamageType> damageTypes) {
        if (key.startsWith("effect:")) {
            ResourceLocation id=ResourceLocation.tryParse(key.substring(7));
            // Previously encountered entries for an absent mod remain visible.
            return id!=null && BuiltInRegistries.MOB_EFFECT.getOptional(id).map(DamageCategories::isHarmful).orElse(true);
        }
        if (key.startsWith("damage:")) {
            ResourceLocation id=ResourceLocation.tryParse(key.substring(7));
            if (id==null) return false;
            return damageTypes.getOptional(id).flatMap(damageTypes::getResourceKey)
                    .flatMap(damageTypes::getHolder).map(holder -> !DamageCategories.excluded(holder)).orElse(true);
        }
        return true;
    }
}

