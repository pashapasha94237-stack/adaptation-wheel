package ru.pasha.adaptationwheel.core;

/** Monotonic observation window. Pauses/disconnects and long sample gaps break continuity. */
public final class SustainedPressure {
    private long began=-1,last=-1;
    public boolean sample(long now,boolean pressure,long required,long maxGap){
        if(!pressure||last>=0&&(now<last||now-last>maxGap)){began=-1;last=-1;}
        if(!pressure)return false;
        if(began<0)began=now;last=now;return now-began>=required;
    }
    public void clear(){began=last=-1;}
}
