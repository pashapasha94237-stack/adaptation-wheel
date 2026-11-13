package ru.pasha.adaptationwheel.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.pasha.adaptationwheel.LearningConfig;
import ru.pasha.adaptationwheel.core.LearningMode;
import ru.pasha.adaptationwheel.net.LearningNetwork;
import static ru.pasha.adaptationwheel.client.WheelUi.*;

/**
 * Server-backed learning choices, as in variant C of the approved Hub design {@code learning-ui-variants-2026-10-31}:
 * a «Сейчас: …» chip, one line saying protection is earned the same way in every mode, three big mode tiles (icons of
 * the knowledge sources, name, short text, load as three squares, «Знает: …», pick button), under them the world study
 * panel (per cent, definitions, time left at the server's speed, 20-segment bar) next to four number tiles, and the
 * «При смене режима» chips. Hub design {@code learning-definitions-help-2026-11-12} added the line saying what a
 * definition is under the count and a gold «?» whose tooltip explains the study ({@link #help}). Short or narrow windows get a list of the modes, one tile and the number tiles under them
 * ({@link LearningLayout}). Keys 1/2/3 pick a mode, ←→ move between the tiles.
 */
public final class LearningScreen extends Screen {
    private static final String KEY = "learning.adaptationwheel.";
    private static final int PICK = 0xFF2A2330, PICK_HOT = 0xFF342C39, STRIP = 0xFF3A2418, KEY_BG = 0xFF2C2632,
            KEY_EDGE = 0xFF4A4050, CHIP = 0xFF282A26, NOW = 0xFF34392F;
    /** 9×9 icons of the knowledge sources: the prepared base, the player's fights, the mods' definitions. */
    private static final String[][] ICONS = {
            {".........", ".#######.", ".#.....#.", ".#.###.#.", ".#.....#.", ".#.###.#.", ".#.....#.", ".#######.", "........."},
            {"........#", ".......#.", "......#..", ".....#...", "#...#....", ".#.#.....", "..#......", ".#.#.....", "#........"},
            {"...###...", ".##.#.##.", ".#..#..#.", "#########", "#...#...#", "#########", ".#..#..#.", ".##.#.##.", "...###..."},
    };
    /** Lit load squares of each mode: none, almost none, up to N definitions a second. */
    private static final int[] PIPS = {0, 1, 3};
    private static final String[] CHANGES = {"change.kept", "change.stages", "change.saved"};
    /** Side of the gold «?» square next to the definitions count. */
    private static final int HELP = 9;
    /** The text of a mode tile: the short description and «Знает: …», both wrapped to the tile. */
    private record Card(List<FormattedCharSequence> tag, List<FormattedCharSequence> knows) {}
    /** A «При смене режима» chip, relative to the top of the scrolled block. */
    private record Chip(int x, int y, int width, String text, int index) {}

    private final Screen parent;
    /** The pick buttons of the tiles (rows of the list in the compact layout), read by the probe's LearningQa. */
    private final EnumMap<LearningMode, Flat> modes = new EnumMap<>(LearningMode.class);
    private LearningLayout layout;
    private List<FormattedCharSequence> lead = List.of();
    private final List<Card> cards = new ArrayList<>();
    private final List<List<FormattedCharSequence>> tips = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private int cardsY, cardH, bodyTop, bodyBottom, contentHeight, scroll, maxScroll, refresh, cursor = -1;
    /** Where the «?» was drawn this frame (wide), -1 when it was not. */
    private int helpX = -1, helpY = -1;
    private boolean keyboard;
    private String signature;
    private LearningMode shownMode;

    public LearningScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override protected void init() {
        layout = new LearningLayout(width, height);
        addRenderableWidget(new IconButton(layout.backX, layout.backY, IconButton.BACK, Component.translatable("gui.back"), b -> onClose()));
        modes.clear();
        for (var mode : LearningMode.values()) {
            Flat button = layout.compact
                    ? new Row(layout.listX, layout.rowY(mode.ordinal()), layout.listW, layout.row, mode)
                    : new Pick(0, 0, 10, 16, mode);
            button.active = false;
            modes.put(mode, addRenderableWidget(button));
        }
        addRenderableWidget(new Flat(layout.buttonX, layout.buttonY, layout.buttonW, layout.buttonH, Component.translatable("gui.back"), b -> onClose()));
        signature = null; refresh = 0;
        relayout(LearningClient.state());
        LearningNetwork.request();
    }

