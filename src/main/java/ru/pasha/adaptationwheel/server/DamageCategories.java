package ru.pasha.adaptationwheel.server;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import ru.pasha.adaptationwheel.core.AbilityEntities;
import ru.pasha.adaptationwheel.core.AdaptationAnalysis;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local registry/tag/name analysis. No model, external requests or generated rules. */
public final class DamageCategories {
    private static final Set<String> BASIC_MELEE=Set.of("minecraft:mob_attack", "minecraft:mob_attack_no_aggro", "minecraft:player_attack");
    private static final AdaptationProfile[] PROFILES=AdaptationProfile.values();
    private static final java.util.Map<AdaptationProfile,TagKey<DamageType>> PROFILE_TAGS=createProfileTags();
    private static final Set<net.minecraft.world.entity.ai.attributes.Attribute> HELPFUL_ATTRIBUTES=Set.of(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED,
            net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
            net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,net.minecraft.world.entity.ai.attributes.Attributes.LUCK,
            net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK);
    private DamageCategories() {}

    public record Exposure(String key, AdaptationProfile profile, boolean boss) {}

    /** A source can teach both its attacker species and its distinct attack mechanism. */
    public static List<Exposure> analyze(DamageSource source) {
        return analyze(source,null);
    }

    /** The victim is never its own attacker: a blast of one's own TNT teaches the blast, not "player". */
    public static List<Exposure> analyze(DamageSource source,LivingEntity victim) {
        if (source==null || excluded(source.typeHolder())) return List.of();
        String primary=from(source,victim);
        if (primary==null) return List.of();
        primary=exposureKey(primary,source);
        boolean attacker=primary.startsWith("mob:");
        boolean isBoss=attacker && boss(source);
        List<Exposure> result=new ArrayList<>(2);
        result.add(new Exposure(primary,attacker ? AdaptationProfile.forKey(primary,isBoss) : exposureProfile(primary,source),isBoss));
        String type=typeId(source.typeHolder());
        if (attacker && type!=null && !BASIC_MELEE.contains(type)) {
            String key=exposureKey("damage:"+type,source);
            if (!key.equals(primary)) result.add(new Exposure(key,exposureProfile(key,source),false));
        }
        return List.copyOf(result);
    }

    public static String canonicalKey(String key) {
        return switch(key) {
            case "damage:minecraft:starve" -> AdaptationProfile.HUNGER_KEY;
            case "damage:minecraft:freeze" -> AdaptationProfile.COLD_KEY;
            case "damage:minecraft:magic" -> "effect:minecraft:instant_damage";
            case "damage:minecraft:wither" -> "effect:minecraft:wither";
            // Vanilla calls every blast with a known culprit (ghast, creeper, wither, lit TNT) a "player explosion".
            case "damage:minecraft:player_explosion" -> "damage:minecraft:explosion";
            case "damage:minecraft:unattributed_fireball" -> "damage:minecraft:fireball";
            case "mob:minecraft:husk", "mob:minecraft:drowned", "mob:minecraft:zombie_villager",
                    "mob:minecraft:zombified_piglin" -> "mob:minecraft:zombie";
            default -> key;
        };
    }

    /** Indirect magic is a potion only when a potion delivered it; a guardian's beam and evoker fangs share the type. */
    private static String exposureKey(String key,DamageSource source) {
        boolean potion=source.getDirectEntity() instanceof ThrownPotion || source.getDirectEntity() instanceof AreaEffectCloud;
        return potion && key.equals("damage:minecraft:indirect_magic") ? "effect:minecraft:instant_damage" : canonicalKey(key);
    }

    private static AdaptationProfile exposureProfile(String key,DamageSource source) {
        AdaptationProfile explicit=explicitProfile(source.typeHolder());
        if(explicit!=null) return explicit;
        if(key.startsWith("effect:")) {
            ResourceLocation id=ResourceLocation.tryParse(key.substring(7));
            if(id!=null) return BuiltInRegistries.MOB_EFFECT.getOptional(id).map(DamageCategories::effectProfile)
                    .orElse(AdaptationProfile.forKey(key,false));
        }
        return key.equals(AdaptationProfile.COLD_KEY) ? AdaptationProfile.COLD : profile(source);
    }

