package ru.pasha.adaptationwheel.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A wheel look built from boxes, read from the model JSON that AdaptationHub shows in «Модели 3D». The rules are a port
 * of the program's {@code WheelRig}, {@code BoxTexture} and {@code ModelMaterial}, so a model looks here as it did there:
 * a group is {@code count} copies spread around the vertical axis, its ring can be tilted and each copy turned; per
 * frame the whole wheel turns (45° a stage) and pulses, and each group adds its own rotation ({@code spin} relative to
 * the wheel, {@code idleDegPerSec}) and bob. Every box shape of a material gets a seeded pixel texture (2 texels per
 * unit, lit top and shaded bottom edge), all of them packed into one atlas per look.
 *
 * <p>Units: 16 per block, player model space (Y down, origin at the neck). Matrices are 3×4, row-major.</p>
 */
public final class SkinRig {
    private static final double DEG = Math.PI / 180;
    /** Far above the real looks (at most 24 copies and ~150 boxes); a broken file must not exhaust memory. */
    static final int MAX_COUNT = 256, MAX_BOXES = 4096, MAX_TEXELS = 1 << 20;
    static final double MAX_SIDE = 256;
    /** Box faces in the program's order (+x, −x, +y, −y, +z, −z): corners as bits (1 = +x, 2 = +y, 4 = +z), outward and
     * counter-clockwise, each with its texture corner (U, V) × face size. */
    public static final int[][] FACE = {{7, 5, 1, 3}, {2, 0, 4, 6}, {2, 6, 7, 3}, {4, 0, 1, 5}, {6, 4, 5, 7}, {3, 1, 0, 2}};
    public static final float[][] NORMAL = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    public static final int[] CU = {0, 0, 1, 1}, CV = {0, 1, 1, 0};

    public final float pitchDeg, mountX, mountY, mountZ;
    public final Group[] groups;
    public final Atlas atlas;
    public final int boxCount;

    /**
     * {@code count} copies of a set of boxes; everything below the group's spin node never moves, except in a flashing
     * group ({@link #flash} not null), whose copies come and go and are placed anew every frame.
     */
    public static final class Group {
        public final float spin, idleDegPerSec, bobAmp, bobPeriodSec;
        public final Box[] boxes;
        /** The flash cycle of a slash ring (Hub m17-r3); null for a group that is always drawn as built. */
        public final Flash flash;
        /** Copies and boxes per copy; {@code boxes} holds copy 0's boxes first, then copy 1's… */
        public final int count, perCopy;
        final String name;
        final double phaseDeg, ringY, radius, rotXDeg, rotYDeg, rotZDeg;
        final double[] tilt;
        /** Per box of one copy: corner x, y, z and size w, h, d as in the file. */
        final double[][] source;
        /** Per box of one copy: whether it is of the flash's fringe material, which goes first when a slash closes. */
        final boolean[] fringe;
        /** The copy's extent along its own x (the sweep) and its thinnest box (max of h and d). */
        final double minX, maxX, thinnest;
        /** Each copy's seeded offset in the wave, seconds. */
        private final double[] offsets;

        Group(float spin, float idle, float bobAmp, float bobPeriod, Box[] boxes) {
            this(spin, idle, bobAmp, bobPeriod, boxes, null, boxes.length == 0 ? 0 : 1, boxes.length, "", 0, 0, 0, 0, 0, 0, identity(), new double[0][], new boolean[0]);
        }

        Group(float spin, float idle, float bobAmp, float bobPeriod, Box[] boxes, Flash flash, int count, int perCopy, String name,
              double phaseDeg, double ringY, double radius, double rotXDeg, double rotYDeg, double rotZDeg, double[] tilt, double[][] source,
              boolean[] fringe) {
            this.spin = spin;
            this.idleDegPerSec = idle;
            this.bobAmp = bobAmp;
            this.bobPeriodSec = bobPeriod;
            this.boxes = boxes;
            this.flash = flash;
            this.count = count;
            this.perCopy = perCopy;
            this.name = name;
            this.phaseDeg = phaseDeg;
            this.ringY = ringY;
            this.radius = radius;
            this.rotXDeg = rotXDeg;
            this.rotYDeg = rotYDeg;
            this.rotZDeg = rotZDeg;
            this.tilt = tilt;
            this.source = source;
            this.fringe = fringe;
            offsets = new double[flash == null ? 0 : count];
            for (int i = 0; i < offsets.length; i++) offsets[i] = new Rng(hash(flash.seed + "|" + i)).next() * flash.spread;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, thin = Double.MAX_VALUE;
            for (double[] b : source) {
                lo = Math.min(lo, b[0]);
                hi = Math.max(hi, b[0] + b[3]);
                thin = Math.min(thin, Math.max(b[4], b[5]));
            }
            minX = source.length == 0 ? 0 : lo;
            maxX = source.length == 0 ? 0 : hi;
            thinnest = source.length == 0 ? 1 : thin;
        }

