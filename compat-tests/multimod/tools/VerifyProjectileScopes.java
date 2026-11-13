import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Verifies native callback frames/exception exits while preserving all call sites. */
public class VerifyProjectileScopes {
 static final String H="ru/pasha/adaptationwheel/server/ModAttackCompatibility";
 static final Map<String,String> SIGNATURES=Map.of("m_8119_","()V","m_5790_","(Lnet/minecraft/world/phys/EntityHitResult;)V","m_6532_","(Lnet/minecraft/world/phys/HitResult;)V","m_8060_","(Lnet/minecraft/world/phys/BlockHitResult;)V");
 static List<Object> initial(String owner,String desc){List<Object> out=new ArrayList<>();out.add(owner);for(Type type:Type.getArgumentTypes(desc))out.add(type.getInternalName());return out;}
 static List<Object> padded(List<Object> locals,int slot){List<Object> out=new ArrayList<>(locals);int width=0;for(Object t:locals)width+=Objects.equals(t,Opcodes.LONG)||Objects.equals(t,Opcodes.DOUBLE)?2:1;if(width>slot)throw new AssertionError("Frame exceeds original locals");while(width++<slot)out.add(Opcodes.TOP);out.add("java/lang/Object");return out;}
 static void transform(ClassNode cls,MethodNode m){
  int slot=m.maxLocals;List<Object> locals=initial(cls.name,m.desc);
  for(var i=m.instructions.getFirst();i!=null;i=i.getNext()){
   if(i instanceof FrameNode f){if(f.type==Opcodes.F_FULL||f.type==Opcodes.F_NEW)locals=new ArrayList<>(f.local);else if(f.type==Opcodes.F_APPEND)locals.addAll(f.local);else if(f.type==Opcodes.F_CHOP)locals=new ArrayList<>(locals.subList(0,locals.size()-f.local.size()));f.type=Opcodes.F_FULL;f.local=padded(locals,slot);f.stack=f.stack==null?new ArrayList<>():new ArrayList<>(f.stack);}
   if(i.getOpcode()==Opcodes.RETURN){InsnList p=new InsnList();p.add(new VarInsnNode(Opcodes.ALOAD,slot));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"endProjectile","(Ljava/lang/Object;)V",false));m.instructions.insertBefore(i,p);}
  }
  LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList p=new InsnList();p.add(new VarInsnNode(Opcodes.ALOAD,0));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"beginProjectile","(Lnet/minecraft/world/entity/Entity;)Ljava/lang/Object;",false));p.add(new VarInsnNode(Opcodes.ASTORE,slot));p.add(start);m.instructions.insert(p);
  m.instructions.add(end);m.instructions.add(handler);List<Object> hs=padded(initial(cls.name,m.desc),slot);m.instructions.add(new FrameNode(Opcodes.F_FULL,hs.size(),hs.toArray(),1,new Object[]{"java/lang/Throwable"}));m.instructions.add(new VarInsnNode(Opcodes.ASTORE,slot+1));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,slot));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"endProjectile","(Ljava/lang/Object;)V",false));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,slot+1));m.instructions.add(new InsnNode(Opcodes.ATHROW));m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));m.maxLocals+=2;m.maxStack=Math.max(m.maxStack+2,2);
 }
 public static void main(String[] args)throws Exception{
  Set<String> owners=new HashSet<>();JsonObject root=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();for(var e:root.getAsJsonObject("projectiles").entrySet())owners.add(e.getValue().getAsJsonObject().get("entity").getAsString());
  for(var value:JsonParser.parseString(Files.readString(Path.of(args[4]))).getAsJsonObject().getAsJsonArray("classes"))owners.add(value.getAsString());
  for(String name:List.of("Projectile","AbstractArrow","AbstractHurtingProjectile","ThrowableProjectile","ThrowableItemProjectile","ThrownPotion","Fireball","SmallFireball","LargeFireball","DragonFireball","WitherSkull","ShulkerBullet","LlamaSpit","Snowball","ThrownEnderpearl","ThrownEgg","ThrownExperienceBottle"))owners.add("net/minecraft/world/entity/projectile/"+name);
  List<String> report=new ArrayList<>();int total=0;
  List<String> jars=new ArrayList<>(Files.readAllLines(Path.of(args[0])));jars.add(args[2]);
  for(String path:jars){int count=0;try(var zip=new ZipFile(path)){for(String owner:owners){var entry=zip.getEntry(owner+".class");if(entry==null)continue;ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(cls,0);boolean changed=false;
   for(MethodNode m:cls.methods){if(!m.desc.equals(SIGNATURES.get(m.name))||(m.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;long before=Arrays.stream(m.instructions.toArray()).filter(i->i instanceof MethodInsnNode).count();transform(cls,m);new Analyzer<>(new BasicVerifier()).analyze(cls.name,m);long after=Arrays.stream(m.instructions.toArray()).filter(i->i instanceof MethodInsnNode c&&!c.owner.equals(H)).count();if(before!=after)throw new AssertionError("Original calls changed: "+owner+"."+m.name);count++;changed=true;}
   if(changed){ClassWriter writer=new ClassWriter(0);cls.accept(writer);new ClassReader(writer.toByteArray()).accept(new ClassNode(),ClassReader.EXPAND_FRAMES);}
  }}if(count>0)report.add(Path.of(path).getFileName()+"\t"+count+" callbacks verified");total+=count;}
  if(total==0)throw new AssertionError("No callbacks verified");report.add("PASS: "+total+" version-specific projectile callbacks; stacks, frames, normal/exception exits and original invocations verified");Files.write(Path.of(args[3]),report);report.forEach(System.out::println);
 }
}
