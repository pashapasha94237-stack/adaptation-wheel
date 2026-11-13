package ru.pasha.adaptationwheel.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/**
 * «Сводка колеса» of the J menu (Hub design ui-five-variants-2026-10-31 rev 3): what comes next, the counters, the
 * protection per category with the weak places, the marks around the wheel and the wheel pictures themselves. Pure,
 * unit-tested in {@code WheelSummaryTest}; {@link AdaptationScreen} draws it.
 */
final class WheelSummary {
    /** List groups, the same numbers as the menu's: running, waiting, finished, switched off. */
    static final int RUNNING = 0, WAITING = 1, DONE = 2, OFF = 3;
    /** Mark colours: finished gold, running light, waiting grey, switched off faint. */
    static final int LIT = 0xFFFFF0C8;
    /** Below this best protection a met category counts as a weak place. */
    static final int WEAK_BELOW = 40;

    private WheelSummary() {
    }

    /**
     * One learned adaptation as the summary sees it: its group, earned stages of how many, the running stage's share
     * done, real seconds left (negative while paused), protection now, whether it protects at all.
     */
    record Item(String key, AdaptationProfile profile, int group, int stage, int total, float running, float seconds,
                int reduction, boolean protects) {
        boolean moving() { return group == RUNNING && seconds >= 0; }
        /** How much of the mark is filled: earned stages plus the running one's share. */
        float fill() { return Math.min(1, (stage + (group == RUNNING ? running : 0)) / (float) Math.max(1, total)); }
    }

    /** Running, waiting and switched-off counts, finished and all, for the counters and «8 из 14». */
    static int[] counts(List<Item> items) {
        int[] counts = new int[5];
        for (Item item : items) {
            counts[item.group()]++;
            counts[4]++;
        }
        return counts;
    }

    /** Running adaptations, the soonest to end first; paused ones after the moving ones. */
    static List<Item> next(List<Item> items) {
        List<Item> running = new ArrayList<>();
        for (Item item : items) if (item.group() == RUNNING) running.add(item);
        running.sort(Comparator.comparing((Item item) -> !item.moving()).thenComparingDouble(Item::seconds));
        return running;
    }

    /** The adaptation that ends before the others («закончится первой»), or null unless two or more are moving. */
    static String first(List<Item> items) {
        Item best = null;
        int moving = 0;
        for (Item item : items) {
            if (!item.moving()) continue;
            moving++;
            if (best == null || item.seconds() < best.seconds()) best = item;
        }
        return moving >= 2 ? best.key() : null;
    }

    /** A category's best protection and whether every adaptation giving it is switched off; best -1 = never met. */
    record Category(AdaptationProfile profile, int best, boolean off) {
        boolean met() { return best >= 0; }
    }

    /** Categories that protect (all but utility), met ones by best protection, then the ones never met in their order. */
    static List<Category> categories(List<Item> items) {
        List<Category> met = new ArrayList<>(), unmet = new ArrayList<>();
        for (AdaptationProfile profile : AdaptationProfile.values()) {
            if (profile == AdaptationProfile.ENVIRONMENT) continue;
            int best = -1;
            boolean on = false;
            for (Item item : items) {
                if (!item.protects() || item.profile() != profile) continue;
                if (item.group() != OFF) {
                    on = true;
                    best = Math.max(best, item.reduction());
                } else if (!on) best = Math.max(best, item.reduction());
            }
            if (best >= 0) met.add(new Category(profile, best, !on));
            else unmet.add(new Category(profile, -1, false));
        }
        met.sort(Comparator.comparingInt((Category c) -> -c.best()).thenComparing(c -> c.profile().ordinal()));
        met.addAll(unmet);
        return met;
    }

    /** Weak places: categories never met (up to {@code max}) and met ones below {@link #WEAK_BELOW}% (weakest first). */
    static List<Category> unmet(List<Category> categories, int max) {
        List<Category> result = new ArrayList<>();
        for (Category category : categories) if (!category.met() && result.size() < max) result.add(category);
        return result;
    }

    static List<Category> low(List<Category> categories, int max) {
        List<Category> result = new ArrayList<>();
        for (Category category : categories) if (category.met() && category.best() < WEAK_BELOW) result.add(category);
        result.sort(Comparator.comparingInt(Category::best));
        return result.size() > max ? result.subList(0, max) : result;
    }

    /** Marks around the wheel and in the summary row: finished first, then running, waiting, switched off (as the mock). */
    static List<Item> marks(List<Item> items) {
        List<Item> marks = new ArrayList<>(items);
        marks.sort(Comparator.comparingInt(item -> item.group() == DONE ? 0 : item.group() == RUNNING ? 1 : item.group() == WAITING ? 2 : 3));
        return marks;
    }

