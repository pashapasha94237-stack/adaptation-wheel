package ru.pasha.adaptationwheel.core;

/**
 * The Infinite Void's effects around a player wearing it (design infinite-void-world-2026-11-10 rev 2): a stream of
 * flickering noise over every mob near the wheel, star dust drawn into its black core, and a blue ring closing in on the
 * feet while a red one spreads out of them. Pure arithmetic, the mock-up's numbers and dice (FNV-1a and its LCG, so a
 * seed gives the same glyph, star and offset as there); the client draws it.
 */
public final class VoidWorldPlan {
    /** A mob this close to the wheel (blocks) gets the stream. */
    public static final double RANGE = 10;
    /** Nothing is drawn this close to the eyes. */
    public static final double NEAR_EYES = 1.5;
    /** One line of noise rises for this long (s). */
    public static final double LINE_LIFE = 1.6;
    /** After this long near the wheel (s) the mob "hangs": it trembles and glyphs run down it. */
    public static final double FREEZE_AT = 8;
    /** How long the stream melts once the mob is out of range or the wheel is gone (s). */
    public static final double MELT = 2;
    public static final String GLYPHS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#$%&@*+=?<>ЖЯШЩФЮБДЛΣΞΨΩ";
    /** The colours of the stream and the stars. */
    public static final int WHITE = 0xFFFFFF, CYAN = 0x9FE8FF, VIOLET = 0xB79BFF, RED = 0xFF6B78;
    public static final int STARS = 24;
    public static final double STAR_LIFE = 3.4;
    /** Rings: out to this many blocks, every {@link #RING_PERIOD} s, running for {@link #RING_RUN} s. */
    public static final double RING_MAX = 6, RING_PERIOD = 4, RING_RUN = 2;
    /** The two faint trails behind each ring, seconds back. */
    public static final double[] TRAILS = {0.16, 0.08};
    /** The rings show violet while they are closer than this to each other (blocks). */
    public static final double MEET_GAP = 1.2;

    private VoidWorldPlan() {
    }