    /** Null means this source must always retain its vanilla damage behavior. */
    public static String from(DamageSource source) {
        return from(source,null);
    }

    public static String from(DamageSource source,LivingEntity victim) {
        if (source==null || excluded(source.typeHolder())) return null;
        if (source.getEntity() instanceof LivingEntity attacker && attacker!=victim) {
            return forEntity(attacker);
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker!=victim) {
            return forEntity(attacker);
        }
        // Vanilla dragon breath clouds lose their original attacker attribution.
        if (source.is(DamageTypes.DRAGON_BREATH)) return "mob:minecraft:ender_dragon";
        return source.typeHolder().unwrapKey()
                .map(key -> "damage:" + key.location())
                .orElseGet(() -> "damage:"+typeId(source.typeHolder()));
    }
    /** Direct holders have no registry id; their message id provides a stable fallback. */
    private static String typeId(Holder<DamageType> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElseGet(() -> {
            String message=holder.value().msgId();
            ResourceLocation named=message.contains(":") ? ResourceLocation.tryParse(message) : null;
            if(named!=null && named.toString().length()<=240) return named.toString();
            String path=message.replaceAll("([a-z0-9])([A-Z])","$1_$2")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]","_");
            if(path.isBlank()) path="unknown";
            if(path.length()>160) path=path.substring(0,140)+"_"+Integer.toUnsignedString(message.hashCode(),16);
            return "adaptationwheel:unregistered/"+path;
        });
    }

    public static boolean excluded(Holder<DamageType> type) {
        if(type.unwrapKey().map(k->k.location().toString().equals("jujutsucraft:start_animation")).orElse(false))return true;
        return type.is(DamageTypes.FELL_OUT_OF_WORLD) || type.is(DamageTypes.GENERIC_KILL)
                || type.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static AdaptationProfile profile(DamageSource source) {
        AdaptationProfile profile=profile(source.typeHolder());
        return profile==AdaptationProfile.ENVIRONMENT && explicitProfile(source.typeHolder())==null
                && source.getDirectEntity() instanceof Projectile ? AdaptationProfile.PROJECTILE : profile;
    }

    /** Datapacks can override any profile at adaptationwheel:adaptation/<profile>. */
    public static TagKey<DamageType> profileTag(AdaptationProfile profile) {
        return PROFILE_TAGS.get(profile);
    }
    private static java.util.Map<AdaptationProfile,TagKey<DamageType>> createProfileTags(){
        var tags=new java.util.EnumMap<AdaptationProfile,TagKey<DamageType>>(AdaptationProfile.class);
        for(var profile:PROFILES)tags.put(profile,TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("adaptationwheel","adaptation/"+profile.name().toLowerCase(Locale.ROOT))));
        return java.util.Collections.unmodifiableMap(tags);
    }

    private static AdaptationProfile explicitProfile(Holder<DamageType> type) {
        // Conflicting override tags resolve deterministically in enum declaration order.
        for(AdaptationProfile profile:PROFILES) if(type.is(profileTag(profile))) return profile;
        return null;
    }

    /** Explicit tags win, then specific vanilla tags, names and the broad projectile tag. */
    public static AdaptationProfile profile(Holder<DamageType> type) {
        AdaptationProfile explicit=explicitProfile(type);
        return explicit!=null?explicit:profileWithoutTable(type);
    }
    /** The compatibility table entry (adaptationwheel:adaptation/<profile> tag) of a damage type, or null. */
    public static AdaptationProfile tableProfile(Holder<DamageType> type) {return explicitProfile(type);}
    /** What the analyzer reads from a damage type when the compatibility table says nothing about it. */
    public static AdaptationProfile profileWithoutTable(Holder<DamageType> type) {
        if (type.is(DamageTypeTags.IS_LIGHTNING)) return AdaptationProfile.ELECTRIC;
        if (type.is(DamageTypeTags.IS_EXPLOSION)) return AdaptationProfile.EXPLOSION;
        if (type.is(DamageTypeTags.IS_FIRE)) return AdaptationProfile.FIRE;
        if (type.is(DamageTypeTags.IS_FREEZING)) return AdaptationProfile.COLD;
        if (type.is(DamageTypeTags.IS_DROWNING)) return AdaptationProfile.SUFFOCATION;
        if (type.is(DamageTypeTags.IS_FALL)) return AdaptationProfile.PHYSICAL;
        AdaptationProfile vanilla=vanillaProfile(type);
        if (vanilla!=null) return vanilla;
        AdaptationProfile tagged=AdaptationAnalysis.strongest(tagProfiles(type));
        if (tagged!=null) return tagged;
        String path=type.unwrapKey().map(key -> key.location().getPath()).orElse("");
        AdaptationProfile named=AdaptationProfile.fromName(path,null);
        if (named==null) {
            String messageId=type.value().msgId();
            named=AdaptationProfile.fromName(messageId.substring(messageId.lastIndexOf(':')+1),null);
        }
        if (named==null) named=DeathTexts.profile(type.value().msgId());
        if (named!=null) return named;
        if (type.is(DamageTypeTags.IS_PROJECTILE)) return AdaptationProfile.PROJECTILE;
        return AdaptationProfile.ENVIRONMENT;
    }

    /** Vanilla's own meaning of its damage types that carry no category tag. */
    public static AdaptationProfile vanillaProfile(Holder<DamageType> type) {
        return type.unwrapKey().filter(key -> key.location().getNamespace().equals("minecraft"))
                .map(key -> AdaptationProfile.vanillaDamage(key.location().getPath())).orElse(null);
    }

    /** Categories named by the type's mod tags; vanilla tags and our override tags are read separately. */
    public static Set<AdaptationProfile> tagProfiles(Holder<DamageType> type) {
        var result=java.util.EnumSet.noneOf(AdaptationProfile.class);
        type.tags().forEach(tag -> {
            String namespace=tag.location().getNamespace();
            if(namespace.equals("minecraft") || namespace.equals("adaptationwheel")) return;
            AdaptationProfile profile=AdaptationProfile.fromTag(tag.location().getPath());
            if(profile!=null) result.add(profile);
        });
        return result;
    }

    public static boolean isHarmful(MobEffect effect) {
        return effect!=null && !JujutsuCompatibility.serviceEffect(effect) && (effect.getCategory()==MobEffectCategory.HARMFUL||ModAttackCompatibility.negativeNeutral(effect)
                || negativeNeutralAttributes(effect)||effect==MobEffects.BAD_OMEN || effect==MobEffects.GLOWING);
    }
    /** Some mods label debuffs neutral; only known beneficial vanilla attributes give an unambiguous sign. */
    private static boolean negativeNeutralAttributes(MobEffect effect){
        if(effect.getCategory()!=MobEffectCategory.NEUTRAL)return false;
        for(var modifier:effect.getAttributeModifiers().entrySet())
            if(HELPFUL_ATTRIBUTES.contains(modifier.getKey())&&modifier.getValue().getAmount()<0)return true;
        return false;
    }

    public static AdaptationProfile effectProfile(MobEffect effect) {
        AdaptationProfile known=ExtraEffects.knownProfile(effect);if(known!=null)return known;
        ResourceLocation id=BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id==null ? AdaptationProfile.EFFECT : AdaptationProfile.forKey("effect:"+id,false);
    }

    public static String translationKey(String category) {
        var meta=ru.pasha.adaptationwheel.core.MetaMechanic.fromKey(category);if(meta!=null)return meta.nameKey();
        if (category==null) return "";
        if(category.equals("condition:toughasnails:thirst"))return "condition.adaptationwheel.native_thirst";
        if(category.equals("condition:sanitydim:insanity"))return "condition.adaptationwheel.native_insanity";
        if(category.equals("condition:mekanism:radiation"))return "condition.adaptationwheel.native_radiation";
        if(category.equals(CorpusStateCompatibility.NUCLEAR))return "condition.adaptationwheel.nuclear_radiation";
        if(category.equals(CorpusStateCompatibility.SEA))return "condition.adaptationwheel.sea_dehydration";
        if(category.startsWith("attack:")&&category.contains(":dynamic_projectile/")){
            String id=category.substring(7).replace(":dynamic_projectile/",":");
            ResourceLocation type=ResourceLocation.tryParse(id);
            if(type!=null)return "entity."+type.getNamespace()+"."+type.getPath().replace('/','.');
        }
        if(!ModAttackCompatibility.translationKey(category).isEmpty())return ModAttackCompatibility.translationKey(category);
        String jjc=JujutsuCompatibility.translationKey(category);
        if(!jjc.isEmpty())return jjc;
        String t2s=T2SCompatibility.translationKey(category);
        if(!t2s.isEmpty()) return t2s;
        if(category.startsWith("effect:")) {
            ResourceLocation id=ResourceLocation.tryParse(category.substring(7));
            if(id!=null) {
                return BuiltInRegistries.MOB_EFFECT.getOptional(id).map(MobEffect::getDescriptionId)
                        .orElse("effect."+id.getNamespace()+"."+id.getPath().replace('/','.'));
            }
        }
        if(category.equals("environment:cold")) return "environment.adaptationwheel.cold";
        if(category.equals("environment:darkness")) return "environment.adaptationwheel.darkness";
        if (category.startsWith("mob:")) {
            ResourceLocation id = ResourceLocation.tryParse(category.substring(4));
            if (id != null) {
                return "entity." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            }
        }
        if (category.startsWith("damage:")) {
            ResourceLocation id = ResourceLocation.tryParse(category.substring(7));
            if (id!=null) return id.getNamespace().equals("minecraft")
                    ? "damage.adaptationwheel."+id.getPath().replace('/','.')
                    : "damage."+id.getNamespace()+"."+id.getPath().replace('/','.');
        }
        return category;
    }

    /** Registry ids remain usable when a mod supplies no standalone damage display translation. */
    public static String fallbackName(String category) {
        if (category==null || category.isBlank()) return "Unknown";
        if(category.startsWith("attack:"))return ModAttackCompatibility.displayName(category).getString();
        String identifier=category.substring(category.indexOf(':')+1);
        ResourceLocation id=ResourceLocation.tryParse(identifier);
        String path=id==null ? identifier : id.getPath();
        String words=path.replaceAll("([a-z0-9])([A-Z])","$1 $2").replace('_',' ').replace('/',' ').trim();
        if (words.isEmpty()) return identifier;
        String label=words.substring(0,1).toUpperCase(Locale.ROOT)+words.substring(1);
        return id!=null && !id.getNamespace().equals("minecraft") ? label+" ["+id.getNamespace()+"]" : label;
    }

    public static Component displayName(String category) {
        Component observed=ObservedAbilities.name(category);if(observed!=null)return observed;
        return Component.translatableWithFallback(translationKey(category),fallbackName(category));
    }

    /** Only vanilla zombies share a row (canonicalKey). A mod's creature built on the Zombie class is its own creature. */
    public static String forEntity(LivingEntity entity) {
        return canonicalKey("mob:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }
    public static boolean boss(LivingEntity entity) {
        return !(entity instanceof Player) && entity.getMaxHealth() >= 100.0F
                && !AbilityEntities.is(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
    /** The creature's own blow with vanilla's plain melee type, whichever of its methods landed it. */
    public static boolean plainMelee(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity()==source.getEntity()
                && BASIC_MELEE.contains(typeId(source.typeHolder()));
    }
    public static boolean boss(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity entity) return boss(entity);
        if (source.getDirectEntity() instanceof LivingEntity entity) return boss(entity);
        return source.is(DamageTypes.DRAGON_BREATH);
    }
}

