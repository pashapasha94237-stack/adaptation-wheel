package ru.pasha.adaptationwheel.client;

import java.util.Arrays;

/**
 * The J menu's list split into groups («Изучаются 2», «Ждут удара 3», …): a short heading over each group, then its
 * rows. Pure arithmetic over content coordinates (0 is the top of the first heading), unit-tested in
 * {@code MenuRowsTest}: where each row sits, which row the mouse is on, how far to scroll to show a row.
 */
final class MenuRows {
    /** Row pitch (the last 2 pixels are the gap between cards) and heading height. */
    final int row, head;
    private int[] top = new int[0];
    private int[] headTop = new int[0], headGroup = new int[0], headCount = new int[0];
    private int height;

    MenuRows(int row, int head) {
        this.row = row;
        this.head = head;
    }

    /** Lays out rows whose groups are already sorted; a new group value starts a heading. */
    void build(int[] groups) {
        top = new int[groups.length];
        int[] tops = new int[groups.length], kinds = new int[groups.length], counts = new int[groups.length];
        int heads = 0, y = 0;
        for (int i = 0; i < groups.length; i++) {
            if (i == 0 || groups[i] != groups[i - 1]) {
                tops[heads] = y;
                kinds[heads] = groups[i];
                heads++;
                y += head;
            }
            counts[heads - 1]++;
            top[i] = y;
            y += row;
        }
        headTop = Arrays.copyOf(tops, heads);
        headGroup = Arrays.copyOf(kinds, heads);
        headCount = Arrays.copyOf(counts, heads);
        height = y;
    }

    int size() { return top.length; }
    int top(int index) { return top[index]; }
    int height() { return height; }
    int heads() { return headTop.length; }
    int headTop(int i) { return headTop[i]; }
    int headGroup(int i) { return headGroup[i]; }
    int headCount(int i) { return headCount[i]; }

    /** The row at a content y, or -1 on a heading, a gap between cards or past the end. */
    int at(int y) {
        if (y < 0 || top.length == 0) return -1;
        int found = Arrays.binarySearch(top, y);
        int index = found >= 0 ? found : -found - 2;
        return index >= 0 && y < top[index] + row - 2 ? index : -1;
    }

    /** The first row whose card is below a content y; for the page keys and the list's first visible row. */
    int firstBelow(int y) {
        int found = Arrays.binarySearch(top, y);
        return found >= 0 ? found : -found - 1;
    }

    /** Whether a row is fully inside the visible window. */
    boolean shown(int index, int scroll, int visible) {
        return index >= 0 && index < top.length && top[index] >= scroll && top[index] + row <= scroll + visible;
    }

    /** The scroll that shows a row with the least movement; the first row of a group brings its heading along. */
    int reveal(int index, int scroll, int visible) {
        if (index < 0 || index >= top.length) return scroll;
        int from = top[index] - (index == 0 || top[index - 1] + row != top[index] ? head : 0), to = top[index] + row;
        if (from < scroll) scroll = from;
        else if (to > scroll + visible) scroll = to - visible;
        return Math.max(0, Math.min(scroll, Math.max(0, height - visible)));
    }

    /** How many rows a page key moves: the rows that fit, minus one for context. */
    int page(int visible) {
        return Math.max(1, visible / row - 1);
    }
}
