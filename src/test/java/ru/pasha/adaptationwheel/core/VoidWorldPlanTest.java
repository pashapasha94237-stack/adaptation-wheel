package ru.pasha.adaptationwheel.core;

/** The Infinite Void's world effects keep the mock-up's numbers (design infinite-void-world-2026-11-10 rev 2). */
public final class VoidWorldPlanTest {
    private VoidWorldPlanTest() {
    }

    public static void run() {
        dice();
        stream();
        stars();
        System.out.println("VoidWorldPlanTest passed");
    }

    private static void dice() {
        check(VoidWorldPlan.fnv("") == 0x811C9DC5, "FNV-1a of nothing is the offset basis");
        check(VoidWorldPlan.fnv("a") == 0xE40C292C, "FNV-1a of 'a' is the known value");
        check(VoidWorldPlan.fnv("foobar") == 0xBF9CF968, "FNV-1a of 'foobar' is the known value");
        VoidWorldPlan.Rng r = new VoidWorldPlan.Rng(0);
        check(Math.abs(r.next() - 1013904223 / 4294967296.0) < 1e-15, "the LCG's first step from 0 is its increment");
        VoidWorldPlan.Rng a = new VoidWorldPlan.Rng("star|3"), b = new VoidWorldPlan.Rng("star|3");
        for (int i = 0; i < 100; i++) {
            double x = a.next();
            check(x >= 0 && x < 1 && x == b.next(), "the LCG is in [0,1) and repeatable");
        }
    }

    private static void stream() {
        check(VoidWorldPlan.lines(1) == 1 && VoidWorldPlan.lines(3) == 2 && VoidWorldPlan.lines(6) == 3 && VoidWorldPlan.lines(9) == 4,
                "one line, two after 2 s, three after 5 s, four after 8 s");
        check(VoidWorldPlan.frozen(7.9) == 0 && VoidWorldPlan.frozen(8.3) > 0 && VoidWorldPlan.frozen(9) == 1, "hangs from 8 s");
        check(VoidWorldPlan.melt(0) == 1 && VoidWorldPlan.melt(1) == 0.5 && VoidWorldPlan.melt(2) == 0, "melts in 2 s");
        check(VoidWorldPlan.lineLength(0, 1) == 5 && VoidWorldPlan.lineLength(0.99, 2) == 8 && VoidWorldPlan.lineLength(0, 3) == 8,
                "5-8 glyphs, three more from the third line");
        check(VoidWorldPlan.lineAlpha(-0.1) == 0 && VoidWorldPlan.lineAlpha(1.6) == 0 && VoidWorldPlan.lineAlpha(0.8) == 1, "a line fades in and out");
        for (int i = 0; i < 1000; i++) {
            double roll = i / 1000.0;
            check(VoidWorldPlan.GLYPHS.indexOf(VoidWorldPlan.glyph(roll)) >= 0, "a glyph comes from the look's noise");
        }
        check(VoidWorldPlan.tone(0.1) == VoidWorldPlan.WHITE && VoidWorldPlan.tone(0.5) == VoidWorldPlan.CYAN
                && VoidWorldPlan.tone(0.9) == VoidWorldPlan.VIOLET && VoidWorldPlan.tone(0.97) == VoidWorldPlan.RED, "glyph tones");
        for (long tick = 0; tick < 500; tick++) {
            double[] j = VoidWorldPlan.tremble(42, tick, 1);
            check(Math.abs(j[0]) <= 0.05 && Math.abs(j[1]) <= 0.05, "the tremble stays within 0.05 block");
            double[] none = VoidWorldPlan.tremble(42, tick, 0);
            check(none[0] == 0 && none[1] == 0, "no tremble before the mob hangs");
        }
        for (int c = 0; c < 5; c++) {
            double[] d = VoidWorldPlan.drip(7, c);
            check(d[0] >= 0.6 && d[0] <= 1.1 && d[1] >= 0 && d[1] < 1, "drip speed and phase");
            for (double t = 0; t < 10; t += 0.05) {
                double head = VoidWorldPlan.dripHead(t, d[0], d[1], 1.9);
                check(head <= 1.9 + 0.15 + 1e-9 && head > 1.9 + 0.15 - (1.9 + 0.5) - 1e-9, "a drip runs from over the head to under the feet");
            }
        }
    }

    private static void stars() {
        for (int i = 0; i < VoidWorldPlan.STARS; i++) {
            for (long cycle = 0; cycle < 20; cycle++) {
                double[] born = VoidWorldPlan.star(i, cycle, 0);
                double r0 = Math.sqrt(born[0] * born[0] + born[1] * born[1] / 0.64 + born[2] * born[2]);
                check(r0 >= 2.3 && r0 <= 4.07, "a spark is born 2.3-4 blocks out: " + r0);
                double[] end = VoidWorldPlan.star(i, cycle, VoidWorldPlan.STAR_LIFE);
                check(Math.sqrt(end[0] * end[0] + end[1] * end[1] + end[2] * end[2]) < 0.07, "a spark ends in the core");
                double previous = Double.MAX_VALUE;
                for (double local = 1.7; local <= VoidWorldPlan.STAR_LIFE; local += 0.1) {
                    double[] s = VoidWorldPlan.star(i, cycle, local);
                    double d = Math.sqrt(s[0] * s[0] + s[1] * s[1] / 0.64 + s[2] * s[2]);
                    check(d <= previous + 1e-9, "a spark falls in steadily");
                    previous = d;
                }
            }
            for (double t = 0; t < 20; t += 0.37) {
                double[] time = VoidWorldPlan.starTime(i, t);
                check(time[1] >= 0 && time[1] < VoidWorldPlan.STAR_LIFE, "a spark's age stays in its life");
            }
        }
        check(VoidWorldPlan.starAlpha(0) == 0 && VoidWorldPlan.starAlpha(0.5) == 1 && VoidWorldPlan.starAlpha(1) == 0, "a spark fades in and out");
        check(VoidWorldPlan.starFlash(0.9) == 0 && VoidWorldPlan.starFlash(0.94) > 0, "the core flashes as a spark dies");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
