package ru.pasha.adaptationwheel.core;

import java.util.ArrayDeque;

/** What one source dealt over the last seconds: standing in fire is many small hits, and one of them says little. */
public final class DamagePace {
    public static final int WINDOW_TICKS=100;
    private record Hit(long tick,double damage) {}
    private final ArrayDeque<Hit> hits=new ArrayDeque<>();
    /** Adds a hit and returns the damage inside the window, this hit included. */
    public double add(long tick,double damage) {
        // Another dimension's clock or /time set can move backwards: the window starts again.
        if(!hits.isEmpty()&&hits.peekLast().tick()>tick)hits.clear();
        while(!hits.isEmpty()&&tick-hits.peekFirst().tick()>=WINDOW_TICKS)hits.pollFirst();
        if(Double.isFinite(damage)&&damage>0)hits.addLast(new Hit(tick,damage));
        double total=0;for(Hit hit:hits)total+=hit.damage();
        return total;
    }
}