    static int markColor(int group) {
        return group == DONE ? WheelUi.GOLD : group == RUNNING ? LIT : group == WAITING ? WheelUi.MUTED : WheelUi.FAINT;
    }

    /**
     * The wheel with a ring of marks, one per adaptation, each filled clockwise by its passed stages; ARGB per pixel,
     * 0 = nothing. size×size pixels around the centre; rc is the wheel's radius, m0..m1 the ring's. Faded when the
     * wheel is not worn. The mock's ringWheel, pixel for pixel.
     */
    static int[] ring(int size, float rc, float m0, float m1, int[] colors, float[] fills, boolean worn) {
        int[] pixels = new int[size * size];
        float c = size / 2f;
        int n = colors.length;
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
            double dx = px + 0.5 - c, dy = py + 0.5 - c, r = Math.hypot(dx, dy);
            int color = 0;
            if (r >= m0 && r < m1) {
                if (n == 0) color = WheelUi.TRACK;
                else {
                    double t = ((Math.atan2(dx, -dy) + Math.PI * 2) % (Math.PI * 2)) / (Math.PI * 2) * n;
                    int index = Math.min(n - 1, (int) Math.floor(t));
                    double f = t - Math.floor(t);
                    if (f < 0.1 || f > 0.9) continue;
                    double along = (f - 0.1) / 0.8;
                    color = along < fills[index] ? (worn ? colors[index] : WheelUi.dim(colors[index])) : WheelUi.TRACK;
                }
            } else {
                double a = Math.atan2(dy, dx), k = Math.round(a / (Math.PI / 4)), da = a - k * Math.PI / 4;
                double perp = r * Math.abs(Math.sin(da));
                if (r < rc * 0.2) color = r < rc * 0.09 ? 0xFF5A4110 : 0xFFF6D77A;
                else if (r >= rc * 0.74 && r < rc * 0.94) color = (r >= rc * 0.89 || r < rc * 0.79) ? 0xFFB88A2A : dy < 0 ? 0xFFF6D77A : 0xFFE0B23A;
                else if (perp < 1.4 && r < rc * 1.1) color = r < rc * 0.74 ? 0xFFC99A32 : 0xFFE0B23A;
                else if (r >= rc * 1.08 && r < rc * 1.26 && perp < 2.3) color = 0xFFF6D77A;
                if (color != 0 && !worn) color = 0x66000000 | (color & 0x00FFFFFF);
            }
            pixels[py * size + px] = color;
        }
        return pixels;
    }

    /** The small 16×16 wheel of the «Сводка колеса» row and title; faded when the wheel is not worn. */
    static int[] wheelIcon(boolean faded) {
        int[] pixels = new int[256];
        for (int py = 0; py < 16; py++) for (int px = 0; px < 16; px++) {
            double dx = px - 7.5, dy = py - 7.5, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
            boolean spoke = r < 5.4 && Math.abs(Math.sin(a * 4)) < 0.28;
            int color = 0;
            if (r < 1.8) color = 0xFFF6D77A;
            else if (r >= 5.4 && r < 7.3) color = dy < 0 ? 0xFFF6D77A : 0xFFE0B23A;
            else if (r >= 7.3 && r < 8.1) color = 0xFF5A4110;
            else if (spoke) color = 0xFF9C7420;
            pixels[py * 16 + px] = color != 0 && faded ? WheelUi.dim(color) : color;
        }
        return pixels;
    }

    /** Horizontal runs of one colour in a raster: x, y, length, colour each; one fill per run instead of per pixel. */
    static int[] runs(int[] pixels, int size) {
        List<int[]> runs = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            int x = 0;
            while (x < size) {
                int color = pixels[y * size + x];
                int start = x;
                while (x < size && pixels[y * size + x] == color) x++;
                if (color != 0) runs.add(new int[]{start, y, x - start, color});
            }
        }
        int[] flat = new int[runs.size() * 4];
        for (int i = 0; i < runs.size(); i++) System.arraycopy(runs.get(i), 0, flat, i * 4, 4);
        return flat;
    }

    /**
     * Where the parts of the summary go in a pane of this width, the mock's numbers: the card's height, the wheel's
     * centre from the left, the right column's left edge and the middle between categories and «Недавно».
     */
    record Layout(boolean dense, int card, int wheelX, int wheelY, int ring, int rightX, int middle, int catRow, int barWidth) {
        static Layout of(int width, boolean dense) {
            if (dense) return new Layout(true, 92, 48, 39, 64, 102, Math.max(150, width * 216 / 442), 10, 34);
            int middle = Math.max(170, width / 2);
            return new Layout(false, 122, 68, 50, 88, 140, middle, 13, width >= 480 ? 60 : 40);
        }
    }
}
