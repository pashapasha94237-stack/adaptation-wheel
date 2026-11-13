package ru.pasha.adaptationwheel.core;

import java.util.*;
/** Bounded exact-repeat grouping; player chat and non-whitelisted warnings never enter it. */
public final class NotificationMemory {
    public record Repeat(int count,boolean group){}
    private record Seen(long first,long last,int count){}
    private final Map<String,Seen> recent=new LinkedHashMap<>();
    public static boolean safe(String text){
        String s=text.toLowerCase(Locale.ROOT);
        if(s.matches(".*(опас|вниман|умер|погиб|убит|здоров|атак|взрыв|warning|danger|death|died|killed|health|attack|explos|ошиб|error).*"))return false;
        return s.startsWith("подобрано")||s.startsWith("получено")||s.startsWith("добыто")||s.startsWith("собрано")
            ||s.startsWith("picked up")||s.startsWith("collected")||s.startsWith("received")||s.startsWith("recipe unlocked")||s.startsWith("новый рецепт");
    }
    public Repeat receive(String text,long millis,boolean unlocked){
        recent.entrySet().removeIf(e->millis-e.getValue().last>5000);
        if(text==null||text.length()>512||!safe(text))return new Repeat(1,false);
        Seen old=recent.get(text);int count=old==null?1:Math.min(9999,old.count+1);
        if(old==null&&recent.size()>=64)recent.remove(recent.keySet().iterator().next());
        recent.put(text,new Seen(old==null?millis:old.first,millis,count));
        return new Repeat(count,unlocked&&count>1);
    }
    public void clear(){recent.clear();}
}
