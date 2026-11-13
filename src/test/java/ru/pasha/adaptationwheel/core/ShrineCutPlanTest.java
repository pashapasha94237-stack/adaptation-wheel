package ru.pasha.adaptationwheel.core;

import java.util.Random;

/** The Malevolent Shrine's cuts on the world around: timing and dice as the approved mock-up (rev 3) has them. */
public final class ShrineCutPlanTest {
    public static void run() {
        check(Math.abs(ShrineCutPlan.LIFE - 0.38) < 1e-12, "A cut lives 0.06 + 0.1 + 0.22 s");
        check(ShrineCutPlan.shape(-0.01) == null && ShrineCutPlan.shape(ShrineCutPlan.LIFE) == null, "No cut before it starts or after it closes");
        ShrineCutPlan.Shape sweep = ShrineCutPlan.shape(0.03);
        check(Math.abs(sweep.progress() - 0.5) < 1e-12 && sweep.core() == 1.35 && sweep.fringe() == 1.35 && sweep.heat() == 1 && sweep.glow() == 1.5 && sweep.tail() == 0,
                "Half swept, thick and white-hot at 0.03 s: " + sweep);
        ShrineCutPlan.Shape cooled = ShrineCutPlan.shape(ShrineCutPlan.DRAW + 0.07);
        check(cooled.progress() == 1 && cooled.heat() < 1e-9 && Math.abs(cooled.glow() - 1) < 1e-9 && cooled.core() > 1, "Cooled to red 0.07 s into the hold: " + cooled);
        ShrineCutPlan.Shape held = ShrineCutPlan.shape(ShrineCutPlan.DRAW + ShrineCutPlan.HOLD - 1e-9);
        check(Math.abs(held.core() - 1) < 1e-6, "Normal thickness at the end of the hold: " + held);
        // closing: the fringe is gone at 55 % of the close, the core narrows to the end, the tail runs after the tip
        ShrineCutPlan.Shape closing = ShrineCutPlan.shape(ShrineCutPlan.DRAW + ShrineCutPlan.HOLD + ShrineCutPlan.CLOSE * 0.6);
        check(closing.fringe() == 0 && Math.abs(closing.core() - 0.4) < 1e-9 && Math.abs(closing.tail() - 0.36) < 1e-9, "The red edge goes first: " + closing);
        double last = 2, lastTail = 0;
        for (double a = ShrineCutPlan.DRAW; a < ShrineCutPlan.LIFE; a += 0.005) {
            ShrineCutPlan.Shape s = ShrineCutPlan.shape(a);
            check(s.core() <= last + 1e-12, "The core only narrows after the sweep");
            check(s.tail() >= lastTail && s.tail() < 1, "The tail only runs on, up to the tip");
            last = s.core();
            lastTail = s.tail();
        }
        check(ShrineCutPlan.heatRgb(1) == 0xFFFFFF && ShrineCutPlan.heatRgb(0) == 0xFF2030, "White-hot to red");
        check(ShrineCutPlan.taper(0) == 0 && Math.abs(ShrineCutPlan.taper(1)) < 1e-6 && ShrineCutPlan.taper(0.5) == 1
                && ShrineCutPlan.taperTable(10).length == 11, "A blade is thin at both ends, full in the middle");
        check(ShrineCutPlan.scar(0.3) == 1 && Math.abs(ShrineCutPlan.scar(ShrineCutPlan.LIFE + ShrineCutPlan.SCAR / 2) - 0.5) < 1e-9
                && ShrineCutPlan.scar(ShrineCutPlan.LIFE + ShrineCutPlan.SCAR) == 0, "A scar fades over 1.8 s after the cut");
        check(ShrineCutPlan.LIFE + ShrineCutPlan.SCAR > 2 && ShrineCutPlan.LIFE + ShrineCutPlan.SCAR < 2.3, "The whole mark is gone in a little over 2 s");
        check(ShrineCutPlan.ember(0.1) == 1 && ShrineCutPlan.ember(ShrineCutPlan.LIFE * 0.6 + 0.5) == 0 && ShrineCutPlan.scar(ShrineCutPlan.LIFE * 0.6 + 0.5) > 0.5,
                "The ember line cools long before the scar fades");

        Random r = new Random(17);
        double total = 0, totalExistence = 0;
        long cuts = 0;
        int n = 100_000;
        double minEl = 90, maxEl = -90;
        for (int i = 0; i < n; i++) {
            double x = r.nextDouble();
            total += ShrineCutPlan.volleyGap(x, false);
            totalExistence += ShrineCutPlan.volleyGap(x, true);
            int count = ShrineCutPlan.volleyCount(r.nextDouble());
            check(count == 2 || count == 3, "A volley is 2 or 3 cuts");
            cuts += count;
            double[] d = ShrineCutPlan.ray(r.nextDouble(), r.nextDouble());
            check(Math.abs(Math.hypot(Math.hypot(d[0], d[1]), d[2]) - 1) < 1e-9, "A ray direction has unit length");
            double el = Math.toDegrees(Math.asin(d[1]));
            minEl = Math.min(minEl, el);
            maxEl = Math.max(maxEl, el);
            double len = ShrineCutPlan.blockLength(r.nextDouble());
            check(len >= 1.1 && len <= 2.8, "Block cut length");
            double sag = Math.abs(ShrineCutPlan.blockSag(r.nextDouble(), r.nextDouble()));
            check(sag >= 0.05 && sag <= 0.12, "A cut on a block bends a little");
            double gap = ShrineCutPlan.mobGap(r.nextDouble(), false);
            check(gap >= 0.5 && gap <= 1.1, "A body is cut every 0.5–1.1 s");
            double slope = ShrineCutPlan.mobSlope(r.nextDouble(), r.nextDouble());
            check(Math.abs(Math.toDegrees(slope)) >= 20 && Math.abs(Math.toDegrees(slope)) <= 80, "A cut across a body slopes 20–80°");
            check(ShrineCutPlan.crossSlope(slope, r.nextDouble()) * slope < 0, "The second cut across a body slopes the other way");
        }
        double rate = cuts / total, rateExistence = cuts / totalExistence;
        check(rate > 9.5 && rate < 10.5, "About ten cuts a second on blocks, got " + rate);
        check(Math.abs(rateExistence / rate - 2) < 1e-9, "Twice as many while the wheel adapts to existence");
        check(minEl >= -72 - 1e-9 && minEl < -71 && maxEl <= 25 + 1e-9 && maxEl > 24, "Rays go 72° down to 25° up: " + minEl + ".." + maxEl);
        check(ShrineCutPlan.mobLength(1.95, 0.6, 0.5) > 1.5 && ShrineCutPlan.mobLength(1.95, 0.6, 0.5) < 2.5, "A zombie's cut is about its height");
        check(ShrineCutPlan.mobLength(16, 16, 1) == 4, "A giant's cut stops at 4 blocks");
        double[] drop = ShrineCutPlan.drop(1, 0, 0.5, 0.5, 0.5, 0.5, 0.5);
        check(drop[0] < 0 && drop[1] > 0, "Drops fly away from the shrine and up");
        check(ShrineCutPlan.dropAlpha(ShrineCutPlan.DRAW) == 0 && ShrineCutPlan.dropAlpha(0.3) == 1
                && ShrineCutPlan.dropAlpha(ShrineCutPlan.DROP_END) == 0, "Drops after the sweep, gone by 0.6 s");
        check(ShrineCutPlan.chipAlpha(0.1) == 1 && ShrineCutPlan.chipAlpha(ShrineCutPlan.CHIP_END) == 0, "Chips gone by 0.55 s");
        double[] chip = ShrineCutPlan.chip(new double[]{0, 1, 0}, new double[]{1, 0, 0}, 0.5, 0.5, 0.5, 0.5, 0.5);
        check(chip[1] > 1 && chip[0] == 0 && chip[2] == 0, "Chips fly off the face");
        spans();
        System.out.println("ShrineCutPlanTest: cut " + ShrineCutPlan.LIFE + " s + scar " + ShrineCutPlan.SCAR + " s, "
                + Math.round(rate * 100) / 100.0 + " block cuts a second (×2 at existence), bodies every 0.5–1.1 s.");
    }

