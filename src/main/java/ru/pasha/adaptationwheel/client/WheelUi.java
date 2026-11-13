package ru.pasha.adaptationwheel.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The wheel's flat look shared by its screens: a near-black panel, a gold accent and category colours. */
final class WheelUi {
    // The panel is opaque: at 95% the HUD's text showed through the empty part of the details.
    static final int GOLD = 0xFFEBC568, TEXT = 0xFFF3E8D2, MUTED = 0xFFB5A78B, FAINT = 0xFF7A6E5E,
            PANEL = 0xFF141117, CARD = 0xFF1F1A22, CARD_HOVER = 0xFF2C2531, SELECTED = 0xFF33291C, TRACK = 0xFF3A3126,
            RULE = 0xFF3A3140, GOOD = 0xFFBDE596, WARN = 0xFFFFAD88, EXISTENCE = 0xFFC9A0FF, FPS = 0xFF8FD3FF,
            LIT = 0xFFFFF0C8;

    private WheelUi() {
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.fill(x0, y0, x1, y0 + 2, GOLD);
    }

    static void bar(GuiGraphics g, int x, int y, int width, int height, float progress, int color) {
        g.fill(x, y, x + width, y + height, TRACK);
        int filled = Mth.floor(width * Mth.clamp(progress, 0, 1));
        if (filled > 0) g.fill(x, y, x + filled, y + height, color);
    }

    /** One square per stage: earned ones filled, the running one filled up to its progress. Returns the width. */
    static int pips(GuiGraphics g, int x, int y, int total, int earned, float running, int color) {
        for (int i = 0; i < total; i++) {
            int left = x + i * 7, shade = i == 4 ? EXISTENCE : color;
            g.fill(left, y, left + 5, y + 7, TRACK);
            if (i < earned) g.fill(left, y, left + 5, y + 7, shade);
            else if (i == earned && running > 0) {
                int height = Math.max(1, Math.round(7 * Mth.clamp(running, 0, 1)));
                g.fill(left, y + 7 - height, left + 5, y + 7, dim(shade));
            }
        }
        return total * 7 - 2;
    }

