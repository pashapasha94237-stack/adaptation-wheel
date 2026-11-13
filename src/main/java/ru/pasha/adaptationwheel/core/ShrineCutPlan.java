package ru.pasha.adaptationwheel.core;

/**
 * The cuts the Malevolent Shrine look lays on the world around its wearer (design malevolent-shrine-2026-11-01 rev 3,
 * ported from its live mock-up): timing, sizes and the dice. Picture only: nothing here touches blocks, entities, the
 * server or the network. Pure, unit-tested; {@code client/ShrineCuts} casts the rays and draws.
 *
 * <p>A cut is a bent blade, thin at both ends: swept in white-hot ({@link #DRAW}), held while it cools to red
 * ({@link #HOLD}) and closed ({@link #CLOSE}), when the tail runs after the tip. Cuts land on blocks in volleys of 2–3
 * crossing at one point, with a glint and chips of the block; each leaves a scar that fades over {@link #SCAR} more
 * seconds. Distances in blocks, times in seconds.</p>
 */
public final class ShrineCutPlan {
    public static final double DRAW = 0.06, HOLD = 0.1, CLOSE = 0.22, SCAR = 1.8, LIFE = DRAW + HOLD + CLOSE;
    /** How far from the shrine a cut lands; cuts closer than {@link #NEAR_EYES} to the camera are not drawn. */
    public static final double RANGE = 10, NEAR_EYES = 1.5;
    /** Half widths of the white core and the red fringe across a body; on a block both are {@link #ON_BLOCK} as wide. */
    public static final double CORE = 0.028, FRINGE = 0.07, ON_BLOCK = 0.7;
    /** The soft light around a cut is this many times as wide as the fringe. */
    public static final double HALO = 2.8;
    /** Half widths of a scar: the dark red rim, the dark slit and the ember line that cools first. */
    public static final double SCAR_RIM = 0.055, SCAR_SLIT = 0.03, SCAR_EMBER = 0.014;
    /** The cuts of a volley follow each other by this much; a cut that fits less than {@link #MIN_SPAN} on its face is dropped. */
    public static final double VOLLEY_STEP = 0.055, MIN_SPAN = 0.5;
    /** The glint where a volley lands lasts this long. */
    public static final double GLINT = 0.12;
    /** Chips of a cut on a block: they fly until {@link #CHIP_END}, fading over the last {@link #CHIP_FADE}. */
    public static final double CHIP_END = 0.55, CHIP_FADE = 0.15, CHIP_GRAVITY = 4.5;
    public static final int CHIPS = 3;
    /** Drops of a cut across a body fly from the end of the sweep until {@link #DROP_END}, red-hot for {@link #DROP_HOT}. */
    public static final double DROP_END = 0.6, DROP_FADE = 0.2, DROP_HOT = 0.16, DROP_HALF = 0.02, DROP_STRETCH = 0.035, DROP_GRAVITY = 4;
    public static final int DROPS = 8;
    /** A cut across a body bends by {@link #MOB_SAG}; about half of them get a second one, shorter, across it. */
    public static final double MOB_SAG = 0.1, CROSS_CHANCE = 0.45, CROSS_DELAY = 0.06, CROSS_LENGTH = 0.9;

    private ShrineCutPlan() {
    }

    /**
     * How far the cut has been swept (0..1), where its tail is (0..1), how thick its core and fringe are now (1 = normal),
     * how white-hot it is (1..0) and how strong the light around it is.
     */
    public record Shape(double progress, double tail, double core, double fringe, double heat, double glow) {
    }

    /** The cut at {@code age} seconds; null before it starts and after it has closed. */
    public static Shape shape(double age) {
        if (age < 0 || age >= LIFE) return null;
        if (age < DRAW) return new Shape(age / DRAW, 0, 1.35, 1.35, 1, 1.5);
        if (age < DRAW + HOLD) {
            double held = age - DRAW, k = 1.35 - 0.35 * Math.min(1, held / HOLD), heat = Math.max(0, 1 - held / 0.07);
            return new Shape(1, 0, k, k, heat, 1 + 0.5 * heat);
        }
        double q = (age - DRAW - HOLD) / CLOSE;
        // the tail catches up with the tip; the red edge is gone first and the core narrows to a thread
        return new Shape(1, q * q, 1 - q, Math.max(0, 1 - q / 0.55), 0, 1 - q);
    }

    /** The colour of the light around a blade: white at heat 1, red at 0. */
    public static int heatRgb(double heat) {
        return 255 << 16 | (int) Math.round(32 + 223 * heat) << 8 | (int) Math.round(48 + 207 * heat);
    }

    /** A blade's half width along it (at {@code w} of 0..1): nothing at the ends, full in the middle. */
    public static double taper(double w) {
        return Math.pow(Math.max(0, Math.sin(Math.PI * w)), 0.75);
    }

    /** {@link #taper} at the {@code segments + 1} points of a blade. */
    public static double[] taperTable(int segments) {
        double[] table = new double[segments + 1];
        for (int i = 0; i <= segments; i++) table[i] = taper((double) i / segments);
        return table;
    }

    /** Opacity factor of a block cut's scar: full while the cut is out, then fading to nothing. */
    public static double scar(double age) {
        if (age < 0 || age >= LIFE + SCAR) return 0;
        return age < LIFE ? 1 : 1 - (age - LIFE) / SCAR;
    }

    /** Opacity of the scar's ember line, which cools long before the scar fades. */
    public static double ember(double age) {
        if (age < 0) return 0;
        return Math.max(0, Math.min(1, 1 - (age - LIFE * 0.6) / 0.5));
    }

