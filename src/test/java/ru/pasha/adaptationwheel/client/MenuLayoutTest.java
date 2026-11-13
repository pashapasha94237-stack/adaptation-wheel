package ru.pasha.adaptationwheel.client;

/** The J menu's layout at every GUI size Minecraft allows, and at the two Pasha plays at. */
public final class MenuLayoutTest {
    public static void run() {
        // Pasha: 3440×1417 maximised at GUI scale 3, and the launcher's 925×530 window (scale 3 is capped to 2).
        MenuLayout ultrawide = new MenuLayout(1146, 472), small = new MenuLayout(462, 265);
        check(ultrawide.split && !ultrawide.compact, "A maximised ultrawide window shows the list and details side by side");
        check(rows(ultrawide) >= 13, "Ultrawide list rows: " + rows(ultrawide));
        check(ultrawide.detailBottom - ultrawide.detailTop >= 300 && ultrawide.detailRight - ultrawide.detailLeft >= 500, "Ultrawide details pane too small");
        check(small.compact && !small.split, "The launcher's window gets the compact header");
        // 4.22 showed 5 full rows here (25 px each from y 86 to 231).
        check(rows(small) >= 8, "Compact list rows: " + rows(small));
        check(!new MenuLayout(480, 320).split && !new MenuLayout(480, 320).compact, "The QA window (960×640, scale 2) keeps the plain layout");
        int checked = 0;
        for (int width = 320; width <= 1920; width += 7)
            for (int height = 240; height <= 1080; height += 11) {
                verify(new MenuLayout(width, height), width, height);
                checked++;
            }
        System.out.println("MenuLayoutTest: " + checked + " GUI sizes keep header, list, details and status apart; ultrawide splits, 462×265 shows " + rows(small) + " rows.");
    }

    private static int rows(MenuLayout layout) { return (layout.listBottom - layout.listTop) / layout.row; }

    private static void verify(MenuLayout l, int width, int height) {
        String at = " at " + width + "×" + height;
        int x0 = l.left + 10, columnRight = l.split ? l.listRight : l.right - 10;
        check(l.left >= 0 && l.right <= width && l.top >= 0 && l.bottom <= height, "Panel outside the screen" + at);
        check(l.closeX >= l.left + 200 && l.closeY + MenuLayout.CLOSE <= l.controlsY, "Close button crowds the title" + at);
        // Rows of the header, top to bottom: title, switches (and tabs), filter, then the list.
        check(l.top + (l.compact ? 19 : 24) <= l.controlsY, "Title runs into the switches" + at);
        check(l.categoryY >= l.controlsY + 18, "Category button overlaps the switches" + at);
        check(l.searchY >= l.categoryY && l.listTop >= Math.max(l.searchY, l.categoryY) + 20, "Filter overlaps the list" + at);
        check(l.tabY == l.controlsY ? x0 + MenuLayout.GLOBAL_WIDTH + 6 + MenuLayout.VISION_WIDTH + 6 <= l.tabX : l.tabY == l.categoryY,
                "Tabs overlap the switches" + at);
        check(l.tabX + MenuLayout.TAB_WIDTH * 2 + 4 <= columnRight, "Tabs past the list column" + at);
        if (l.searchY == l.categoryY) check(l.searchX + l.searchWidth <= l.categoryX - 4, "Search overlaps the category button" + at);
        if (l.searchY == l.tabY) check(l.searchX >= l.tabX + MenuLayout.TAB_WIDTH * 2 + 4, "Search overlaps the tabs" + at);
        check(l.searchWidth >= 80 && l.searchX >= x0 && l.searchX + l.searchWidth <= columnRight, "Search field too small or outside" + at);
        check(l.categoryX >= x0 && l.categoryX + l.categoryWidth <= columnRight, "Category button outside the column" + at);
        check(rows(l) >= 4, "Fewer than four list rows" + at);
        check(l.listBottom <= l.statusY && l.statusY + 9 <= l.bottom, "Status line overlaps" + at);
        if (l.split) {
            check(l.listRight - l.listLeft >= MenuLayout.LIST_MIN, "List column too narrow" + at);
            check(l.detailLeft >= l.listRight + 8 && l.detailRight <= l.right - 8 && l.detailRight - l.detailLeft >= 320, "Details pane" + at);
            check(l.detailTop >= l.controlsY && l.detailBottom <= l.statusY, "Details pane height" + at);
        } else {
            int[] panel = MenuLayout.detailPanel(width, height);
            check(panel[0] >= 0 && panel[2] <= width && panel[3] <= height && panel[2] - panel[0] >= 300, "Detail screen panel" + at);
        }
    }

    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
