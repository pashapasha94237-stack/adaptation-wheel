package ru.pasha.adaptationwheel.server;
import java.util.*;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.*;
import net.minecraft.util.text.*;
import net.minecraftforge.registries.ForgeRegistries;
import ru.pasha.adaptationwheel.core.*;
public final class DamageCategories {
 public static final class Exposure {
  public final String key;public final AdaptationProfile profile;public final boolean boss;
  public Exposure(String k,AdaptationProfile p,boolean b){key=k;profile=p;boss=b;}
 }
 private static final Map<String,AdaptationProfile> SEEN=new LinkedHashMap<>();
 static {for(java.lang.reflect.Field f:DamageSource.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==DamageSource.class)try{DamageSource s=(DamageSource)f.get(null);if(s!=null&&!excluded(s))SEEN.put(canonical("damage:"+s.getMsgId()),profile(s));}catch(ReflectiveOperationException ignored){}}
 private DamageCategories(){}
 public static boolean excluded(DamageSource s){return s==DamageSource.OUT_OF_WORLD||s.getMsgId().equals("outOfWorld")||s.getMsgId().equals("genericKill");}
 public static String canonical(String key){
  if(key.equals("damage:starve"))return AdaptationProfile.HUNGER_KEY;
  if(key.equals("damage:freeze"))return AdaptationProfile.COLD_KEY;
  if(key.equals("damage:magic")||key.equals("damage:indirectMagic"))return "effect:minecraft:instant_damage";
  if(key.equals("damage:wither"))return "effect:minecraft:wither";
  if(key.equals("mob:minecraft:husk")||key.equals("mob:minecraft:drowned")||key.equals("mob:minecraft:zombie_villager")||key.equals("mob:minecraft:zombified_piglin"))return "mob:minecraft:zombie";
  return key;
 }
 public static List<Exposure> analyze(DamageSource source){
  if(excluded(source))return Compat.list();
  String type=canonical("damage:"+source.getMsgId());AdaptationProfile profile=profile(source);
  if(type.startsWith("effect:")){Effect effect=ForgeRegistries.POTIONS.getValue(new net.minecraft.util.ResourceLocation(type.substring(7)));if(effect!=null)profile=effectProfile(effect);}
  SEEN.put(type,profile);
  Entity owner=source.getEntity();if(!(owner instanceof LivingEntity))owner=source.getDirectEntity();
  List<Exposure> result=new ArrayList<>();
  if(owner instanceof LivingEntity){
   String mob=forEntity((LivingEntity)owner);boolean boss=boss((LivingEntity)owner);
   result.add(new Exposure(mob,AdaptationProfile.forKey(mob,boss),boss));
   if(!source.getMsgId().equals("mob")&&!source.getMsgId().equals("player"))result.add(new Exposure(type,profile,false));
  }else if(source==DamageSource.DRAGON_BREATH){result.add(new Exposure("mob:minecraft:ender_dragon",AdaptationProfile.MAGIC,true));result.add(new Exposure(type,profile,false));}
  else result.add(new Exposure(type,profile,false));
  return result;
 }
 public static AdaptationProfile profile(DamageSource s){
  AdaptationProfile override=ru.pasha.adaptationwheel.AdaptationConfig.override(s.getMsgId());if(override!=null)return override;
  if(s.isFire())return AdaptationProfile.FIRE;
  if(s.isExplosion())return AdaptationProfile.EXPLOSION;
  String id=s.getMsgId();AdaptationProfile named=AdaptationProfile.fromName(id.substring(id.lastIndexOf(':')+1),null);
  if(named!=null)return named;
  if(s.isMagic())return AdaptationProfile.MAGIC;
  if(s.isProjectile()||s.getDirectEntity() instanceof net.minecraft.entity.projectile.ProjectileEntity)return AdaptationProfile.PROJECTILE;
  return AdaptationProfile.ENVIRONMENT;
 }
 public static boolean harmful(Effect effect){return effect.getCategory()==EffectType.HARMFUL||effect==Effects.BAD_OMEN||effect==Effects.GLOWING;}
 public static String effectKey(Effect effect){return "effect:"+ForgeRegistries.POTIONS.getKey(effect);}
 public static AdaptationProfile effectProfile(Effect effect){return AdaptationProfile.forKey(effectKey(effect),false);}
 public static boolean boss(LivingEntity entity){return !(entity instanceof PlayerEntity)&&entity.getMaxHealth()>100;}
 public static String forEntity(LivingEntity e){return e instanceof ZombieEntity?"mob:minecraft:zombie":"mob:"+ForgeRegistries.ENTITIES.getKey(e.getType());}
 public static String translationKey(String key){
  if(key.equals(AdaptationProfile.DARK_KEY))return "environment.adaptationwheel.darkness";
  if(key.equals(AdaptationProfile.COLD_KEY))return "environment.adaptationwheel.cold";
  if(key.equals("mob:minecraft:zombie"))return "entity.adaptationwheel.zombie_family";
  if(key.startsWith("mob:"))return "entity."+key.substring(4).replace(':','.').replace('/','.');
  if(key.startsWith("effect:")){Effect e=ForgeRegistries.POTIONS.getValue(new net.minecraft.util.ResourceLocation(key.substring(7)));if(e!=null)return e.getDescriptionId();}
  if(key.startsWith("damage:")){
   String raw=key.substring(7);Map<String,String> aliases=new HashMap<>();
   aliases.put("inFire","in_fire");aliases.put("onFire","on_fire");aliases.put("hotFloor","hot_floor");aliases.put("inWall","in_wall");aliases.put("flyIntoWall","fly_into_wall");aliases.put("fallingBlock","falling_block");aliases.put("dragonBreath","dragon_breath");aliases.put("dryout","dry_out");aliases.put("sweetBerryBush","sweet_berry_bush");aliases.put("lightningBolt","lightning_bolt");aliases.put("indirectMagic","indirect_magic");aliases.put("mob","mob_attack");aliases.put("player","player_attack");
   if(!raw.contains(":"))return "damage.adaptationwheel."+aliases.getOrDefault(raw,raw);
   return "damage."+raw.replace(':','.').replace('/','.');
  }
  return key;
 }
 public static String fallback(String key){return key.substring(key.indexOf(':')+1).replaceAll("([a-z0-9])([A-Z])","$1 $2").replace('_',' ').replace('/',' ');}
 public static ITextComponent name(String key){String k=translationKey(key);return key.startsWith("damage:")&&key.substring(7).contains(":")?new StringTextComponent(fallback(key)):new TranslationTextComponent(k);}
 public static Map<String,AdaptationProfile> knownTypes(){return new LinkedHashMap<>(SEEN);}
}