    /** Seconds to a shrine's next volley on a block: 0.1–0.4, about four a second; twice as often while existence. */
    public static double volleyGap(double r, boolean existence) {
        return (1 + 3 * r) / 10 / (existence ? 2 : 1);
    }

    /** Cuts in a volley: 2 or 3, so about ten cuts a second. */
    public static int volleyCount(double r) {
        return r < 0.5 ? 2 : 3;
    }

    /** Seconds to the next cut across one body: 0.5–1.1; twice as often while existence. */
    public static double mobGap(double r, boolean existence) {
        return (0.5 + 0.6 * r) / (existence ? 2 : 1);
    }

    /** First cut across a body that just came into range: within 0.8 s. */
    public static double mobFirst(double r) {
        return 0.8 * r;
    }

    /** A volley's ray from the shrine (y up): 72° down to 25° up, any side. */
    public static double[] ray(double r1, double r2) {
        double el = Math.toRadians(-72 + 97 * r1), az = r2 * 2 * Math.PI;
        return new double[]{Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)};
    }

    /** A volley's first angle on its block face, against the face's first axis. */
    public static double blockAngle(double r) {
        return r * Math.PI;
    }

    /** Angle of cut {@code k} of {@code count} in a volley: spread evenly over a half turn, so they cross. */
    public static double volleyAngle(double first, int k, int count, double r) {
        return first + k * Math.PI / count + (r - 0.5) * 0.5;
    }

    /** How far from the point of a volley a cut's centre lies along each of the face's axes. */
    public static double volleyOffset(double r) {
        return (r - 0.5) * 0.5;
    }

    public static double blockLength(double r) {
        return 1.1 + 1.7 * r;
    }

    /** How far a cut on a block bends to either side. */
    public static double blockSag(double r1, double r2) {
        return (r1 < 0.5 ? -1 : 1) * (0.05 + 0.07 * r2);
    }

    /** Whether a point is inside something solid; the client asks the world. */
    public interface Solid {
        boolean at(double x, double y, double z);
    }

    /**
     * How far a cut centred at {@code c} on a face with the normal {@code n} may run back and forth along {@code d} before
     * the face ends, at most about {@code half} each way, in steps of 0.1; null when {@code c} itself is not on the face.
     */
    public static double[] span(double[] c, double[] d, double[] n, double half, Solid solid) {
        if (!onFace(c[0], c[1], c[2], n, solid)) return null;
        return new double[]{walk(c, d, n, -1, half, solid), walk(c, d, n, 1, half, solid)};
    }

    private static double walk(double[] c, double[] d, double[] n, int sign, double half, Solid solid) {
        double run = 0;
        while (run < half) {
            double s = sign * (run + 0.1);
            if (!onFace(c[0] + d[0] * s, c[1] + d[1] * s, c[2] + d[2] * s, n, solid)) break;
            run += 0.1;
        }
        return run;
    }

    private static boolean onFace(double x, double y, double z, double[] n, Solid solid) {
        return solid.at(x - n[0] * 0.05, y - n[1] * 0.05, z - n[2] * 0.05) && !solid.at(x + n[0] * 0.05, y + n[1] * 0.05, z + n[2] * 0.05);
    }

    /** Half size of a chip. */
    public static double chipHalf(double r) {
        return 0.03 + 0.025 * r;
    }

    /** A chip's start velocity (y up): off the face with the normal {@code n}, along the cut {@code d} and a little up. */
    public static double[] chip(double[] n, double[] d, double r1, double r2, double r3, double r4, double r5) {
        double out = 1 + 1.6 * r1, along = (r2 - 0.5) * 2;
        return new double[]{n[0] * out + d[0] * along + (r3 - 0.5) * 0.8, n[1] * out + d[1] * along + 0.8 * r4, n[2] * out + d[2] * along + (r5 - 0.5) * 0.8};
    }

    /** Opacity of a chip at {@code age} seconds after its cut started; 0 when gone. */
    public static double chipAlpha(double age) {
        if (age < 0 || age >= CHIP_END) return 0;
        return Math.min(1, (CHIP_END - age) / CHIP_FADE);
    }

    /** Slope of a cut across a body, ±20–80° from level. */
    public static double mobSlope(double r1, double r2) {
        return Math.toRadians((r1 < 0.5 ? -1 : 1) * (20 + 60 * r2));
    }

    /** Slope of the second cut across a body: to the other side of the first. */
    public static double crossSlope(double slope, double r) {
        return -slope * (0.7 + 0.5 * r);
    }

    /** Length of a cut across a body of this size: about its height, at most 4 blocks for giants. */
    public static double mobLength(double height, double width, double r) {
        return Math.min(4, (height * 0.9 + width * 0.5) * (0.9 + 0.25 * r));
    }

    /** Where a cut crosses a body: this share of its height. */
    public static final double MOB_HEIGHT = 0.55;

    /**
     * A drop's start velocity (blocks a second; y up) for a cut on the side of a body facing the shrine along
     * {@code (toX, toZ)}: it flies away from the shrine and up.
     */
    public static double[] drop(double toX, double toZ, double r1, double r2, double r3, double r4, double r5) {
        return new double[]{-toX * (0.8 + 1.4 * r1) + (r2 - 0.5) * 1.8, 0.8 + 2.2 * r3, -toZ * (0.8 + 1.4 * r4) + (r5 - 0.5) * 1.8};
    }

    /** Opacity of a drop at {@code age} seconds after its cut started; 0 when not shown. */
    public static double dropAlpha(double age) {
        if (age <= DRAW || age >= DROP_END) return 0;
        return Math.min(1, (DROP_END - age) / DROP_FADE);
    }
}
