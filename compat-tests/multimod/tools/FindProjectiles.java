import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Resolve actual DeferredRegister entity factories, including nested supplier lambdas. */
public class FindProjectiles {
 static Map<String,ClassNode> classes=new HashMap<>();static Map<String,String> ids=new HashMap<>();
 static boolean projectile(String owner,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;
  if(owner.startsWith("net/minecraft/world/entity/projectile/"))return true;
  ClassNode cls=classes.get(owner);if(cls==null)return false;
  if(projectile(cls.superName,seen))return true;
  return cls.superName.equals("net/minecraft/world/entity/Entity")&&owner.matches("(?i).*(projectile|beam|rayentity|spellentity|meteor|bullet|waveentity).*?");
 }
 static Set<String> factories(Handle handle,Set<String> seen,int depth){
  if(depth>8)return Set.of();if(handle.getName().equals("<init>"))return projectile(handle.getOwner(),new HashSet<>())?Set.of(handle.getOwner()):Set.of();
  if(!seen.add(handle.getOwner()+handle.getName()+handle.getDesc()))return Set.of();
  ClassNode cls=classes.get(handle.getOwner());if(cls==null)return Set.of();Set<String> found=new HashSet<>();
  for(MethodNode method:cls.methods)if(method.name.equals(handle.getName())&&method.desc.equals(handle.getDesc())){
   for(var ins:method.instructions){
    if(ins instanceof InvokeDynamicInsnNode indy)for(Object arg:indy.bsmArgs)if(arg instanceof Handle nested)found.addAll(factories(nested,seen,depth+1));
    if(ins instanceof MethodInsnNode call&&call.name.equals("<init>")&&projectile(call.owner,new HashSet<>()))found.add(call.owner);
   }
  }return found;
 }
 public static void main(String[] args)throws Exception{
  var modids=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray();
  for(var row:modids){var o=row.getAsJsonObject();ids.put(o.get("file").getAsString()+".jar",o.get("id").getAsString());}
  var jars=Files.readAllLines(Path.of(args[0]));
  for(String path:jars)try(var zip=new ZipFile(path)){for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class")&&!entry.getName().startsWith("META-INF/")){
   ClassNode node=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);
  }}
  JsonObject projectileIds=new JsonObject();
  for(String path:jars)try(var zip=new ZipFile(path)){
   String mod=ids.get(Path.of(path).getFileName().toString());if(mod==null)continue;
   for(var entry:Collections.list(zip.entries())){
    if(!entry.getName().endsWith(".class"))continue;ClassNode node=classes.get(entry.getName().replaceFirst("\\.class$",""));if(node==null)continue;
    for(MethodNode method:node.methods){String last=null;Boolean entityRegister=null;Set<String> constructors=new HashSet<>();
     for(var ins:method.instructions){
      if(ins instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.desc.equals("Lnet/minecraftforge/registries/DeferredRegister;")){
       entityRegister=null;ClassNode registryOwner=classes.get(field.owner);
       if(registryOwner!=null)for(FieldNode definition:registryOwner.fields)if(definition.name.equals(field.name)&&definition.signature!=null)entityRegister=definition.signature.contains("net/minecraft/world/entity/EntityType");
      }
      if(ins instanceof LdcInsnNode ldc&&ldc.cst instanceof String s&&s.matches("[a-z0-9_./-]+")){last=s;constructors.clear();}
      // MCreator erases suppliers to Object. Check the actual DeferredRegister
      // field's generic type instead of rejecting those genuine entity factories.
      if(ins instanceof InvokeDynamicInsnNode indy)for(Object arg:indy.bsmArgs)if(arg instanceof Handle h)constructors.addAll(factories(h,new HashSet<>(),0));
      if(ins instanceof MethodInsnNode call&&call.name.equals("register")&&(call.owner.equals("net/minecraftforge/registries/DeferredRegister")||call.desc.startsWith("(Ljava/lang/String;"))){
       if(last!=null&&constructors.size()==1&&!Boolean.FALSE.equals(entityRegister)){String entity=constructors.iterator().next();projectileIds.add(mod+":"+last,new Gson().toJsonTree(Map.of("mod",mod,"entity",entity,"key","attack:"+mod+":projectile/"+last)));}
       last=null;entityRegister=null;constructors.clear();
      }
     }
    }
   }
  }
  Path manifest=Path.of(args[2]);JsonObject root=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();root.add("projectiles",projectileIds);Files.writeString(manifest,new Gson().toJson(root));
  System.out.println("Resolved "+projectileIds.size()+" registered projectile/beam types from constructor factories");
 }
}
