package ru.pasha.adaptationwheel.core;

/** Absolute damage reduction at each earned degree, not an additive bonus. */
public record StageProtections(int first,int second,int third,int fourth) {
    public static final StageProtections DEFAULTS=new StageProtections(20,40,70,100);
    public StageProtections {
        for(int value:new int[]{first,second,third,fourth})
            if(value<0||value>100)throw new IllegalArgumentException("Protection percentage out of range");
    }
    public int percent(int stage) {
        if(stage<=0)return 0;
        return switch(stage){case 1->first;case 2->second;case 3->third;case 4->fourth;default->100;};
    }
}
