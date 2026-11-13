package ru.pasha.adaptationwheel.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.StageDurations;
import ru.pasha.adaptationwheel.net.WheelEntry;

/** 4.34 «Интерфейс понятнее»: category icons, list groups, footer hints, the corner panel's place, real time left, lit stages. */
public final class UiClarityTest {
    public static void run() {
        icons();
        groups();
        hints();
        hud();
        realTime();
        completions();
        System.out.println("UiClarityTest: 16 distinct category icons, grouped list rows, whole key hints, HUD under effects and within a third, real time left, lit stages passed.");
    }

    private static void icons() {
        Set<String> seen = new HashSet<>();
        for (AdaptationProfile profile : AdaptationProfile.values()) {
            int[] icon = CategoryIcons.of(profile);
            check(icon != null && icon.length == CategoryIcons.SIZE, "Every category has a 7×7 icon: " + profile);
            check(seen.add(java.util.Arrays.toString(icon)), "Category icons differ: " + profile);
        }
        check(seen.add(java.util.Arrays.toString(CategoryIcons.GEAR)) && seen.add(java.util.Arrays.toString(CategoryIcons.SCREEN)), "Gear and screen are their own");
        check(CategoryIcons.forKey("environment:day_sleep", AdaptationProfile.ENVIRONMENT) == CategoryIcons.GEAR, "Mechanics get the gear");
        check(CategoryIcons.forKey(FpsAdaptation.KEY, AdaptationProfile.ENVIRONMENT) == CategoryIcons.SCREEN, "Low FPS gets the screen");
        check(CategoryIcons.forKey("minecraft:on_fire", AdaptationProfile.FIRE) == CategoryIcons.of(AdaptationProfile.FIRE), "Damage gets its category's");
        // Physical attacks, projectiles and hunger are almost the same colour: their icons must tell them apart.
        check(!java.util.Arrays.equals(CategoryIcons.of(AdaptationProfile.PHYSICAL), CategoryIcons.of(AdaptationProfile.PROJECTILE))
                && !java.util.Arrays.equals(CategoryIcons.of(AdaptationProfile.PHYSICAL), CategoryIcons.of(AdaptationProfile.HUNGER)), "Similar colours, different icons");
    }

    private static void groups() {
        MenuRows rows = new MenuRows(25, 13);
        rows.build(new int[]{0, 0, 1, 1, 1, 2, 3});
        check(rows.heads() == 4 && rows.headCount(0) == 2 && rows.headCount(1) == 3 && rows.headCount(2) == 1 && rows.headGroup(3) == 3, "Headings with counts");
        check(rows.top(0) == 13 && rows.top(1) == 38 && rows.top(2) == 13 + 50 + 13, "A heading pushes its rows down");
        check(rows.height() == 4 * 13 + 7 * 25, "Height: headings and rows");
        check(rows.at(5) == -1 && rows.at(13) == 0 && rows.at(13 + 22) == 0 && rows.at(13 + 23) == -1 && rows.at(38) == 1 && rows.at(70) == -1, "Headings and gaps are not rows");
        check(rows.at(rows.height() + 5) == -1, "Past the end");
        // The first row of a group brings its heading into view.
        check(rows.reveal(2, 200, 100) == rows.top(2) - 13, "Revealing a group's first row shows its heading");
        check(rows.reveal(3, 0, 100) == rows.top(3) + 25 - 100, "Revealing a lower row scrolls just enough");
        check(rows.reveal(0, 0, 100) == 0 && rows.shown(0, 0, 100) && !rows.shown(6, 0, 100), "Top rows show at once");
        check(rows.reveal(6, 0, 10_000) == 0, "No scroll when everything fits");
        check(rows.firstBelow(14) == 1 && rows.firstBelow(-25) == 0, "First row below a line");
        rows.build(new int[0]);
        check(rows.size() == 0 && rows.height() == 0 && rows.at(0) == -1 && rows.heads() == 0, "An empty list");
        // A real list: every row can be revealed and clicked where it is drawn.
        int[] groups = new int[300];
        for (int i = 0; i < groups.length; i++) groups[i] = i < 3 ? 0 : i < 20 ? 1 : i < 120 ? 2 : i < 125 ? 3 : 4;
        rows.build(groups);
        int scroll = 0, visible = 180;
        for (int i = 0; i < groups.length; i++) {
            scroll = rows.reveal(i, scroll, visible);
            check(rows.shown(i, scroll, visible), "Row " + i + " revealed");
            check(rows.at(rows.top(i) + 3) == i, "Row " + i + " clicked where drawn");
        }
    }

    private static void hints() {
        int[] widths = {40, 70, 50, 60};
        check(MenuLayout.hintsThatFit(widths, 10, 1000) == 4, "All fit");
        check(MenuLayout.hintsThatFit(widths, 10, 40) == 1, "Exactly one");
        check(MenuLayout.hintsThatFit(widths, 10, 125) == 2, "Two whole ones, the third is left out");
        check(MenuLayout.hintsThatFit(widths, 10, 39) == 0, "None rather than a cut one");
        // A later short hint is not shown out of order once a more important one did not fit.
        check(MenuLayout.hintsThatFit(new int[]{40, 200, 10}, 10, 70) == 1, "Order of importance kept");
    }

