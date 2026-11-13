package ru.pasha.adaptationwheel;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
public final class AdaptationConfig {
 public static final ForgeConfigSpec SPEC;
 public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;
 static {
  ForgeConfigSpec.Builder builder=new ForgeConfigSpec.Builder();
  OVERRIDES=builder.comment("Optional exact damage message-id classification. Example: examplemod:opaque_ray=LIGHT", "Profiles: FIRE, PHYSICAL, MAGIC, EFFECT, HUNGER, COLD, DARKNESS, ENVIRONMENT, PROJECTILE, EXPLOSION, LIGHT, ELECTRIC, POISON, SUFFOCATION")
    .defineListAllowEmpty(Arrays.asList("damageCategoryOverrides"),()->Collections.<String>emptyList(),AdaptationConfig::valid);
  SPEC=builder.build();
 }
 private static boolean valid(Object o){if(!(o instanceof String))return false;String[] pair=((String)o).split("=",2);if(pair.length!=2||pair[0].trim().isEmpty())return false;try{AdaptationProfile.valueOf(pair[1].trim().toUpperCase(Locale.ROOT));return true;}catch(IllegalArgumentException e){return false;}}
 public static AdaptationProfile override(String id){if(!SPEC.isLoaded())return null;for(String rule:OVERRIDES.get()){String[] pair=rule.split("=",2);if(pair.length==2&&pair[0].trim().equals(id))return AdaptationProfile.valueOf(pair[1].trim().toUpperCase(Locale.ROOT));}return null;}
}