    /** A cut stops at the edge of its face: here a floor of one block's width and a wall on its far side. */
    private static void spans() {
        // the floor is y < 0 for x in 0..1, a wall stands at x >= 3
        ShrineCutPlan.Solid world = (x, y, z) -> y < 0 && x >= 0 && x < 1 || x >= 3;
        double[] up = {0, 1, 0}, alongX = {1, 0, 0}, alongZ = {0, 0, 1};
        double[] span = ShrineCutPlan.span(new double[]{0.5, 0.02, 0.5}, alongX, up, 1.4, world);
        check(Math.abs(span[0] - 0.5) < 0.11 && Math.abs(span[1] - 0.5) < 0.11, "The cut ends at the edges of the floor: " + span[0] + ", " + span[1]);
        span = ShrineCutPlan.span(new double[]{0.5, 0.02, 0.5}, alongZ, up, 1.4, world);
        check(span[0] >= 1.4 && span[0] < 1.51 && span[1] >= 1.4 && span[1] < 1.51, "A floor without an edge lets the cut run its length: " + span[0]);
        check(ShrineCutPlan.span(new double[]{2, 0.02, 0.5}, alongX, up, 1.4, world) == null, "No cut where there is no face");
        // a wall beside the floor closes the face: the point just above it is solid
        ShrineCutPlan.Solid corner = (x, y, z) -> y < 0 || x >= 1;
        span = ShrineCutPlan.span(new double[]{0.5, 0.02, 0.5}, alongX, up, 1.4, corner);
        check(span[1] < 0.6 && span[0] >= 1.4, "A cut on a floor stops at the wall: " + span[1]);
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
