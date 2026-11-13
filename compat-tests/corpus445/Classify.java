import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Replays DamageCategories.profile() for the corpus damage types dumped to damage.tsv. */
public class Classify {
    public static void main(String[] a) throws Exception {
        Path dir=Path.of(a[0]);
        String seeds=Files.readString(Path.of(a[1]));
        Set<String> seeded=new HashSet<>();
        Matcher sm=Pattern.compile("\"key\"\\s*:\\s*\"(damage:[^\"]+)\"").matcher(seeds);
        while(sm.find()) seeded.add(sm.group(1));
        Map<String,Integer> bySource=new TreeMap<>();
        Map<String,int[]> byMod=new TreeMap<>();
        List<String> out=new ArrayList<>();
        for(String line:Files.readAllLines(dir.resolve("damage.tsv"))) {
            String[] p=line.split("\t",-1);
            String id=p[0], msg=p[1], jar=p[4];
            List<String> tags=p[2].isEmpty()?List.of():List.of(p[2].split(","));
            String text=p[3];
            String src; AdaptationProfile prof=null;
            if(tags.contains("minecraft:is_lightning")){prof=AdaptationProfile.ELECTRIC;src="vanilla-tag";}
            else if(tags.contains("minecraft:is_explosion")){prof=AdaptationProfile.EXPLOSION;src="vanilla-tag";}
            else if(tags.contains("minecraft:is_fire")){prof=AdaptationProfile.FIRE;src="vanilla-tag";}
            else if(tags.contains("minecraft:is_freezing")){prof=AdaptationProfile.COLD;src="vanilla-tag";}
            else if(tags.contains("minecraft:is_drowning")){prof=AdaptationProfile.SUFFOCATION;src="vanilla-tag";}
            else if(tags.contains("minecraft:is_fall")){prof=AdaptationProfile.PHYSICAL;src="vanilla-tag";}
            else src=null;
            if(prof==null) for(String t:tags) {
                if(t.startsWith("minecraft:")||t.startsWith("adaptationwheel:")) continue;
                AdaptationProfile x=AdaptationProfile.fromTag(t.substring(t.indexOf(':')+1));
                if(x!=null){prof=x;src="mod-tag";break;}
            }
            String path=id.substring(id.indexOf(':')+1);
            if(prof==null){prof=AdaptationProfile.fromName(path,null);if(prof!=null)src="name";}
            if(prof==null){prof=AdaptationProfile.fromName(msg.substring(msg.lastIndexOf(':')+1),null);if(prof!=null)src="msg-id";}
            if(prof==null&&!text.isEmpty()){prof=AdaptationProfile.fromText(text);if(prof!=null)src="death-text";}
            if(prof==null&&tags.contains("minecraft:is_projectile")){prof=AdaptationProfile.PROJECTILE;src="projectile-tag";}
            if(prof==null){prof=AdaptationProfile.ENVIRONMENT;src="fallback";}
            boolean seed=seeded.contains("damage:"+id);
            if(seed) src="seed+"+src;
            bySource.merge(src.startsWith("seed+")?"seed":src,1,Integer::sum);
            String ns=id.substring(0,id.indexOf(':'));
            int[] c=byMod.computeIfAbsent(ns,k->new int[3]);
            c[0]++; if(src.endsWith("fallback")) c[1]++; if(src.endsWith("name")||src.endsWith("msg-id")||src.endsWith("death-text")) c[2]++;
            out.add(id+"\t"+prof+"\t"+src+"\t"+tags+"\t"+text);
        }
        Files.write(dir.resolve("damage-classified.tsv"),out);
        System.out.println("by source: "+bySource);
        StringBuilder sb=new StringBuilder("per mod (total/fallback/name):");
        byMod.entrySet().stream().sorted((x,y)->y.getValue()[1]*10+y.getValue()[0]-x.getValue()[1]*10-x.getValue()[0])
            .limit(45).forEach(e->sb.append(' ').append(e.getKey()).append('=').append(e.getValue()[0]).append('/').append(e.getValue()[1]).append('/').append(e.getValue()[2]));
        System.out.println(sb);
    }
}