        /**
         * Where copy {@code i} of a flashing ring is in its cycle at {@code seconds}; null while it is out. Each copy has its
         * own seeded offset within the wave ({@code spread}); each cycle seeds a new yaw and slope jitter and sweep
         * direction. A ring with the same {@code seed} and a {@code delay} repeats another ring's slashes a moment later.
         */
        public FlashState flashState(int i, double seconds) {
            Flash f = flash;
            double u = seconds + offsets[i] - f.delay, cycle = Math.floor(u / f.period), local = u - cycle * f.period;
            if (local >= f.draw + f.hold + f.close) return null;
            Rng r = new Rng(hash(f.seed + "|" + i + "|" + jsNumber(cycle)));
            double yaw = (2 * r.next() - 1) * f.yawJitterDeg, slope = (2 * r.next() - 1) * f.tiltJitterDeg;
            int dir = r.next() < 0.5 ? 1 : -1;
            double front = 1, thick = 1, close = 0, heat = 0;
            if (local < f.draw) {
                front = local / f.draw;
                thick = 1.35;
                heat = 1;
            } else if (local < f.draw + f.hold) {
                double ease = Math.min(0.1, f.hold), held = local - f.draw;
                thick = held < ease ? 1.35 - 0.35 * held / ease : 1;
                heat = Math.max(0, 1 - held / 0.08);
            } else close = (local - f.draw - f.hold) / f.close;
            return new FlashState(yaw, slope, dir, front, thick, close, heat);
        }

        /** The transform of copy {@code i} relative to the spin node, with the cycle's jitter (st null: as built). */
        public double[] copyMatrix(int i, FlashState st) {
            double[] piece = mul(translation(0, ringY, radius),
                    eulerZYX(rotXDeg * DEG, rotYDeg * DEG, (rotZDeg + (st == null ? 0 : st.slopeDeg)) * DEG));
            return mul(mul(tilt, rotY((phaseDeg + i * 360.0 / count + (st == null ? 0 : st.yawDeg)) * DEG)), piece);
        }

        /**
         * Box {@code k} of a copy in flash state {@code st}: writes its centre transform (3×4, relative to the spin node)
         * into {@code local} and its size into {@code size}; false while the box is not drawn. While the slash is drawn a
         * front sweeps the copy's x-span and each box shows only its part behind it. While it closes the tail runs after
         * the tip along the blade, the fringe material is gone at 55 % of the close and the rest narrows to the end; a ring
         * without a fringe material has no tail, each box thins in proportion to its own thickness.
         */
        public boolean flashBox(double[] copy, int k, FlashState st, double[] local, double[] size) {
            double[] b = source[k];
            double x0 = b[0], x1 = b[0] + b[3];
            if (st.front < 1) {
                double front = st.dir > 0 ? minX + (maxX - minX) * st.front : maxX - (maxX - minX) * st.front;
                if (st.dir > 0) x1 = Math.min(x1, front);
                else x0 = Math.max(x0, front);
                if (x1 - x0 <= 0.001) return false;
            }
            double scale = st.thick;
            if (st.close > 0 && flash.fringe != null) {
                double tail = st.close * st.close, span = maxX - minX;
                if (st.dir > 0) x0 = Math.max(x0, minX + span * tail);
                else x1 = Math.min(x1, maxX - span * tail);
                if (x1 - x0 <= 0.001) return false;
                scale = fringe[k] ? Math.max(0, 1 - st.close / 0.55) : 1 - st.close;
                if (scale <= 0.001) return false;
            } else if (st.close > 0) {
                scale = Math.max(0, 1 - st.close * (Math.max(b[4], b[5]) / thinnest));
                if (scale <= 0.001) return false;
            }
            double[] m = mul(copy, translation((x0 + x1) / 2, b[1] + b[4] / 2, b[2] + b[5] / 2));
            System.arraycopy(m, 0, local, 0, 12);
            size[0] = x1 - x0;
            size[1] = b[4] * scale;
            size[2] = b[5] * scale;
            return true;
        }

