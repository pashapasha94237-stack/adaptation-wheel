package ru.pasha.adaptationwheel.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The six box-built wheel looks load, pack their textures without overlap and sit above the head; ids are stable. */
public final class SkinRigTest {
    public static void run() {
        ids();
        numbers();
        int boxes = 0;
        StringBuilder bounds = new StringBuilder();
        for (WheelSkin skin : WheelSkin.values()) {
            if (skin == WheelSkin.CLASSIC) {
                check(skin.resource() == null, "The classic wheel keeps its original model");
                continue;
            }
            SkinRig rig = SkinRig.load(skin.resource());
            check(rig.boxCount > 20 && rig.boxCount < 1000, skin.id + ": box count " + rig.boxCount);
            atlas(skin, rig);
            // the same file gives the same pixels: textures are seeded from the material and the box size only
            check(Arrays.equals(rig.atlas.argb, SkinRig.load(skin.resource()).atlas.argb), skin.id + ": texture noise is not deterministic");
            double[] y = rig.restBoundsY();
            check(y[0] > -48 && y[1] < 0, skin.id + ": heights " + y[0] + ".." + y[1]);
            clearOfHead(skin, rig);
            boxes += rig.boxCount;
            bounds.append(' ').append(skin.id).append(' ').append(Math.round(y[0] * 10) / 10.0).append("..").append(Math.round(y[1] * 10) / 10.0);
        }
        eventHorizonHasNoBeam();
        armillaryRingsAllTurn();
        malevolentShrineFlashes();
        infiniteVoid();
        System.out.println("SkinRigTest: " + (WheelSkin.values().length - 1) + " looks, " + boxes + " boxes, atlases packed without overlap; heights" + bounds + ".");
    }

    /**
     * Design infinite-void-2026-11-02 rev 1: the Infinite Void is Hub m18, 122 boxes (102 glowing), no flashes. Design
     * void-text-adaptation-2026-11-03 rev 2 makes it m18-r2: the stream nearest the sphere is the red of the Red orb.
     */
    private static void infiniteVoid() {
        String json;
        try (java.io.InputStream in = SkinRig.class.getResourceAsStream(WheelSkin.INFINITE_VOID.resource())) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        check(json.contains("\"id\": \"m18-r2\""), "infinite_void: must be the approved model m18-r2");
        check(json.contains("— красный, голубой и фиолетовый") && !json.contains("белый"), "infinite_void: the description names the red stream");
        check(groupMaterial(json, "streamInner").equals("red") && groupMaterial(json, "redOrb").equals("red"), "infinite_void: the inner stream is the Red orb's red");
        check(groupMaterial(json, "streamMid").equals("cyan") && groupMaterial(json, "streamOuter").equals("violet"), "infinite_void: only the inner stream turned red");
        check(groupMaterial(json, "coreGlints").equals("star") && groupMaterial(json, "starsFarA").equals("star"), "infinite_void: sparks and far stars stay white");
        SkinRig rig = SkinRig.load(WheelSkin.INFINITE_VOID.resource());
        int glowing = 0;
        for (SkinRig.Group g : rig.groups) {
            check(g.flash == null, "infinite_void: nothing flashes");
            for (SkinRig.Box b : g.boxes) if (b.emissive) glowing++;
        }
        check(rig.boxCount == 122 && glowing == 102, "infinite_void: 122 boxes, 102 glowing; got " + rig.boxCount + ", " + glowing);
    }

    /** The material of a group's first box. */
    private static String groupMaterial(String json, String group) {
        var found = java.util.regex.Pattern.compile("\"name\": \"" + group + "\".*?\"mat\": \"(\\w+)\"", java.util.regex.Pattern.DOTALL).matcher(json);
        check(found.find(), "No group " + group);
        return found.group(1);
    }

