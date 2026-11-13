package ru.pasha.adaptationwheel.client;

/**
 * Where the corner panel of running adaptations goes. Pure arithmetic, unit-tested in {@code HudLayoutTest}.
 * Vanilla 1.20.1 draws effect icons in the top right corner: beneficial ones at y 1, harmful ones at y 27, both 24
 * high ({@code Gui.renderEffects}); the panel goes under the lowest row that is there instead of over it.
 */
final class HudLayout {
    static final int ROW = 25, HEADER = 18, MORE = 11, MAX_ROWS = 5, WIDTH = 220;

    private HudLayout() {
    }

    /** The panel's top edge for the effect rows on screen. */
    static int top(boolean beneficial, boolean harmful) {
        return harmful ? 27 + 24 + 3 : beneficial ? 1 + 24 + 3 : 4;
    }

    /**
     * Rows shown out of {@code entries}: at most five and at most a third of the screen height including the header and
     * the «Ещё адаптаций: N» line, but always one.
     */
    static int rows(int entries, int height) {
        if (entries <= 0) return 0;
        int room = height / 3 - HEADER, rows = Math.min(MAX_ROWS, room / ROW);
        if (entries > rows) rows = Math.min(MAX_ROWS, (room - MORE) / ROW);
        return Math.max(1, Math.min(entries, rows));
    }

    /** The panel height for the rows shown out of {@code entries}. */
    static int height(int rows, int entries) {
        return HEADER + rows * ROW + (entries > rows ? MORE : 0);
    }
}
