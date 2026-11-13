package ru.pasha.adaptationwheel.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.pasha.adaptationwheel.core.VoidText;
import ru.pasha.adaptationwheel.core.WheelSkin;
import static ru.pasha.adaptationwheel.client.WheelUi.*;

/**
 * «Облик колеса»: the looks with thumbnails on the left, a live preview of the player wearing the selected one on the
 * right (it turns slowly, the mouse turns it, «Щелчок этапа» shows the 45° stage turn), and «Надеть». Opened from the
 * J menu («Облик», Ctrl+K) or from Configured's «Облик колеса»; in the second case the choice also goes to Configured.
 * The Infinite Void's tagline and description are obfuscated noise until «Адаптироваться к тексту» has run its one
 * stage (server/VoidTextAdaptation) with this look open; the letters settle as the stage goes on.
 */
public final class WheelSkinScreen extends Screen {
    private static final int LIT = 0xFFFFF0C8, THUMB_BACK = 0xFF0F0D12, STRIPE = 0xFF2E2733, PREVIEW_EDGE = 0xFF2A2430,
            PREVIEW_TOP = 0xFF1E1925, PREVIEW_BOTTOM = 0xFF0C0A0F, NOISE = 0xFFB9C7FF, STAGE_BAR = 0xFF9AB8FF;
    private static final Style NOISE_STYLE = Style.EMPTY.withColor(NOISE & 0xFFFFFF).withObfuscated(true), SETTLED_STYLE = Style.EMPTY.withColor(LIT & 0xFFFFFF);
    private static final WheelSkin[] SKINS = WheelSkin.values();
    private static final WheelSkin VOID = WheelSkin.INFINITE_VOID;
    private static final String TEXT_KEY = "skin.adaptationwheel.void.";
    private static final float CLICK_SECONDS = 0.35F;
    private final Screen parent;
    /** Configured's own save of the enum value, or null when opened from the J menu. */
    private final Consumer<WheelSkin> configured;
    private final long opened = Util.getMillis();
    private SkinLayout layout;
    private Flat wear, adapt;
    private String adaptHint;
    private int selected, voidLines;
    private boolean dragging, autoSpin = true;
    private double dragX, yawOffset;
    private float turnFrom, turnTo, clickStart = -1e9F;

    public WheelSkinScreen(Screen parent, Consumer<WheelSkin> configured) {
        super(Component.translatable("skin.adaptationwheel.title"));
        this.parent = parent;
        this.configured = configured;
        selected = WheelSkinClient.chosen().ordinal();
    }

    @Override
    protected void init() {
        layout = new SkinLayout(width, height);
        SkinLayout l = layout;
        addRenderableWidget(new IconButton(l.backX, l.backY, IconButton.BACK, Component.translatable("gui.back"), b -> onClose()));
        addRenderableWidget(new Flat(l.clickX, l.clickY, l.clickW, l.clickH, Component.translatable("skin.adaptationwheel.click"), b -> click()));
        wear = addRenderableWidget(new Flat(l.wearX, l.buttonY, l.wearW, l.buttonH, Component.empty(), b -> wear()));
        addRenderableWidget(new Flat(l.backButtonX, l.buttonY, l.backButtonW, l.buttonH, Component.translatable("gui.back"), b -> onClose()));
        voidLines = Math.min(l.voidLines(), wrap(description(VOID), l.px1 - l.px0).size());
        Component label = Component.translatable(TEXT_KEY + (l.compact ? "adapt.short" : "adapt"));
        int adaptWidth = font.width(label) + 16;
        adapt = addRenderableWidget(new Flat(l.compact ? l.px1 - adaptWidth : l.px0, l.adaptY(voidLines), adaptWidth, l.adaptH(), label, b -> startAdapting()));
        adaptHint = null;
        updateWear();
        updateAdapt();
    }

    /** The server counts the stage only while it hears this look is open, so the menu keeps telling it. */
    @Override
    public void tick() {
        ClientVoidText.watch(SKINS[selected] == VOID && ClientVoidText.adapting());
        updateAdapt();
    }

    @Override
    public void removed() {
        ClientVoidText.watch(false);
    }

    private void startAdapting() {
        ClientVoidText.start();
        tick();
    }

    /** Why the wheel cannot adapt now, as a text key's ending; null when it can. */
    private String blocked() {
        var state = minecraft.player == null ? null : ClientWheelState.get(minecraft.player.getId());
        if (state == null || !state.equipped()) return "unworn";
        return state.enabled() ? null : "off";
    }

