package ru.pasha.adaptationwheel.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/** 4.37 «Сводка колеса»: the journal of this game, what comes next, categories and weak places, the ring, the layout. */
public final class WheelSummaryTest {
    private static final int[] PERCENTS = {0, 20, 40, 70, 100, 100};

    public static void run() {
        journal();
        next();
        categories();
        ring();
        layout();
        System.out.println("WheelSummaryTest: journal of this game, next stages and «ends first», categories and weak places, ring marks, summary layout over GUI sizes.");
    }

    private static WheelJournal.Seen seen(String key, AdaptationProfile profile, int stage) {
        return new WheelJournal.Seen(key, "", profile, stage, 4, PERCENTS, true);
    }

    private static void journal() {
        WheelJournal journal = new WheelJournal();
        check(journal.observe(true, List.of(seen("fire", AdaptationProfile.FIRE, 2)), 0) == 0, "The first state is the baseline");
        check(journal.observe(true, List.of(seen("fire", AdaptationProfile.FIRE, 3)), 1000) == 1, "A stage passed");
        WheelJournal.Event stage = journal.recent().get(0);
        check(stage.kind() == WheelJournal.Kind.STAGE && stage.stage() == 3 && stage.percent() == 70, "Stage 3 gives 70%: " + stage);
        // A new key from a command jumps 0 → 4: started, three stages, then finished.
        check(journal.observe(false, List.of(seen("fire", AdaptationProfile.FIRE, 3), seen("cold", AdaptationProfile.COLD, 4)), 2000) == 6,
                "Unworn + started + 3 stages + finished");
        List<WheelJournal.Kind> kinds = journal.recent().stream().map(WheelJournal.Event::kind).toList();
        check(kinds.equals(List.of(WheelJournal.Kind.FINISHED, WheelJournal.Kind.STAGE, WheelJournal.Kind.STAGE, WheelJournal.Kind.STAGE,
                WheelJournal.Kind.STARTED, WheelJournal.Kind.UNWORN, WheelJournal.Kind.STAGE)), "Newest first: " + kinds);
        check(Arrays.equals(journal.totals(), new int[]{5, 1, 1}), "Totals: " + Arrays.toString(journal.totals()));
        check(journal.of("cold").size() == 5 && journal.of("fire").size() == 1, "Per adaptation history");
        check(journal.observe(false, List.of(seen("fire", AdaptationProfile.FIRE, 1)), 3000) == 0, "Lowering or forgetting is silent");
        check(journal.observe(true, List.of(seen("fire", AdaptationProfile.FIRE, 1)), 4000) == 1
                && journal.recent().get(0).kind() == WheelJournal.Kind.WORN, "Put on again");
        for (int i = 0; i < 100; i++) journal.observe(i % 2 == 0, List.of(), i);
        check(journal.recent().size() == WheelJournal.LIMIT, "Bounded");
        journal.clear();
        check(journal.recent().isEmpty() && journal.observe(true, List.of(seen("a", AdaptationProfile.FIRE, 3)), 0) == 0, "Cleared journal starts a new baseline");
        check(Arrays.equals(WheelJournal.ago(59_999), new long[]{0, 0}) && Arrays.equals(WheelJournal.ago(240_000), new long[]{1, 4})
                && Arrays.equals(WheelJournal.ago(7_300_000), new long[]{2, 2}), "Ago");
    }

    private static WheelSummary.Item item(String key, AdaptationProfile profile, int group, int stage, float seconds, int reduction, boolean protects) {
        return new WheelSummary.Item(key, profile, group, stage, 4, 0.5f, seconds, reduction, protects);
    }

    private static void next() {
        List<WheelSummary.Item> items = List.of(
                item("burn", AdaptationProfile.FIRE, WheelSummary.RUNNING, 2, 9.5f, 40, true),
                item("creeper", AdaptationProfile.EXPLOSION, WheelSummary.RUNNING, 1, 31, 20, true),
                item("paused", AdaptationProfile.COLD, WheelSummary.RUNNING, 1, -1, 20, true),
                item("skeleton", AdaptationProfile.PROJECTILE, WheelSummary.WAITING, 1, -1, 20, true));
        check(WheelSummary.next(items).stream().map(WheelSummary.Item::key).toList().equals(List.of("burn", "creeper", "paused")), "Soonest first, paused last");
        check("burn".equals(WheelSummary.first(items)), "Burn ends first");
        check(WheelSummary.first(items.subList(1, 4)) == null, "One moving stage is not «first» of anything");
        check(Arrays.equals(WheelSummary.counts(items), new int[]{3, 1, 0, 0, 4}), "Counts");
        List<WheelSummary.Item> withDone = new ArrayList<>(items);
        withDone.add(item("done", AdaptationProfile.FIRE, WheelSummary.DONE, 4, -1, 100, true));
        check(WheelSummary.marks(withDone).get(0).key().equals("done") && WheelSummary.marks(withDone).get(4).key().equals("skeleton"), "Marks: finished, running, waiting");
        WheelSummary.Item running = item("x", AdaptationProfile.FIRE, WheelSummary.RUNNING, 2, 1, 40, true);
        check(Math.abs(running.fill() - 2.5f / 4) < 1e-6 && item("y", AdaptationProfile.FIRE, WheelSummary.WAITING, 2, -1, 40, true).fill() == 0.5f, "Mark fill");
    }