    private static void hud() {
        check(HudLayout.top(false, false) < 27 && HudLayout.top(true, false) >= 25, "No effects: at the top; beneficial ones: under their row");
        check(HudLayout.top(false, true) >= 27 + 24 && HudLayout.top(true, true) >= 27 + 24, "Under the harmful effects' row");
        // The launcher's 925×530 window at scale 2 is 265 high: at most a third of it.
        for (int height = 200; height <= 1080; height++)
            for (int entries = 1; entries <= 12; entries++) {
                int rows = HudLayout.rows(entries, height), panel = HudLayout.height(rows, entries);
                check(rows >= 1 && rows <= Math.min(5, entries), "Rows " + rows + " of " + entries + " at " + height);
                if (height >= 3 * (HudLayout.HEADER + HudLayout.ROW + HudLayout.MORE))
                    check(panel <= height / 3, "A third of " + height + ": " + panel + " for " + entries);
            }
        check(HudLayout.rows(9, 265) == 2 && HudLayout.rows(9, 472) == 5, "Launcher 2 rows + «Ещё», wide window 5");
        check(HudLayout.rows(0, 472) == 0, "Nothing to show");
    }

    private static WheelEntry running(int hits, double work) {
        return new WheelEntry("minecraft:on_fire", "x", true, false, 2, work, hits, true, true, AdaptationProfile.FIRE,
                new StageDurations(160, 240, 320, 480, 600), 30);
    }

    private static void realTime() {
        ClientWheelState.clear();
        WheelEntry entry = running(1, 320 - 248);
        ClientWheelState.accept(9802, true, true, false, List.of(entry), 1, 0);
        var state = ClientWheelState.get(9802);
        // 12.4 s of work at ×1.30 ends in 9.5 s.
        check(Math.abs(state.remaining(entry, 0) / 20 - 12.4f) < 0.01f, "Work left at ×1");
        check(Math.abs(state.realRemaining(entry, 0) / 20 - 12.4f / 1.3f) < 0.01f, "Real time at ×1.30: " + state.realRemaining(entry, 0) / 20);
WheelEntry seasoned = entry.withExperience(15);        check(Math.abs(seasoned.speed() - 1.45) < 1e-9, "Experience adds to the hit speed: " + seasoned.speed());        check(Math.abs(state.remaining(seasoned, 0) - state.remaining(entry, 0)) < 1e-4, "Shown time stays at ×1 with experience");        check(state.hitFlashAge(entry.key(), 0) == Float.MAX_VALUE, "The first packet lights no hit");        WheelEntry hit = running(2, 320 - 248);        ClientWheelState.accept(9802, true, true, false, List.of(hit), 1, 1);        check(ClientWheelState.get(9802).hitFlashAge(hit.key(), 0) < ClientWheelState.HIT_FLASH_TICKS, "A repeat hit lights the row");        for (int i = 0; i < ClientWheelState.HIT_FLASH_TICKS; i++) ClientWheelState.tick();        ClientWheelState.accept(9802, true, true, false, List.of(hit), 1, 2 + ClientWheelState.HIT_FLASH_TICKS);        check(ClientWheelState.get(9802).hitFlashAge(hit.key(), 0) == Float.MAX_VALUE, "The flash goes out after 1.5 s");
        ClientWheelState.clear();
    }

    private static void completions() {
        ClientWheelState.clear();
        WheelEntry before = running(0, 0), after = new WheelEntry("minecraft:on_fire", "x", true, false, 3, 0, 0, false, false, AdaptationProfile.FIRE,
                new StageDurations(160, 240, 320, 480, 600), 30);
        ClientWheelState.accept(9803, true, true, false, List.of(before), 1, 0);
        check(ClientWheelState.get(9803).completions().isEmpty(), "Nothing completed yet");
        ClientWheelState.accept(9803, true, true, false, List.of(after), 2, 1);
        var done = ClientWheelState.get(9803).completions();
        check(done.size() == 1 && done.get(0).key().equals("minecraft:on_fire") && done.get(0).stage() == 3, "Stage 3 reached is lit");
        for (int i = 0; i < ClientWheelState.COMPLETION_TICKS - 1; i++) ClientWheelState.tick();
        ClientWheelState.accept(9803, true, true, false, List.of(after), 2, 2);
        check(ClientWheelState.get(9803).completions().size() == 1, "Still lit just before three seconds");
        ClientWheelState.tick();
        ClientWheelState.accept(9803, true, true, false, List.of(after), 2, 3);
        check(ClientWheelState.get(9803).completions().isEmpty(), "Gone after three seconds");
        // A lowered stage (a command) is not a completion.
        ClientWheelState.accept(9803, true, true, false, List.of(before), 3, 4);
        check(ClientWheelState.get(9803).completions().isEmpty(), "A lowered stage does not light");
        ClientWheelState.clear();
    }

    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