    /** FNV-1a over the string's UTF-16 units, never 0, as the mock-up's {@code fnv}. */
    public static int fnv(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 16777619;
        }
        return h == 0 ? 1 : h;
    }

    /** The mock-up's {@code rng}: a 32-bit LCG giving numbers in [0, 1). */
    public static final class Rng {
        private int s;

        public Rng(int seed) {
            s = seed;
        }

        public Rng(String seed) {
            this(fnv(seed));
        }

        public double next() {
            s = s * 1664525 + 1013904223;
            return (s & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    // ------------------------------------------------------------------ stream

    /** Lines of noise over a mob that has been near the wheel this long: 1, 2 after 2 s, 3 after 5 s, 4 after 8 s. */
    public static int lines(double near) {
        return near < 2 ? 1 : near < 5 ? 2 : near < FREEZE_AT ? 3 : 4;
    }

    /** How far the mob has "hung", 0..1: nothing before 8 s, full 0.6 s later. */
    public static double frozen(double near) {
        return near < FREEZE_AT ? 0 : Math.min(1, (near - FREEZE_AT) / 0.6);
    }

    /** The stream's strength while it melts, {@code since} seconds after the mob left: 1 → 0 in 2 s. */
    public static double melt(double since) {
        return Math.max(0, 1 - since / MELT);
    }

    /** Glyphs in a line: 5–8, three more from the third line on. */
    public static int lineLength(double roll, int lines) {
        return 5 + (int) Math.floor(roll * 4) + (lines >= 3 ? 3 : 0);
    }

    /** A line's opacity at this age: in over 0.2 s, out over the last 0.5 s. */
    public static double lineAlpha(double age) {
        if (age < 0 || age >= LINE_LIFE) return 0;
        return Math.min(1, age / 0.2) * Math.min(1, (LINE_LIFE - age) / 0.5);
    }

    /** How high a line has risen over the mob's head at this age. */
    public static double lineRise(double age) {
        return age * 0.34;
    }

    public static char glyph(double roll) {
        return GLYPHS.charAt(Math.min(GLYPHS.length() - 1, (int) Math.floor(roll * GLYPHS.length())));
    }

    /** A glyph's colour by its fixed tone: mostly white and cyan, some violet, a few red. */
    public static int tone(double tone) {
        return tone < 0.45 ? WHITE : tone < 0.78 ? CYAN : tone < 0.95 ? VIOLET : RED;
    }

    /** The frame of the flicker: every glyph changes every 0.1 s. */
    public static long flicker(double t) {
        return (long) Math.floor(t * 10);
    }

    /** The hanging mob's tremble in this tick: x and z, up to 0.05 block each way, scaled by {@link #frozen}. */
    public static double[] tremble(int mob, long tick, double frozen) {
        Rng r = new Rng("m" + mob + "|j|" + tick);
        return new double[]{(r.next() - 0.5) * 0.1 * frozen, (r.next() - 0.5) * 0.1 * frozen};
    }

    /** A dripping column: its speed (heights per second), phase and side shift. */
    public static double[] drip(int mob, int column) {
        Rng r = new Rng("m" + mob + "|drip|" + column);
        double speed = 0.6 + r.next() * 0.5, phase = r.next(), x = (column - 2) * 0.12 + (r.next() - 0.5) * 0.04;
        return new double[]{speed, phase, x};
    }

    /** Where a dripping column's first glyph is over a body {@code top} high: from just over the head down below the feet. */
    public static double dripHead(double t, double speed, double phase, double top) {
        double cycle = (t * speed + phase) % 1;
        if (cycle < 0) cycle += 1;
        return top + 0.15 - cycle * (top + 0.5);
    }

    // ------------------------------------------------------------------ stars

    /** A spark's cycle and its age in that cycle at time {@code t}. */
    public static double[] starTime(int i, double t) {
        double off = new Rng("star|" + i).next() * STAR_LIFE, v = t + off, cycle = Math.floor(v / STAR_LIFE);
        return new double[]{cycle, v - cycle * STAR_LIFE};
    }

    /**
     * A spark of one cycle at this age: its offset from the core (x, y, z), u = age/life, k (how far it has fallen in),
     * tone and size, as the mock-up's {@code starAt}. Born 2.3–4 blocks out, circling, then spiralling in faster.
     */
    public static double[] star(int i, long cycle, double local) {
        Rng q = new Rng("star|" + i + "|" + cycle);
        double yaw = q.next() * 2 * Math.PI, el = (q.next() * 2 - 1) * 0.75, r0 = 2.3 + q.next() * 1.7;
        double sw = (q.next() < 0.5 ? -1 : 1) * (0.35 + q.next() * 0.3), tone = q.next(), size = 0.8 + 0.4 * q.next();
        double u = local / STAR_LIFE, k = Math.pow(u, 2.4), rad = r0 * (1 - k) + 0.06;
        double ang = yaw + sw * local + sw * 2.2 * k, e = el * (1 - k);
        return new double[]{Math.cos(ang) * Math.cos(e) * rad, Math.sin(e) * rad * 0.8, Math.sin(ang) * Math.cos(e) * rad, u, k, tone, size};
    }

    public static int starColour(double tone) {
        return tone < 0.6 ? WHITE : tone < 0.88 ? CYAN : VIOLET;
    }

    /** A spark's opacity: in over the first 12 % of its life, out over the last 5 %. */
    public static double starAlpha(double u) {
        return Math.min(1, u / 0.12) * (u > 0.95 ? Math.max(0, (1 - u) / 0.05) : 1);
    }

    /** A spark's radius: smaller as it falls in, twinkling. */
    public static double starRadius(double size, double k, double t, int i) {
        return 0.045 * size * (1 - 0.55 * k) * (0.75 + 0.25 * Math.sin(t * 9 + i * 1.7));
    }

    /** The cyan flash in the core as a spark dies, 0..1 (0 = none), from the last 7 % of its life. */
    public static double starFlash(double u) {
        return u > 0.93 ? Math.max(0, 1 - (u - 0.93) / 0.07) : 0;
    }

    // ------------------------------------------------------------------ rings

    public static double smooth(double u) {
        return u * u * (3 - 2 * u);
    }

    /** The rings' progress 0..1 in this period, or -1 in the pause. */
    public static double ringU(double t) {
        double local = ((t % RING_PERIOD) + RING_PERIOD) % RING_PERIOD;
        return local < RING_RUN ? local / RING_RUN : -1;
    }

    /** The blue ring closes in: 6 blocks → the feet. */
    public static double blueRadius(double u) {
        return RING_MAX * (1 - smooth(u));
    }

    /** The red ring spreads out: the feet → 6 blocks. */
    public static double redRadius(double u) {
        return RING_MAX * smooth(u);
    }

    public static double blueAlpha(double u) {
        return Math.min(1, u / 0.08);
    }

    /** Red fades out at the edge over the last 30 %. */
    public static double redAlpha(double u) {
        return Math.min(1, u / 0.08) * Math.max(0, Math.min(1, (1 - u) / 0.3));
    }

    /** How violet the meeting is, 0..1: the closer the rings, the more. */
    public static double meeting(double u) {
        double gap = Math.abs(blueRadius(u) - redRadius(u));
        return gap < MEET_GAP ? (1 - gap / MEET_GAP) * Math.min(blueAlpha(u), redAlpha(u)) : 0;
    }

    /** The blue flash at the feet once the blue ring arrives, 0..1 of its growth (−1 = none). */
    public static double landing(double u) {
        return u > 0.85 ? (u - 0.85) / 0.15 : -1;
    }

    /** Segments around a ring of this radius: finer for a bigger one. */
    public static int segments(double radius) {
        return Math.max(24, (int) Math.round(radius * 14));
    }
}
