package ru.pasha.adaptationwheel.core;

/**
 * Mastered sources of one element make the next ones adapt faster: a starting speed on top of the repeat hits
 * (+5 % per source, at most +35 %). Stage times, stages and protection never carry over.
 */
public final class ElementExperience {
    public static final int PER_SOURCE_PERCENT=5,MOST_PERCENT=35;
    private ElementExperience() {}
    /** Environment rows are unrelated oddities (world border, log errors): they share a label, not an element. */
    public static int percent(AdaptationProfile profile,int mastered) {
        if(profile==null||profile==AdaptationProfile.ENVIRONMENT||mastered<=0)return 0;
        return (int)Math.min(MOST_PERCENT,(long)PER_SOURCE_PERCENT*mastered);
    }
}