    private static void categories() {
        List<WheelSummary.Item> items = List.of(
                item("fire", AdaptationProfile.FIRE, WheelSummary.DONE, 4, -1, 100, true),
                item("lava", AdaptationProfile.FIRE, WheelSummary.RUNNING, 2, 3, 40, true),
                item("cold", AdaptationProfile.COLD, WheelSummary.WAITING, 1, -1, 20, true),
                item("boom", AdaptationProfile.EXPLOSION, WheelSummary.OFF, 1, -1, 20, true),
                item("arrow", AdaptationProfile.PROJECTILE, WheelSummary.WAITING, 2, -1, 40, true),
                item("sleep", AdaptationProfile.ENVIRONMENT, WheelSummary.DONE, 4, -1, 0, false));
        List<WheelSummary.Category> categories = WheelSummary.categories(items);
        check(categories.size() == AdaptationProfile.values().length - 1, "Every protecting category, utility left out");
        check(categories.stream().noneMatch(c -> c.profile() == AdaptationProfile.ENVIRONMENT), "No utility row");
        check(categories.get(0).profile() == AdaptationProfile.FIRE && categories.get(0).best() == 100, "Best first");
        check(categories.get(1).profile() == AdaptationProfile.PROJECTILE, "Then 40%: " + categories.get(1));
        WheelSummary.Category boom = categories.stream().filter(c -> c.profile() == AdaptationProfile.EXPLOSION).findFirst().orElseThrow();
        check(boom.off() && boom.best() == 20, "A category only switched off is marked off");
        for (int i = 4; i < categories.size(); i++) check(!categories.get(i).met(), "Unmet after met: " + categories.get(i));
        List<WheelSummary.Category> low = WheelSummary.low(categories, 2);
        check(low.size() == 2 && low.stream().allMatch(c -> c.best() == 20), "Weak met categories: " + low);
        List<WheelSummary.Category> unmet = WheelSummary.unmet(categories, 3);
        check(unmet.size() == 3 && unmet.get(0).profile() == AdaptationProfile.PHYSICAL, "Unmet in their order: " + unmet);
    }

    private static void ring() {
        int size = 88;
        int[] full = WheelSummary.ring(size, 29, 36, 43, new int[]{WheelUi.GOLD}, new float[]{1}, true);
        int[] empty = WheelSummary.ring(size, 29, 36, 43, new int[]{WheelUi.GOLD}, new float[]{0}, true);
        int[] faded = WheelSummary.ring(size, 29, 36, 43, new int[]{WheelUi.GOLD}, new float[]{1}, false);
        int center = (size / 2) * size + size / 2;
        check(full[center] == 0xFF5A4110, "Hub in the middle");
        check(count(full, WheelUi.GOLD) > 100 && count(empty, WheelUi.GOLD) == 0 && count(empty, WheelUi.TRACK) > 100, "A full mark is gold, an empty one track");
        check(count(faded, WheelUi.GOLD) == 0 && count(faded, WheelUi.dim(WheelUi.GOLD)) > 100 && (faded[center] >>> 24) == 0x66, "Unworn wheel is faded");
        int[] many = WheelSummary.ring(size, 29, 36, 43, new int[72], new float[72], true);
        check(count(many, 0) > 0, "Gaps between 72 marks");
        int[] runs = WheelSummary.runs(full, size), back = new int[size * size];
        for (int i = 0; i < runs.length; i += 4) for (int x = 0; x < runs[i + 2]; x++) back[runs[i + 1] * size + runs[i] + x] = runs[i + 3];
        check(Arrays.equals(back, full) && runs.length / 4 < count(full, -1) / 3, "Runs redraw the raster with far fewer fills");
        int[] icon = WheelSummary.wheelIcon(false);
        check(icon[7 * 16 + 7] == 0xFFF6D77A && icon[0] == 0, "Small wheel");
    }

    private static int count(int[] pixels, int color) {
        int n = 0;
        for (int pixel : pixels) if (color == -1 ? pixel != 0 : pixel == color) n++;
        return n;
    }

    /** The summary keeps its columns apart for every wide menu and every narrow summary screen. */
    private static void layout() {
        int checked = 0;
        for (int w = 400; w <= 2400; w += 7) for (int h = 240; h <= 1000; h += 37) {
            MenuLayout menu = new MenuLayout(w, h);
            int width = menu.split ? menu.detailRight - menu.detailLeft : menu.right - menu.left - 20;
            WheelSummary.Layout at = WheelSummary.Layout.of(width, menu.compact);
            check(at.wheelX() + at.ring() / 2 < at.rightX(), "Wheel left of «Что дальше» at " + w + "×" + h);
            check(at.rightX() + 150 <= width, "«Что дальше» has room at " + w + "×" + h + ": " + width);
            check(at.middle() >= 150 && at.middle() + 8 + 120 <= width, "Categories and «Недавно» both fit at " + w + "×" + h + ": " + width);
            checked++;
        }
        check(checked > 1000, "Sizes checked");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
