package ru.pasha.adaptationwheel.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure logic for adaptations to things outside the game world: files, clock, logs, idleness. */
public final class OutsideSignals {
    private OutsideSignals(){}

    public record ModChanges(List<String> added,List<String> removed,List<String> updated){
        public boolean empty(){return added.isEmpty()&&removed.isEmpty()&&updated.isEmpty();}
    }
    /** Compares mod id -> version maps from the previous and the current launch. */
    public static ModChanges diffMods(Map<String,String> before,Map<String,String> after){
        List<String> added=new ArrayList<>(),removed=new ArrayList<>(),updated=new ArrayList<>();
        for(var e:after.entrySet()){String old=before.get(e.getKey());if(old==null)added.add(e.getKey());else if(!old.equals(e.getValue()))updated.add(e.getKey()+" "+old+" → "+e.getValue());}
        for(String id:before.keySet())if(!after.containsKey(id))removed.add(id);
        Collections.sort(added);Collections.sort(removed);Collections.sort(updated);
        return new ModChanges(List.copyOf(added),List.copyOf(removed),List.copyOf(updated));
    }
    public static String encodeMods(Map<String,String> mods){
        StringBuilder b=new StringBuilder();
        new TreeMap<>(mods).forEach((id,v)->{if(b.length()>0)b.append(';');b.append(id).append('@').append(v.replace(";","_").replace("@","_"));});
        return b.toString();
    }
    public static Map<String,String> decodeMods(String text){
        Map<String,String> result=new TreeMap<>();if(text==null||text.isBlank())return result;
        for(String part:text.split(";")){int at=part.indexOf('@');if(at>0)result.put(part.substring(0,at),part.substring(at+1));}
        return result;
    }

    /** Flattened config values: path -> printable value. Returns "path: old → new" lines. */
    public static List<String> diffConfig(Map<String,String> before,Map<String,String> after){
        List<String> lines=new ArrayList<>();
        for(var e:new TreeMap<>(after).entrySet()){String old=before.get(e.getKey());if(old!=null&&!old.equals(e.getValue()))lines.add(e.getKey()+": "+clip(old)+" → "+clip(e.getValue()));}
        return List.copyOf(lines);
    }
    private static String clip(String s){return s.length()>40?s.substring(0,37)+"...":s;}

    /** Detects the system clock being moved: wall time and monotonic time disagree. */
    public static final class ClockWatch {
        public static final long THRESHOLD_MS=30_000;
        private long wall,mono;private boolean started;
        /** @return the detected wall-clock shift in milliseconds, or 0. */
        public long sample(long wallMillis,long monoNanos){
            if(!started){started=true;wall=wallMillis;mono=monoNanos;return 0;}
            long shift=(wallMillis-wall)-(monoNanos-mono)/1_000_000L;wall=wallMillis;mono=monoNanos;
            return Math.abs(shift)>=THRESHOLD_MS?shift:0;
        }
        public void reset(){started=false;}
    }

    /** Lets the first copies of a repeated log line through, then one in {@code every}. Thread-safe and bounded. */
    public static final class RepeatLimiter {
        private final int free,every,maxKeys;
        private final ConcurrentHashMap<String,AtomicInteger> counts=new ConcurrentHashMap<>();
        private final AtomicInteger suppressed=new AtomicInteger();
        public RepeatLimiter(int free,int every,int maxKeys){this.free=free;this.every=every;this.maxKeys=maxKeys;}
        public boolean allow(String key){
            if(counts.size()>=maxKeys&&!counts.containsKey(key))counts.clear();
            int n=counts.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
            if(n<=free||n%every==0)return true;
            suppressed.incrementAndGet();return false;
        }
        public int suppressed(){return suppressed.get();}
        public int count(String key){var n=counts.get(key);return n==null?0:n.get();}
        public void clear(){counts.clear();suppressed.set(0);}
    }

    /** Counts events inside a sliding time window (errors per minute). */
    public static final class RateWindow {
        private final long window;private final ArrayDeque<Long> times=new ArrayDeque<>();private final int cap;
        public RateWindow(long windowNanos,int cap){this.window=windowNanos;this.cap=cap;}
        public synchronized void add(long now){times.addLast(now);while(times.size()>cap)times.removeFirst();}
        public synchronized int count(long now){while(!times.isEmpty()&&now-times.peekFirst()>window)times.removeFirst();return times.size();}
        public synchronized void clear(){times.clear();}
    }

    /** Idle time of a player: any change of position or view resets it. */
    public static final class IdleClock {
        private double x,y,z;private float yaw,pitch;private long since=-1;
        public long update(double x,double y,double z,float yaw,float pitch,long tick){
            boolean moved=since<0||Math.abs(x-this.x)>.05||Math.abs(y-this.y)>.05||Math.abs(z-this.z)>.05||Math.abs(yaw-this.yaw)>.5F||Math.abs(pitch-this.pitch)>.5F;
            this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
            if(moved)since=tick;
            return tick-since;
        }
    }

    public static String duration(long millis){
        long s=Math.abs(millis)/1000,h=s/3600,m=s%3600/60,sec=s%60;
        String text=h>0?h+"h "+m+"m":m>0?m+"m "+sec+"s":sec+"s";
        return (millis<0?"-":"+")+text;
    }
}
