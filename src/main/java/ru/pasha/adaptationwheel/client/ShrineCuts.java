package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.WheelSkinConfig;
import ru.pasha.adaptationwheel.core.ShrineCutPlan;
import ru.pasha.adaptationwheel.core.WheelSkin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The Malevolent Shrine's cuts on the world around every player this client sees wearing it (design
 * malevolent-shrine-2026-11-01 rev 3): about four times a second a volley of 2–3 bent blades lands crossed on the first
 * block face along a ray from the shrine within 10 blocks, with a glint and chips of the block, each stopping at the
 * edge of its face and leaving a smouldering scar; mobs, animals and other players in range get a blade across the body,
 * about half the time a second one over it, with red drops, every 0.5–1.1 s. Twice as many while the wheel adapts to a
 * boss's existence. The wearer gets no cut. Picture only, on this client only: nothing touches blocks, entities, the
 * server or the network. Client config {@code shrineCuts} turns it off.
 */
@Mod.EventBusSubscriber(modid = AdaptationWheel.MOD_ID, value = Dist.CLIENT)
public final class ShrineCuts {
    /** Shrines farther than this from the camera cast no cuts. */
    private static final double SOURCE_RANGE = 64;
    /** A hard cap: far more than ten shrines' worth of cuts and scars. */
    private static final int MAX_CUTS = 800;
    /** How many segments a blade has; a drop, being tiny, gets fewer. */
    private static final int SEGMENTS = 10, DROP_SEGMENTS = 4;
    private static final double[] TAPER = ShrineCutPlan.taperTable(SEGMENTS), DROP_TAPER = ShrineCutPlan.taperTable(DROP_SEGMENTS);
    /** How many blocks down a chip of a cut on a wall may look for the ground it stops on. */
    private static final int CHIP_DROP = 3;
    private static final List<Cut> CUTS = new ArrayList<>();
    private static final Map<Integer, Double> NEXT_BLOCK = new HashMap<>();
    private static final Map<Long, Double> NEXT_BODY = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final long ORIGIN = System.nanoTime();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    private static ClientLevel level;

    private ShrineCuts() {
    }

    /** A chip of a block: start, start velocity, half size and colour. */
    private record Chip(double x, double y, double z, double vx, double vy, double vz, double half, int rgb) {
    }

    /** One cut: on a block (anchor null, world coordinates) or across a body (coordinates relative to its feet). */
    private static final class Cut {
        final double born;
        final Entity anchor;
        final double ax, ay, az, bx, by, bz;
        /** The block face's normal; unused across a body. */
        final float nx, ny, nz;
        /** The centre of the cut, where the glint is and the drops start. */
        final double cx, cy, cz;
        /** How far the blade bends. */
        final double sag;
        /** The cut that opens a volley has the glint. */
        final boolean first;
        final Chip[] chips;
        /** Across a body: the start velocities of its drops. */
        final double[][] drops;
        /** On a block: the height chips stop falling at. */
        final double floor;

        Cut(double born, Entity anchor, Vec3 a, Vec3 b, Direction face, Vec3 centre, double sag, boolean first, Chip[] chips, double[][] drops, double floor) {
            this.born = born;
            this.anchor = anchor;
            ax = a.x;
            ay = a.y;
            az = a.z;
            bx = b.x;
            by = b.y;
            bz = b.z;
            nx = face == null ? 0 : face.getStepX();
            ny = face == null ? 0 : face.getStepY();
            nz = face == null ? 0 : face.getStepZ();
            cx = centre.x;
            cy = centre.y;
            cz = centre.z;
            this.sag = sag;
            this.first = first;
            this.chips = chips;
            this.drops = drops;
            this.floor = floor;
        }

        boolean done(double now) {
            double age = now - born;
            if (anchor == null) return age >= ShrineCutPlan.LIFE + ShrineCutPlan.SCAR;
            return age >= Math.max(ShrineCutPlan.LIFE, ShrineCutPlan.DROP_END) || anchor.isRemoved();
        }
    }

