package ru.pasha.adaptationwheel.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Crash-safe note of what the adaptations lowered ("name=original,applied" per line), so a killed game cannot leave a capped option behind. */
public final class KnobRecord{
    public record Entry(int original,int applied){}
    private KnobRecord(){}
    public static String format(Map<String,Entry> held){
        var out=new StringBuilder();
        for(var e:held.entrySet())out.append(e.getKey()).append('=').append(e.getValue().original()).append(',').append(e.getValue().applied()).append('\n');
        return out.toString();
    }
    /** Tolerant: lines that are not a lowered value (original above applied, both positive) are skipped. */
    public static Map<String,Entry> parse(String text){
        var out=new LinkedHashMap<String,Entry>();
        if(text==null)return out;
        for(String line:text.split("\\R")){
            int eq=line.indexOf('='),comma=line.indexOf(',');
            if(eq<=0||comma<eq)continue;
            try{
                int original=Integer.parseInt(line.substring(eq+1,comma).trim()),applied=Integer.parseInt(line.substring(comma+1).trim());
                if(applied>0&&original>applied)out.put(line.substring(0,eq).trim(),new Entry(original,applied));
            }catch(NumberFormatException ignored){}
        }
        return out;
    }
    /** The value to put back after a restart, or -1 when the option holds something else (the player changed it) and must be left alone. */
    public static int recover(Entry entry,int current){return current==entry.applied()?entry.original():-1;}
}