    /** Design wheel-models-2026-10-29 rev 2: the Event Horizon is m10-r2, m10 without its 3-box violet beam ("jet"). */
    private static void eventHorizonHasNoBeam() {
        String json;
        try (java.io.InputStream in = SkinRig.class.getResourceAsStream(WheelSkin.EVENT_HORIZON.resource())) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        check(json.contains("\"id\": \"m10-r2\""), "event_horizon: must be the approved model m10-r2");
        check(!json.contains("\"jet\""), "event_horizon: the beam group is removed in m10-r2");
        double[] y = SkinRig.load(WheelSkin.EVENT_HORIZON.resource()).restBoundsY();
        // the top is now the ring of light; the beam tip reached about 1.1 blocks above the head
        check(y[0] > -8 - 16, "event_horizon: top " + y[0] + " is higher than one block above the head");
    }

    /**
     * Design armillary-spin-2026-10-30 rev 1: the Armillary Sphere is m15, m9 with every ring turning. Only the axle
     * stands still; each ring turns by itself, from the rim inwards ever faster and each the other way than its outer
     * neighbour. The rings lie in separate shells around the centre and turn about the vertical axis through it, so no
     * relative angle can make them meet. Design armillary-all-rings-2026-10-30 rev 1 (4.33.3): it is m16, the same
     * boxes, but every ring's segments alternate in colour and each ring makes a turn in under half a minute, so the
     * turning of each one is seen.
     */
    private static void armillaryRingsAllTurn() {
        String json;
        try (java.io.InputStream in = SkinRig.class.getResourceAsStream(WheelSkin.ARMILLARY.resource())) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        check(json.contains("\"id\": \"m16\""), "armillary: must be the approved model m16");
        SkinRig rig = SkinRig.load(WheelSkin.ARMILLARY.resource());
        check(rig.boxCount == 99, "armillary: m9's 99 boxes, got " + rig.boxCount);
        // shells from the centre outwards: {idle speed, nearest and farthest distance of its boxes}
        java.util.TreeMap<Double, double[]> shells = new java.util.TreeMap<>();
        int still = 0;
        for (SkinRig.Group g : rig.groups) {
            if (g.spin == 0 && g.idleDegPerSec == 0) {
                still++;
                for (SkinRig.Box b : g.boxes)
                    check(Math.hypot(b.local[3], b.local[11]) < 0.01, "armillary: only the axle may stand still");
                continue;
            }
            check(g.idleDegPerSec != 0, "armillary: a ring turns only with the stage clicks");
            double near = Double.MAX_VALUE, far = 0;
            for (SkinRig.Box b : g.boxes) {
                double[] d = reach(b);
                near = Math.min(near, d[0]);
                far = Math.max(far, d[1]);
            }
            double[] shell = shells.get((double) g.idleDegPerSec);
            shells.put((double) g.idleDegPerSec, shell == null ? new double[]{g.idleDegPerSec, near, far}
                    : new double[]{g.idleDegPerSec, Math.min(shell[1], near), Math.max(shell[2], far)});
        }
        check(still == 1, "armillary: exactly one still group, the axle; got " + still);
        List<double[]> order = new java.util.ArrayList<>(shells.values());
        order.sort(java.util.Comparator.comparingDouble(s -> s[1]));
        check(order.size() == 5, "armillary: sun, ecliptic, colure, meridian and horizon turn at their own speeds");
        for (int i = 1; i < order.size(); i++) {
            double[] in = order.get(i - 1), out = order.get(i);
            check(in[2] < out[1], "armillary: shells " + in[0] + " and " + out[0] + " overlap: " + in[2] + " >= " + out[1]);
            check(Math.signum(in[0]) == -Math.signum(out[0]), "armillary: neighbouring rings " + in[0] + " and " + out[0] + " turn the same way");
            check(Math.abs(in[0]) > Math.abs(out[0]), "armillary: inner ring " + in[0] + " is not faster than " + out[0]);
        }
        check(Math.abs(order.get(order.size() - 1)[0]) >= 12, "armillary: the rim must turn at least once in 30 s, got " + order.get(order.size() - 1)[0]);
        ringsAlternateInColour(json);
    }