    /** A small tinted label, e.g. a category or the wheel's state. Returns its width. */
    static int chip(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        int width = font.width(text) + 8;
        g.fill(x, y, x + width, y + 11, tint(color));
        g.fill(x, y, x + 1, y + 11, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return width;
    }

    /** A category chip with its icon before the name. Returns its width. */
    static int chip(GuiGraphics g, Font font, Component text, int[] icon, int x, int y, int color) {
        int width = font.width(text) + 17;
        g.fill(x, y, x + width, y + 11, tint(color));
        g.fill(x, y, x + 1, y + 11, color);
        icon(g, icon, x + 4, y + 2, color);
        g.drawString(font, text, x + 13, y + 2, color, false);
        return width;
    }

    /** A 7×7 icon of {@link CategoryIcons}, one fill per run of pixels. */
    static void icon(GuiGraphics g, int[] icon, int x, int y, int color) {
        for (int row = 0; row < CategoryIcons.SIZE; row++) {
            int bits = icon[row], column = 0;
            while (column < CategoryIcons.SIZE) {
                if ((bits & 1 << (CategoryIcons.SIZE - 1 - column)) == 0) { column++; continue; }
                int start = column;
                while (column < CategoryIcons.SIZE && (bits & 1 << (CategoryIcons.SIZE - 1 - column)) != 0) column++;
                g.fill(x + start, y + row, x + column, y + row + 1, color);
            }
        }
    }

    /** A square with the category icon in the middle; a switched-off adaptation's is dim. */
    static void tile(GuiGraphics g, int[] icon, int x, int y, int size, int color, boolean on) {
        g.fill(x, y, x + size, y + size, on ? tint(color) : 0x14FFFFFF);
        icon(g, icon, x + (size - CategoryIcons.SIZE) / 2, y + (size - CategoryIcons.SIZE) / 2, on ? color : dim(color));
    }

    /** A key in a frame for the hints at the bottom of the menu. Returns its width. */
    static int keycap(GuiGraphics g, Font font, String key, int x, int y) {
        int width = keycapWidth(font, key);
        g.fill(x, y - 2, x + width, y + 9, 0xFF2C2632);
        g.fill(x, y + 9, x + width, y + 10, 0xFF4A4050);
        g.drawString(font, key, x + 3, y, TEXT, false);
        return width;
    }

    static int keycapWidth(Font font, String key) {
        return font.width(key) + 5;
    }

    /** A piece of a line in its own colour. */
    record Part(String text, int color) {
    }

    static Part part(String text, int color) {
        return new Part(text, color);
    }

    static Part part(Component text, int color) {
        return new Part(text.getString(), color);
    }

    /** Pieces of one line in their colours, one after another; what does not fit ends with «…». Returns the width used. */
    static int parts(GuiGraphics g, Font font, java.util.List<Part> parts, int x, int y, int maxWidth) {
        int used = 0;
        for (Part part : parts) {
            if (part == null || part.text().isEmpty()) continue;
            int width = font.width(part.text());
            if (used + width <= maxWidth) {
                g.drawString(font, part.text(), x + used, y, part.color(), false);
                used += width;
                continue;
            }
            String cut = fit(font, part.text(), maxWidth - used);
            if (!cut.isEmpty()) g.drawString(font, cut, x + used, y, part.color(), false);
            return maxWidth;
        }
        return used;
    }

    /** Text at twice the size, for the main number of a card. */
    static void bigText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    static int tint(int color) {
        return (color & 0x00FFFFFF) | 0x30000000;
    }

    static int dim(int color) {
        return (color & 0x00FFFFFF) | 0x80000000;
    }

    static String fit(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    static void toggle(GuiGraphics g, int x, int y, boolean on, boolean active) {
        g.fill(x, y, x + 22, y + 10, !active ? 0xFF29231F : on ? 0xFF7E6526 : 0xFF3A3238);
        int knob = on ? x + 12 : x;
        g.fill(knob, y, knob + 10, y + 10, !active ? FAINT : on ? GOLD : 0xFF9C9098);
    }

    /** Flat button; accent buttons are gold, a selected one (current tab) stays lit. */
    static class Flat extends Button {
        boolean accent, selected;

        Flat(int x, int y, int width, int height, Component message, OnPress press) {
            super(x, y, width, height, message, press, DEFAULT_NARRATION);
        }

        Flat accent() {
            accent = true;
            return this;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int background = selected ? 0xFF4C3B1B : accent ? (hot ? 0xFF6B5323 : 0xFF54411C) : hot ? 0xFF342C39 : 0xFF241F28;
            g.fill(getX(), getY(), getX() + width, getY() + height, background);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, selected || accent ? GOLD : 0xFF4A4050);
            renderScrollingString(g, Minecraft.getInstance().font, 3, !active && !selected ? FAINT : selected || accent ? 0xFFFFF0C8 : TEXT);
        }
    }

    /** A tab of the menu with the number of its entries after the name: «Мои 14», «Каталог 1113». */
    static class Tab extends Flat {
        private String count = "";

        Tab(int x, int y, int width, int height, Component message, OnPress press) {
            super(x, y, width, height, message, press);
        }

        void count(int count) {
            this.count = String.valueOf(count);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, selected ? 0xFF4C3B1B : hot ? 0xFF342C39 : 0xFF241F28);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, selected ? GOLD : 0xFF4A4050);
            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString() + " ";
            int countWidth = font.width(count);
            label = fit(font, label, width - 6 - countWidth);
            int x = getX() + (width - font.width(label) - countWidth) / 2, y = getY() + (height - 8) / 2;
            g.drawString(font, label, x, y, selected ? 0xFFFFF0C8 : TEXT, false);
            g.drawString(font, count, x + font.width(label), y, selected ? GOLD : MUTED, false);
        }
    }

    /** A small square button with a drawn icon: the menu's close cross or the details' back chevron. */
    static class IconButton extends Button {
        static final int CLOSE = 0, BACK = 1;
        private final int icon;

        IconButton(int x, int y, int icon, Component narration, OnPress press) {
            super(x, y, 14, 14, narration, press, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            if (hot) g.fill(getX(), getY(), getX() + width, getY() + height, CARD_HOVER);
            int color = hot ? GOLD : MUTED, x = getX() + 3, y = getY() + 3;
            for (int i = 0; i < (icon == CLOSE ? 8 : 4); i++) {
                if (icon == CLOSE) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
                    g.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, color);
                } else {
                    g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, color);
                    g.fill(x + 5 - i, y + 7 - i, x + 6 - i, y + 8 - i, color);
                }
            }
        }
    }

    /** A labelled on/off switch. */
    static class Switch extends Button {
        private final BooleanSupplier on;

        Switch(int x, int y, int width, Component label, BooleanSupplier on, OnPress press) {
            super(x, y, width, 16, label, press, DEFAULT_NARRATION);
            this.on = on;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            g.fill(getX(), getY(), getX() + width, getY() + height, active && isHoveredOrFocused() ? CARD_HOVER : CARD);
            g.drawString(font, fit(font, getMessage().getString(), width - 34), getX() + 5, getY() + 4, active ? TEXT : FAINT, false);
            toggle(g, getX() + width - 26, getY() + 3, on.getAsBoolean(), active);
        }
    }
}
