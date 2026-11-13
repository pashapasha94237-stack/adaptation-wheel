package ru.pasha.adaptationwheel.net;import ru.pasha.adaptationwheel.core.*;public final class WheelEntry {private final String key;
private final String translationKey;
private final boolean enabled;
private final boolean boss;
private final int stage;
private final double work;
private final int hits;
private final boolean running;
private final boolean progressing;
private final AdaptationProfile profile;
public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing,AdaptationProfile profile){this.key=key;this.translationKey=translationKey;this.enabled=enabled;this.boss=boss;this.stage=stage;this.work=work;this.hits=hits;this.running=running;this.progressing=progressing;this.profile=profile;}public String key(){return key;}
public String translationKey(){return translationKey;}
public boolean enabled(){return enabled;}
public boolean boss(){return boss;}
public int stage(){return stage;}
public double work(){return work;}
public int hits(){return hits;}
public boolean running(){return running;}
public boolean progressing(){return progressing;}
public AdaptationProfile profile(){return profile;}
public WheelEntry(String key,String translationKey,boolean enabled,boolean boss,int stage,double work,int hits,boolean running,boolean progressing){this(key,translationKey,enabled,boss,stage,work,hits,running,progressing,AdaptationProfile.forKey(key,boss));}
public int duration(){return profile.duration(stage);}public double speed(){return AdaptationState.speedFor(hits);}
public int reduction(){return AdaptationState.reductionPercent(stage);}public boolean finished(){return stage>=(boss?5:4);}
public boolean pending(){return running&&!finished();}
@Override public boolean equals(Object value){if(!(value instanceof WheelEntry))return false;WheelEntry other=(WheelEntry)value;return java.util.Objects.equals(key,other.key) && java.util.Objects.equals(translationKey,other.translationKey) && enabled==other.enabled && boss==other.boss && stage==other.stage && Double.compare(work,other.work)==0 && hits==other.hits && running==other.running && progressing==other.progressing && java.util.Objects.equals(profile,other.profile);} @Override public int hashCode(){return java.util.Objects.hash(key,translationKey,enabled,boss,stage,work,hits,running,progressing,profile);}}