    /**
     * An 8-fold ring of identical segments turning in its own plane looks still (m15). In m16 each of the four rings is
     * two interleaved 8-segment groups of one plain material each, at the same speed, half a segment apart, and the two
     * materials differ clearly in colour.
     */
    private static void ringsAlternateInColour(String json) {
        com.google.gson.JsonObject model = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        java.util.Map<String, Integer> colour = new java.util.HashMap<>();
        Set<String> glowing = new HashSet<>();
        for (com.google.gson.JsonElement e : model.getAsJsonArray("materials")) {
            com.google.gson.JsonObject m = e.getAsJsonObject();
            colour.put(m.get("key").getAsString(), Integer.parseInt(m.get("base").getAsString().substring(1), 16));
            if (m.get("emissive").getAsBoolean()) glowing.add(m.get("key").getAsString());
        }
        // ring speed -> {phase, material} of each plain segment group turning at it
        java.util.TreeMap<Double, List<Object[]>> rings = new java.util.TreeMap<>();
        for (com.google.gson.JsonElement e : model.getAsJsonArray("groups")) {
            com.google.gson.JsonObject g = e.getAsJsonObject();
            com.google.gson.JsonArray boxes = g.getAsJsonArray("boxes");
            String mat = boxes.get(0).getAsJsonObject().get("mat").getAsString();
            if (g.get("count").getAsInt() != 8 || boxes.size() != 1 || glowing.contains(mat)) continue;
            rings.computeIfAbsent(g.get("idleDegPerSec").getAsDouble(), k -> new java.util.ArrayList<>())
                    .add(new Object[]{g.get("phaseDeg").getAsDouble(), mat});
        }
        check(rings.size() == 4, "armillary: horizon, meridian, colure and ecliptic are plain segment rings, got " + rings.keySet());
        for (java.util.Map.Entry<Double, List<Object[]>> ring : rings.entrySet()) {
            List<Object[]> halves = ring.getValue();
            check(halves.size() == 2, "armillary: ring " + ring.getKey() + " must be two interleaved halves");
            double gap = Math.abs((double) halves.get(0)[0] - (double) halves.get(1)[0]);
            check(Math.abs(gap - 22.5) < 1e-9, "armillary: ring " + ring.getKey() + " halves are " + gap + "° apart, not half a segment");
            int a = colour.get((String) halves.get(0)[1]), b = colour.get((String) halves.get(1)[1]);
            int diff = Math.abs((a >> 16) - (b >> 16)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
            check(diff >= 90, "armillary: ring " + ring.getKey() + " halves " + halves.get(0)[1] + " and " + halves.get(1)[1] + " look alike (" + diff + ")");
        }
    }

    /**
     * Design malevolent-shrine-2026-11-01 rev 3: the shrine is Hub m17-r3, 612 boxes (453 glowing), and only its five
     * slash rings (31 slashes, each a bent blade of 12–16 boxes) flash, in waves: many at once, then almost none, about
     * eight on average. Each is swept in white-hot, held while it cools, closed tail first and out until its wave comes
     * again, elsewhere; the long slashes are crossed a moment later by a ring with the same dice; the red fringe closes
     * before the white core, and no slash in any state enters the head.
     */
    private static void malevolentShrineFlashes() {
        String json;
        try (java.io.InputStream in = SkinRig.class.getResourceAsStream(WheelSkin.MALEVOLENT_SHRINE.resource())) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        check(json.contains("\"id\": \"m17-r3\""), "malevolent_shrine: must be the approved model m17-r3");
        SkinRig rig = SkinRig.load(WheelSkin.MALEVOLENT_SHRINE.resource());
        int glowing = 0, slashes = 0;
        List<SkinRig.Group> rings = new java.util.ArrayList<>();
        SkinRig.Group longCuts = null, crossCuts = null;
        for (SkinRig.Group g : rig.groups) {
            for (SkinRig.Box b : g.boxes) if (b.emissive) glowing++;
            if (g.flash == null) continue;
            rings.add(g);
            slashes += g.count;
            if (g.name.equals("cutsLong")) longCuts = g;
            if (g.name.equals("cutsLongX")) crossCuts = g;
            check(g.perCopy >= 12 && g.perCopy <= 16 && g.boxes.length == g.count * g.perCopy, "malevolent_shrine: a slash is 12–16 boxes, got " + g.perCopy);
            check("cleave".equals(g.flash.fringe()) && g.flash.arc() > 0 && g.flash.glowHalf() > 0, "malevolent_shrine: a blade has a fringe, a bend and light");
            int cores = 0, fringes = 0;
            for (int k = 0; k < g.perCopy; k++) {
                check(g.boxes[k].emissive, "malevolent_shrine: all of a blade glows");
                if (g.isFringe(k)) fringes++;
                else cores++;
            }
            check(cores > 0 && fringes > 0, "malevolent_shrine: a blade is a white core and a red fringe");
        }
        check(rig.boxCount == 612 && glowing == 453, "malevolent_shrine: 612 boxes, 453 glowing; got " + rig.boxCount + ", " + glowing);
        check(rings.size() == 5 && slashes == 31, "malevolent_shrine: five flashing rings, 31 slashes; got " + rings.size() + ", " + slashes);
        check(longCuts != null && crossCuts != null && crossCuts.flash.seed().equals("cutsLong") && crossCuts.flash.delay() == 0.07,
                "malevolent_shrine: the crossing ring follows the long slashes by 0.07 s");
        for (int step = 0; step < 4000; step++) {
            double t = step * 0.0125;
            for (int i = 0; i < longCuts.count; i++) {
                SkinRig.FlashState first = longCuts.flashState(i, t), second = crossCuts.flashState(i, t + 0.07);
                check((first == null) == (second == null), "malevolent_shrine: the crossing slash comes with its long one");
                if (first != null) check(first.yawDeg() == second.yawDeg() && first.dir() == second.dir(), "malevolent_shrine: the crossing slash lies at the same place");
            }
        }
        for (WheelSkin other : WheelSkin.values())
            if (other != WheelSkin.CLASSIC && other != WheelSkin.MALEVOLENT_SHRINE)
                for (SkinRig.Group g : SkinRig.load(other.resource()).groups) check(g.flash == null, other.id + ": nothing else flashes");
        check(SkinRig.jsNumber(-0.0).equals("0") && SkinRig.jsNumber(12).equals("12") && SkinRig.jsNumber(-3).equals("-3"), "JavaScript String(n)");

        double[] root = SkinRig.mul(SkinRig.translation(rig.mountX, rig.mountY, rig.mountZ), SkinRig.rotX(Math.toRadians(rig.pitchDeg)));
        double[] local = new double[12], size = new double[3];
        long lit = 0, samples = 0;
        int minLit = Integer.MAX_VALUE, maxLit = 0;
        java.util.Map<String, Set<Long>> places = new java.util.HashMap<>();
        boolean fringeFirst = false, tailRan = false, sweptBack = false;
        double[] point = new double[3];
        for (int step = 0; step < 12000; step++) {
            double t = step * 0.005;
            int now = 0;
            for (SkinRig.Group g : rings) {
                SkinRig.Flash f = g.flash;
                for (int i = 0; i < g.count; i++) {
                    SkinRig.FlashState st = g.flashState(i, t);
                    if (st == null) continue;
                    now++;
                    check(st.front() >= 0 && st.front() <= 1 && st.close() >= 0 && st.close() < 1, "malevolent_shrine: state " + st);
                    check(Math.abs(st.yawDeg()) <= f.yawJitterDeg() && Math.abs(st.slopeDeg()) <= f.tiltJitterDeg(), "malevolent_shrine: jitter " + st);
                    check(st.front() == 1 || st.heat() == 1, "malevolent_shrine: a slash sweeps out white-hot");
                    check(st.close() == 0 || st.heat() == 0, "malevolent_shrine: a closing slash has cooled");
                    check(st.from() >= 0 && st.to() <= 1 && st.glow() >= 0 && st.glow() <= 1.5, "malevolent_shrine: light " + st);
                    tailRan |= st.close() > 0 && st.to() - st.from() < 0.5;
                    sweptBack |= st.front() < 1 && st.dir() < 0 && st.from() > 0;
                    places.computeIfAbsent(g.name + i, k -> new HashSet<>()).add(Math.round(st.yawDeg() * 10));
                    double[] copy = g.copyMatrix(i, st);
                    boolean core = false, fringe = false;
                    for (int k = 0; k < g.perCopy; k++) {
                        if (!g.flashBox(copy, k, st, local, size)) continue;
                        clearOfHead(root, g.bobAmp, local, size);
                        if (g.isFringe(k)) fringe = true;
                        else core = true;
                    }
                    g.bladePoint(copy, 0.5, point);
                    check(Math.hypot(point[0], point[2]) > 4.5 * Math.sqrt(2) || point[1] < -8.5, "malevolent_shrine: the light of a slash enters the head");
                    if (st.close() > 0) {
                        check(core || !fringe, "malevolent_shrine: the core is gone before the fringe");
                        fringeFirst |= core && !fringe;
                    }
                    if (st.front() == 1 && st.close() == 0) check(core && fringe, "malevolent_shrine: a held slash shows core and fringe");
                }
            }
            lit += now;
            samples++;
            minLit = Math.min(minLit, now);
            maxLit = Math.max(maxLit, now);
        }
        double average = (double) lit / samples;
        check(average > 7 && average < 9, "malevolent_shrine: about 8 of 31 slashes out on average, got " + average);
        check(maxLit >= 20 && minLit <= 2, "malevolent_shrine: waves, not an even stream, got " + minLit + ".." + maxLit);
        check(fringeFirst, "malevolent_shrine: the red fringe closes before the white core");
        check(tailRan, "malevolent_shrine: the tail catches up with the tip");
        check(sweptBack, "malevolent_shrine: a slash can sweep from either end");
        check(places.size() == 31, "malevolent_shrine: every slash comes out, got " + places.size());
        for (var e : places.entrySet()) check(e.getValue().size() >= 5, "malevolent_shrine: " + e.getKey() + " always comes back at the same place");
        System.out.println("SkinRigTest: shrine slashes out " + minLit + ".." + maxLit + ", average " + Math.round(average * 100) / 100.0 + ".");
        // the expected share from the file: sum over rings of count × life / period
        double expected = 0;
        for (SkinRig.Group g : rings) expected += g.count * g.flash.life() / g.flash.period();
        check(Math.abs(average - expected) < 0.6, "malevolent_shrine: average " + average + " vs the cycle's " + expected);
    }

    /** One posed box (centre transform and size) against the head and hat, with its group's bob at either end. */
    private static void clearOfHead(double[] root, double bobAmp, double[] local, double[] size) {
        for (double bob : new double[]{-bobAmp, bobAmp}) {
            double[] m = SkinRig.mul(SkinRig.mul(root, SkinRig.translation(0, bob, 0)), local);
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int k = 0; k < 8; k++) {
                double x = (k & 1) != 0 ? size[0] / 2 : -size[0] / 2, y = (k & 2) != 0 ? size[1] / 2 : -size[1] / 2, z = (k & 4) != 0 ? size[2] / 2 : -size[2] / 2;
                for (int a = 0; a < 3; a++) {
                    double v = m[a * 4] * x + m[a * 4 + 1] * y + m[a * 4 + 2] * z + m[a * 4 + 3];
                    lo[a] = Math.min(lo[a], v);
                    hi[a] = Math.max(hi[a], v);
                }
            }
            double near = Math.hypot(Math.max(0, Math.max(lo[0], -hi[0])), Math.max(0, Math.max(lo[2], -hi[2])));
            check(!(hi[1] > -8.5 && lo[1] < 0.5 && near < 4.5 * Math.sqrt(2)), "malevolent_shrine: a flashing slash enters the head");
        }
    }