    static double now() {
        return (System.nanoTime() - ORIGIN) / 1e9;
    }

    static boolean enabled() {
        try {
            return WheelSkinConfig.SHRINE_CUTS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** A player whose shrine this client draws: worn, the Malevolent Shrine look, visible. */
    private static boolean shrine(Player player) {
        ClientWheelState.State state = ClientWheelState.get(player.getId());
        return state != null && state.equipped() && player.isAlive() && !player.isInvisible() && !player.isSpectator()
                && WheelSkinClient.of(player) == WheelSkin.MALEVOLENT_SHRINE;
    }

    /** The shrine's centre over the player's head: the neck at 1.5 of 1.8 blocks, the look's mount 10 units above it. */
    private static Vec3 centre(Player player) {
        return new Vec3(player.getX(), player.getY() + player.getBbHeight() * (1.5 / 1.8) + 10 / 16.0, player.getZ());
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
        double now = now();
        if (!enabled()) {
            clear();
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Set<Integer> sources = new HashSet<>();
        Set<Long> bodies = new HashSet<>();
        for (Player player : level.players()) {
            if (!shrine(player) || player.position().distanceToSqr(camera) > SOURCE_RANGE * SOURCE_RANGE) continue;
            ClientWheelState.State state = ClientWheelState.get(player.getId());
            boolean existence = state != null && state.existenceAdapting();
            Vec3 centre = centre(player);
            sources.add(player.getId());
            double next = NEXT_BLOCK.getOrDefault(player.getId(), now);
            // after a pause or a lag spike do not pour out the missed cuts at once
            if (next < now - 0.5) next = now;
            while (next <= now) {
                volley(player, centre, next);
                next += ShrineCutPlan.volleyGap(RANDOM.nextDouble(), existence);
            }
            NEXT_BLOCK.put(player.getId(), next);
            double r = ShrineCutPlan.RANGE;
            for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(r),
                    e -> e != player && e.isAlive() && !e.isInvisible() && !(e instanceof ArmorStand) && !e.isSpectator())) {
                if (body.getBoundingBox().getCenter().distanceToSqr(centre) > r * r) continue;
                long key = (long) player.getId() << 32 | (body.getId() & 0xFFFFFFFFL);
                bodies.add(key);
                Double due = NEXT_BODY.get(key);
                double at = due == null ? now + ShrineCutPlan.mobFirst(RANDOM.nextDouble()) : Math.max(due, now - 0.5);
                while (at <= now) {
                    bodyCut(body, centre, at);
                    at += ShrineCutPlan.mobGap(RANDOM.nextDouble(), existence);
                }
                NEXT_BODY.put(key, at);
            }
        }
        NEXT_BLOCK.keySet().retainAll(sources);
        NEXT_BODY.keySet().retainAll(bodies);
        CUTS.removeIf(cut -> cut.done(now));
        if (CUTS.size() > MAX_CUTS) CUTS.subList(0, CUTS.size() - MAX_CUTS).clear();
    }

    /** A volley from the shrine lands on the first block face on its way: 2–3 cuts crossing there, lying flat on it. */
    private static void volley(Player player, Vec3 centre, double born) {
        double[] d = ShrineCutPlan.ray(RANDOM.nextDouble(), RANDOM.nextDouble());
        Vec3 end = centre.add(d[0] * ShrineCutPlan.RANGE, d[1] * ShrineCutPlan.RANGE, d[2] * ShrineCutPlan.RANGE);
        BlockHitResult hit = level.clip(new ClipContext(centre, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || hit.isInside()) return;
        Direction face = hit.getDirection();
        double[] n = {face.getStepX(), face.getStepY(), face.getStepZ()};
        // two axes in the face's plane
        Vec3 u = face.getAxis() == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 v = face.getAxis() == Direction.Axis.Z ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        int count = ShrineCutPlan.volleyCount(RANDOM.nextDouble());
        double first = ShrineCutPlan.blockAngle(RANDOM.nextDouble());
        Vec3 point = hit.getLocation().add(n[0] * 0.02, n[1] * 0.02, n[2] * 0.02);
        for (int k = 0; k < count; k++) {
            double angle = ShrineCutPlan.volleyAngle(first, k, count, RANDOM.nextDouble());
            double half = ShrineCutPlan.blockLength(RANDOM.nextDouble()) / 2;
            double sag = ShrineCutPlan.blockSag(RANDOM.nextDouble(), RANDOM.nextDouble());
            Vec3 c = point.add(u.scale(ShrineCutPlan.volleyOffset(RANDOM.nextDouble()))).add(v.scale(ShrineCutPlan.volleyOffset(RANDOM.nextDouble())));
            Vec3 along = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            double[] dir = {along.x, along.y, along.z};
            double[] span = ShrineCutPlan.span(new double[]{c.x, c.y, c.z}, dir, n, half, ShrineCuts::solid);
            if (span == null || span[0] + span[1] < ShrineCutPlan.MIN_SPAN) continue;
            Vec3 a = c.subtract(along.scale(span[0])), b = c.add(along.scale(span[1]));
            add(new Cut(born + k * ShrineCutPlan.VOLLEY_STEP, null, a, b, face, c, sag, k == 0, chips(a, b, n, dir), null, floor(c, n)));
        }
    }

    /** Whether a point is inside a block's collision shape: where a cut stops at the edge of its face. */
    private static boolean solid(double x, double y, double z) {
        PROBE.set(Math.floor(x), Math.floor(y), Math.floor(z));
        VoxelShape shape = level.getBlockState(PROBE).getCollisionShape(level, PROBE);
        if (shape.isEmpty()) return false;
        if (shape == Shapes.block()) return true;
        double lx = x - PROBE.getX(), ly = y - PROBE.getY(), lz = z - PROBE.getZ();
        for (AABB box : shape.toAabbs()) if (box.contains(lx, ly, lz)) return true;
        return false;
    }

    private static Chip[] chips(Vec3 a, Vec3 b, double[] n, double[] dir) {
        Chip[] chips = new Chip[ShrineCutPlan.CHIPS];
        for (int i = 0; i < chips.length; i++) {
            Vec3 at = a.lerp(b, 0.2 + 0.6 * RANDOM.nextDouble());
            double[] velocity = ShrineCutPlan.chip(n, dir, RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
            chips[i] = new Chip(at.x, at.y, at.z, velocity[0], velocity[1], velocity[2], ShrineCutPlan.chipHalf(RANDOM.nextDouble()), chipColour(at, n));
        }
        return chips;
    }

    /** The map colour of the block under the point on a face, in one of two shades, as dark as the light before the face. */
    private static int chipColour(Vec3 at, double[] n) {
        PROBE.set(Math.floor(at.x - n[0] * 0.1), Math.floor(at.y - n[1] * 0.1), Math.floor(at.z - n[2] * 0.1));
        int rgb = level.getBlockState(PROBE).getMapColor(level, PROBE).col;
        if (rgb == 0) rgb = 0x6B6B6B;
        PROBE.set(Math.floor(at.x + n[0] * 0.6), Math.floor(at.y + n[1] * 0.6), Math.floor(at.z + n[2] * 0.6));
        double shade = (0.35 + 0.65 * level.getMaxLocalRawBrightness(PROBE) / 15.0) * (RANDOM.nextBoolean() ? 1 : 0.75);
        return (int) ((rgb >> 16 & 255) * shade) << 16 | (int) ((rgb >> 8 & 255) * shade) << 8 | (int) ((rgb & 255) * shade);
    }

    /** The height of the first solid top under a cut, so its chips do not fall through the ground. */
    private static double floor(Vec3 c, double[] n) {
        if (n[1] > 0) return c.y + 0.01;
        double x = c.x + n[0] * 0.3, z = c.z + n[2] * 0.3;
        for (int down = 0; down <= CHIP_DROP; down++) {
            PROBE.set(Math.floor(x), Math.floor(c.y) - down, Math.floor(z));
            VoxelShape shape = level.getBlockState(PROBE).getCollisionShape(level, PROBE);
            if (!shape.isEmpty()) return PROBE.getY() + shape.max(Direction.Axis.Y) + 0.03;
        }
        return -Double.MAX_VALUE;
    }

    /** A blade across a body on its side towards the shrine, sloped, with red drops flying off; often a second one over it. */
    private static void bodyCut(LivingEntity body, Vec3 centre, double born) {
        double height = body.getBbHeight(), width = body.getBbWidth();
        double tx = centre.x - body.getX(), tz = centre.z - body.getZ(), length = Math.hypot(tx, tz);
        if (length < 1e-4) {
            tx = 1;
            tz = 0;
        } else {
            tx /= length;
            tz /= length;
        }
        double slope = ShrineCutPlan.mobSlope(RANDOM.nextDouble(), RANDOM.nextDouble());
        Vec3 c = new Vec3(tx * (width / 2 + 0.05), height * ShrineCutPlan.MOB_HEIGHT, tz * (width / 2 + 0.05));
        double size = ShrineCutPlan.mobLength(height, width, RANDOM.nextDouble());
        double[][] drops = new double[ShrineCutPlan.DROPS][];
        for (int k = 0; k < drops.length; k++)
            drops[k] = ShrineCutPlan.drop(tx, tz, RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
        Vec3 along = across(tx, tz, slope).scale(size / 2);
        add(new Cut(born, body, c.subtract(along), c.add(along), null, c, ShrineCutPlan.MOB_SAG, true, null, drops, 0));
        if (RANDOM.nextDouble() < ShrineCutPlan.CROSS_CHANCE) {
            Vec3 cross = across(tx, tz, ShrineCutPlan.crossSlope(slope, RANDOM.nextDouble())).scale(size / 2 * ShrineCutPlan.CROSS_LENGTH);
            add(new Cut(born + ShrineCutPlan.CROSS_DELAY, body, c.subtract(cross), c.add(cross), null, c, -ShrineCutPlan.MOB_SAG, false, null, new double[0][], 0));
        }
    }

    /** Level and across the line to the shrine, then sloped. */
    private static Vec3 across(double tx, double tz, double slope) {
        return new Vec3(-tz * Math.cos(slope), Math.sin(slope), tx * Math.cos(slope));
    }

    private static void add(Cut cut) {
        CUTS.add(cut);
    }

    static void clear() {
        CUTS.clear();
        NEXT_BLOCK.clear();
        NEXT_BODY.clear();
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CUTS.isEmpty() || !enabled() || CutRibbon.shadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        Vec3 eye = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        float partial = event.getPartialTick();
        double now = now();
        PoseStack pose = event.getPoseStack();
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(CutRibbon.type());
        // everything below is relative to the eye: floats far from the world's origin would shake
        for (Cut cut : CUTS) {
            double age = now - cut.born;
            if (age < 0) continue;
            double ox = -eye.x, oy = -eye.y, oz = -eye.z;
            if (cut.anchor != null) {
                if (cut.anchor.isRemoved()) continue;
                Vec3 at = cut.anchor.getPosition(partial);
                ox += at.x;
                oy += at.y;
                oz += at.z;
            }
            double ax = cut.ax + ox, ay = cut.ay + oy, az = cut.az + oz, bx = cut.bx + ox, by = cut.by + oy, bz = cut.bz + oz;
            double mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
            // nothing flashes right in front of the eyes
            if (mx * mx + my * my + mz * mz < ShrineCutPlan.NEAR_EYES * ShrineCutPlan.NEAR_EYES) continue;
            boolean block = cut.anchor == null;
            // flat on the block face, turned to the eye across a body
            double nx = block ? cut.nx : -mx, ny = block ? cut.ny : -my, nz = block ? cut.nz : -mz;
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double sx = dy * nz - dz * ny, sy = dz * nx - dx * nz, sz = dx * ny - dy * nx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9) continue;
            sx /= sl;
            sy /= sl;
            sz /= sl;
            ShrineCutPlan.Shape shape = ShrineCutPlan.shape(age);
            double p = shape == null ? 1 : shape.progress();
            if (block) {
                double scar = ShrineCutPlan.scar(age), ember = ShrineCutPlan.ember(age);
                if (scar > 0) {
                    path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, 0, p, ShrineCutPlan.SCAR_RIM);
                    CutRibbon.solid(out, m, 0x5E0812, (float) (0.45 * scar));
                    path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, 0, p, ShrineCutPlan.SCAR_SLIT);
                    CutRibbon.solid(out, m, 0x16020A, (float) (0.85 * scar));
                }
                if (ember > 0) {
                    path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, 0, p, ShrineCutPlan.SCAR_EMBER);
                    CutRibbon.solid(out, m, 0xFF5A1E, (float) ember);
                }
            }
            if (shape != null) {
                double thin = block ? ShrineCutPlan.ON_BLOCK : 1;
                path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, shape.tail(), p, ShrineCutPlan.FRINGE * thin * ShrineCutPlan.HALO);
                CutRibbon.light(out, m, ShrineCutPlan.heatRgb(shape.heat()), (float) shape.glow());
                path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, shape.tail(), p, ShrineCutPlan.FRINGE * thin * shape.fringe());
                CutRibbon.solid(out, m, 0xFF2A2A, 1);
                path(ax, ay, az, dx, dy, dz, sx, sy, sz, cut.sag, shape.tail(), p, ShrineCutPlan.CORE * thin * shape.core());
                CutRibbon.solid(out, m, 0xFFFFFF, 1);
            }
            if (cut.first && age < ShrineCutPlan.GLINT) glint(out, m, left, up, cut.cx + ox, cut.cy + oy, cut.cz + oz, 1 - age / ShrineCutPlan.GLINT);
            if (block) chips(out, m, left, up, cut, ox, oy, oz, age);
            else drops(out, m, cut, ox, oy, oz, age);
        }
        buffers.endBatch(CutRibbon.type());
    }

    /**
     * Lays the blade from A along (dx, dy, dz) on the ribbon, from {@code u0} to {@code u1} of its length: bent by
     * {@code sag} along the unit side (sx, sy, sz), {@code half} wide in the middle of the part shown, nothing at its ends.
     */
    private static void path(double ax, double ay, double az, double dx, double dy, double dz, double sx, double sy, double sz,
                             double sag, double u0, double u1, double half) {
        CutRibbon.begin(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            double u = u0 + (u1 - u0) * i / SEGMENTS, bend = sag * Math.sin(Math.PI * u), width = half * TAPER[i];
            CutRibbon.point(i, ax + dx * u + sx * bend, ay + dy * u + sy * bend, az + dz * u + sz * bend, sx * width, sy * width, sz * width);
        }
    }

    /** A white star where a volley lands: a level and an upright sliver with soft red light, shrinking as it fades. */
    private static void glint(VertexConsumer out, Matrix4f m, Vector3f left, Vector3f up, double x, double y, double z, double k) {
        double wide = 0.1 + 0.45 * k, tall = 0.06 + 0.28 * k, half = 0.008 + 0.03 * k;
        bar(left, up, x, y, z, wide, 0.16);
        CutRibbon.light(out, m, 0xFF5A5A, (float) k);
        bar(left, up, x, y, z, wide, half);
        CutRibbon.solid(out, m, 0xFFFFFF, 1);
        bar(up, left, x, y, z, tall, half);
        CutRibbon.solid(out, m, 0xFFFFFF, 1);
    }

    /** A straight blade on the ribbon around a point, {@code length} each way along {@code along}, its width along {@code side}. */
    private static void bar(Vector3f along, Vector3f side, double x, double y, double z, double length, double half) {
        CutRibbon.begin(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            double s = length * (2.0 * i / SEGMENTS - 1), width = half * TAPER[i];
            CutRibbon.point(i, x + along.x() * s, y + along.y() * s, z + along.z() * s, side.x() * width, side.y() * width, side.z() * width);
        }
    }

    /** Chips of the block, small squares facing the camera, thrown off the cut and falling to the ground. */
    private static void chips(VertexConsumer out, Matrix4f m, Vector3f left, Vector3f up, Cut cut, double ox, double oy, double oz, double age) {
        float alpha = (float) ShrineCutPlan.chipAlpha(age);
        if (alpha <= 0) return;
        for (Chip chip : cut.chips) {
            double px = chip.x + chip.vx * age + ox, pz = chip.z + chip.vz * age + oz;
            double py = Math.max(cut.floor, chip.y + chip.vy * age - ShrineCutPlan.CHIP_GRAVITY * age * age) + oy;
            double lx = left.x() * chip.half, ly = left.y() * chip.half, lz = left.z() * chip.half;
            double ux = up.x() * chip.half, uy = up.y() * chip.half, uz = up.z() * chip.half;
            float r = (chip.rgb >> 16 & 255) / 255F, g = (chip.rgb >> 8 & 255) / 255F, b = (chip.rgb & 255) / 255F;
            CutRibbon.vertex(out, m, px + lx + ux, py + ly + uy, pz + lz + uz, r, g, b, alpha);
            CutRibbon.vertex(out, m, px - lx + ux, py - ly + uy, pz - lz + uz, r, g, b, alpha);
            CutRibbon.vertex(out, m, px - lx - ux, py - ly - uy, pz - lz - uz, r, g, b, alpha);
            CutRibbon.vertex(out, m, px + lx - ux, py + ly - uy, pz + lz - uz, r, g, b, alpha);
        }
    }

    /** Red drops, stretched along their flight and turned to the eye, bright at first and dark as they fall. */
    private static void drops(VertexConsumer out, Matrix4f m, Cut cut, double ox, double oy, double oz, double age) {
        float alpha = (float) ShrineCutPlan.dropAlpha(age);
        if (alpha <= 0) return;
        double tau = age - ShrineCutPlan.DRAW;
        int rgb = tau < ShrineCutPlan.DROP_HOT ? 0xFF4A3A : 0xA80C18;
        for (double[] v : cut.drops) {
            double qx = ox + cut.cx + v[0] * tau, qy = oy + cut.cy + v[1] * tau - ShrineCutPlan.DROP_GRAVITY * tau * tau, qz = oz + cut.cz + v[2] * tau;
            double vx = v[0], vy = v[1] - 2 * ShrineCutPlan.DROP_GRAVITY * tau, vz = v[2];
            // the side: across the flight, seen from the eye at the origin
            double sx = vy * qz - vz * qy, sy = vz * qx - vx * qz, sz = vx * qy - vy * qx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9) continue;
            CutRibbon.begin(DROP_SEGMENTS + 1);
            for (int i = 0; i <= DROP_SEGMENTS; i++) {
                double back = ShrineCutPlan.DROP_STRETCH * (1 - (double) i / DROP_SEGMENTS), width = ShrineCutPlan.DROP_HALF * DROP_TAPER[i] / sl;
                CutRibbon.point(i, qx - vx * back, qy - vy * back, qz - vz * back, sx * width, sy * width, sz * width);
            }
            CutRibbon.solid(out, m, rgb, alpha);
        }
    }
}
