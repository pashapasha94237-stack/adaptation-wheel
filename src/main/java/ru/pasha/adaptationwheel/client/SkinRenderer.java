package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.ShrineCutPlan;
import ru.pasha.adaptationwheel.core.SkinRig;
import ru.pasha.adaptationwheel.core.WheelSkin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a wheel look, on a player in the world and in the look menu. The classic wheel keeps its original model and
 * texture; the other looks are {@link SkinRig}s: their boxes go into one lit pass and one unlit pass for glowing
 * materials (the beacon beam's shader has no shading, so a glowing face is as bright as the model shows it).
 */
public final class SkinRenderer {
    private static final ResourceLocation CLASSIC_TEXTURE = new ResourceLocation(AdaptationWheel.MOD_ID, "textures/entity/wheel.png");
    /** The light directions of every entity in the world (vanilla Lighting), so the menu lights a model as the world does. */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize(), LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Map<WheelSkin, Look> LOOKS = new EnumMap<>(WheelSkin.class);
    private static ModelPart classic;
    // Scratch space: everything here runs on the render thread.
    private static final Matrix4f BOX = new Matrix4f();
    private static final Matrix3f BOX_NORMAL = new Matrix3f();
    private static final Vector3f CORNER = new Vector3f(), NORMAL = new Vector3f();
    private static final float[] CX = new float[8], CY = new float[8], CZ = new float[8];
    private static final Matrix4f FLASH_BOX = new Matrix4f();
    private static final Matrix3f FLASH_NORMAL = new Matrix3f();
    private static final double[] FLASH_LOCAL = new double[12], FLASH_SIZE = new double[3];
    /** How many segments the light around a slash has. */
    private static final int LIGHT_SEGMENTS = 12;
    private static final double[] LIGHT_TAPER = ShrineCutPlan.taperTable(LIGHT_SEGMENTS), LIGHT_POINT = new double[3];
    private static final double[] LX = new double[LIGHT_SEGMENTS + 1], LY = new double[LIGHT_SEGMENTS + 1], LZ = new double[LIGHT_SEGMENTS + 1];
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** True between {@link #begin} and {@link #end}: the look is drawn in a menu. */
    private static boolean inMenu;

    private SkinRenderer() {
    }

    /** A box look ready to draw: its rig, the boxes' matrices and the atlas uploaded as a texture. */
    private static final class Look {
        final SkinRig rig;
        final Matrix4f[] local;
        final Matrix3f[] normal;
        final ResourceLocation texture;
        final boolean glows, lit;

        Look(WheelSkin skin, SkinRig rig) {
            this.rig = rig;
            local = new Matrix4f[rig.boxCount];
            normal = new Matrix3f[rig.boxCount];
            int i = 0;
            boolean glow = false, light = false;
            for (SkinRig.Group g : rig.groups) {
                light |= g.flash != null && g.flash.glowHalf() > 0;
                for (SkinRig.Box b : g.boxes) {
                    float[] m = b.local;
                    // row-major 3×4 into JOML's column-major constructor
                    local[i] = new Matrix4f(m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, m[3], m[7], m[11], 1);
                    normal[i] = new Matrix3f(m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10]);
                    glow |= b.emissive;
                    i++;
                }
            }
            glows = glow;
            lit = light;
            NativeImage image = new NativeImage(rig.atlas.width, rig.atlas.height, false);
            for (int y = 0; y < rig.atlas.height; y++)
                for (int x = 0; x < rig.atlas.width; x++) {
                    int argb = rig.atlas.argb[y * rig.atlas.width + x];
                    // NativeImage keeps ABGR
                    image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF);
                }
            texture = new ResourceLocation(AdaptationWheel.MOD_ID, "dynamic/wheel_skin_" + skin.id);
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
        }
    }

    /** The look's box model, built and uploaded on first use; null for the classic wheel or a look that failed to load. */
    private static Look look(WheelSkin skin) {
        if (skin == WheelSkin.CLASSIC) return null;
        if (LOOKS.containsKey(skin)) return LOOKS.get(skin);
        Look look = null;
        try {
            look = new Look(skin, SkinRig.load(skin.resource()));
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Wheel look {} could not be loaded; the classic wheel is drawn instead", skin.id, failure);
        }
        LOOKS.put(skin, look);
        return look;
    }

    private static ModelPart classic() {
        if (classic == null) classic = Minecraft.getInstance().getEntityModels().bakeLayer(WheelModel.LAYER).getChild("wheel");
        return classic;
    }

    /** Model-space height (units, Y down) of the look's centre over the neck. */
    static float mountY(WheelSkin skin) {
        Look look = look(skin);
        return look == null ? -14.72F : look.rig.mountY;
    }

    /**
     * Draws a look with the pose at the head's pivot in player model space (blocks, Y down): {@code turn} in radians
     * (45° a stage), {@code pulse} the stage pulse, {@code seconds} the clock of the looks' own motion.
     */
    static void render(WheelSkin skin, PoseStack pose, MultiBufferSource buffers, int light, float turn, float pulse, float seconds) {
        Look look = look(skin);
        pose.pushPose();
        if (look == null) {
            pose.translate(0, -0.92F, 0);
            pose.mulPose(Axis.YP.rotation(turn));
            pose.scale(pulse, pulse, pulse);
            classic().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CLASSIC_TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        } else {
            SkinRig rig = look.rig;
            pose.translate(rig.mountX / 16F, rig.mountY / 16F, rig.mountZ / 16F);
            if (rig.pitchDeg != 0) pose.mulPose(Axis.XP.rotationDegrees(rig.pitchDeg));
            pose.mulPose(Axis.YP.rotation(turn));
            pose.scale(pulse / 16F, pulse / 16F, pulse / 16F);
            double turnDeg = Math.toDegrees(turn);
            boxes(look, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(look.texture)), light, false, turnDeg, seconds);
            // a second render type only after the first one is complete: the shared buffer draws the first on the switch
            // under a shader pack beaconBeam goes through the pack's gbuffers_beaconbeam, which may stretch it into a beam
            // (Builders' FANCY_BEACONS: rays to the sky); eyes goes through gbuffers_spidereyes, plain glow in every pack
            RenderType glow = CutRibbon.shaders() ? RenderType.eyes(look.texture) : RenderType.beaconBeam(look.texture, false);
            if (look.glows) boxes(look, pose, buffers.getBuffer(glow), LightTexture.FULL_BRIGHT, true, turnDeg, seconds);
            if (look.lit && !CutRibbon.shadowPass()) slashLight(look, pose, buffers.getBuffer(CutRibbon.type()), turnDeg, seconds);
        }
        pose.popPose();
    }

    /**
     * The soft light around the slashes that are out now: a ribbon along each blade's bend, turned to the eye point by
     * point, red with a white flash. The pose is already in view space, so the points are written as they are.
     */
    private static void slashLight(Look look, PoseStack pose, VertexConsumer out, double turnDeg, double seconds) {
        for (SkinRig.Group g : look.rig.groups) {
            if (g.flash == null || g.flash.glowHalf() <= 0) continue;
            pose.pushPose();
            double bob = g.bob(seconds), angle = g.angleDeg(turnDeg, seconds) % 360;
            if (bob != 0) pose.translate(0, bob, 0);
            if (angle != 0) pose.mulPose(Axis.YP.rotationDegrees((float) angle));
            Matrix4f spin = pose.last().pose();
            double scale = spin.getScale(CORNER).x * g.flash.glowHalf();
            for (int copy = 0; copy < g.count; copy++) {
                SkinRig.FlashState st = g.flashState(copy, seconds);
                if (st == null) continue;
                double from = st.from(), to = st.to();
                if (to - from < 0.01) continue;
                double[] copyMatrix = g.copyMatrix(copy, st);
                for (int i = 0; i <= LIGHT_SEGMENTS; i++) {
                    g.bladePoint(copyMatrix, from + (to - from) * i / LIGHT_SEGMENTS, LIGHT_POINT);
                    spin.transformPosition((float) LIGHT_POINT[0], (float) LIGHT_POINT[1], (float) LIGHT_POINT[2], CORNER);
                    LX[i] = CORNER.x;
                    LY[i] = CORNER.y;
                    LZ[i] = CORNER.z;
                }
                CutRibbon.begin(LIGHT_SEGMENTS + 1);
                for (int i = 0; i <= LIGHT_SEGMENTS; i++) {
                    int next = Math.min(LIGHT_SEGMENTS, i + 1), last = Math.max(0, i - 1);
                    double dx = LX[next] - LX[last], dy = LY[next] - LY[last], dz = LZ[next] - LZ[last];
                    // in the menu the picture is orthographic and looks along z; in the world the eye is the origin
                    double ex = inMenu ? 0 : -LX[i], ey = inMenu ? 0 : -LY[i], ez = inMenu ? 1 : -LZ[i];
                    double sx = dy * ez - dz * ey, sy = dz * ex - dx * ez, sz = dx * ey - dy * ex, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                    double width = sl < 1e-9 ? 0 : scale * LIGHT_TAPER[i] / sl;
                    CutRibbon.point(i, LX[i], LY[i], LZ[i], sx * width, sy * width, sz * width);
                }
                CutRibbon.light(out, IDENTITY, ShrineCutPlan.heatRgb(st.heat()), (float) st.glow());
            }
            pose.popPose();
        }
    }

    private static void boxes(Look look, PoseStack pose, VertexConsumer out, int light, boolean glowing, double turnDeg, double seconds) {
        SkinRig rig = look.rig;
        float aw = rig.atlas.width, ah = rig.atlas.height;
        int index = 0;
        for (SkinRig.Group g : rig.groups) {
            pose.pushPose();
            double bob = g.bob(seconds), angle = g.angleDeg(turnDeg, seconds) % 360;
            if (bob != 0) pose.translate(0, bob, 0);
            if (angle != 0) pose.mulPose(Axis.YP.rotationDegrees((float) angle));
            Matrix4f spin = pose.last().pose();
            Matrix3f spinNormal = pose.last().normal();
            if (g.flash != null) {
                // a slash ring: only the copies that are out now, each placed and sized for this moment
                for (int copy = 0; copy < g.count; copy++) {
                    SkinRig.FlashState st = g.flashState(copy, seconds);
                    if (st == null) continue;
                    double[] copyMatrix = g.copyMatrix(copy, st);
                    for (int k = 0; k < g.perCopy; k++) {
                        SkinRig.Box b = g.boxes[copy * g.perCopy + k];
                        if (b.emissive != glowing || !g.flashBox(copyMatrix, k, st, FLASH_LOCAL, FLASH_SIZE)) continue;
                        double[] m = FLASH_LOCAL;
                        FLASH_BOX.set((float) m[0], (float) m[4], (float) m[8], 0, (float) m[1], (float) m[5], (float) m[9], 0,
                                (float) m[2], (float) m[6], (float) m[10], 0, (float) m[3], (float) m[7], (float) m[11], 1);
                        BOX.set(spin).mul(FLASH_BOX);
                        BOX_NORMAL.set(spinNormal).mul(FLASH_BOX.get3x3(FLASH_NORMAL));
                        box(out, b.texture, (float) FLASH_SIZE[0], (float) FLASH_SIZE[1], (float) FLASH_SIZE[2], light, aw, ah);
                    }
                }
                index += g.boxes.length;
                pose.popPose();
                continue;
            }
            for (SkinRig.Box b : g.boxes) {
                int i = index++;
                if (b.emissive != glowing) continue;
                BOX.set(spin).mul(look.local[i]);
                BOX_NORMAL.set(spinNormal).mul(look.normal[i]);
                box(out, b.texture, b.w, b.h, b.d, light, aw, ah);
            }
            pose.popPose();
        }
    }

    /** One box of size w×h×d around the origin of {@link #BOX}, its normals through {@link #BOX_NORMAL}. */
    private static void box(VertexConsumer out, SkinRig.Texture t, float w, float h, float d, int light, float aw, float ah) {
        for (int k = 0; k < 8; k++) {
            BOX.transformPosition((k & 1) != 0 ? w / 2 : -w / 2, (k & 2) != 0 ? h / 2 : -h / 2, (k & 4) != 0 ? d / 2 : -d / 2, CORNER);
            CX[k] = CORNER.x;
            CY[k] = CORNER.y;
            CZ[k] = CORNER.z;
        }
        for (int f = 0; f < 6; f++) {
            float[] n = SkinRig.NORMAL[f];
            BOX_NORMAL.transform(n[0], n[1], n[2], NORMAL).normalize();
            for (int j = 0; j < 4; j++) {
                int c = SkinRig.FACE[f][j];
                float u = (t.atlasX + t.x0[f] + SkinRig.CU[j] * t.fw[f]) / aw, v = (t.atlasY + SkinRig.CV[j] * t.fh[f]) / ah;
                out.vertex(CX[c], CY[c], CZ[c], 1, 1, 1, 1, u, v, OverlayTexture.NO_OVERLAY, light, NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }
    }

    // ------------------------------------------------------------------ the look menu

    /**
     * An orbit camera as in AdaptationHub's model view: {@code yaw}/{@code pitch} in radians around a target (units, world:
     * y up, 24 = the neck), {@code distance} and a 30° field of view decide the scale; the picture is orthographic.
     * Fills the rectangle and returns with the depth buffer as it was before the call.
     */
    record Camera(double yaw, double pitch, double distance, double targetY) {
        /** GUI pixels per model unit for a box {@code height} pixels high. */
        float scale(int height) {
            return (float) (height / (2 * distance * Math.tan(Math.toRadians(15))));
        }
    }

    /** A look alone, as the list's thumbnails show it: still (time 2 s, no turn). */
    static void thumbnail(GuiGraphics g, WheelSkin skin, int x, int y, int size) {
        Camera camera = new Camera(Math.PI - 0.5, 0.62, 66, 24 - mountY(skin));
        g.flush();
        g.enableScissor(x, y, x + size, y + size);
        begin(g, camera, x + size / 2F, y + size / 2F, camera.scale(size));
        PoseStack pose = g.pose();
        // player model space (Y down, origin at the neck) into the world (y up, the neck at 24 units)
        pose.translate(0, 24 / 16F, 0);
        pose.scale(-1, -1, 1);
        render(skin, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, 0, 1, 2);
        end(g);
        g.disableScissor();
    }

    /**
     * The player wearing a look, as the menu's preview shows it; without a player (a world is not open) the look alone.
     * {@code turn}/{@code pulse}/{@code seconds} drive the look exactly as a worn wheel.
     */
    static void preview(GuiGraphics g, LivingEntity player, WheelSkin skin, int x0, int y0, int x1, int y1, Camera camera,
                        float turn, float pulse, float seconds) {
        g.flush();
        g.enableScissor(x0, y0, x1, y1);
        // a tall narrow preview (a narrow window that is very high) must not cut the wheel at the sides
        begin(g, camera, (x0 + x1) / 2F, (y0 + y1) / 2F, camera.scale(Math.min(y1 - y0, x1 - x0)));
        PoseStack pose = g.pose();
        if (player == null) {
            pose.translate(0, 24 / 16F, 0);
            pose.scale(-1, -1, 1);
            render(skin, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, turn, pulse, seconds);
        } else {
            // facing the camera's start as in the program's view: body and head at 180°, looking straight ahead
            float body = player.yBodyRot, bodyO = player.yBodyRotO, head = player.yHeadRot, headO = player.yHeadRotO;
            float yaw = player.getYRot(), yawO = player.yRotO, pitch = player.getXRot(), pitchO = player.xRotO;
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 180;
            player.setYRot(180);
            player.yRotO = 180;
            player.setXRot(0);
            player.xRotO = 0;
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            WheelSkinClient.Preview previous = WheelSkinClient.preview;
            WheelSkinClient.preview = new WheelSkinClient.Preview(player, skin, turn, pulse, seconds);
            try {
                dispatcher.setRenderShadow(false);
                RenderSystem.runAsFancy(() -> dispatcher.render(player, 0, 0, 0, 0, 1, pose, g.bufferSource(), LightTexture.FULL_BRIGHT));
                g.flush();
            } finally {
                WheelSkinClient.preview = previous;
                dispatcher.setRenderShadow(true);
                player.yBodyRot = body;
                player.yBodyRotO = bodyO;
                player.yHeadRot = head;
                player.yHeadRotO = headO;
                player.setYRot(yaw);
                player.yRotO = yawO;
                player.setXRot(pitch);
                player.xRotO = pitchO;
            }
        }
        end(g);
        g.disableScissor();
    }

    /**
     * Puts the camera on the GUI pose: world blocks (y up) around the target to GUI pixels (y down, z towards the viewer),
     * normals into the camera's frame, and the world's two lights into the same frame.
     */
    private static void begin(GuiGraphics g, Camera c, float centreX, float centreY, float pixelsPerUnit) {
        double ex = Math.sin(c.yaw()) * Math.cos(c.pitch()), ey = Math.sin(c.pitch()), ez = Math.cos(c.yaw()) * Math.cos(c.pitch());
        // the program's CameraView: forward −e, right (−fz, 0, fx) normalised, up = right × forward
        double fx = -ex, fy = -ey, fz = -ez, rl = Math.max(1e-9, Math.hypot(fz, fx));
        float rx = (float) (-fz / rl), rz = (float) (fx / rl);
        float ux = (float) (0 * fz - rz * fy), uy = (float) (rz * fx - rx * fz), uz = (float) (rx * fy - 0 * fx);
        Matrix3f view = new Matrix3f(rx, ux, (float) ex, 0, uy, (float) ey, rz, uz, (float) ez);
        float s = pixelsPerUnit * 16;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.last().pose().translate(centreX, centreY, 150).scale(s, -s, s).mul(new Matrix4f().set(view))
                .translate(0, (float) (-c.targetY() / 16), 0);
        pose.last().normal().mul(view);
        RenderSystem.setShaderLights(view.transform(new Vector3f(LIGHT_0)), view.transform(new Vector3f(LIGHT_1)));
        inMenu = true;
    }

    private static void end(GuiGraphics g) {
        g.flush();
        inMenu = false;
        g.pose().popPose();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        // the 3D picture sits in front of the panel; later flat parts of the menu must not be hidden behind it
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }
}