    /** Nearest and farthest distance of a box from its group's centre (the box is centred on local[3,7,11]). */
    private static double[] reach(SkinRig.Box b) {
        double[] half = {b.w / 2.0, b.h / 2.0, b.d / 2.0};
        double near = 0, far = 0;
        for (int a = 0; a < 3; a++) {
            // the centre seen in the box's own axes: −Rᵀ·t; rotations are orthonormal
            double p = -(b.local[a] * b.local[3] + b.local[4 + a] * b.local[7] + b.local[8 + a] * b.local[11]);
            double out = Math.max(0, Math.abs(p) - half[a]), in = Math.abs(p) + half[a];
            near += out * out;
            far += in * in;
        }
        return new double[]{Math.sqrt(near), Math.sqrt(far)};
    }

    private static void ids() {
        Set<String> seen = new HashSet<>();
        for (WheelSkin skin : WheelSkin.values()) {
            check(seen.add(skin.id), "Duplicate look id " + skin.id);
            check(skin.id.matches("[a-z_]+"), "Look id must be a plain lower-case name: " + skin.id);
            check(WheelSkin.byId(skin.id) == skin, "Look id does not round-trip: " + skin.id);
            check(skin.translationKey().equals("skin.adaptationwheel." + skin.id), "Translation key of " + skin.id);
        }
        check(WheelSkin.values()[0] == WheelSkin.CLASSIC, "The classic wheel is first in the list");
        check(WheelSkin.byId(null) == WheelSkin.CLASSIC && WheelSkin.byId("forge_wheel") == WheelSkin.CLASSIC
                && WheelSkin.byId("CLOCKWORK") == WheelSkin.CLASSIC, "Unknown look ids fall back to the classic wheel");
        check(WheelSkin.values().length == 9, "Nine looks: the classic one, the six accepted models, the Malevolent Shrine and the Infinite Void");
        check(WheelSkin.INFINITE_VOID.id.equals("infinite_void"), "The void's id is saved in configs and sent over the network");
        check(WheelSkin.MALEVOLENT_SHRINE.id.equals("malevolent_shrine"), "The shrine's id is saved in configs and sent over the network");
    }

