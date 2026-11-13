import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Verifies native callback guards against every supplied archive, including both Born versions. */
public class VerifyEffectHooks {
 public static void main(String[] args)throws Exception {
  var defs=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonObject("classes");
  int methods=0,abstracts=0;List<String> report=new ArrayList<>();
  for(String path:Files.readAllLines(Path.of(args[0])))try(var zip=new ZipFile(path)){
   int count=0;
   for(var entry:Collections.list(zip.entries())){
    String owner=entry.getName().replaceFirst("\\.class$","");if(!entry.getName().endsWith(".class")||!defs.has(owner))continue;
    ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(cls,0);
    var rules=defs.getAsJsonObject(owner);
    for(MethodNode method:cls.methods){
     String signature=method.name+method.desc;if(!rules.has(signature))continue;
     if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0){abstracts++;continue;}
     String kind=rules.get(signature).getAsString();InsnList patch=new InsnList();LabelNode next=new LabelNode();
     patch.add(new VarInsnNode(Opcodes.ALOAD,0));patch.add(new VarInsnNode(Opcodes.ALOAD,kind.equals("instant")||kind.equals("extended_application")?3:1));
     patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"ru/pasha/adaptationwheel/server/NegativeEffects",kind.equals("punitive_expiry")?"suppressPunitiveExpiry":"skipNativeAction","(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;)Z",false));
     patch.add(new JumpInsnNode(Opcodes.IFEQ,next));
     if(kind.equals("extended_incoming")||kind.equals("extended_outgoing")){patch.add(new VarInsnNode(Opcodes.FLOAD,kind.equals("extended_incoming")?4:5));patch.add(new InsnNode(Opcodes.FRETURN));}
     else if(kind.equals("extended_allow")){patch.add(new InsnNode(Opcodes.ICONST_1));patch.add(new InsnNode(Opcodes.IRETURN));}
     else patch.add(new InsnNode(Opcodes.RETURN));
     patch.add(next);patch.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
     if(kind.equals("punitive_expiry")){
      boolean found=false;for(var ins:method.instructions)if(ins instanceof MethodInsnNode c&&c.getOpcode()==Opcodes.INVOKESPECIAL&&c.name.equals("m_6386_")){method.instructions.insert(ins,patch);found=true;break;}
      if(!found)throw new AssertionError("No native attribute cleanup before punitive expiry: "+owner);
     }else method.instructions.insert(patch);
     method.maxStack+=2;new Analyzer<>(new BasicVerifier()).analyze(cls.name,method);count++;methods++;
    }
    ClassWriter writer=new ClassWriter(0);cls.accept(writer);new ClassReader(writer.toByteArray());
   }
   report.add(Path.of(path).getFileName()+"\t"+count+" native callbacks");
  }
  report.add("PASS: "+methods+" native effect callbacks verified; "+abstracts+" abstract/native declarations preserved without code");
  Files.write(Path.of(args[2]),report);report.forEach(System.out::println);
 }
}