    @Override public void tick() {
        if (++refresh % 20 == 0) LearningNetwork.request();
        var state = LearningClient.state();
        modes.forEach((mode, button) -> button.active = state != null && state.editable() && (layout.compact || mode != state.mode()));
    }

    // ----- texts -----

    private static Style color(int color) { return Style.EMPTY.withColor(color & 0xFFFFFF); }
    private static Component tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static Component name(LearningMode mode) { return Component.translatable(mode.translationKey()); }

    private static Component load(LearningMode mode) {
        if (mode != LearningMode.RESEARCH) return Component.translatable(mode.translationKey() + ".load.short");
        int perSecond = LearningConfig.definitionsPerSecond();
        return Component.translatable(mode.translationKey() + ".load.short", perSecond);
    }

    private static boolean current(LearningNetwork.State state, LearningMode mode) { return state != null && state.mode() == mode; }

    /** «наблюдений в боях» / «наблюдения в боях» / «наблюдение в боях» after {@code value}. */
    private static String counted(String key, int value) {
        return tr(key + "." + LearningLayout.plural(value).name().toLowerCase(Locale.ROOT)).getString();
    }

    /** Values and labels of the four number tiles; {@code small} uses the compact tiles' short labels. */
    private static String[][] numbers(LearningNetwork.State state, boolean small) {
        String suffix = small ? ".short" : "";
        return new String[][]{
                {state.pretrained() ? LearningLayout.group(state.seedFacts()) : "—", tr((state.pretrained() ? "tile.seeds" : "tile.seeds.none") + suffix).getString()},
                {LearningLayout.group(state.learnedFacts()), tr("tile.learned" + suffix).getString()},
                {LearningLayout.group(state.observations()), counted("tile.observations" + suffix, state.observations())},
                {LearningLayout.group(state.contradictions()), counted("tile.contradictions" + suffix, state.contradictions())},
        };
    }

    /** «осталось ≈ 3,5 мин, пока колесо надето» and the other second lines of the study panel. */
    private static Component studyNote(LearningNetwork.State state) {
        var study = LearningLayout.study(state.scanning(), state.inspected(), state.total());
        if (study == LearningLayout.Study.COMPLETE) return tr("study.done");
        if (state.mode() != LearningMode.RESEARCH) return tr("study.only", name(LearningMode.RESEARCH));
        var eta = eta(state);
        return eta == null ? tr("study.counting") : tr("study.left", eta);
    }

    private static String eta(LearningNetwork.State state) {
        var eta = LearningLayout.remaining(state.inspected(), state.total(), LearningConfig.definitionsPerSecond());
        if (eta == null) return null;
        return tr("time." + eta.unit().name().toLowerCase(Locale.ROOT), eta.number(tr("time.decimal").getString())).getString();
    }

    // ----- layout -----