    /** The ports of JavaScript arithmetic the program's textures depend on. */
    private static void numbers() {
        check(SkinRig.hash("") == 0x811C9DC5, "FNV-1a offset basis");
        check(SkinRig.hash("a") == 0xE40C292C, "FNV-1a of 'a'");
        check(SkinRig.clamp8(2.5) == 2 && SkinRig.clamp8(3.5) == 4 && SkinRig.clamp8(2.51) == 3, "Uint8ClampedArray rounds half to even");
        check(SkinRig.clamp8(-3) == 0 && SkinRig.clamp8(Double.NaN) == 0 && SkinRig.clamp8(300) == 255, "Uint8ClampedArray clamps");
        check(SkinRig.fixed2(1.005).equals("1.00") && SkinRig.fixed2(0.125).equals("0.13") && SkinRig.fixed2(-0.125).equals("-0.13")
                && SkinRig.fixed2(2).equals("2.00"), "Number.toFixed(2)");
        double[] m = SkinRig.mul(SkinRig.translation(1, 2, 3), SkinRig.rotY(Math.PI / 2));
        // rotY(90°) takes +x to -z, then the translation
        check(Math.abs(m[0]) < 1e-12 && Math.abs(m[8] + 1) < 1e-12 && m[3] == 1 && m[7] == 2 && m[11] == 3, "Matrix order a × b");
        SkinRig.Group still = new SkinRig.Group(0, 0, 0, 0, new SkinRig.Box[0]), back = new SkinRig.Group(-1, 10, 0, 0, new SkinRig.Box[0]);
        check(still.angleDeg(45, 7) == -45 && back.angleDeg(45, 2) == -70, "spin 0 stays still, spin -1 turns back");
    }

