package ru.pasha.adaptationwheel.server;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
public final class AdaptationCatalogue {
 public static List<CatalogueEntry> entries(ServerPlayerEntity player){
  Map<String,CatalogueEntry> result=new LinkedHashMap<>();
  for(EntityType<?> type:ForgeRegistries.ENTITIES.getValues()){
   if(type==EntityType.PLAYER||type==EntityType.ARMOR_STAND||!GlobalEntityTypeAttributes.hasSupplier(type))continue;
   String key=DamageCategories.canonical("mob:"+ForgeRegistries.ENTITIES.getKey(type));boolean boss=false;
   AttributeModifierMap a=GlobalEntityTypeAttributes.getSupplier((EntityType<? extends LivingEntity>)type);
   if(a!=null&&a.hasAttribute(Attributes.MAX_HEALTH))boss=a.getValue(Attributes.MAX_HEALTH)>100;
   put(result,key,AdaptationProfile.forKey(key,boss));
  }
  for(Effect e:ForgeRegistries.POTIONS.getValues())if(DamageCategories.harmful(e))put(result,DamageCategories.effectKey(e),DamageCategories.effectProfile(e));
  DamageCategories.knownTypes().forEach((k,v)->put(result,k,v));
  for(String type:Arrays.asList("arrow","trident","fireball","witherSkull","explosion","playerExplosion","thrown","thorns","lightningBolt"))put(result,"damage:"+type,AdaptationProfile.fromName(type,AdaptationProfile.ENVIRONMENT));
  put(result,AdaptationProfile.DARK_KEY,AdaptationProfile.DARKNESS);put(result,AdaptationProfile.COLD_KEY,AdaptationProfile.COLD);
  for(AdaptationState.Snapshot s:AdaptationEvents.state(player).snapshots())put(result,s.key(),s.profile());
  return Compat.copyList(result.values());
 }
 private static void put(Map<String,CatalogueEntry> result,String key,AdaptationProfile p){result.put(key,new CatalogueEntry(key,DamageCategories.translationKey(key),DamageCategories.fallback(key),p));}
}