    private void relayout(LearningNetwork.State state) {
        String next = state == null ? "none" : state.mode() + "|" + state.editable() + "|" + state.pretrained();
        next += "|" + LearningConfig.defaultMode() + "|" + LearningConfig.definitionsPerSecond();
        if (next.equals(signature)) return;
        if (state != null && (signature == null || shownMode != state.mode())) cursor = state.mode().ordinal();
        signature = next; shownMode = state == null ? null : state.mode();
        cards.clear(); tips.clear();
        int inner = layout.compact ? layout.detailW : layout.cardW - 20;
        for (var mode : LearningMode.values()) {
            cards.add(new Card(font.split(Component.translatable(mode.translationKey() + ".tag"), inner),
                    font.split(Component.translatable(mode.translationKey() + ".knows"), inner)));
            var tip = new ArrayList<FormattedCharSequence>();
            tip.add(name(mode).copy().withStyle(color(GOLD)).getVisualOrderText());
            tip.addAll(font.split(Component.translatable(mode.descriptionKey()), 220));
            Component when = Component.translatable(mode.translationKey() + ".when");
            tip.addAll(font.split((mode == LearningConfig.defaultMode() ? tr("default", when) : when).copy().withStyle(color(FAINT)), 220));
            tips.add(tip);
        }
        if (layout.compact) return;
        lead = font.split(tr("lead", tr("lead.source").copy().withStyle(color(GOLD)), tr("lead.same").copy().withStyle(color(GOOD))).copy().withStyle(color(TEXT)), layout.textW);
        cardsY = layout.cardsY(lead.size(), state != null && !state.editable());
        cardH = 0;
        for (var card : cards) cardH = Math.max(cardH, 68 + card.tag().size() * 10 + 4 + 12 + card.knows().size() * 10 + 8 + 16 + 10);
        for (var mode : LearningMode.values()) {
            var button = modes.get(mode);
            button.setX(layout.cardX(mode.ordinal()) + 10); button.setY(cardsY + cardH - 26); button.setWidth(layout.cardW - 20);
        }
        bodyTop = cardsY + cardH + 10; bodyBottom = layout.bottom - 33;
        chips.clear();
        int x = 0, y = LearningLayout.STUDY_H + 12 + 13;
        for (int i = 0; i < CHANGES.length; i++) {
            String text = "✓ " + tr(CHANGES[i] + ".chip").getString();
            int w = Math.min(layout.textW, font.width(text) + 10);
            if (x > 0 && x + w > layout.textW) { x = 0; y += 16; }
            chips.add(new Chip(x, y, w, text, i));
            x += w + 6;
        }
        contentHeight = y + 13;
    }

    // ----- drawing -----

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        var state = LearningClient.state();
        relayout(state);
        panel(g, layout.left, layout.top, layout.right, layout.bottom);
        header(g, state);
        helpX = helpY = -1;
        if (layout.compact) renderCompact(g, state, mouseX, mouseY);
        else renderWide(g, state, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (layout.compact) {
            // The compact study line has no room for the «?»: hovering the line itself explains the definitions.
            if (state != null && mouseX >= layout.listX && mouseX < layout.right - 10 && mouseY >= layout.studyY - 1 && mouseY < layout.barY + 4)
                g.renderTooltip(font, help(), mouseX, mouseY);
        } else {
            int hovered = layout.cardAt(mouseX, mouseY, cardsY, cardH);
            if (hovered >= 0) g.renderTooltip(font, tips.get(hovered), mouseX, mouseY);
            else if (helpX >= 0 && mouseX >= helpX && mouseX < helpX + HELP && mouseY >= helpY && mouseY < helpY + HELP
                    && mouseY >= bodyTop && mouseY < bodyBottom)
                g.renderTooltip(font, help(), mouseX, mouseY);
            else if (mouseY >= bodyTop && mouseY < bodyBottom)
                for (var chip : chips) {
                    int cx = layout.textX + chip.x(), cy = bodyTop - scroll + chip.y();
                    if (mouseX >= cx && mouseX < cx + chip.width() && mouseY >= cy && mouseY < cy + 12)
                        g.renderTooltip(font, font.split(tr(CHANGES[chip.index()]), 220), mouseX, mouseY);
                }
        }
    }

    /** Title on the left, «Сейчас: …» on the right as in the mock-up. */
    private void header(GuiGraphics g, LearningNetwork.State state) {
        LearningLayout l = layout;
        int room = l.right - 10 - l.titleX;
        if (state != null) {
            String now = tr("now", name(state.mode())).getString();
            int w = Math.min(font.width(now) + 10, room / 2), x = l.right - 12 - w, y = l.titleY - 2;
            g.fill(x, y, x + w, y + 12, NOW);
            g.fill(x, y, x + 1, y + 12, GOOD);
            g.drawString(font, fit(font, now, w - 8), x + 5, y + 2, GOOD, false);
            room = x - 8 - l.titleX;
        }
        g.drawString(font, fit(font, title.getString(), room), l.titleX, l.titleY, GOLD, false);
    }