    private static void atlas(WheelSkin skin, SkinRig rig) {
        SkinRig.Atlas atlas = rig.atlas;
        check(Integer.bitCount(atlas.width) == 1 && Integer.bitCount(atlas.height) == 1 && atlas.width <= 1024 && atlas.height <= 1024,
                skin.id + ": atlas " + atlas.width + "×" + atlas.height);
        Set<SkinRig.Texture> unique = new HashSet<>();
        for (SkinRig.Group g : rig.groups) for (SkinRig.Box b : g.boxes) unique.add(b.texture);
        List<SkinRig.Texture> textures = List.copyOf(unique);
        for (int i = 0; i < textures.size(); i++) {
            SkinRig.Texture t = textures.get(i);
            check(t.atlasX >= 0 && t.atlasY >= 0 && t.atlasX + t.width <= atlas.width && t.atlasY + t.height <= atlas.height,
                    skin.id + ": texture outside the atlas");
            for (int row = 0; row < t.height; row++)
                for (int col = 0; col < t.width; col++)
                    check(atlas.argb[(t.atlasY + row) * atlas.width + t.atlasX + col] == t.argb[row * t.width + col], skin.id + ": texel not copied");
            int faces = 0;
            for (int f = 0; f < 6; f++) {
                check(t.fw[f] >= 2 && t.fh[f] >= 2 && t.fh[f] <= t.height && t.x0[f] == faces, skin.id + ": face layout");
                faces += t.fw[f];
            }
            check(faces == t.width, skin.id + ": faces fill the texture width");
            for (int j = i + 1; j < textures.size(); j++) {
                SkinRig.Texture o = textures.get(j);
                boolean apart = t.atlasX + t.width < o.atlasX + 1 || o.atlasX + o.width < t.atlasX + 1
                        || t.atlasY + t.height < o.atlasY + 1 || o.atlasY + o.height < t.atlasY + 1;
                check(apart, skin.id + ": textures overlap in the atlas");
            }
            for (int px : t.argb) check((px >>> 24) == 0xFF, skin.id + ": texture must be opaque");
        }
    }

