import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Reproduces full-method scope and verifies frames/stacks without replacing mixin call sites. */
public class VerifyHurtScopes {
 static final String HELPER="ru/pasha/adaptationwheel/server/ModAttackCompatibility";
 static List<Object> initial(String owner){return new ArrayList<>(List.of(owner,"net/minecraft/world/damagesource/DamageSource",Opcodes.FLOAT));}
 static List<Object> padded(List<Object> locals,int slot){
  List<Object> out=new ArrayList<>(locals);int width=0;
  for(Object type:locals)width+=Objects.equals(type,Opcodes.LONG)||Objects.equals(type,Opcodes.DOUBLE)?2:1;
  if(width>slot)throw new AssertionError("Frame exceeds original locals");
  while(width++<slot)out.add(Opcodes.TOP);out.add("java/lang/Object");return out;
 }
 static void transform(ClassNode cls,MethodNode method){
  int slot=method.maxLocals;List<Object> locals=initial(cls.name);
  for(var ins=method.instructions.getFirst();ins!=null;ins=ins.getNext()){
   if(ins instanceof FrameNode frame){
    if(frame.type==Opcodes.F_FULL||frame.type==Opcodes.F_NEW)locals=new ArrayList<>(frame.local);
    else if(frame.type==Opcodes.F_APPEND)locals.addAll(frame.local);
    else if(frame.type==Opcodes.F_CHOP)locals=new ArrayList<>(locals.subList(0,locals.size()-frame.local.size()));
    frame.type=Opcodes.F_FULL;frame.local=padded(locals,slot);frame.stack=frame.stack==null?new ArrayList<>():new ArrayList<>(frame.stack);
   }
   if(ins.getOpcode()==Opcodes.IRETURN){InsnList leave=new InsnList();leave.add(new VarInsnNode(Opcodes.ALOAD,slot));leave.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"endHurt","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(ins,leave);}
  }
  LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList enter=new InsnList();
  enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new VarInsnNode(Opcodes.ALOAD,1));enter.add(new VarInsnNode(Opcodes.FLOAD,2));
  enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"beginHurt","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)Ljava/lang/Object;",false));enter.add(new VarInsnNode(Opcodes.ASTORE,slot));enter.add(start);method.instructions.insert(enter);
  method.instructions.add(end);method.instructions.add(handler);var handlerLocals=padded(initial(cls.name),slot);
  method.instructions.add(new FrameNode(Opcodes.F_FULL,handlerLocals.size(),handlerLocals.toArray(),1,new Object[]{"java/lang/Throwable"}));
  method.instructions.add(new VarInsnNode(Opcodes.ASTORE,slot+1));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,slot));method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"endHurt","(Ljava/lang/Object;)V",false));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,slot+1));method.instructions.add(new InsnNode(Opcodes.ATHROW));
  method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxLocals+=2;method.maxStack=Math.max(method.maxStack+2,3);
 }
 public static void main(String[] args)throws Exception {
  List<String> report=new ArrayList<>();
  try(var zip=new ZipFile(args[0])){
   for(String owner:List.of("net/minecraft/world/entity/LivingEntity","net/minecraft/world/entity/player/Player","net/minecraft/server/level/ServerPlayer")){
    ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(owner+".class")).readAllBytes()).accept(cls,0);
    for(MethodNode method:cls.methods)if(method.name.equals("m_6469_")&&method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")){
     int before=0;for(var ins:method.instructions)if(ins instanceof MethodInsnNode c&&c.name.equals("m_6469_"))before++;
     transform(cls,method);new Analyzer<>(new BasicVerifier()).analyze(cls.name,method);
     int after=0;for(var ins:method.instructions)if(ins instanceof MethodInsnNode c&&c.name.equals("m_6469_"))after++;
     if(before!=after)throw new AssertionError("An original mixin call site changed");
     report.add("PASS: "+owner+".hurt; frames, handler stack, normal/exception exit verified; "+after+" original hurt invocations preserved");
    }
    ClassWriter writer=new ClassWriter(0);cls.accept(writer);ClassNode expanded=new ClassNode();new ClassReader(writer.toByteArray()).accept(expanded,ClassReader.EXPAND_FRAMES);
   }
  }Files.write(Path.of(args[1]),report);report.forEach(System.out::println);
 }
}