    private void renderWide(GuiGraphics g, LearningNetwork.State state, int mouseX, int mouseY) {
        LearningLayout l = layout;
        for (int i = 0; i < lead.size(); i++) g.drawString(font, lead.get(i), l.textX, l.leadY + i * 10, TEXT, false);
        if (state != null && !state.editable()) {
            String locked = fit(font, tr("locked").getString(), l.textW - 12);
            int y = cardsY - LearningLayout.strip();
            g.fill(l.textX, y, l.textX + font.width(locked) + 12, y + 13, STRIP);
            g.drawString(font, locked, l.textX + 6, y + 3, WARN, false);
        }
        int hovered = l.cardAt(mouseX, mouseY, cardsY, cardH);
        for (var mode : LearningMode.values()) {
            int i = mode.ordinal();
            boolean selected = current(state, mode), hot = hovered == i || keyboard && cursor == i;
            tile(g, mode, l.cardX(i), cardsY, l.cardW, selected, hot);
        }
        maxScroll = Math.max(0, contentHeight - (bodyBottom - bodyTop)); scroll = Mth.clamp(scroll, 0, maxScroll);
        if (bodyBottom <= bodyTop) { hints(g, "footer"); return; }
        g.enableScissor(l.textX, bodyTop, l.right - 8, bodyBottom);
        int top = bodyTop - scroll;
        if (state == null) {
            g.drawString(font, tr("waiting"), l.textX, top, MUTED, false);
        } else {
            studyPanel(g, state, l.textX, top, l.studyW);
            var numbers = numbers(state, false);
            for (int i = 0; i < 4; i++) {
                int x = l.numberX(i), y = top + LearningLayout.numberY(i);
                g.fill(x, y, x + l.tileW, y + LearningLayout.TILE_H, CARD);
                big(g, numbers[i][0], x + 6, y + 6, i == 3 ? TEXT : GOLD);
                g.drawString(font, fit(font, numbers[i][1], l.tileW - 12), x + 6, y + LearningLayout.TILE_H - 13, MUTED, false);
            }
            g.drawString(font, tr("change"), l.textX, top + LearningLayout.STUDY_H + 12, GOLD, false);
            for (var chip : chips) {
                int x = l.textX + chip.x(), y = top + chip.y();
                g.fill(x, y, x + chip.width(), y + 12, CHIP);
                g.fill(x, y, x + 1, y + 12, GOOD);
                g.drawString(font, fit(font, chip.text(), chip.width() - 8), x + 5, y + 2, TEXT, false);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int h = bodyBottom - bodyTop, thumb = Math.max(12, h * h / Math.max(1, contentHeight));
            int y0 = bodyTop + (h - thumb) * scroll / maxScroll;
            g.fill(l.right - 7, bodyTop, l.right - 4, bodyBottom, TRACK);
            g.fill(l.right - 7, y0, l.right - 4, y0 + thumb, GOLD);
        }
        hints(g, "footer");
    }

    /** A big mode tile: number key, «● выбран», icons, name, short text, load squares and «Знает: …». */
    private void tile(GuiGraphics g, LearningMode mode, int x, int y, int w, boolean selected, boolean hot) {
        int i = mode.ordinal();
        var card = cards.get(i);
        g.fill(x, y, x + w, y + cardH, selected ? SELECTED : hot ? CARD_HOVER : CARD);
        if (selected) { g.renderOutline(x, y, w, cardH, GOLD); g.fill(x, y, x + w, y + 2, GOLD); }
        key(g, String.valueOf(i + 1), x + 6, y + 6);
        if (selected) {
            String mark = tr("selected").getString().strip();
            g.drawString(font, mark, x + w - 6 - font.width(mark), y + 6, GOOD, false);
        }
        int count = i + 1, iconsW = count * 27 + (count - 1) * 6;
        for (int k = 0; k < count; k++) icon(g, k, x + (w - iconsW) / 2 + k * 33, y + 20, 3, selected ? GOLD : MUTED);
        String name = fit(font, name(mode).getString(), w - 12);
        g.drawString(font, name, x + (w - font.width(name)) / 2, y + 54, GOLD, false);
        int ty = y + 68;
        for (var line : card.tag()) { g.drawString(font, line, x + 10, ty, MUTED, false); ty += 10; }
        loadRow(g, mode, x + 10, ty + 4, w - 20);
        ty += 4 + 12;
        for (var line : card.knows()) { g.drawString(font, line, x + 10, ty, FAINT, false); ty += 10; }
    }

    /** «Нагрузка ■■■ до 8 определений/с». */
    private void loadRow(GuiGraphics g, LearningMode mode, int x, int y, int width) {
        String label = tr("load").getString();
        g.drawString(font, label, x, y, FAINT, false);
        int px = x + font.width(label) + 5;
        for (int p = 0; p < 3; p++) g.fill(px + p * 9, y, px + p * 9 + 7, y + 7, p < PIPS[mode.ordinal()] ? GOLD : TRACK);
        int tx = px + 3 * 9 + 3;
        g.drawString(font, fit(font, load(mode).getString(), x + width - tx), tx, y, MUTED, false);
    }

    /** The world study panel: title and status, big per cent, definitions, time left, segments, what it never does. */
    private void studyPanel(GuiGraphics g, LearningNetwork.State state, int x, int y, int w) {
        var study = LearningLayout.study(state.scanning(), state.inspected(), state.total());
        boolean stopped = study == LearningLayout.Study.STOPPED;
        g.fill(x, y, x + w, y + LearningLayout.STUDY_H, CARD);
        g.drawString(font, tr("study"), x + 8, y + 7, GOLD, false);
        String status = study == LearningLayout.Study.RUNNING ? tr("study.status.running", LearningConfig.definitionsPerSecond()).getString()
                : tr("study.status." + study.name().toLowerCase(Locale.ROOT)).getString();
        g.drawString(font, status, x + w - 8 - font.width(status), y + 7, stopped ? MUTED : GOOD, false);
        big(g, LearningLayout.percent(state.inspected(), state.total()) + "%", x + 8, y + 21, stopped ? MUTED : GOOD);
        int tx = x + 8 + 2 * font.width("100%") + 8;
        String count = fit(font, definitions(state), x + w - 8 - tx - HELP - 4);
        g.drawString(font, count, tx, y + 22, TEXT, false);
        helpX = tx + font.width(count) + 4; helpY = y + 21;
        g.fill(helpX, helpY, helpX + HELP, helpY + HELP, GOLD);
        g.drawString(font, "?", helpX + (HELP - font.width("?") + 1) / 2, helpY + 1, 0xFF000000, false);
        g.drawString(font, fit(font, studyNote(state).getString(), x + w - 8 - tx), tx, y + 32, MUTED, false);
        g.drawString(font, fit(font, tr("study.what").getString(), w - 16), x + 8, y + 46, MUTED, false);
        segments(g, state, x + 8, y + 60, w - 16, 8, stopped);
        g.drawString(font, fit(font, tr("study.scope.short").getString(), w - 16), x + 8, y + 76, FAINT, false);
    }

    /** The «?» tooltip: what a definition is, what the study reads and what it never does. */
    private List<FormattedCharSequence> help() {
        var lines = new ArrayList<FormattedCharSequence>();
        lines.add(tr("study.what.title").copy().withStyle(color(GOLD)).getVisualOrderText());
        for (String key : new String[]{"study.what.intro", "study.what.damage", "study.what.effects", "study.what.mobs"})
            lines.addAll(font.split(tr(key), 260));
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(font.split(tr("study.what.pace", LearningConfig.definitionsPerSecond()), 260));
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(font.split(tr("study.what.new").copy().withStyle(color(MUTED)), 260));
        return lines;
    }

    private String definitions(LearningNetwork.State state) {
        return tr(LearningLayout.genitiveOne(state.total()) ? "study.count.one" : "study.count",
                LearningLayout.group(state.inspected()), LearningLayout.group(state.total())).getString();
    }

    private void segments(GuiGraphics g, LearningNetwork.State state, int x, int y, int width, int height, boolean stopped) {
        int lit = LearningLayout.segments(state.inspected(), state.total()), n = LearningLayout.SEGMENTS;
        for (int k = 0; k < n; k++) {
            int x0 = x + Math.round(k * (width + 2) / (float) n), x1 = x + Math.round((k + 1) * (width + 2) / (float) n) - 2;
            g.fill(x0, y, x1, y + height, k < lit ? (stopped ? FAINT : GOOD) : TRACK);
        }
    }

    private void key(GuiGraphics g, String text, int x, int y) {
        int w = font.width(text) + 4;
        g.fill(x, y, x + w, y + 10, KEY_BG);
        g.fill(x, y + 10, x + w, y + 11, KEY_EDGE);
        g.drawString(font, text, x + 2, y + 1, TEXT, false);
    }

    private static void icon(GuiGraphics g, int index, int x, int y, int scale, int color) {
        var rows = ICONS[index];
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++)
                if (rows[j].charAt(i) == '#') g.fill(x + i * scale, y + j * scale, x + (i + 1) * scale, y + (j + 1) * scale, color);
    }

