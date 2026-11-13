package ru.pasha.adaptationwheel.core;

/** Immutable durations in server ticks, also used in authoritative menu packets. */
public record StageDurations(int first,int second,int third,int fourth,int existence) {
    public static final int MAX_SECONDS=86400;
    public StageDurations {
        for(int value:new int[]{first,second,third,fourth,existence})
            if(value<20 || value>MAX_SECONDS*20)throw new IllegalArgumentException("Stage duration out of range");
    }
    public int ticks(int stage) {
        return switch(Math.max(0,Math.min(4,stage))) {case 0->first;case 1->second;case 2->third;case 3->fourth;default->existence;};
    }
    public static StageDurations defaults(AdaptationProfile profile) {
        return new StageDurations(profile.duration(0),profile.duration(1),profile.duration(2),profile.duration(3),profile.duration(4));
    }
    public static StageDurations seconds(int a,int b,int c,int d,int e) {
        return new StageDurations(Math.multiplyExact(a,20),Math.multiplyExact(b,20),Math.multiplyExact(c,20),Math.multiplyExact(d,20),Math.multiplyExact(e,20));
    }
}
