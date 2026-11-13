package ru.pasha.adaptationwheel.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The Infinite Void's text: its tagline and description are noise until the wearer adapts to them in one 20 s stage
 * (design void-text-adaptation-2026-11-03 rev 2). The letters settle one by one in a fixed scattered order, the one of
 * the mock-up. Pure arithmetic; the server counts the ticks, the look menu draws them.
 */
public final class VoidText {
    public static final int STAGE_TICKS = 400;

    /** A stretch of a line that is either settled or still noise; spaces belong to whichever stretch they fall in. */
    public record Run(int start, int end, boolean settled) {
    }

    private VoidText() {
    }

    public static double progress(int ticks) {
        return Math.max(0, Math.min(STAGE_TICKS, ticks)) / (double) STAGE_TICKS;
    }

    /** Rounds down: 100 only when the stage is done. */
    public static int percent(int ticks) {
        return Math.max(0, Math.min(STAGE_TICKS, ticks)) * 100 / STAGE_TICKS;
    }

    public static int secondsLeft(int ticks) {
        return (STAGE_TICKS - Math.max(0, Math.min(STAGE_TICKS, ticks)) + 19) / 20;
    }

    /** Whether the letter at this place of the whole text has settled at this progress (0..1). */
    public static boolean settled(int index, double progress) {
        return (index * 2654435761L & 0xFFFFFFFFL) % 1000 / 1000.0 < progress;
    }

    /** One line of the text cut into stretches; {@code offset} is where the line starts in the whole text. */
    public static List<Run> runs(String line, int offset, double progress) {
        List<Run> runs = new ArrayList<>();
        int start = 0;
        Boolean state = null;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') continue;
            boolean settled = settled(offset + i, progress);
            if (state != null && settled != state) {
                runs.add(new Run(start, i, state));
                start = i;
            }
            state = settled;
        }
        if (!line.isEmpty()) runs.add(new Run(start, line.length(), state != null && state));
        return runs;
    }
}
