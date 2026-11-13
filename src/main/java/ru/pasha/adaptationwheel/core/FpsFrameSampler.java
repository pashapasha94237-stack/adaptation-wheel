package ru.pasha.adaptationwheel.core;

/** Counts completed render-frame intervals, including the frame limiter/VSync wait. */
public final class FpsFrameSampler {
    private static final long NANOS_PER_SECOND=1_000_000_000L;
    private boolean hasPrevious;
    private long previous, windowNanos;
    private int windowFrames;

    public record Measurement(double fps,double seconds,boolean valid) {}

    /** Null means the one-second window is still filling; an invalid result marks a loading gap. */
    public Measurement frame(long now) {
        if(!hasPrevious) { previous=now;hasPrevious=true;return null; }
        long elapsed=now-previous;
        // Duplicate callbacks must neither count another rendered frame nor reset qualification.
        if(elapsed==0) return null;
        previous=now;
        if(elapsed<0 || elapsed>FpsAdaptationState.MAX_SAMPLE_SECONDS*NANOS_PER_SECOND) {
            windowFrames=0;windowNanos=0;
            return new Measurement(0,0,false);
        }
        windowFrames++;
        windowNanos+=elapsed;
        if(windowNanos<NANOS_PER_SECOND) return null;
        double seconds=windowNanos/(double)NANOS_PER_SECOND;
        var measurement=new Measurement(windowFrames/seconds,seconds,true);
        windowFrames=0;windowNanos=0;
        return measurement;
    }

    /** Starts the next window at the first resumed frame, without crediting any paused time. */
    public void pause() { hasPrevious=false;previous=0;windowNanos=0;windowFrames=0; }
}
