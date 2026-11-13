package ru.pasha.adaptationwheel.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A blade of the Malevolent Shrine as a ribbon of plain coloured quads: its points with each one's half width as a side
 * vector, drawn solid or as soft light fading to its edges. The mock-up's stack of six ever narrower see-through ribbons
 * is one ribbon here, with the same opacity across it. Everything runs on the render thread, so the points are shared.
 */
final class CutRibbon {
    /** Light across a ribbon: this opacity on its line and half way to its edge, none at the edge. */
    private static final float LIGHT_LINE = 0.4F, LIGHT_MIDDLE = 0.22F;
    private static final int MAX_POINTS = 13;
    private static final double[] X = new double[MAX_POINTS], Y = new double[MAX_POINTS], Z = new double[MAX_POINTS];
    private static final double[] SX = new double[MAX_POINTS], SY = new double[MAX_POINTS], SZ = new double[MAX_POINTS];
    private static int points;
    /** OptiFine's {@code Config.isShaders}, or null without OptiFine or when it cannot be asked. */
    private static Method shadersOn;
    private static boolean shadersLooked;
    /** OptiFine's {@code Shaders.isShadowPass}, or null without OptiFine or when it cannot be read. */
    private static Field shadowPass;
    private static boolean shadowLooked;
    /** The type last handed out takes the full entity vertex (shader packs) instead of position and colour. */
    private static boolean entityVertex;

    private CutRibbon() {
    }

    /**
     * The plain type without a shader pack. With one, OptiFine draws an untextured type through the pack's
     * gbuffers_basic, which some packs make opaque (Chocapic: a white blob) and others black
     * (LUMA). A type named "eyes" goes through gbuffers_spidereyes, the glowing program every pack has.
     */
    static RenderType type() {
        entityVertex = shaders();
        return entityVertex ? Types.SHADER_CUTS : Types.CUTS;
    }

    /** A shader pack is on (OptiFine). */
    static boolean shaders() {
        if (!shadersLooked) {
            shadersLooked = true;
            try {
                shadersOn = Class.forName("net.optifine.Config", false, CutRibbon.class.getClassLoader()).getMethod("isShaders");
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        if (shadersOn == null) return false;
        try {
            return (Boolean) shadersOn.invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            shadersOn = null;
            return false;
        }
    }

    /** A shader pack's shadow pass is drawing the world from the sun: soft light casts no shadow, so it is left out. */
    static boolean shadowPass() {
        if (!shadowLooked) {
            shadowLooked = true;
            try {
                shadowPass = Class.forName("net.optifine.shaders.Shaders", false, CutRibbon.class.getClassLoader()).getField("isShadowPass");
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        if (shadowPass == null) return false;
        try {
            return shadowPass.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            shadowPass = null;
            return false;
        }
    }

    /** Starts a ribbon of {@code count} points, at most 13. */
    static void begin(int count) {
        points = count;
    }

    static void point(int i, double x, double y, double z, double sideX, double sideY, double sideZ) {
        X[i] = x;
        Y[i] = y;
        Z[i] = z;
        SX[i] = sideX;
        SY[i] = sideY;
        SZ[i] = sideZ;
    }

    static void solid(VertexConsumer out, Matrix4f m, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        for (int i = 0; i + 1 < points; i++) {
            int j = i + 1;
            vertex(out, m, X[i] + SX[i], Y[i] + SY[i], Z[i] + SZ[i], r, g, b, alpha);
            vertex(out, m, X[j] + SX[j], Y[j] + SY[j], Z[j] + SZ[j], r, g, b, alpha);
            vertex(out, m, X[j] - SX[j], Y[j] - SY[j], Z[j] - SZ[j], r, g, b, alpha);
            vertex(out, m, X[i] - SX[i], Y[i] - SY[i], Z[i] - SZ[i], r, g, b, alpha);
        }
    }

    /** Soft light of this colour along the ribbon; {@code strength} 1 is the normal glow, above it the white-hot moment. */
    static void light(VertexConsumer out, Matrix4f m, int rgb, float strength) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        float line = Math.min(1, LIGHT_LINE * strength), middle = Math.min(1, LIGHT_MIDDLE * strength);
        for (int i = 0; i + 1 < points; i++) {
            band(out, m, i, 0, 0.5, r, g, b, line, middle);
            band(out, m, i, 0.5, 1, r, g, b, middle, 0);
            band(out, m, i, 0, -0.5, r, g, b, line, middle);
            band(out, m, i, -0.5, -1, r, g, b, middle, 0);
        }
    }

    /** The quad between segment {@code i}'s lines at {@code from} and {@code to} of the half width. */
    private static void band(VertexConsumer out, Matrix4f m, int i, double from, double to, float r, float g, float b, float alphaFrom, float alphaTo) {
        int j = i + 1;
        vertex(out, m, X[i] + SX[i] * from, Y[i] + SY[i] * from, Z[i] + SZ[i] * from, r, g, b, alphaFrom);
        vertex(out, m, X[j] + SX[j] * from, Y[j] + SY[j] * from, Z[j] + SZ[j] * from, r, g, b, alphaFrom);
        vertex(out, m, X[j] + SX[j] * to, Y[j] + SY[j] * to, Z[j] + SZ[j] * to, r, g, b, alphaTo);
        vertex(out, m, X[i] + SX[i] * to, Y[i] + SY[i] * to, Z[i] + SZ[i] * to, r, g, b, alphaTo);
    }

    static void vertex(VertexConsumer out, Matrix4f m, double x, double y, double z, float r, float g, float b, float a) {
        out.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a);
        if (entityVertex) out.uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0);
        out.endVertex();
    }

    /**
     * Unlit coloured quads, blended over the world, both sides, no depth write (a scar, fringe and core on one face draw
     * in order), pulled towards the camera so a cut lying on a block face never flickers with it.
     */
    private static final class Types extends RenderType {
        static final RenderType CUTS = create("adaptationwheel_shrine_cuts", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1 << 16, false, false, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        /** The same under a shader pack: vanilla eyes' vertex and shader over a white texture, blended like {@link #CUTS}. */
        static final RenderType SHADER_CUTS = create("eyes", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                1 << 16, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(new ResourceLocation("adaptationwheel", "textures/misc/white.png"), false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