        /** Whether a box of one copy is of the flash's fringe material. */
        public boolean isFringe(int k) {
            return fringe[k];
        }

        /**
         * The point at {@code u} (0..1) along the blade's bend, in the frame of {@code copy}, written to {@code out}: what the
         * soft light around a slash follows.
         */
        public void bladePoint(double[] copy, double u, double[] out) {
            double x = minX + (maxX - minX) * u, y = -flash.arc * Math.sin(Math.PI * u);
            out[0] = copy[0] * x + copy[1] * y + copy[3];
            out[1] = copy[4] * x + copy[5] * y + copy[7];
            out[2] = copy[8] * x + copy[9] * y + copy[11];
        }

        /** The group's rotation around Y in degrees, on top of the wheel's own turn. */
        public double angleDeg(double turnDeg, double seconds) {
            return turnDeg * (spin - 1) + idleDegPerSec * seconds;
        }

        public double bob(double seconds) {
            return bobAmp != 0 && bobPeriodSec != 0 ? bobAmp * Math.sin(2 * Math.PI * seconds / bobPeriodSec) : 0;
        }
    }

    /**
     * A slash ring's cycle (seconds): each copy is swept in in {@code draw}, held for {@code hold}, closed in
     * {@code close} and stays out for the rest of {@code period}; each cycle turns it by up to ±{@code yawJitterDeg}
     * around the ring and slopes it by up to ±{@code tiltJitterDeg}. The copies of a ring start within {@code spread} of
     * each other, so they come as a wave; {@code seed} (the group's name unless given) decides the dice and {@code delay}
     * holds the whole ring back. {@code fringe} is the material that goes first when a slash closes, {@code arc} the bend
     * of the blade and {@code glowHalf} the half width of the soft light around it (0: none). Keys {@code flashPeriodSec}…
     * of Hub m17-r3.
     */
    public record Flash(double period, double draw, double hold, double close, double yawJitterDeg, double tiltJitterDeg,
                        double spread, String seed, double delay, String fringe, double arc, double glowHalf) {
        static Flash of(JsonObject g, String name) {
            double period = num(g, "flashPeriodSec", 0);
            if (!(period > 0)) return null;
            String seed = str(g, "flashSeed"), fringe = str(g, "flashFringeMat");
            // JavaScript's `x || fallback`: 0 and a missing key both take the fallback
            return new Flash(period, or(num(g, "flashDrawSec", 0), 1e-4), or(num(g, "flashHoldSec", 0), 0),
                    or(num(g, "flashCloseSec", 0), 1e-4), num(g, "flashYawJitterDeg", 0), num(g, "flashTiltJitterDeg", 0),
                    or(num(g, "flashSpreadSec", 0), period), seed == null || seed.isEmpty() ? name : seed, num(g, "flashDelaySec", 0),
                    fringe == null || fringe.isEmpty() ? null : fringe, num(g, "flashArc", 0), num(g, "flashGlowHalf", 0));
        }

        private static double or(double v, double fallback) {
            return v != 0 && !Double.isNaN(v) ? v : fallback;
        }

        /** Seconds a copy is seen in each period. */
        public double life() {
            return draw + hold + close;
        }
    }

    /**
     * One copy at one moment: its jitter, sweep direction (+1 towards +x), how far the sweep has come (1 = whole), the
     * thickness factor, how far it has closed (0..1) and how white-hot it still is (1..0).
     */
    public record FlashState(double yawDeg, double slopeDeg, int dir, double front, double thick, double close, double heat) {
        /** Where along the blade (0..1) the part that is out now starts: the tail, which runs after the tip while closing. */
        public double from() {
            double tail = close > 0 && dir > 0 ? close * close : 0;
            return front < 1 && dir < 0 ? Math.max(tail, 1 - front) : tail;
        }

        /** Where along the blade (0..1) the part that is out now ends. */
        public double to() {
            double tail = close > 0 && dir < 0 ? 1 - close * close : 1;
            return front < 1 && dir > 0 ? Math.min(tail, front) : tail;
        }

