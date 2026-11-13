package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;
import static ru.pasha.multimodqa.MultiModQa.*;
import static ru.pasha.multimodqa.SmartAnalysisQa.*;

/**
 * The 4.22 analyzer with the real mods of Pasha's pack: untagged vanilla damage (a bee's sting, a wall), Jujutsu Craft's
 * own damage type tags (forge:curse, forge:combat) and its technique table. Every row is logged before it is checked,
 * so a run on an older wheel shows what that one decided.
 */
final class AnalyzerQa {
 private AnalyzerQa(){}
 static void run(ServerPlayer p)throws Exception{
  reset(p);int differences=0;
  var level=p.serverLevel();
  var bee=EntityType.BEE.create(level);bee.moveTo(p.getX()+2,p.getY(),p.getZ());
  hurt(p,level.damageSources().sting(bee));
  differences+=expect(p,"damage:minecraft:sting","PHYSICAL","registry.physical");
  differences+=expect(p,"mob:minecraft:bee","PHYSICAL","registry.physical");
  hurt(p,level.damageSources().inWall());
  differences+=expect(p,"damage:minecraft:in_wall","SUFFOCATION","registry.suffocation");
  if(ModList.get().isLoaded("jujutsucraft")){
   var types=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
   var curse=types.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("jujutsucraft","damage_curse")));
   var combat=types.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("jujutsucraft","damage_combat")));
   hurt(p,new DamageSource(curse));
   differences+=expect(p,"damage:jujutsucraft:damage_curse","MAGIC","tag.category");
   hurt(p,new DamageSource(combat));
   differences+=expect(p,"damage:jujutsucraft:damage_combat","PHYSICAL","tag.category");
   // A cursed spirit using a listed technique (Overhead Attack) and an unlisted one, through Jujutsu's own skill number.
   var spirit=(Mob)BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("jujutsucraft","cursed_spirit_grade_33")).create(level);
   spirit.setNoAi(true);spirit.moveTo(p.getX()+3,p.getY(),p.getZ());level.addFreshEntity(spirit);
   try{
    spirit.getPersistentData().putDouble("skill",1606);hurt(p,new DamageSource(curse,spirit));
    differences+=expect(p,"condition:jujutsucraft:technique/1606","MAGIC","compat.known");
    spirit.getPersistentData().putDouble("skill",4200);hurt(p,new DamageSource(curse,spirit));
    differences+=expect(p,"condition:jujutsucraft:technique/4200","MAGIC","tag.category");
   }finally{spirit.discard();}
  }
  reset(p);
  check(differences==0,differences+" analyzer rows differ from the expected 4.22 classification");
  LogUtils.getLogger().info("MULTIMOD_QA ANALYZER PASS: real hits classify a bee's sting and a wall from vanilla's definition, Jujutsu curse/combat damage from its own tags, a listed technique from the wheel's table");
 }
 static void hurt(ServerPlayer p,DamageSource source)throws Exception{p.setHealth(p.getMaxHealth());hit(p,source,1);wheelTick(p);}
 static int expect(ServerPlayer p,String key,String profile,String reason)throws Exception{
  Object row=snapshot(p,key);
  String actual=row==null?"missing":getter(row,"profile")+" "+getter(row,"analysisReason")+" "+getter(row,"analysisConfidence")+"%";
  boolean same=row!=null&&getter(row,"profile").toString().equals(profile)&&getter(row,"analysisReason").toString().equals(reason);
  LogUtils.getLogger().info("MULTIMOD_QA ANALYZER ROW {} {}: {} (expected {} {})",same?"OK":"DIFF",key,actual,profile,reason);
  return same?0:1;
 }
}
