package ru.pasha.adaptationwheel.client;

/**
 * Where the learning screen («Обучение анализатора») puts its parts for one GUI size, as in variant C of the approved
 * Hub design {@code learning-ui-variants-2026-10-31}: on wide windows three mode tiles side by side, under them the
 * world study panel next to four number tiles and the «При смене режима» chips; on windows narrower than 560 or lower
 * than 340 (the launcher's 925×530 window, 462×265) a list of the modes with one tile of the chosen mode, and the number
 * tiles with the study line under them. Tile heights depend on the font, so the screen measures them and passes the
 * result back ({@link #cardsY}, {@link #cardAt}). Pure arithmetic, unit-tested.
 */
final class LearningLayout {
    static final int COMPACT_BELOW_WIDTH = 560, WIDE_MIN_HEIGHT = 340, MODES = 3, LINE = 10, CLOSE = 14;
    /** Wide: height of the study panel and of one number tile; the study bar has this many segments. */
    static final int STUDY_H = 96, TILE_H = 45, SEGMENTS = 20;
    final boolean compact;
    final int left, top, right, bottom;
    final int backX, backY, titleX, titleY;
    /** Wide: the lead text, the tiles' columns, the study panel and the number tiles beside it. */
    final int leadY, textX, textW, cardW, gap, studyW, tilesX, tileW;
    /** Compact: the list of modes, the tile of one mode and the knowledge block under them. */
    final int listX, listY, listW, row, rowStep, lockedY, detailX, detailY, detailW, ruleY, statsY, smallTileW, smallTileH,
            studyY, barY, changeY;
    final int hintsX, hintsY, buttonX, buttonY, buttonW, buttonH;

    LearningLayout(int width, int height) {
        compact = width < COMPACT_BELOW_WIDTH || height < WIDE_MIN_HEIGHT;
        if (!compact) {
            int panelWidth = Math.min(660, width - 16);
            left = (width - panelWidth) / 2;
            right = left + panelWidth;
            top = 6;
            bottom = height - 6;
            backX = left + 8;
            backY = top + 8;
            titleX = left + 29;
            titleY = top + 11;
            leadY = top + 28;
            textX = left + 12;
            textW = panelWidth - 24;
            gap = 6;
            cardW = (textW - 2 * gap) / MODES;
            studyW = (textW - 12) / 2;
            tilesX = textX + studyW + 12;
            tileW = (textX + textW - tilesX - gap) / 2;
            buttonW = 100;
            buttonH = 18;
            buttonX = right - 112;
            buttonY = bottom - 25;
            hintsY = bottom - 19;
            listX = listY = listW = row = rowStep = lockedY = detailX = detailY = detailW = ruleY = statsY = smallTileW = smallTileH
                    = studyY = barY = changeY = -1;
        } else {
            left = 6;
            right = width - 6;
            top = 6;
            bottom = height - 6;
            backX = left + 8;
            backY = top + 6;
            titleX = left + 29;
            titleY = top + 9;
            listX = left + 10;
            listY = top + 26;
            // 150 in the mock-up of 4.35; Minecraft's font cut «3. Бой + изучение мира» there, the real client needs 160.
            listW = 160;
            row = 16;
            rowStep = 18;
            lockedY = listY + MODES * rowStep + 4;
            detailX = listX + listW + 8;
            detailY = top + 28;
            detailW = right - 10 - detailX;
            buttonW = 72;
            buttonH = 16;
            buttonX = right - 82;
            buttonY = bottom - 22;
            hintsY = bottom - 17;
            ruleY = Math.min(top + 134, buttonY - 66);
            statsY = ruleY + 6;
            smallTileH = 22;
            smallTileW = (right - 10 - listX - 3 * 6) / 4;
            studyY = statsY + smallTileH + 6;
            barY = studyY + 11;
            changeY = barY + 8;
            leadY = textW = cardW = gap = studyW = tilesX = tileW = -1;
            textX = listX;
        }
        hintsX = textX;
    }

    int cardX(int index) {
        return textX + index * (cardW + gap);
    }

    /** Top of the tiles under a lead of {@code leadLines} lines and, when the server fixes the mode, its strip. */
    int cardsY(int leadLines, boolean locked) {
        return leadY + leadLines * LINE + 6 + (locked ? strip() : 0);
    }

    /** Height of the orange «Режим задан настройками сервера» strip with the space under it. */
    static int strip() {
        return 17;
    }

    /** The tile under the mouse, or -1; the gaps between tiles are not a tile. */
    int cardAt(double x, double y, int cardsY, int cardH) {
        if (compact || y < cardsY || y >= cardsY + cardH || x < textX) return -1;
        int i = (int) Math.floor((x - textX) / (cardW + gap));
        return i < MODES && x - cardX(i) < cardW ? i : -1;
    }

