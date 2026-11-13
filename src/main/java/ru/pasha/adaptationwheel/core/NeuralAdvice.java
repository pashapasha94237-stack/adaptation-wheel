package ru.pasha.adaptationwheel.core;

import com.google.gson.*;
import java.util.Set;

/** Strict data-only response. It cannot grant stages, protection, damage or commands. */
public record NeuralAdvice(AdaptationProfile profile,int firstSeconds,int stepSeconds,int confidence) {
    public NeuralAdvice {if(profile==null||firstSeconds<3||firstSeconds>600||stepSeconds<5||stepSeconds>300||confidence<0||confidence>95)throw new IllegalArgumentException("Out-of-range neural advice");}
    public StageDurations timings(){return StageDurations.seconds(firstSeconds,firstSeconds+stepSeconds,firstSeconds+stepSeconds*2,firstSeconds+stepSeconds*3,firstSeconds+stepSeconds*4);}
    public static NeuralAdvice parse(String json){
        if(json==null||json.length()>2048)throw new IllegalArgumentException("Oversized neural advice");
        JsonObject o=JsonParser.parseString(json).getAsJsonObject();
        if(!o.keySet().equals(Set.of("profile","firstSeconds","stepSeconds","confidence")))throw new IllegalArgumentException("Unexpected advice fields");
        return new NeuralAdvice(AdaptationProfile.valueOf(o.get("profile").getAsString()),integer(o,"firstSeconds"),integer(o,"stepSeconds"),integer(o,"confidence"));
    }
    private static int integer(JsonObject o,String key){var p=o.get(key);if(!p.isJsonPrimitive()||!p.getAsJsonPrimitive().isNumber()||!p.getAsString().matches("[0-9]{1,4}"))throw new IllegalArgumentException("Invalid advice integer");return p.getAsInt();}
}
