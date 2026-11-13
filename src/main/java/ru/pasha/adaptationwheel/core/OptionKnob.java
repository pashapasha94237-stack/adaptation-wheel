package ru.pasha.adaptationwheel.core;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/** One integer option an adaptation lowers: remembers the player's value, gives it back exactly, and never fights a change by someone else. */
public final class OptionKnob{
    private final IntSupplier option;
    private final IntConsumer setter;
    private int original=-1,applied=-1,parkedAt=-1;
    private boolean yielded,restored;
    public OptionKnob(IntSupplier option,IntConsumer setter){this.option=option;this.setter=setter;}
    /** What the option held before the cap, or -1 while nothing is capped. */
    public int original(){return original;}
    /** What the option holds because of the cap (after the option's own rounding), or -1 while nothing is capped. */
    public int applied(){return applied;}
    /** True once after a value was really given back in memory: the caller saves the options then, so a capped value is never what stays on disk. */
    public boolean takeRestored(){boolean r=restored;restored=false;return r;}
    /** goal maps the player's own value to the wanted one; null when nothing should be limited. */
    public void apply(IntUnaryOperator goal,boolean park){
        if(goal==null)end();else if(park)park();else on(goal);
    }
    private void on(IntUnaryOperator goal){
        int current=option.getAsInt();
        if(applied>=0&&current!=applied){original=applied=-1;yielded=true;}
        if(parkedAt>=0){if(current!=parkedAt)yielded=true;parkedAt=-1;}
        if(yielded)return;
        int base=applied>=0?original:current,want=goal.applyAsInt(base);
        if(want>=base){if(applied>=0){setter.accept(original);restored=true;}original=applied=-1;return;}
        if(want!=current)setter.accept(want);
        // The option rounds to its own steps (the frame limit to tens), so remember what it really holds.
        int now=option.getAsInt();
        if(now<base){if(applied<0)original=base;applied=now;}else original=applied=-1;
    }
    /** An options screen is open: its own save must store the player's value, so hand it back without a save of ours. */
    private void park(){
        if(applied<0)return;
        if(option.getAsInt()==applied){setter.accept(original);parkedAt=option.getAsInt();}else yielded=true;
        original=applied=-1;
    }
    public void end(){
        if(applied<0&&parkedAt<0&&!yielded)return;
        if(applied>=0&&option.getAsInt()==applied){setter.accept(original);restored=true;}
        original=applied=parkedAt=-1;yielded=false;
    }
}