    /**
     * No box at rest (and with its group's bob at either end) enters the head with its hat layer: model space has Y
     * down, the head is 8 units wide around the neck from y = 0 up to -8, the hat 0.5 bigger on every side. Rain under
     * the storm ring falls below the top of the head, but far out at the sides.
     */
    private static void clearOfHead(WheelSkin skin, SkinRig rig) {
        double[] root = SkinRig.mul(SkinRig.translation(rig.mountX, rig.mountY, rig.mountZ), SkinRig.rotX(Math.toRadians(rig.pitchDeg)));
        for (SkinRig.Group g : rig.groups)
            for (double bob : new double[]{-g.bobAmp, g.bobAmp})
                for (SkinRig.Box b : g.boxes) {
                    double[] local = new double[12];
                    for (int i = 0; i < 12; i++) local[i] = b.local[i];
                    double[] m = SkinRig.mul(SkinRig.mul(root, SkinRig.translation(0, bob, 0)), local);
                    double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
                    for (int k = 0; k < 8; k++) {
                        double x = (k & 1) != 0 ? b.w / 2 : -b.w / 2, y = (k & 2) != 0 ? b.h / 2 : -b.h / 2, z = (k & 4) != 0 ? b.d / 2 : -b.d / 2;
                        for (int a = 0; a < 3; a++) {
                            double v = m[a * 4] * x + m[a * 4 + 1] * y + m[a * 4 + 2] * z + m[a * 4 + 3];
                            lo[a] = Math.min(lo[a], v);
                            hi[a] = Math.max(hi[a], v);
                        }
                    }
                    // groups turn around the vertical axis, so the hat counts as the circle through its corners
                    double near = Math.hypot(Math.max(0, Math.max(lo[0], -hi[0])), Math.max(0, Math.max(lo[2], -hi[2])));
                    boolean inside = hi[1] > -8.5 && lo[1] < 0.5 && near < 4.5 * Math.sqrt(2);
                    check(!inside, skin.id + ": a box enters the head: x " + lo[0] + ".." + hi[0] + ", y " + lo[1] + ".." + hi[1] + ", z " + lo[2] + ".." + hi[2]);
                }
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
