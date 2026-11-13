package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.WheelSkinConfig;
import ru.pasha.adaptationwheel.core.VoidWorldPlan;
import ru.pasha.adaptationwheel.core.WheelSkin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Infinite Void's effects around every player this client sees wearing it (design infinite-void-world-2026-11-10
 * rev 2): lines of flickering noise rise over every mob within 10 blocks of the wheel, more of them the longer it stays,
 * and after 8 s the mob trembles and glyphs run down it; about 24 sparks circle the wheel and fall into its black core
 * with a cyan flash; every 4 s a blue ring closes in on the wearer's feet from 6 blocks while a red one spreads out of
 * them, violet where they meet. Picture only, on this client only: the tremble moves the drawn model, never the mob, and
 * nothing touches blocks, entities, the server or the network. Client config {@code voidStream}, {@code voidStars} and
 * {@code voidRings} turn each part off.
 */
@Mod.EventBusSubscriber(modid = AdaptationWheel.MOD_ID, value = Dist.CLIENT)
public final class VoidWorld {
    /** Wheels farther than this from the camera show nothing. */
    private static final double SOURCE_RANGE = 64;
    /** Corners of a soft spot. */
    private static final int SPOT_SIDES = 10;
    private static final double[] SPOT_COS = new double[SPOT_SIDES + 1], SPOT_SIN = new double[SPOT_SIDES + 1];
    /** How many blocks down the rings look for the ground under the feet. */
    private static final int GROUND_DROP = 3;
    private static final String[] GLYPH_TEXT = new String[VoidWorldPlan.GLYPHS.length()];
    private static final Map<Integer, Stream> STREAMS = new HashMap<>();
    /** The tremble given to a model this frame, taken back after it is drawn. */
    private static final Map<Integer, double[]> SHAKEN = new HashMap<>();
    private static final long ORIGIN = System.nanoTime();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    private static final Matrix4f GLYPH = new Matrix4f();
    private static final boolean[] SKIP = new boolean[256];
    private static ClientLevel level;

    static {
        for (int i = 0; i <= SPOT_SIDES; i++) {
            SPOT_COS[i] = Math.cos(2 * Math.PI * i / SPOT_SIDES);
            SPOT_SIN[i] = Math.sin(2 * Math.PI * i / SPOT_SIDES);
        }
        for (int i = 0; i < GLYPH_TEXT.length; i++) GLYPH_TEXT[i] = String.valueOf(VoidWorldPlan.GLYPHS.charAt(i));
    }

    private VoidWorld() {
    }

    /** The stream over one mob: since when it is near a wheel, and since when it is not (NaN while it is). */
    private static final class Stream {
        final LivingEntity mob;
        final double nearSince;
        double leftAt = Double.NaN;

        Stream(LivingEntity mob, double nearSince) {
            this.mob = mob;
            this.nearSince = nearSince;
        }

        boolean leaving() {
            return !Double.isNaN(leftAt);
        }

        /** How long it has been near: frozen once it leaves. */
        double near(double now) {
            return (leaving() ? leftAt : now) - nearSince;
        }

        double fade(double now) {
            return leaving() ? VoidWorldPlan.melt(now - leftAt) : 1;
        }
    }

    static double now() {
        return (System.nanoTime() - ORIGIN) / 1e9;
    }