    /** Wide: left edge of number tile {@code index} (0..3, two per row); compact: of the small tile in the row of four. */
    int numberX(int index) {
        return compact ? listX + index * (smallTileW + 6) : tilesX + index % 2 * (tileW + gap);
    }

    /** Wide: top of number tile {@code index} relative to the top of the study panel. */
    static int numberY(int index) {
        return index / 2 * (TILE_H + 6);
    }

    int rowY(int index) {
        return listY + index * rowStep;
    }

    /** The compact list row under the mouse, or -1. */
    int rowAt(double x, double y) {
        if (!compact || x < listX || x >= listX + listW || y < listY) return -1;
        int i = (int) Math.floor((y - listY) / rowStep);
        return i < MODES && (y - listY) - i * rowStep < row ? i : -1;
    }

    /** Width left for the key hints at the bottom, before the «Назад» button. */
    int hintsWidth() {
        return buttonX - 8 - hintsX;
    }

    /** ←→ (↑↓ in the list) move between the modes and stop at the ends. */
    static int step(int cursor, int delta) {
        return Math.max(0, Math.min(MODES - 1, cursor + delta));
    }

    /** «1 184», «1 650»: thousands split by a space like the mock-up. */
    static String group(int value) {
        String digits = Integer.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return value < 0 ? "-" + out : out.toString();
    }

    /** Russian genitive after «до»/«из»: «до 1 определения», «из 21 определения», but «до 8 / 11 определений». */
    static boolean genitiveOne(int value) {
        return value % 10 == 1 && value % 100 != 11;
    }

    enum Plural { ONE, FEW, MANY }

    /** Russian noun form after a number: 1/21 наблюдение, 2–4/22 наблюдения, 0/5–20/11–14 наблюдений. */
    static Plural plural(int value) {
        int n = Math.abs(value), last = n % 10, tens = n % 100;
        if (last == 1 && tens != 11) return Plural.ONE;
        if (last >= 2 && last <= 4 && (tens < 12 || tens > 14)) return Plural.FEW;
        return Plural.MANY;
    }

    enum Study { RUNNING, COMPLETE, STOPPED }

    /** The world study line: running while the server scans, complete once everything is inspected, else stopped (grey). */
    static Study study(boolean scanning, int inspected, int total) {
        if (scanning) return Study.RUNNING;
        return total > 0 && inspected >= total ? Study.COMPLETE : Study.STOPPED;
    }

    static float progress(int inspected, int total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(1, inspected / (float) total));
    }

    /** Whole per cent shown in the study panel: 72% for 1 184 of 1 650, 100% only when everything is inspected. */
    static int percent(int inspected, int total) {
        if (total <= 0) return 0;
        return inspected >= total ? 100 : Math.min(99, (int) Math.floor(100.0 * Math.max(0, inspected) / total));
    }

    /** Lit segments of the {@value #SEGMENTS}-segment study bar. */
    static int segments(int inspected, int total) {
        if (total <= 0) return 0;
        return inspected >= total ? SEGMENTS : (int) Math.floor(SEGMENTS * Math.max(0, inspected) / (double) total);
    }

    enum Unit { SECONDS, MINUTES, HOURS }

    /** «≈ 1 мин», «≈ 3,5 мин»: {@code value} is whole or ends in .5 under ten, whole from ten up. */
    record Eta(double value, Unit unit) {
        /** The number with the given decimal separator: «3,5», «1», «40». */
        String number(String decimal) {
            long whole = (long) Math.floor(value);
            return value - whole >= 0.25 ? whole + decimal + "5" : Long.toString(whole);
        }
    }

    /**
     * Time the world study still needs: the definitions left divided by the server's definitions per second (the only
     * data the client has; nothing new is synced for it). Under 50 s in seconds, then minutes, from two hours on hours;
     * under ten minutes or hours halves are kept. Null when nothing is left or the speed is unknown.
     */
    static Eta remaining(int inspected, int total, int perSecond) {
        int left = total - inspected;
        if (left <= 0 || perSecond <= 0) return null;
        long seconds = (left + perSecond - 1L) / perSecond;
        if (seconds < 50) return new Eta(Math.max(1, seconds), Unit.SECONDS);
        double minutes = seconds / 60.0;
        if (minutes < 120) return new Eta(rounded(minutes), Unit.MINUTES);
        return new Eta(rounded(minutes / 60.0), Unit.HOURS);
    }

    private static double rounded(double value) {
        return value < 10 ? Math.max(1, Math.round(value * 2) / 2.0) : Math.round(value);
    }
}