    private void updateAdapt() {
        String blocked = blocked(), hint = TEXT_KEY + "adapt." + (blocked == null ? "hint" : blocked);
        adapt.visible = SKINS[selected] == VOID && !ClientVoidText.read() && !ClientVoidText.adapting();
        adapt.active = adapt.visible && blocked == null;
        adapt.accent = adapt.active;
        // the wide layout writes this next to the button; the compact one has no room
        if (layout.compact && !hint.equals(adaptHint)) adapt.setTooltip(Tooltip.create(Component.translatable(hint)));
        adaptHint = hint;
    }

    private float now() {
        return (Util.getMillis() - opened) / 1000F;
    }

    private float turn(float t) {
        float k = Mth.clamp((t - clickStart) / CLICK_SECONDS, 0, 1), eased = 1 - (1 - k) * (1 - k) * (1 - k);
        return turnFrom + (turnTo - turnFrom) * eased;
    }

    /** The same pulse as a worn wheel after a stage (18 ticks). */
    private float pulse(float t) {
        return 1 + Math.max(0, 1 - (t - clickStart) * 20 / 18) * 0.08F;
    }

    private void click() {
        float t = now();
        turnFrom = turn(t);
        turnTo += Mth.PI / 4;
        clickStart = t;
    }

    private void select(int index) {
        index = Mth.clamp(index, 0, SKINS.length - 1);
        if (index == selected) return;
        selected = index;
        turnFrom = turnTo = 0;
        clickStart = -1e9F;
        updateWear();
        updateAdapt();
    }

    private void wear() {
        WheelSkin skin = SKINS[selected];
        WheelSkinClient.wear(skin);
        if (configured != null) configured.accept(skin);
        updateWear();
    }

    private void updateWear() {
        boolean worn = SKINS[selected] == WheelSkinClient.chosen();
        wear.setMessage(Component.translatable(worn ? "skin.adaptationwheel.worn" : "skin.adaptationwheel.wear"));
        wear.active = !worn;
        wear.accent = !worn;
        wear.selected = worn;
    }

    private static String name(WheelSkin skin) {
        return Component.translatable(skin.translationKey()).getString();
    }

    private static String tagline(WheelSkin skin) {
        return Component.translatable(skin.translationKey() + ".tagline").getString();
    }

    private static String description(WheelSkin skin) {
        return Component.translatable(skin.translationKey() + ".description").getString();
    }

    private List<String> wrap(String text, int width) {
        return font.getSplitter().splitLines(text, width, Style.EMPTY).stream().map(FormattedText::getString).toList();
    }

    /** Whether this look's text is still noise. */
    private static boolean sealed(WheelSkin skin) {
        return skin == VOID && !ClientVoidText.read();
    }

    /** A line of the sealed text: settled letters are plain, the others flicker; widths stay those of the plain text. */
    private static Component noise(String line, int offset, double progress) {
        MutableComponent out = Component.empty();
        for (VoidText.Run run : VoidText.runs(line, offset, progress))
            out.append(Component.literal(line.substring(run.start(), run.end())).withStyle(run.settled() ? SETTLED_STYLE : NOISE_STYLE));
        return out;
    }

