package ru.pasha.adaptationwheel.server;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/** The English death lines mods ship: the author's own words for a damage type whose id says nothing. Always English, whatever the game's language. */
final class DeathTexts {
    private static volatile Map<String,String> lines;
    private DeathTexts() {}
    static AdaptationProfile profile(String messageId) {
        return AdaptationProfile.fromText(lines().get("death.attack."+messageId));
    }
    /** Read while the world loads, not in the middle of a fight. */
    static void warm() {lines();}
    private static Map<String,String> lines() {
        if(lines==null)synchronized(DeathTexts.class){if(lines==null)lines=read();}
        return lines;
    }
    private static Map<String,String> read() {
        var rows=new HashMap<String,String>();
        for(var info:ModList.get().getModFiles())try{
            Path assets=info.getFile().findResource("assets");
            if(!Files.isDirectory(assets))continue;
            try(var spaces=Files.newDirectoryStream(assets)){
                for(Path space:spaces){
                    Path file=space.resolve("lang").resolve("en_us.json");
                    if(!Files.isRegularFile(file))continue;
                    var json=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
                    for(var row:json.entrySet())if(row.getKey().startsWith("death.attack.")&&row.getValue().isJsonPrimitive())rows.putIfAbsent(row.getKey(),row.getValue().getAsString());
                }
            }
        }catch(IOException|RuntimeException ignored){
            // One broken language file costs that mod its hints, nothing more.
        }
        return rows;
    }
}
