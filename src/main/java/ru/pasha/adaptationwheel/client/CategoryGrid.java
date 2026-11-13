package ru.pasha.adaptationwheel.client;

/**
 * The window that picks a row's category: where it stands over the details and how the arrows walk it. Categories go
 * left to right in two columns. Pure arithmetic, tested over every GUI size.
 */
final class CategoryGrid {
    static final int COLUMNS = 2, ROW = 14, HEAD = 20, FOOT = 16, PAD = 6, GAP = 6, MAX_WIDTH = 320;
    final int x, y, width, height, cell;
    private final int count;

    /** At the right edge of the rectangle, as near the wanted top as it fits. */
    CategoryGrid(int left, int top, int right, int bottom, int wanted, int count) {
        this.count = count;
        width = Math.min(MAX_WIDTH, right - left);
        height = HEAD + (count + COLUMNS - 1) / COLUMNS * ROW + FOOT;
        x = right - width;
        y = Math.max(top, Math.min(wanted, bottom - height));
        cell = (width - PAD * 2 - GAP * (COLUMNS - 1)) / COLUMNS;
    }

    int cellX(int index) { return x + PAD + index % COLUMNS * (cell + GAP); }

    int cellY(int index) { return y + HEAD + index / COLUMNS * ROW; }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** The category under the mouse, or -1. */
    int at(double mouseX, double mouseY) {
        for (int i = 0; i < count; i++) {
            int cx = cellX(i), cy = cellY(i);
            if (mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + ROW) return i;
        }
        return -1;
    }

    /** One step of the arrows; at an edge the cursor stays. */
    static int move(int index, int dx, int dy, int count) {
        int column = index % COLUMNS + dx, moved = index + dx + dy * COLUMNS;
        return column < 0 || column >= COLUMNS || moved < 0 || moved >= count ? index : moved;
    }
}
