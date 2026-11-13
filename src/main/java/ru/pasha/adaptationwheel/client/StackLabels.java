package ru.pasha.adaptationwheel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Called from coremods/stack_limits.js for the count in GuiGraphics.renderItemDecorations. A label wider than two
 * digits (128, 12k, 1.5k) fills a whole slot and runs into its neighbour's, so it is drawn at three quarters size,
 * still anchored to the slot's bottom-right corner. Ordinary counts are drawn exactly as before.
 */
public final class StackLabels {
    private StackLabels() {}
    public static int draw(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int width = font.width(text);
        if (width <= 12) return graphics.drawString(font, text, x, y, color, shadow);
        int bottom = font.lineHeight - 1;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width, y + bottom, 0);
        pose.scale(.75f, .75f, 1);
        int drawn = graphics.drawString(font, text, -width, -bottom, color, shadow);
        pose.popPose();
        return drawn;
    }
}
