import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Reproduces the branch-free attack transformation and checks stacks for every supplied version. */
public class VerifyHooks {
 public static void main(String[] args)throws Exception {
  Path manifest=Path.of(args[1]);JsonObject root=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
  JsonObject definitions=root.getAsJsonObject("classes");Set<String> activeRules=new HashSet<>(),activeFamilies=new HashSet<>();
  List<String> report=new ArrayList<>();int verified=0,calls=0;
  for(String path:Files.readAllLines(Path.of(args[0])))try(ZipFile zip=new ZipFile(path)){
   int methods=0,sites=0;
   for(var entry:Collections.list(zip.entries())){
    if(!entry.getName().endsWith(".class"))continue;String owner=entry.getName().replaceFirst("\\.class$","");
    if(owner.contains("/mixin")||!definitions.has(owner))continue;
    JsonObject rules=definitions.getAsJsonObject(owner).getAsJsonObject("methods");
    ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(cls,0);
    for(MethodNode method:cls.methods){
     String signature=method.name+method.desc;if(!rules.has(signature))continue;
     JsonObject rule=rules.getAsJsonObject(signature);boolean changed=false;
     for(var ins=method.instructions.getFirst();ins!=null;ins=ins.getNext()){
      if(!(ins instanceof MethodInsnNode call)||(call.getOpcode()!=Opcodes.INVOKEVIRTUAL&&call.getOpcode()!=Opcodes.INVOKEINTERFACE))continue;
      boolean hurt=call.name.equals("m_6469_")&&call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z");
      boolean health=rule.get("kind").getAsString().contains("health")&&call.name.equals("m_21153_")&&call.desc.equals("(F)V");
      if(!hurt&&!health)continue;
      method.instructions.insertBefore(ins,new LdcInsnNode(rule.get("key").getAsString()));call.setOpcode(Opcodes.INVOKESTATIC);
      call.owner="ru/pasha/adaptationwheel/server/ModAttackCompatibility";call.name=hurt?"hurt":"setHealth";
      call.desc=hurt?"(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FLjava/lang/String;)Z":"(Lnet/minecraft/world/entity/LivingEntity;FLjava/lang/String;)V";call.itf=false;
      if(health&&owner.equals("com/brandon3055/draconicevolution/entity/guardian/control/LaserBeamPhase")){
       method.instructions.insertBefore(ins,new VarInsnNode(Opcodes.ALOAD,0));method.instructions.insertBefore(ins,new FieldInsnNode(Opcodes.GETFIELD,owner,"damage","Lnet/minecraft/world/damagesource/DamageSource;"));
       call.name="setHealthFromSource";call.desc="(Lnet/minecraft/world/entity/LivingEntity;FLjava/lang/String;Lnet/minecraft/world/damagesource/DamageSource;)V";method.maxStack++;
      }
      if(hurt&&Set.of("twilightforest/entity/boss/HydraHeadContainer","twilightforest/entity/boss/SnowQueen","twilightforest/entity/boss/UrGhast").contains(owner)){
       method.instructions.insertBefore(ins,new VarInsnNode(Opcodes.ALOAD,0));
       if(owner.endsWith("HydraHeadContainer"))method.instructions.insertBefore(ins,new FieldInsnNode(Opcodes.GETFIELD,owner,"hydra","Ltwilightforest/entity/boss/Hydra;"));
       call.name="hurtOwned";call.desc="(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FLjava/lang/String;Lnet/minecraft/world/entity/Entity;)Z";method.maxStack++;
      }
      changed=true;sites++;
     }
     if(changed){method.maxStack++;new Analyzer<>(new BasicVerifier()).analyze(cls.name,method);methods++;
      activeRules.add(owner+"#"+signature);activeFamilies.add(rule.get("key").getAsString());}
    }
   }
   report.add(Path.of(path).getFileName()+"\t"+methods+" methods\t"+sites+" invocations");verified+=methods;calls+=sites;
  }
  for(String owner:new ArrayList<>(definitions.keySet())){
   JsonObject rules=definitions.getAsJsonObject(owner).getAsJsonObject("methods");
   for(String signature:new ArrayList<>(rules.keySet()))if(!activeRules.contains(owner+"#"+signature))rules.remove(signature);
   if(rules.size()==0)definitions.remove(owner);
  }
  JsonArray entries=new JsonArray();for(var entry:root.getAsJsonArray("entries"))if(activeFamilies.contains(entry.getAsJsonObject().get("key").getAsString()))entries.add(entry);
  root.add("entries",entries);Files.writeString(manifest,new Gson().toJson(root));
  report.add("PASS: "+verified+" transformed version-specific methods / "+calls+" call sites / "+activeFamilies.size()+" actual attack families; super-only methods excluded");
  Files.write(Path.of(args[2]),report);report.forEach(System.out::println);
 }
}
