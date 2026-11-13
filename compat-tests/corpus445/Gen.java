import ru.pasha.adaptationwheel.core.AdaptationProfile;
import java.nio.file.*;
import java.util.*;

/** Builds the 4.45 effect table and mob seeds from the corpus dumps (effects.tsv, mobs.tsv). */
public class Gen {
    static final String NOT_MOB=".*:.*(dart|spit|crystal|ball|arrow|bolt|bullet|projectile|tnt|knife|shard|orb|bomb|grenade|missile|spell|beam|ray|wave|parachute|present|snowball|charge|rocket|shot|blast|mine|trap|boat|cart|cloud|area|breath|flame|fire$|lightning|_bolt|spark|laser|meteor|seed|potion|bottle|item|block|falling|effect|field|portal|vortex|zone|aura|nova|burst|explosion|explosive|bomb|cannon|turret|trail|ember|flare|pellet|needle|bullet|spear|javelin|dagger|blade|slash|strike|storm|tornado|mark|sigil|totem|pillar|wall|circle|rune|chain|hook|net|web|egg|lure|stone|rock|boulder|ice_|frost_|icicle|wisp|ghost_fire|fireball|_ball|bubble|drop|glob|blob|sphere|star|comet|pulse|shockwave|jet|shell|missile|hammer|axe|sword|thrown|throwable|torpedo|mortar|flak|ammo|bean|fuel|liquid|gas|smoke|dust|particle|light$|heat|sun$|moon$|grave|pile|crate|chest|barrel|statue|core|device|machine|generator|reactor|launcher|broom|mount|tomato|bobber|boomerang|splash|painting|harpoon|cone_|hex|pearl|ground|fang|chakra|quill|eruption|skull|armor_stand).*";
    static AdaptationProfile guess(String id,String name) {
        String path=id.substring(id.indexOf(':')+1);
        AdaptationProfile p=AdaptationProfile.fromName(path,null);
        if(p!=null) return p;
        return AdaptationProfile.fromName(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_"),null);
    }
    public static void main(String[] a) throws Exception {
        Path dir=Path.of(a[0]);
        StringBuilder eff=new StringBuilder("{\n  \"_comment\": \"4.45.0: harmful effects of the 616-jar corpus whose id alone says nothing; the category comes from the English name.\",\n  \"effects\": {\n");
        TreeMap<String,String> effects=new TreeMap<>();
        for(String line:Files.readAllLines(dir.resolve("effects.tsv"))) {
            String[] p=line.split("\t",-1);
            if(p.length<2||p[0].startsWith("minecraft:")) continue;
            String path=p[0].substring(p[0].indexOf(':')+1);
            if(AdaptationProfile.fromName(path,null)!=null) continue;
            AdaptationProfile n=AdaptationProfile.fromName(p[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_"),null);
            if(n==null||n==AdaptationProfile.EFFECT||n==AdaptationProfile.ENVIRONMENT) continue;
            effects.put(p[0],n.name());
        }
        int i=0;
        for(var e:effects.entrySet()) eff.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append('"').append(++i<effects.size()?",\n":"\n");
        eff.append("  }\n}\n");
        Files.writeString(Path.of(a[1]),eff.toString());
        StringBuilder mobs=new StringBuilder();
        int m=0;
        Set<String> seen=new HashSet<>();
        for(String line:Files.readAllLines(dir.resolve("mobs.tsv"))) {
            String[] p=line.split("\t",-1);
            if(p.length<2||p[0].startsWith("minecraft:")||!seen.add(p[0])) continue;
            AdaptationProfile g=guess(p[0],p[1]);
            if(g==null||g==AdaptationProfile.PHYSICAL||g==AdaptationProfile.EFFECT||g==AdaptationProfile.ENVIRONMENT
               ||g==AdaptationProfile.HUNGER||g==AdaptationProfile.SUFFOCATION) continue;
            if(p[0].matches(NOT_MOB)) continue;
            String mod=p[0].substring(0,p[0].indexOf(':'));
            mobs.append(",\n    {\n      \"key\": \"mob:").append(p[0]).append("\",\n      \"profile\": \"").append(g.name())
                .append("\",\n      \"confidence\": 72,\n      \"source\": \"seed:compat/").append(mod).append("/mob-name\"\n    }");
            m++;
        }
        Files.writeString(Path.of(a[2]),mobs.toString());
        System.out.println("effects "+effects.size()+" mobs "+m);
    }
}
