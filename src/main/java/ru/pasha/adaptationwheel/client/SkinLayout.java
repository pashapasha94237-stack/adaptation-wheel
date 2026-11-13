package ru.pasha.adaptationwheel.client;

/**
 * Where the look menu («Облик колеса») puts its parts for one GUI size, as in the approved mock-up: the list of looks
 * on the left, the preview on the right with the name and description under it, the buttons at the bottom. Windows
 * narrower than 560 or too short for eight 38 px cards (the launcher's 925×530 window, 462×265) get the compact
 * variant: smaller cards, a smaller preview and one line instead of the description. Since 4.38.0 there are eight
 * looks; compact cards keep their 26 px rows where they fit (462×265 is unchanged) and shrink to 23 px on the
 * lowest windows. Since 4.39.0 there are nine: 462×265 gets 23 px rows, and the lowest windows 17–19 px rows with the
 * name only (no tagline). Pure arithmetic, unit-tested.
 */
final class SkinLayout {
    static final int COMPACT_BELOW_WIDTH = 560, CARDS = 9, CLOSE = 14, ADAPT_HEIGHT = 16, ADAPT_HEIGHT_COMPACT = 13;
    /** Nine wide cards (top + 45 + 9 × 38) and the buttons under them need this much height. */
    static final int WIDE_MIN_HEIGHT = 6 + 45 + CARDS * 38 + 33 + 6;
    final boolean compact;
    final int left, top, right, bottom;
    final int backX, backY, titleX, titleY, chipY, chipRight, subtitleY;
    final int listX, listW, listY, row, card, thumb;
    final int px0, py0, px1, py1, descY, descBottom, noteY, noteBottom, ruleBottom;
    final int clickX, clickY, clickW, clickH;
    final int wearX, wearW, backButtonX, backButtonW, buttonY, buttonH, statusY;

    SkinLayout(int width, int height) {
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
            chipY = top + 9;
            chipRight = right - 12;
            subtitleY = top + 29;
            listX = left + 12;
            listW = 196;
            listY = top + 45;
            row = 38;
            card = 36;
            thumb = 32;
            px0 = listX + listW + 12;
            px1 = right - 12;
            py0 = listY;
            py1 = Math.min(listY + 205, bottom - 110);
            descY = py1 + 8;
            wearX = right - 218;
            wearW = 100;
            backButtonX = right - 112;
            backButtonW = 100;
            buttonY = bottom - 25;
            buttonH = 18;
            statusY = bottom - 19;
            ruleBottom = bottom - 33;
            descBottom = bottom - 33;
            noteY = listY + CARDS * row + 4;
            noteBottom = statusY - 2;
        } else {
            left = 4;
            right = width - 4;
            top = 4;
            bottom = height - 4;
            backX = left + 6;
            backY = top + 5;
            titleX = left + 25;
            titleY = top + 8;
            chipY = top + 6;
            chipRight = right - 8;
            subtitleY = -1;
            listX = left + 8;
            listW = 150;
            listY = top + 24;
            // the list ends 2 px above the buttons (bottom − 22)
            row = Math.max(17, Math.min(26, (bottom - 24 - listY + 2) / CARDS));
            card = row - 2;
            thumb = Math.min(22, card - 2);
            px0 = listX + listW + 8;
            px1 = right - 8;
            py0 = listY;
            py1 = bottom - 50;
            descY = py1 + 5;
            wearW = 80;
            backButtonW = 70;
            backButtonX = right - 8 - backButtonW;
            wearX = backButtonX - 4 - wearW;
            buttonY = bottom - 22;
            buttonH = 16;
            statusY = bottom - 17;
            ruleBottom = -1;
            descBottom = buttonY - 2;
            noteY = -1;
            noteBottom = -1;
        }
        clickW = 92;
        clickH = 12;
        clickX = px1 - 4 - clickW;
        clickY = py0 + 4;
    }

    /** The card under the mouse, or -1 (the gap between cards is not a card). */
    int cardAt(double x, double y) {
        if (x < listX || x >= listX + listW || y < listY) return -1;
        int i = (int) Math.floor((y - listY) / row);
        return i < CARDS && (y - listY) - i * row < card ? i : -1;
    }

    int cardY(int index) {
        return listY + index * row;
    }

    boolean inPreview(double x, double y) {
        return x >= px0 && x < px1 && y >= py0 && y < py1;
    }

    /** Whether a compact card is tall enough for its tagline under the name. */
    boolean compactTagline() {
        return card >= 20;
    }

    /** Top of a compact card's name line; its tagline is 10 px lower. */
    int compactNameY(int index) {
        return cardY(index) + (compactTagline() ? (card - 20) / 2 + 1 : (card - 8) / 2);
    }

    /** How many 10 px lines of the description fit under the name (the wide layout only). */
    int descriptionLines() {
        return compact ? 0 : Math.max(0, (descBottom - (descY + 13)) / 10);
    }

    /** How many description lines leave room for the Infinite Void's adapt button under them (the wide layout only). */
    int voidLines() {
        return compact ? 0 : Math.max(0, (descBottom - ADAPT_HEIGHT - 3 - (descY + 13)) / 10);
    }

    /** Top of the adapt button: under {@code lines} lines of the description, or at the end of the compact name line. */
    int adaptY(int lines) {
        return compact ? descY - 2 : descY + 16 + lines * 10;
    }

    int adaptH() {
        return compact ? ADAPT_HEIGHT_COMPACT : ADAPT_HEIGHT;
    }

    /** The thin stage bar under the compact tagline. */
    int compactBarY() {
        return descY + 20;
    }

    /** Whether a note of {@code lines} lines fits under the list. */
    boolean noteFits(int lines) {
        return !compact && noteY + lines * 10 <= noteBottom;
    }
}
