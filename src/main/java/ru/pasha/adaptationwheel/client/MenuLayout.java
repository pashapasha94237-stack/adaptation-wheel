package ru.pasha.adaptationwheel.client;

/**
 * Where the J menu puts its parts for one GUI size. Pure arithmetic, unit-tested for the sizes Pasha plays at: a
 * maximised 3440×1417 window at GUI scale 3 (1146×472) shows the list and the details side by side, the launcher's
 * 925×530 window (462×265, scale 2) gets the compact header.
 */
final class MenuLayout {
    static final int SPLIT_MIN_WIDTH = 720, COMPACT_BELOW_HEIGHT = 300, LIST_MIN = 330, LIST_MAX = 520,
            GLOBAL_WIDTH = 124, VISION_WIDTH = 136, TAB_WIDTH = 72, CLOSE = 14;
    final boolean split, compact;
    /** The panel. */
    final int left, top, right, bottom;
    final int row, titleY, controlsY, closeX, closeY;
    final int tabX, tabY, searchX, searchY, searchWidth, categoryX, categoryY, categoryWidth;
    final int listLeft, listTop, listRight, listBottom, statusY;
    /** The details pane; only meaningful when split. */
    final int detailLeft, detailTop, detailRight, detailBottom;

    MenuLayout(int width, int height) {
        split = width >= SPLIT_MIN_WIDTH;
        compact = height < COMPACT_BELOW_HEIGHT;
        int margin = compact ? 4 : 6;
        int panelWidth = Math.min(split ? 1100 : 620, width - margin * 2);
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        top = margin;
        bottom = height - margin;
        row = compact ? 22 : 25;
        titleY = top + 8;
        closeX = right - 10 - CLOSE;
        closeY = top + (compact ? 4 : 6);
        controlsY = top + (compact ? 24 : 34);
        int x0 = left + 10, x1 = right - 10;
        int column = split ? Math.max(LIST_MIN, Math.min(LIST_MAX, (x1 - x0 - 12) * 45 / 100)) : x1 - x0;
        int columnRight = x0 + column;
        int filterY = controlsY + (compact ? 20 : 22);
        categoryWidth = Math.min(140, column / 3);
        categoryX = columnRight - categoryWidth;
        categoryY = filterY;
        int search, searchRow = filterY;
        if (column >= GLOBAL_WIDTH + 6 + VISION_WIDTH + 8 + TAB_WIDTH * 2 + 4) {
            tabX = columnRight - TAB_WIDTH * 2 - 4;
            tabY = controlsY;
            search = x0;
        } else {
            tabX = x0;
            tabY = filterY;
            search = tabX + TAB_WIDTH * 2 + 10;
        }
        int room = categoryX - 6 - search;
        if (room < 80) {
            // Very narrow windows: the search gets its own row.
            search = x0;
            searchRow = filterY + 22;
            room = column;
        }
        searchX = search;
        searchY = searchRow;
        searchWidth = room;
        listLeft = x0 - 2;
        listRight = split ? columnRight : x1 + 2;
        listTop = searchRow + (compact ? 22 : 24);
        statusY = bottom - (compact ? 11 : 13);
        listBottom = statusY - 3;
        detailLeft = split ? columnRight + 12 : 0;
        detailRight = split ? x1 : 0;
        detailTop = split ? controlsY : 0;
        detailBottom = split ? listBottom : 0;
    }

    /**
     * How many key hints of the footer fit into a room, in order of importance: a hint that does not fit whole is left
     * out, never cut in the middle of a word. Widths include each hint's own key cap; gap is the space after a hint.
     */
    static int hintsThatFit(int[] widths, int gap, int room) {
        int used = 0, count = 0;
        for (int width : widths) {
            if (used + width > room) break;
            used += width + gap;
            count++;
        }
        return count;
    }

    /** The separate details screen used when the menu is too narrow for two panes: x0, y0, x1, y1 of its panel. */
    static int[] detailPanel(int width, int height) {
        int margin = height < COMPACT_BELOW_HEIGHT ? 4 : 6, panel = Math.min(560, width - margin * 2);
        int left = (width - panel) / 2;
        return new int[]{left, margin, left + panel, height - margin};
    }
}
