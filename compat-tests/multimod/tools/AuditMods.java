import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class AuditMods {
 static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[1]);Files.createDirectories(out);
  List<Object> inventory=new ArrayList<>();
  for(String path:Files.readAllLines(Path.of(args[0]))) {
   String file=Path.of(path).getFileName().toString(),stem=file.replace(".jar","");
   Path root=out.resolve(stem);Files.createDirectories(root);
   List<Object> classes=new ArrayList<>(),methods=new ArrayList<>();List<String> dangerous=new ArrayList<>();
   Map<String,Object> resources=new LinkedHashMap<>();
   try(ZipFile zip=new ZipFile(path)) {
    for(var entry:Collections.list(zip.entries())) {
     String name=entry.getName();
     if(name.equals("META-INF/mods.toml")){Files.write(root.resolve("mods.toml"),zip.getInputStream(entry).readAllBytes());continue;}
     if(name.matches("assets/[^/]+/lang/(en_us|ru_ru)\\.json")||name.matches("data/[^/]+/(damage_type|tags/damage_type)/.*\\.json")) {
      try{resources.put(name,JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8)));}catch(Exception ignored){}
     }
     if(!name.endsWith(".class")||name.startsWith("META-INF/"))continue;
     ClassNode cls=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(cls,ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);
     classes.add(Map.of("name",cls.name,"super",String.valueOf(cls.superName)));
     for(MethodNode method:cls.methods) {
      List<String> strings=new ArrayList<>(),calls=new ArrayList<>(),fields=new ArrayList<>();boolean sensitive=false;
      for(var instruction:method.instructions) {
       if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof String s)strings.add(s);
       if(instruction instanceof FieldInsnNode f)fields.add(f.getOpcode()+" "+f.owner+"."+f.name+" "+f.desc);
       if(instruction instanceof MethodInsnNode c){
        calls.add(c.owner+"."+c.name+c.desc);
        if(c.name.matches("m_6469_|m_21153_|m_7290_|m_19467_|m_21190_|m_147207_|m_147208_|m_147209_|m_19457_|m_19443_|m_20334_|m_20256_|m_6667_|m_20254_|m_21199_|m_146870_|m_8101_")
          ||c.name.matches(".*(amage|nfect|adiation|ollution|emperature|xygen|lend|reeze|fflict|ffect|lood|ornado|nockback|bsorb).*"))sensitive=true;
       }
      }
      // Retain all methods for evidence-driven caller/field analysis, avoiding generated language/runtime classes.
      methods.add(Map.of("owner",cls.name,"name",method.name,"desc",method.desc,"calls",calls,"strings",strings,"fields",fields));
      if(sensitive)dangerous.add(cls.name+"\t"+method.name+method.desc+"\t"+String.join(" | ",strings));
     }
    }
   }
   Files.writeString(root.resolve("classes.json"),JSON.toJson(classes));
   Files.writeString(root.resolve("methods.json"),JSON.toJson(methods));
   Files.writeString(root.resolve("resources.json"),JSON.toJson(resources));
   Files.write(root.resolve("sensitive.tsv"),dangerous);
   inventory.add(Map.of("file",file,"path",path,"classes",classes.size(),"methods",methods.size(),"sensitive",dangerous.size()));
   System.out.println(file+": "+classes.size()+" classes, "+dangerous.size()+" sensitive methods");
  }
  Files.writeString(out.resolve("inventory.json"),JSON.toJson(inventory));
 }
}