    /** Text at twice the size, for the per cent and the number tiles. */
    private void big(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void hints(GuiGraphics g, String key) {
        g.drawString(font, fit(font, tr(key).getString(), layout.hintsWidth()), layout.hintsX, layout.hintsY, FAINT, false);
    }

    private void renderCompact(GuiGraphics g, LearningNetwork.State state, int mouseX, int mouseY) {
        LearningLayout l = layout;
        if (state != null && !state.editable()) {
            String locked = fit(font, tr("locked.short").getString(), l.listW - 12);
            g.fill(l.listX, l.lockedY, l.listX + font.width(locked) + 12, l.lockedY + 13, STRIP);
            g.drawString(font, locked, l.listX + 6, l.lockedY + 3, WARN, false);
        }
        int hovered = l.rowAt(mouseX, mouseY);
        int shown = hovered >= 0 ? hovered : cursor >= 0 ? cursor : LearningConfig.defaultMode().ordinal();
        LearningMode mode = LearningMode.values()[shown];
        var card = cards.get(shown);
        g.enableScissor(l.detailX, l.detailY, l.right - 8, l.ruleY - 2);
        int x = l.detailX, y = l.detailY;
        boolean selected = current(state, mode);
        for (int k = 0; k <= shown; k++) icon(g, k, x + k * 22, y, 2, selected ? GOLD : MUTED);
        int nameX = x + (shown + 1) * 22 + 4;
        g.drawString(font, fit(font, name(mode).getString(), x + l.detailW - nameX), nameX, y + 5, GOLD, false);
        y += 24;
        for (var line : card.tag()) { g.drawString(font, line, x, y, MUTED, false); y += 10; }
        loadRow(g, mode, x, y + 3, l.detailW);
        y += 3 + 12;
        for (var line : card.knows()) { g.drawString(font, line, x, y, FAINT, false); y += 10; }
        y += 3;
        if (selected) g.drawString(font, tr("selected").getString().strip(), x, y, GOOD, false);
        else if (state != null && state.editable()) g.drawString(font, fit(font, tr("apply").getString(), l.detailW), x, y, GOLD, false);
        g.disableScissor();
        int width = l.right - 10 - l.listX;
        g.fill(l.listX, l.ruleY, l.listX + width, l.ruleY + 1, RULE);
        if (state == null) {
            g.drawString(font, fit(font, tr("waiting").getString(), width), l.listX, l.statsY, MUTED, false);
        } else {
            var numbers = numbers(state, true);
            for (int i = 0; i < 4; i++) {
                int tx = l.numberX(i);
                g.fill(tx, l.statsY, tx + l.smallTileW, l.statsY + l.smallTileH, CARD);
                g.drawString(font, numbers[i][0], tx + 5, l.statsY + 3, i == 3 ? TEXT : GOLD, false);
                g.drawString(font, fit(font, numbers[i][1], l.smallTileW - 10), tx + 5, l.statsY + 12, MUTED, false);
            }
            var study = LearningLayout.study(state.scanning(), state.inspected(), state.total());
            boolean stopped = study == LearningLayout.Study.STOPPED;
            String eta = state.mode() == LearningMode.RESEARCH ? eta(state) : null;
            String line = tr("study.compact", LearningLayout.percent(state.inspected(), state.total()),
                    LearningLayout.group(state.inspected()), LearningLayout.group(state.total())).getString();
            if (eta != null) line += tr("study.compact.left", eta).getString();
            g.drawString(font, fit(font, line, width), l.listX, l.studyY, stopped ? MUTED : GOOD, false);
            segments(g, state, l.listX, l.barY, width, 4, stopped);
            g.drawString(font, fit(font, tr("change.short").getString(), width), l.listX, l.changeY, FAINT, false);
        }
        hints(g, "footer.compact");
    }

    // ----- input -----

    private void choose(int index) {
        cursor = index;
        var state = LearningClient.state();
        var mode = LearningMode.values()[index];
        if (state != null && state.editable() && state.mode() != mode) LearningNetwork.choose(mode);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int)(delta * 22), 0, maxScroll); return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || ClientEvents.MENU.matches(key, scanCode)) { onClose(); return true; }
        for (int i = 0; i < LearningLayout.MODES; i++)
            if (key == GLFW.GLFW_KEY_1 + i || key == GLFW.GLFW_KEY_KP_1 + i) { keyboard = true; choose(i); return true; }
        boolean across = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT;
        boolean down = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
        if (across || down && layout.compact) {
            int start = cursor >= 0 ? cursor : LearningConfig.defaultMode().ordinal();
            cursor = keyboard || layout.compact ? LearningLayout.step(start, key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1 : 1) : start;
            keyboard = true; return true;
        }
        if (down) { scroll = Mth.clamp(scroll + (key == GLFW.GLFW_KEY_DOWN ? 22 : -22), 0, maxScroll); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && (keyboard || layout.compact) && cursor >= 0) {
            choose(cursor); return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Mth.clamp(scroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(22, bodyBottom - bodyTop - 11), 0, maxScroll); return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { scroll = key == GLFW.GLFW_KEY_HOME ? 0 : maxScroll; return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    // ----- widgets -----

    /** A tile's button: «Выбрать», «Выбран» on the current mode, «Недоступно» when the server fixes the mode. */
    private final class Pick extends Flat {
        private final LearningMode mode;

        Pick(int x, int y, int width, int height, LearningMode mode) {
            super(x, y, width, height, Component.empty(), b -> choose(mode.ordinal()));
            this.mode = mode;
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var state = LearningClient.state();
            boolean selected = current(state, mode), locked = state != null && !state.editable();
            Component label = selected ? tr("picked") : locked ? tr("unavailable") : tr("pick.short");
            setMessage(label);
            int background = selected ? TRACK : active && isHoveredOrFocused() ? PICK_HOT : PICK;
            int color = selected ? (locked ? FAINT : MUTED) : !active ? FAINT : TEXT;
            g.fill(getX(), getY(), getX() + width, getY() + height, background);
            String text = fit(font, label.getString(), width - 6);
            g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2, color, false);
        }
    }

    /** A row of the compact list: «3. Бой + изучение мира  3/3», the current mode framed in gold. */
    private final class Row extends Flat {
        private final LearningMode mode;

        Row(int x, int y, int width, int height, LearningMode mode) {
            super(x, y, width, height, Component.literal((mode.ordinal() + 1) + ". ").append(name(mode)), b -> choose(mode.ordinal()));
            this.mode = mode;
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var state = LearningClient.state();
            boolean selected = current(state, mode), hot = isHovered() || cursor == mode.ordinal() && keyboard;
            g.fill(getX(), getY(), getX() + width, getY() + height, selected ? SELECTED : hot ? CARD_HOVER : CARD);
            if (selected) g.renderOutline(getX(), getY(), width, height, GOLD);
            String count = (mode.ordinal() + 1) + "/" + LearningLayout.MODES;
            int countWidth = font.width(count);
            g.drawString(font, fit(font, getMessage().getString(), width - 16 - countWidth), getX() + 5, getY() + (height - 8) / 2, TEXT, false);
            g.drawString(font, count, getX() + width - 5 - countWidth, getY() + (height - 8) / 2, MUTED, false);
        }
    }
}