    private static boolean on(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static boolean streamOn() {
        return on(WheelSkinConfig.VOID_STREAM);
    }

    static boolean starsOn() {
        return on(WheelSkinConfig.VOID_STARS);
    }

    static boolean ringsOn() {
        return on(WheelSkinConfig.VOID_RINGS);
    }

    /** A player whose void this client draws: worn, the Infinite Void look, visible. */
    private static boolean wearer(Player player) {
        ClientWheelState.State state = ClientWheelState.get(player.getId());
        return state != null && state.equipped() && player.isAlive() && !player.isInvisible() && !player.isSpectator()
                && WheelSkinClient.of(player) == WheelSkin.INFINITE_VOID;
    }

    /** The wheel's centre: the neck at 1.5 of 1.8 blocks, the look's black core a block above it. */
    private static Vec3 centre(Vec3 feet, Player player) {
        return new Vec3(feet.x, feet.y + player.getBbHeight() * (1.5 / 1.8) + 1.0, feet.z);
    }

    private static List<Player> wearers(Vec3 camera) {
        List<Player> list = new ArrayList<>();
        if (level == null) return list;
        for (Player player : level.players())
            if (wearer(player) && player.position().distanceToSqr(camera) <= SOURCE_RANGE * SOURCE_RANGE) list.add(player);
        return list;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            clear();
            level = mc.level;
        }
        if (level == null || mc.isPaused()) return;
        if (!streamOn()) {
            clear();
            return;
        }
        double now = now();
        Set<Integer> near = new HashSet<>();
        double r = VoidWorldPlan.RANGE;
        for (Player player : wearers(mc.gameRenderer.getMainCamera().getPosition())) {
            Vec3 centre = centre(player.position(), player);
            for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(r),
                    e -> e != player && e.isAlive() && !e.isInvisible() && !(e instanceof ArmorStand) && !e.isSpectator())) {
                if (mob.getBoundingBox().getCenter().distanceToSqr(centre) > r * r || !near.add(mob.getId())) continue;
                Stream stream = STREAMS.get(mob.getId());
                // a mob coming back starts over
                if (stream == null || stream.leaving() || stream.mob != mob) STREAMS.put(mob.getId(), new Stream(mob, now));
            }
        }
        STREAMS.values().removeIf(s -> {
            if (s.mob.isRemoved()) return true;
            if (!near.contains(s.mob.getId()) && !s.leaving()) s.leftAt = now;
            return s.leaving() && now - s.leftAt >= VoidWorldPlan.MELT;
        });
    }

    static void clear() {
        STREAMS.clear();
        SHAKEN.clear();
    }

    // ------------------------------------------------------------------ tremble

    /** A hanging mob's model trembles a little; the mob itself stays where it is. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shake(RenderLivingEvent.Pre<?, ?> event) {
        if (STREAMS.isEmpty()) return;
        Stream stream = STREAMS.get(event.getEntity().getId());
        if (stream == null || stream.leaving() || stream.mob != event.getEntity()) return;
        double now = now(), frozen = VoidWorldPlan.frozen(stream.near(now));
        if (frozen <= 0) return;
        double[] j = VoidWorldPlan.tremble(stream.mob.getId(), (long) Math.floor(now * 20), frozen);
        event.getPoseStack().translate(j[0], 0, j[1]);
        SHAKEN.put(stream.mob.getId(), j);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unshake(RenderLivingEvent.Post<?, ?> event) {
        if (SHAKEN.isEmpty()) return;
        double[] j = SHAKEN.remove(event.getEntity().getId());
        if (j != null) event.getPoseStack().translate(-j[0], 0, -j[1]);
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CutRibbon.shadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        boolean stars = starsOn(), rings = ringsOn(), stream = streamOn() && !STREAMS.isEmpty();
        if (!stars && !rings && !stream) return;
        Vec3 eye = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        float partial = event.getPartialTick();
        double now = now();
        PoseStack pose = event.getPoseStack();
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        List<Player> wearers = stars || rings ? wearers(eye) : List.of();
        if (!wearers.isEmpty()) {
            VertexConsumer out = buffers.getBuffer(CutRibbon.type());
            // everything is relative to the eye: floats far from the world's origin would shake
            for (Player player : wearers) {
                Vec3 feet = player.getPosition(partial);
                if (rings) rings(out, m, player, feet.subtract(eye), now);
                if (stars) stars(out, m, left, up, player, centre(feet, player).subtract(eye), now);
            }
            buffers.endBatch(CutRibbon.type());
        }
        if (stream) {
            Quaternionf facing = mc.getEntityRenderDispatcher().cameraOrientation();
            Font font = mc.font;
            for (Stream s : STREAMS.values()) {
                if (s.mob.isRemoved()) continue;
                streamOf(font, buffers, m, facing, left, s, s.mob.getPosition(partial).subtract(eye), now);
            }
            buffers.endBatch();
        }
    }

    /** The lines of noise rising over a mob and, once it hangs, the glyphs running down it towards the eye. */
    private static void streamOf(Font font, MultiBufferSource buffers, Matrix4f m, Quaternionf facing, Vector3f left, Stream s, Vec3 feet, double t) {
        double near = s.near(t), fade = s.fade(t);
        if (fade <= 0) return;
        int id = s.mob.getId(), lines = VoidWorldPlan.lines(near);
        double top = s.mob.getBbHeight(), rx = -left.x(), ry = -left.y(), rz = -left.z();
        double bx = feet.x, by = feet.y + top + 0.28, bz = feet.z;
        double gap = VoidWorldPlan.LINE_LIFE / lines;
        long k0 = (long) Math.floor(t / gap), frame = VoidWorldPlan.flicker(t);
        for (int j = 0; j < lines; j++) {
            long k = k0 - j;
            double age = t - k * gap;
            VoidWorldPlan.Rng r = new VoidWorldPlan.Rng("s|" + id + "|" + k + "|" + lines);
            int n = VoidWorldPlan.lineLength(r.next(), lines);
            double x0 = (r.next() * 2 - 1) * 0.18, h = VoidWorldPlan.lineRise(age), a = fade * VoidWorldPlan.lineAlpha(age);
            if (a < 0.02) continue;
            for (int c = 0; c < n; c++) {
                VoidWorldPlan.Rng q = new VoidWorldPlan.Rng("s|" + id + "|" + k + "|" + c + "|" + frame);
                char ch = VoidWorldPlan.glyph(q.next());
                int rgb = VoidWorldPlan.tone(new VoidWorldPlan.Rng("s|" + id + "|" + k + "|" + c).next());
                double side = (c - (n - 1) / 2.0) * 0.115 + x0;
                glyph(font, buffers, m, facing, ch, bx + rx * side, by + h + ry * side, bz + rz * side, 0.16, rgb, a * (0.75 + 0.25 * q.next()));
            }
        }
        double frozen = VoidWorldPlan.frozen(near) * fade;
        if (frozen <= 0) return;
        // the drips run on the side towards the eye
        double tx = -feet.x, tz = -feet.z, tl = Math.hypot(tx, tz), shift = s.mob.getBbWidth() / 2 + 0.05;
        if (tl < 1e-4) {
            tx = 0;
            tz = 0;
        } else {
            tx = tx / tl * shift;
            tz = tz / tl * shift;
        }
        for (int col = 0; col < 5; col++) {
            double[] d = VoidWorldPlan.drip(id, col);
            double head = VoidWorldPlan.dripHead(t, d[0], d[1], top);
            for (int c = 0; c < 4; c++) {
                double y = head + c * 0.13;
                if (y < 0.08 || y > top + 0.05) continue;
                VoidWorldPlan.Rng q = new VoidWorldPlan.Rng("d|" + id + "|" + col + "|" + c + "|" + frame);
                glyph(font, buffers, m, facing, VoidWorldPlan.glyph(q.next()), feet.x + tx + rx * d[2], feet.y + y + ry * d[2], feet.z + tz + rz * d[2],
                        0.1, c == 0 ? VoidWorldPlan.WHITE : VoidWorldPlan.CYAN, frozen * (0.95 - c * 0.2));
            }
        }
    }

    /** One glyph turned to the camera, {@code size} blocks high, centred on the point (relative to the eye). */
    private static void glyph(Font font, MultiBufferSource buffers, Matrix4f m, Quaternionf facing, char ch, double x, double y, double z,
                              double size, int rgb, double alpha) {
        // the font takes an alpha near 0 for opaque
        if (alpha < 0.02 || x * x + y * y + z * z < VoidWorldPlan.NEAR_EYES * VoidWorldPlan.NEAR_EYES) return;
        int index = VoidWorldPlan.GLYPHS.indexOf(ch);
        String text = index < 0 ? String.valueOf(ch) : GLYPH_TEXT[index];
        float scale = (float) (size / 8);
        GLYPH.set(m).translate((float) x, (float) y, (float) z).rotate(facing).scale(-scale, -scale, scale);
        int argb = (int) Math.round(Math.min(1, alpha) * 255) << 24 | rgb;
        font.drawInBatch(text, -font.width(text) / 2F, -4, argb, false, GLYPH, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    /** The sparks circling the wheel's core and falling into it, each with a streak, a halo and, as it dies, a flash. */
    private static void stars(VertexConsumer out, Matrix4f m, Vector3f left, Vector3f up, Player player, Vec3 core, double now) {
        double t = now + player.getId() * 0.37;
        for (int i = 0; i < VoidWorldPlan.STARS; i++) {
            double[] time = VoidWorldPlan.starTime(i, t);
            long cycle = (long) time[0];
            double local = time[1];
            double[] s = VoidWorldPlan.star(i, cycle, local);
            double u = s[3], a = VoidWorldPlan.starAlpha(u);
            double x = core.x + s[0], y = core.y + s[1], z = core.z + s[2];
            double r = VoidWorldPlan.starRadius(s[6], s[4], t, i);
            int rgb = VoidWorldPlan.starColour(s[5]);
            if (a > 0.01 && x * x + y * y + z * z >= VoidWorldPlan.NEAR_EYES * VoidWorldPlan.NEAR_EYES) {
                if (local > 0.18) {
                    double[] p = VoidWorldPlan.star(i, cycle, local - 0.18);
                    streak(out, m, core.x + p[0], core.y + p[1], core.z + p[2], x, y, z, r * 0.5, rgb, a * 0.35);
                }
                spot(out, m, left, up, x, y, z, r * 3.2, rgb, a * 0.16);
                spot(out, m, left, up, x, y, z, r, rgb, a);
            }
            double flash = VoidWorldPlan.starFlash(u);
            if (flash > 0 && core.lengthSqr() >= VoidWorldPlan.NEAR_EYES * VoidWorldPlan.NEAR_EYES)
                spot(out, m, left, up, core.x, core.y, core.z, 0.05 + 0.22 * flash, VoidWorldPlan.CYAN, 0.55 * flash);
        }
    }

    /** A thin straight streak from P to Q, its width across the line seen from the eye. */
    private static void streak(VertexConsumer out, Matrix4f m, double px, double py, double pz, double qx, double qy, double qz, double half, int rgb, double alpha) {
        double dx = qx - px, dy = qy - py, dz = qz - pz;
        double sx = dy * qz - dz * qy, sy = dz * qx - dx * qz, sz = dx * qy - dy * qx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-9) return;
        sx = sx / sl * half;
        sy = sy / sl * half;
        sz = sz / sl * half;
        CutRibbon.begin(2);
        CutRibbon.point(0, px, py, pz, sx * 0.3, sy * 0.3, sz * 0.3);
        CutRibbon.point(1, qx, qy, qz, sx, sy, sz);
        CutRibbon.solid(out, m, rgb, (float) alpha);
    }

    /** A soft disc facing the camera: full alpha in the middle, none at the edge. */
    private static void spot(VertexConsumer out, Matrix4f m, Vector3f left, Vector3f up, double x, double y, double z, double radius, int rgb, double alpha) {
        if (alpha < 0.005) return;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F, a = (float) Math.min(1, alpha);
        for (int i = 0; i < SPOT_SIDES; i++) {
            double c0 = SPOT_COS[i] * radius, s0 = SPOT_SIN[i] * radius, c1 = SPOT_COS[i + 1] * radius, s1 = SPOT_SIN[i + 1] * radius;
            CutRibbon.vertex(out, m, x, y, z, r, g, b, a);
            CutRibbon.vertex(out, m, x, y, z, r, g, b, a);
            CutRibbon.vertex(out, m, x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0, r, g, b, 0);
            CutRibbon.vertex(out, m, x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1, r, g, b, 0);
        }
    }

    /** The blue ring closing in and the red one spreading out on the ground round the feet, violet where they meet. */
    private static void rings(VertexConsumer out, Matrix4f m, Player player, Vec3 feet, double now) {
        double u = VoidWorldPlan.ringU(now + player.getId() * 0.37);
        if (u < 0) return;
        Vec3 world = feet.add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        double ground = ground(world), gy = ground - world.y + feet.y + 0.015;
        double blueR = VoidWorldPlan.blueRadius(u), redR = VoidWorldPlan.redRadius(u);
        double aB = VoidWorldPlan.blueAlpha(u), aR = VoidWorldPlan.redAlpha(u);
        for (int j = 0; j < VoidWorldPlan.TRAILS.length; j++) {
            double back = u - VoidWorldPlan.TRAILS[j] / VoidWorldPlan.RING_RUN;
            if (back < 0) continue;
            ring(out, m, world, feet.x, gy, feet.z, VoidWorldPlan.blueRadius(back), 0.06, 0x2F6BFF, aB * (0.2 + 0.18 * j), ground);
            ring(out, m, world, feet.x, gy, feet.z, VoidWorldPlan.redRadius(back), 0.06, 0xFF3B4A, aR * (0.2 + 0.18 * j), ground);
        }
        one(out, m, world, feet.x, gy, feet.z, blueR, aB, 0x8FB0FF, 0x2F6BFF, ground);
        one(out, m, world, feet.x, gy, feet.z, redR, aR, 0xFF8C95, 0xFF2A3C, ground);
        double f = VoidWorldPlan.meeting(u);
        if (f > 0) {
            double mid = (blueR + redR) / 2;
            ring(out, m, world, feet.x, gy, feet.z, mid, 0.26, 0x05040C, f * 0.3, ground);
            ring(out, m, world, feet.x, gy, feet.z, mid, 0.18, 0x9A5CFF, f * 0.8, ground);
            ring(out, m, world, feet.x, gy, feet.z, mid, 0.05, 0xE0CCFF, f * 0.85, ground);
        }
        double land = VoidWorldPlan.landing(u);
        if (land >= 0) flat(out, m, feet.x, feet.y + 0.12, feet.z, 0.3 + 0.5 * land, 0x5E8BFF, 0.6 * (1 - land));
    }

    /** One ring: a dark shadow under a soft glow, a bright line and a light core. */
    private static void one(VertexConsumer out, Matrix4f m, Vec3 world, double x, double y, double z, double radius, double a, int core, int glow, double ground) {
        ring(out, m, world, x, y, z, radius, 0.38, 0x05040C, a * 0.45, ground);
        ring(out, m, world, x, y, z, radius, 0.3, glow, a * 0.24, ground);
        ring(out, m, world, x, y, z, radius, 0.11, glow, a * 0.85, ground);
        ring(out, m, world, x, y, z, radius, 0.04, core, a * 0.9, ground);
    }

    /** A flat band from radius−w to radius+w round (x, y, z), leaving out its pieces that would lie inside a block. */
    private static void ring(VertexConsumer out, Matrix4f m, Vec3 world, double x, double y, double z, double radius, double w, int rgb, double alpha, double ground) {
        if (alpha < 0.005 || radius < 0.02) return;
        int n = Math.min(SKIP.length, VoidWorldPlan.segments(radius));
        double inner = Math.max(0, radius - w), outer = radius + w;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F, a = (float) Math.min(1, alpha);
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n, am = (a0 + a1) / 2;
            if (solid(world.x + Math.cos(am) * radius, ground + 0.5, world.z + Math.sin(am) * radius)) continue;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            CutRibbon.vertex(out, m, x + c0 * inner, y, z + s0 * inner, r, g, b, a);
            CutRibbon.vertex(out, m, x + c0 * outer, y, z + s0 * outer, r, g, b, a);
            CutRibbon.vertex(out, m, x + c1 * outer, y, z + s1 * outer, r, g, b, a);
            CutRibbon.vertex(out, m, x + c1 * inner, y, z + s1 * inner, r, g, b, a);
        }
    }

    /** A soft flat spot on the ground. */
    private static void flat(VertexConsumer out, Matrix4f m, double x, double y, double z, double radius, int rgb, double alpha) {
        if (alpha < 0.005) return;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F, a = (float) Math.min(1, alpha);
        for (int i = 0; i < SPOT_SIDES; i++) {
            CutRibbon.vertex(out, m, x, y, z, r, g, b, a);
            CutRibbon.vertex(out, m, x, y, z, r, g, b, a);
            CutRibbon.vertex(out, m, x + SPOT_COS[i] * radius, y, z + SPOT_SIN[i] * radius, r, g, b, 0);
            CutRibbon.vertex(out, m, x + SPOT_COS[i + 1] * radius, y, z + SPOT_SIN[i + 1] * radius, r, g, b, 0);
        }
    }

    /** The top of the first solid block under the feet within a few blocks, or the feet themselves. */
    private static double ground(Vec3 feet) {
        for (int down = 0; down <= GROUND_DROP; down++) {
            PROBE.set(Math.floor(feet.x), Math.floor(feet.y - 0.01) - down, Math.floor(feet.z));
            VoxelShape shape = level.getBlockState(PROBE).getCollisionShape(level, PROBE);
            if (shape.isEmpty()) continue;
            double top = PROBE.getY() + shape.max(Direction.Axis.Y);
            if (top <= feet.y + 0.01) return top;
        }
        return feet.y;
    }

    /** Whether a point is inside a block's collision shape. */
    private static boolean solid(double x, double y, double z) {
        PROBE.set(Math.floor(x), Math.floor(y), Math.floor(z));
        VoxelShape shape = level.getBlockState(PROBE).getCollisionShape(level, PROBE);
        if (shape.isEmpty()) return false;
        if (shape == Shapes.block()) return true;
        double lx = x - PROBE.getX(), ly = y - PROBE.getY(), lz = z - PROBE.getZ();
        for (AABB box : shape.toAabbs()) if (box.contains(lx, ly, lz)) return true;
        return false;
    }
}