    private void taglineLine(GuiGraphics g, WheelSkin skin, String line, int x, int y) {
        if (sealed(skin)) g.drawString(font, noise(line, 0, 0), x, y, NOISE, false);
        else g.drawString(font, line, x, y, MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        SkinLayout l = layout;
        panel(g, l.left, l.top, l.right, l.bottom);
        float t = now();
        WheelSkin worn = WheelSkinClient.chosen();
        g.drawString(font, title, l.titleX, l.titleY, GOLD, false);
        if (minecraft.player != null) {
            ClientWheelState.State state = ClientWheelState.get(minecraft.player.getId());
            boolean equipped = state != null && state.equipped();
            Component chip = Component.translatable(equipped ? "gui.adaptationwheel.equipped" : "gui.adaptationwheel.chip.unequipped");
            chip(g, font, chip, l.chipRight - font.width(chip) - 8, l.chipY, equipped ? GOOD : WARN);
        }
        if (l.subtitleY >= 0)
            g.drawString(font, fit(font, Component.translatable("skin.adaptationwheel.subtitle").getString(), l.right - l.left - 24),
                    l.left + 12, l.subtitleY, MUTED, false);

        int hover = l.cardAt(mouseX, mouseY);
        for (int i = 0; i < SKINS.length; i++) card(g, i, i == hover, SKINS[i] == worn);
        if (!l.compact) g.fill(l.listX + l.listW + 5, l.listY, l.listX + l.listW + 6, l.ruleBottom, RULE);
        if (!l.compact) {
            List<String> note = wrap(Component.translatable("skin.adaptationwheel.note").getString(), l.listW);
            if (l.noteFits(note.size()))
                for (int i = 0; i < note.size(); i++) g.drawString(font, note.get(i), l.listX, l.noteY + i * 10, FAINT, false);
        }

        preview(g, t);
        description(g, worn);
        g.drawString(font, fit(font, Component.translatable("skin.adaptationwheel.status").getString(), l.wearX - l.listX - 8),
                l.listX, l.statusY, FAINT, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void card(GuiGraphics g, int i, boolean hot, boolean worn) {
        SkinLayout l = layout;
        WheelSkin skin = SKINS[i];
        int x0 = l.listX, x1 = l.listX + l.listW, y = l.cardY(i), h = l.card;
        boolean chosen = i == selected;
        g.fill(x0, y, x1, y + h, chosen ? SELECTED : hot ? CARD_HOVER : CARD);
        if (chosen) g.renderOutline(x0, y, x1 - x0, h, dim(GOLD));
        g.fill(x0, y, x0 + 2, y + h, worn ? GOOD : chosen ? GOLD : STRIPE);
        int size = l.thumb, tx = x0 + 4, ty = y + (h - size) / 2;
        g.fill(tx, ty, tx + size, ty + size, THUMB_BACK);
        SkinRenderer.thumbnail(g, skin, tx, ty, size);
        int textX = tx + size + 6, nameWidth = x1 - 5 - textX, textWidth = x1 - 5 - textX;
        if (worn && !l.compact) {
            Component mark = Component.translatable("skin.adaptationwheel.worn");
            int width = font.width(mark) + 8;
            chip(g, font, mark, x1 - 4 - width, y + 4, GOOD);
            nameWidth -= width + 4;
        }
        int nameColor = chosen ? LIT : TEXT;
        if (l.compact) {
            int nameY = l.compactNameY(i);
            g.drawString(font, fit(font, name(skin), nameWidth), textX, nameY, nameColor, false);
            if (l.compactTagline()) taglineLine(g, skin, fit(font, tagline(skin), textWidth), textX, nameY + 10);
        } else {
            g.drawString(font, fit(font, name(skin), nameWidth), textX, y + 5, nameColor, false);
            List<String> lines = wrap(tagline(skin), textWidth);
            if (!lines.isEmpty()) taglineLine(g, skin, lines.get(0), textX, y + 16);
            if (lines.size() > 1)
                taglineLine(g, skin, fit(font, String.join(" ", lines.subList(1, lines.size())), textWidth), textX, y + 25);
        }
    }

    private void preview(GuiGraphics g, float t) {
        SkinLayout l = layout;
        g.fillGradient(l.px0, l.py0, l.px1, l.py1, PREVIEW_TOP, PREVIEW_BOTTOM);
        double yaw = Math.PI - 0.6 + yawOffset + (autoSpin ? t * 0.3 : 0);
        SkinRenderer.Camera camera = new SkinRenderer.Camera(yaw, 0.3, l.compact ? 74 : 70, 35);
        SkinRenderer.preview(g, minecraft.player, SKINS[selected], l.px0, l.py0, l.px1, l.py1, camera, turn(t), pulse(t), t + 2);
        g.renderOutline(l.px0, l.py0, l.px1 - l.px0, l.py1 - l.py0, PREVIEW_EDGE);
        String hint = Component.translatable(l.compact ? "skin.adaptationwheel.drag.short" : "skin.adaptationwheel.drag").getString();
        g.drawString(font, fit(font, hint, l.clickX - l.px0 - 12), l.px0 + 6, l.py0 + 6, FAINT, false);
    }

    private void description(GuiGraphics g, WheelSkin worn) {
        SkinLayout l = layout;
        WheelSkin skin = SKINS[selected];
        int x = l.px0, width = l.px1 - l.px0;
        boolean isVoid = skin == VOID, sealed = sealed(skin), adapting = isVoid && ClientVoidText.adapting();
        double progress = VoidText.progress(ClientVoidText.ticks());
        // the end of the name line: the mark that the text is read, or in the compact layout the button or the stage
        int taken = 0;
        if (isVoid && ClientVoidText.read()) {
            Component read = Component.translatable(TEXT_KEY + (l.compact ? "read.short" : "read"));
            taken = font.width(read) + 14;
            chip(g, font, read, l.px1 - taken + 6, l.descY - 2, GOOD);
        } else if (l.compact && adapting) {
            String stage = stageShort();
            taken = font.width(stage) + 6;
            g.drawString(font, stage, l.px1 - taken + 6, l.descY, MUTED, false);
        } else if (l.compact && adapt.visible) {
            taken = adapt.getWidth() + 6;
        }
        String name = fit(font, name(skin), taken == 0 ? width - 60 : width - taken);
        g.drawString(font, name, x, l.descY, GOLD, false);
        Component wornMark = Component.translatable("skin.adaptationwheel.worn");
        if (skin == worn && font.width(name) + font.width(wornMark) + 14 <= width - taken)
            chip(g, font, wornMark, x + font.width(name) + 6, l.descY - 2, GOOD);
        if (l.compact) {
            String tagline = fit(font, tagline(skin), width);
            if (sealed) g.drawString(font, noise(tagline, 0, progress), x, l.descY + 11, NOISE, false);
            else g.drawString(font, tagline, x, l.descY + 11, MUTED, false);
            if (adapting) stageBar(g, x, l.compactBarY(), width, 2, progress);
            return;
        }
        String text = description(skin);
        List<String> lines = wrap(text, width);
        int max = isVoid ? l.voidLines() : l.descriptionLines(), offset = 0;
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            String line = i == max - 1 && lines.size() > max ? fit(font, lines.get(i) + " …", width) : lines.get(i);
            if (sealed) {
                // a wrapped line loses the space it broke at, so its place in the whole text has to be looked up
                offset = Math.max(offset, text.indexOf(lines.get(i), offset));
                g.drawString(font, noise(line, offset, progress), x, l.descY + 13 + i * 10, NOISE, false);
                offset += lines.get(i).length();
            } else {
                g.drawString(font, line, x, l.descY + 13 + i * 10, TEXT, false);
            }
        }
        if (!sealed) return;
        int y = l.adaptY(voidLines);
        if (adapting) {
            g.drawString(font, fit(font, stageLine(), width), x, y - 1, MUTED, false);
            stageBar(g, x, y + 10, width, 4, progress);
        } else {
            int hintX = x + adapt.getWidth() + 6;
            g.drawString(font, fit(font, Component.translatable(adaptHint).getString(), l.px1 - hintX), hintX, y + 4, FAINT, false);
        }
    }

    private String stageLine() {
        int ticks = ClientVoidText.ticks(), percent = VoidText.percent(ticks);
        String blocked = blocked();
        return (blocked == null ? Component.translatable(TEXT_KEY + "progress", percent, VoidText.secondsLeft(ticks))
                : Component.translatable(TEXT_KEY + "paused." + blocked, percent)).getString();
    }

    private String stageShort() {
        int ticks = ClientVoidText.ticks(), percent = VoidText.percent(ticks);
        return (blocked() == null ? Component.translatable(TEXT_KEY + "progress.short", percent, VoidText.secondsLeft(ticks))
                : Component.translatable(TEXT_KEY + "paused.short", percent)).getString();
    }

    private static void stageBar(GuiGraphics g, int x, int y, int width, int height, double progress) {
        g.fill(x, y, x + width, y + height, TRACK);
        g.fill(x, y, x + (int) (width * progress), y + height, STAGE_BAR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            // a clicked button keeps no focus: Enter wears the look and the arrows choose, as the status line says
            setFocused(null);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int card = layout.cardAt(mouseX, mouseY);
        if (card >= 0) {
            select(card);
            return true;
        }
        if (layout.inPreview(mouseX, mouseY)) {
            // the first turn by hand stops the slow spin where it is
            if (autoSpin) {
                yawOffset += now() * 0.3;
                autoSpin = false;
            }
            dragging = true;
            dragX = mouseX;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            yawOffset += (mouseX - dragX) * 0.02;
            dragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || ClientEvents.MENU.matches(key, scanCode)) {
            onClose();
            return true;
        }
        // before vanilla's arrow navigation, which would walk the focus over the buttons instead
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            setFocused(null);
            select(selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1));
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() == null) {
            wear();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