        /** The strength of the soft light around the slash: above 1 in the white-hot moment, fading out as it closes. */
        public double glow() {
            return (close > 0 ? 1 - close : 1) * (1 + 0.5 * heat);
        }
    }

    /** JavaScript's String(n) for the whole numbers a cycle index can be. */
    static String jsNumber(double v) {
        if (v == 0) return "0";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    /** One box: its centre transform relative to the spin node, its size and its texture in the atlas. */
    public static final class Box {
        public final float[] local;
        public final float w, h, d;
        public final Texture texture;
        public final boolean emissive;

        Box(double[] local, double w, double h, double d, Texture texture, boolean emissive) {
            this.local = new float[12];
            for (int i = 0; i < 12; i++) this.local[i] = (float) local[i];
            this.w = (float) w;
            this.h = (float) h;
            this.d = (float) d;
            this.texture = texture;
            this.emissive = emissive;
        }
    }

    /** The six faces of one box shape side by side, as the program lays them out, and where they sit in the atlas. */
    public static final class Texture {
        public final int width, height;
        public final int[] x0 = new int[6], fw = new int[6], fh = new int[6];
        final int[] argb;
        public int atlasX, atlasY;

        Texture(int width, int height) {
            this.width = width;
            this.height = height;
            argb = new int[width * height];
        }
    }

    /** All textures of a look in one image (ARGB, row 0 on top). */
    public static final class Atlas {
        public final int width, height;
        public final int[] argb;

        Atlas(int width, int height) {
            this.width = width;
            this.height = height;
            argb = new int[width * height];
        }
    }

    private SkinRig(JsonObject mount, Group[] groups, Atlas atlas) {
        pitchDeg = (float) num(mount, "pitchDeg", 0);
        mountX = (float) num(mount, "x", 0);
        mountY = (float) num(mount, "y", -14.72);
        mountZ = (float) num(mount, "z", 0);
        this.groups = groups;
        this.atlas = atlas;
        int n = 0;
        for (Group g : groups) n += g.boxes.length;
        boxCount = n;
    }

    /** Reads a look shipped in the mod jar. */
    public static SkinRig load(String resource) {
        try (InputStream in = SkinRig.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Missing wheel look " + resource);
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read wheel look " + resource, failure);
        }
    }

    public static SkinRig parse(JsonObject model) {
        List<Material> materials = new ArrayList<>();
        for (JsonElement e : arr(model, "materials")) if (e.isJsonObject()) materials.add(Material.of(e.getAsJsonObject()));
        Material fallback = materials.isEmpty() ? new Material("default", "#9A9AA0", "#C0C0C6", "#6A6A70", "plain", false) : materials.get(0);
        Map<String, Texture> textures = new LinkedHashMap<>();
        List<Group> groups = new ArrayList<>();
        int total = 0;
        for (JsonElement ge : arr(model, "groups")) {
            if (!ge.isJsonObject()) continue;
            JsonObject g = ge.getAsJsonObject();
            double copies = num(g, "count", 1);
            if (copies > MAX_COUNT) throw new IllegalArgumentException("Too many copies: " + copies);
            int count = (int) Math.round(copies);
            List<JsonObject> boxes = new ArrayList<>();
            for (JsonElement be : arr(g, "boxes")) if (be.isJsonObject()) boxes.add(be.getAsJsonObject());
            if (count <= 0 || boxes.isEmpty()) continue;
            total += count * boxes.size();
            if (total > MAX_BOXES) throw new IllegalArgumentException("Too many boxes");
            double[] tilt = mul(rotZ(num(g, "tiltZDeg", 0) * DEG), rotX(num(g, "tiltXDeg", 0) * DEG));
            double[] piece = mul(translation(0, num(g, "y", 0), num(g, "radius", 0)),
                    eulerZYX(num(g, "rotXDeg", 0) * DEG, num(g, "rotYDeg", 0) * DEG, num(g, "rotZDeg", 0) * DEG));
            double phase = num(g, "phaseDeg", 0);
            Box[] out = new Box[count * boxes.size()];
            double[][] source = new double[boxes.size()][];
            for (int j = 0; j < boxes.size(); j++) {
                JsonObject b = boxes.get(j);
                source[j] = new double[]{num(b, "x", 0), num(b, "y", 0), num(b, "z", 0), num(b, "w", 1), num(b, "h", 1), num(b, "d", 1)};
            }
            String name = str(g, "name");
            if (name == null) name = "undefined";
            Flash flash = Flash.of(g, name);
            boolean[] fringe = new boolean[boxes.size()];
            for (int j = 0; flash != null && j < fringe.length; j++) {
                String key = find(materials, str(boxes.get(j), "mat"), fallback).key;
                fringe[j] = key != null && key.equals(flash.fringe);
            }
            int k = 0;
            for (int i = 0; i < count; i++) {
                double[] slot = mul(mul(tilt, rotY((phase + i * 360.0 / count) * DEG)), piece);
                for (JsonObject b : boxes) {
                    double bw = num(b, "w", 1), bh = num(b, "h", 1), bd = num(b, "d", 1);
                    if (Math.abs(bw) > MAX_SIDE || Math.abs(bh) > MAX_SIDE || Math.abs(bd) > MAX_SIDE)
                        throw new IllegalArgumentException("Box too large");
                    Material m = find(materials, str(b, "mat"), fallback);
                    String key = textureKey(m, bw, bh, bd);
                    Texture t = textures.computeIfAbsent(key, ignored -> texture(m, key, bw, bh, bd));
                    double[] local = mul(slot, translation(num(b, "x", 0) + bw / 2, num(b, "y", 0) + bh / 2, num(b, "z", 0) + bd / 2));
                    out[k++] = new Box(local, bw, bh, bd, t, m.emissive);
                }
            }
            groups.add(new Group((float) num(g, "spin", 1), (float) num(g, "idleDegPerSec", 0),
                    (float) num(g, "bobAmp", 0), (float) num(g, "bobPeriodSec", 0), out, flash, count, boxes.size(),
                    name, phase, num(g, "y", 0), num(g, "radius", 0),
                    num(g, "rotXDeg", 0), num(g, "rotYDeg", 0), num(g, "rotZDeg", 0), tilt, source, fringe));
        }
        return new SkinRig(obj(model, "mount"), groups.toArray(new Group[0]), pack(new ArrayList<>(textures.values())));
    }

    /**
     * Shelf packing, tallest first, one empty texel between textures; the width is a power of two that holds the widest
     * texture (at least 64), the height the next power of two above the last shelf.
     */
    static Atlas pack(List<Texture> textures) {
        int width = 64, texels = 0;
        for (Texture t : textures) {
            while (width < t.width + 1) width <<= 1;
            texels += (t.width + 1) * (t.height + 1);
        }
        if (texels > MAX_TEXELS) throw new IllegalArgumentException("Textures too large");
        // a square-ish atlas: widen while the area alone needs more rows than columns
        while ((long) width * width < texels * 2L) width <<= 1;
        List<Texture> order = new ArrayList<>(textures);
        order.sort(Comparator.comparingInt((Texture t) -> -t.height).thenComparingInt(t -> -t.width));
        int x = 0, y = 0, shelf = 0;
        for (Texture t : order) {
            if (x + t.width > width) {
                x = 0;
                y += shelf + 1;
                shelf = 0;
            }
            t.atlasX = x;
            t.atlasY = y;
            x += t.width + 1;
            shelf = Math.max(shelf, t.height);
        }
        int height = 1;
        while (height < y + shelf) height <<= 1;
        Atlas atlas = new Atlas(width, Math.max(height, 16));
        for (Texture t : order)
            for (int row = 0; row < t.height; row++)
                System.arraycopy(t.argb, row * t.width, atlas.argb, (t.atlasY + row) * atlas.width + t.atlasX, t.width);
        return atlas;
    }

    // ------------------------------------------------------------------ textures (the program's BoxTexture)

    private static final int PX = 2;

    /** One texture per material and box size (sizes to 0.01); the key also seeds the noise. */
    static String textureKey(Material mat, double bw, double bh, double bd) {
        return String.join("|", nz(mat.key), nz(mat.base), nz(mat.light), nz(mat.dark), nz(mat.pattern),
                String.valueOf(mat.emissive), fixed2(bw), fixed2(bh), fixed2(bd));
    }

    static Texture texture(Material mat, String key, double bw, double bh, double bd) {
        double[][] faces = {{bd, bh}, {bd, bh}, {bw, bd}, {bw, bd}, {bw, bh}, {bw, bh}};
        int[][] dims = new int[6][2];
        int total = 0, height = 0;
        for (int f = 0; f < 6; f++) {
            dims[f][0] = Math.max(2, (int) Math.round(faces[f][0] * PX));
            dims[f][1] = Math.max(2, (int) Math.round(faces[f][1] * PX));
            total += dims[f][0];
            height = Math.max(height, dims[f][1]);
        }
        Texture t = new Texture(total, height);
        double[] b = mat.baseRgb, l = mat.lightRgb, d = mat.darkRgb;
        Rng r = new Rng(hash(key));
        int x0 = 0;
        for (int f = 0; f < 6; f++) {
            int w = dims[f][0], h = dims[f][1];
            // Model space has Y down, so on the side faces texture row 0 ends up at the bottom in the world.
            int lit = (f == 2 || f == 3) ? 0 : h - 1, shade = h - 1 - lit;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double[] c = texel(mat.pattern, x, y, r, b, l, d);
                    if (mat.emissive) {
                        if (y == shade || x == w - 1) c = mix(c, b, .6);
                    } else if (y == lit) c = mix(c, l, .55);
                    else if (y == shade) c = mix(c, d, .5);
                    else if (x == 0) c = mix(c, l, .22);
                    else if (x == w - 1) c = mix(c, d, .28);
                    t.argb[y * total + x0 + x] = 0xFF000000 | clamp8(c[0]) << 16 | clamp8(c[1]) << 8 | clamp8(c[2]);
                }
            }
            // rows below a short face stay as the face's last row would continue: never sampled, kept opaque
            for (int y = h; y < height; y++) for (int x = 0; x < w; x++) t.argb[y * total + x0 + x] = t.argb[(h - 1) * total + x0 + x];
            t.x0[f] = x0;
            t.fw[f] = w;
            t.fh[f] = h;
            x0 += w;
        }
        return t;
    }

    /** The order of random draws matters: it decides the noise. */
    private static double[] texel(String pattern, int x, int y, Rng r, double[] b, double[] l, double[] d) {
        double n = r.next();
        switch (pattern == null ? "" : pattern) {
            case "plate":
                return (x % 6 == 2 && y % 6 == 2) ? mix(b, l, .55) : mix(b, n < .5 ? l : d, .08 * r.next());
            case "metal":
                return mix(b, (y % 2) != 0 ? l : d, .05 + .08 * n);
            case "stone":
                return n < .7 ? mix(b, d, .08 * r.next()) : n < .85 ? mix(b, l, .4) : mix(b, d, .4);
            case "rope":
                return ((x + y) % 4 < 2) ? mix(b, l, .3) : mix(b, d, .3);
            case "bone":
                return n < .05 ? mix(b, d, .5) : (x % 4 == 1) ? mix(b, l, .25) : mix(b, d, .06 * r.next());
            case "lacquer":
                return n < .04 ? mix(b, l, .5) : mix(b, d, .05 * r.next());
            case "wood":
                return (y % 3 == 0) ? mix(b, d, .35) : mix(b, l, .12 * n);
            case "paper":
                return mix(l, b, .2 * n);
            case "glow":
                return n < .3 ? l : mix(b, l, .4);
            default:
                return mix(b, n < .5 ? l : d, .05 * n);
        }
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Uint8ClampedArray conversion: clamp, round half to even. */
    static int clamp8(double v) {
        if (!(v > 0)) return 0;
        if (v >= 255) return 255;
        double f = Math.floor(v), frac = v - f;
        int i = (int) f;
        if (frac > .5) return i + 1;
        if (frac < .5) return i;
        return (i & 1) == 0 ? i : i + 1;
    }

    /** FNV-1a over UTF-16 units. */
    static int hash(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 16777619;
        }
        return h == 0 ? 1 : h;
    }

    /** JavaScript's Number.toFixed(2): the exact decimal value, ties away from zero. */
    static String fixed2(double v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final class Rng {
        private int seed;

        Rng(int seed) {
            this.seed = seed;
        }

        double next() {
            seed = seed * 1664525 + 1013904223;
            return (seed & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    /** One entry of "materials": three colours, a pixel pattern and whether it glows. */
    static final class Material {
        final String key, base, light, dark, pattern;
        final boolean emissive;
        final double[] baseRgb, lightRgb, darkRgb;

        Material(String key, String base, String light, String dark, String pattern, boolean emissive) {
            this.key = key;
            this.base = base;
            this.light = light;
            this.dark = dark;
            this.pattern = pattern;
            this.emissive = emissive;
            baseRgb = rgb(base, 0x9A9AA0);
            lightRgb = rgb(light, 0xC0C0C6);
            darkRgb = rgb(dark, 0x6A6A70);
        }

        static Material of(JsonObject o) {
            JsonElement e = o.get("emissive");
            boolean emissive = e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
            return new Material(str(o, "key"), str(o, "base"), str(o, "light"), str(o, "dark"), str(o, "pattern"), emissive);
        }

        private static double[] rgb(String hex, int fallback) {
            int v = fallback;
            if (hex != null) {
                try {
                    v = Integer.parseInt(hex.replace("#", "").trim(), 16);
                } catch (NumberFormatException ignored) {
                    // keep the fallback colour: one bad colour should not hide the whole model
                }
            }
            return new double[]{(v >> 16) & 255, (v >> 8) & 255, v & 255};
        }
    }

    private static Material find(List<Material> materials, String key, Material fallback) {
        for (Material m : materials) if (m.key != null && m.key.equals(key)) return m;
        return fallback;
    }

    // ------------------------------------------------------------------ 3×4 row-major matrices (the program's Mat4)

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    }

    static double[] translation(double x, double y, double z) {
        double[] r = identity();
        r[3] = x;
        r[7] = y;
        r[11] = z;
        return r;
    }

    static double[] rotX(double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        double[] r = identity();
        r[5] = c;
        r[6] = -s;
        r[9] = s;
        r[10] = c;
        return r;
    }

    static double[] rotY(double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        double[] r = identity();
        r[0] = c;
        r[2] = s;
        r[8] = -s;
        r[10] = c;
        return r;
    }

    static double[] rotZ(double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        double[] r = identity();
        r[0] = c;
        r[1] = -s;
        r[4] = s;
        r[5] = c;
        return r;
    }

    /** three.js Euler order 'ZYX': Rz · Ry · Rx. */
    static double[] eulerZYX(double x, double y, double z) {
        return mul(mul(rotZ(z), rotY(y)), rotX(x));
    }

    /** a × b */
    static double[] mul(double[] a, double[] b) {
        double[] c = new double[12];
        for (int k = 0; k < 12; k += 4) {
            double a0 = a[k], a1 = a[k + 1], a2 = a[k + 2];
            c[k] = a0 * b[0] + a1 * b[4] + a2 * b[8];
            c[k + 1] = a0 * b[1] + a1 * b[5] + a2 * b[9];
            c[k + 2] = a0 * b[2] + a1 * b[6] + a2 * b[10];
            c[k + 3] = a0 * b[3] + a1 * b[7] + a2 * b[11] + a[k + 3];
        }
        return c;
    }

    // ------------------------------------------------------------------ JSON as the program reads it

    static double num(JsonObject o, String key, double fallback) {
        if (o == null) return fallback;
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    /** Lowest and highest model-space Y over all copies at rest (turn 0, t 0, no pulse); for tests and the preview. */
    public double[] restBoundsY() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        double[] root = mul(translation(mountX, mountY, mountZ), rotX(pitchDeg * DEG));
        for (Group g : groups)
            for (Box b : g.boxes) {
                double[] local = new double[12];
                for (int i = 0; i < 12; i++) local[i] = b.local[i];
                double[] m = mul(root, local);
                for (int k = 0; k < 8; k++) {
                    double x = (k & 1) != 0 ? b.w / 2 : -b.w / 2, y = (k & 2) != 0 ? b.h / 2 : -b.h / 2, z = (k & 4) != 0 ? b.d / 2 : -b.d / 2;
                    double wy = m[4] * x + m[5] * y + m[6] * z + m[7];
                    lo = Math.min(lo, wy);
                    hi = Math.max(hi, wy);
                }
            }
        return new double[]{lo, hi};
    }

    @Override
    public String toString() {
        return "SkinRig[" + boxCount + " boxes, " + groups.length + " groups, atlas " + atlas.width + "×" + atlas.height + ", "
                + Arrays.stream(groups).filter(g -> g.idleDegPerSec != 0 || g.bobAmp != 0).count() + " animated groups]";
    }
}
