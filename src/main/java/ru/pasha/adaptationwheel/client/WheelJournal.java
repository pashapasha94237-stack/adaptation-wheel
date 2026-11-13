package ru.pasha.adaptationwheel.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/**
 * «Недавно» of the wheel's summary: what happened to the wheel during this game, found by comparing each synced state
 * with the one before (an adaptation started, a stage passed, an adaptation finished, the wheel put on or taken off).
 * The first state after joining is the baseline and makes no events. Pure, unit-tested in {@code WheelSummaryTest}.
 */
final class WheelJournal {
    static final int LIMIT = 64;

    enum Kind { STARTED, STAGE, FINISHED, WORN, UNWORN }

    /** One event; for an adaptation its key, category, the stage reached, the stage count and the protection it gives. */
    record Event(Kind kind, String key, String translationKey, AdaptationProfile profile, int stage, int total, int percent,
                 boolean protects, long at) {
        boolean wheel() { return kind == Kind.WORN || kind == Kind.UNWORN; }
    }

    /** What the journal needs of an adaptation. */
    record Seen(String key, String translationKey, AdaptationProfile profile, int stage, int total, int[] percents, boolean protects) {
        int percent(int stage) { return percents.length == 0 ? 0 : percents[Math.max(0, Math.min(stage, percents.length - 1))]; }
    }

    private final List<Event> events = new ArrayList<>();
    private Map<String, Seen> last;
    private boolean equipped;

    /** Compares with the previous state; returns how many events were added. */
    int observe(boolean equipped, List<Seen> entries, long now) {
        Map<String, Seen> current = new HashMap<>();
        for (Seen seen : entries) current.put(seen.key(), seen);
        if (last == null) {
            last = current;
            this.equipped = equipped;
            return 0;
        }
        int before = events.size();
        if (equipped != this.equipped) add(new Event(equipped ? Kind.WORN : Kind.UNWORN, "", "", null, 0, 0, 0, false, now));
        for (Seen seen : entries) {
            Seen was = last.get(seen.key());
            int from = was == null ? 0 : was.stage();
            if (was == null) add(event(Kind.STARTED, seen, 0, now));
            // A jump of several stages (a command) is told stage by stage; the last of all is «завершена».
            for (int stage = from + 1; stage <= seen.stage(); stage++)
                add(event(stage >= seen.total() ? Kind.FINISHED : Kind.STAGE, seen, stage, now));
        }
        last = current;
        this.equipped = equipped;
        return events.size() - before;
    }

    private static Event event(Kind kind, Seen seen, int stage, long now) {
        return new Event(kind, seen.key(), seen.translationKey(), seen.profile(), stage, seen.total(),
                kind == Kind.STARTED ? 0 : seen.percent(stage), seen.protects(), now);
    }

    private void add(Event event) {
        events.add(event);
        if (events.size() > LIMIT) events.remove(0);
    }

    /** Newest first. */
    List<Event> recent() {
        List<Event> result = new ArrayList<>(events);
        java.util.Collections.reverse(result);
        return result;
    }

    /** One adaptation's events, newest first. */
    List<Event> of(String key) {
        List<Event> result = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0; i--) if (events.get(i).key().equals(key)) result.add(events.get(i));
        return result;
    }

    /** Stages passed (the finishing one included), adaptations finished and adaptations started during this game. */
    int[] totals() {
        int stages = 0, finished = 0, started = 0;
        for (Event event : events) switch (event.kind()) {
            case STAGE -> stages++;
            case FINISHED -> { stages++; finished++; }
            case STARTED -> started++;
            default -> { }
        }
        return new int[]{stages, finished, started};
    }

    void clear() {
        events.clear();
        last = null;
    }

    /** How long ago, for «только что» / «4 мин назад» / «2 ч назад»: {0, 0}, {1, minutes} or {2, hours}. */
    static long[] ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) return new long[]{0, 0};
        if (seconds < 3600) return new long[]{1, seconds / 60};
        return new long[]{2, seconds / 3600};
    }
}
