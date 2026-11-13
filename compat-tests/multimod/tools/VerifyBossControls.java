import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Mirrors the three branch-free boss control rewrites in mod_controls.js. */
public class VerifyBossControls {
 public static void main(String[] args)throws Exception{
  String entity="Lnet/minecraft/world/entity/Entity;";
  String helper="ru/pasha/adaptationwheel/server/MobControlCompatibility";
  List<String> report=new ArrayList<>();int count=0;
  for(String path:Files.readAllLines(Path.of(args[0])))try(ZipFile zip=new ZipFile(path)){
   for(var entry:Collections.list(zip.entries())){
    String owner=entry.getName().replaceFirst("\\.class$","");
    boolean hydra=owner.equals("twilightforest/entity/boss/HydraHeadContainer"),guardian=owner.equals("com/brandon3055/draconicevolution/entity/guardian/DraconicGuardianEntity"),charge=owner.equals("net/tslat/aoa3/content/entity/brain/task/custom/ChargeAttack");
    if(!entry.getName().endsWith(".class")||!(hydra||guardian||charge))continue;
    ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(cls,0);
    for(MethodNode m:cls.methods){int changed=0;
     for(var ins=m.instructions.getFirst();ins!=null;ins=ins.getNext())if(ins instanceof MethodInsnNode call){
      if(hydra&&m.name.equals("executeAttacks")&&call.name.equals("m_45933_")&&call.desc.equals("("+entity+"Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")){
       call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.name="hydraBiteTargets";call.desc="(Lnet/minecraft/world/level/Level;"+entity+"Lnet/minecraft/world/phys/AABB;)Ljava/util/List;";call.itf=false;changed++;
      }else if(guardian&&m.name.equals("collideWithEntities")&&call.name.equals("m_5997_")&&call.desc.equals("(DDD)V")){
       m.instructions.insertBefore(ins,new VarInsnNode(Opcodes.ALOAD,0));call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.name="guardianPush";call.desc="("+entity+"DDD"+entity+")V";call.itf=false;m.maxStack++;changed++;
      }else if(charge&&m.name.equals("tick")&&call.owner.equals("net/tslat/aoa3/util/EntityUtil")&&call.name.equals("pushEntityAway")&&call.desc.equals("("+entity+entity+"F)V")){
       call.owner=helper;call.name="aoaChargePush";changed++;
      }
     }
     if(changed>0){new Analyzer<>(new BasicVerifier()).analyze(cls.name,m);report.add(owner+"#"+m.name+m.desc+"\t"+changed+" verified calls");count+=changed;}
    }
   }
  }
  if(count!=3)throw new AssertionError("Expected 3 exact boss control call sites; got "+count);
  report.add("PASS: 3 native boss control call sites, argument stacks verified");Files.write(Path.of(args[1]),report);report.forEach(System.out::println);
 }
}
