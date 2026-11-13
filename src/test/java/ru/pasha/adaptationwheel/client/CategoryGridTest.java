package ru.pasha.adaptationwheel.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ru.pasha.adaptationwheel.core.AdaptationProfile;

/** 4.44 category picker: every GUI size keeps the window inside the details, the arrows walk it, every reason has its short text. */
public final class CategoryGridTest {
    private static final int COUNT = AdaptationProfile.values().length;

    public static void run() {
        everySize();
        arrows();
        texts();
        System.out.println("CategoryGridTest: the window fits every GUI size, arrows stay on the grid, both languages have every text.");
    }

    private static void everySize() {
        check(COUNT == 14, "The design shows 14 categories");
        for (int width = 320; width <= 1920; width++) for (int height = 240; height <= 1080; height += 7) {
            MenuLayout layout = new MenuLayout(width, height);
            int[] panel = MenuLayout.detailPanel(width, height);
            int[] box = layout.split ? new int[]{layout.detailLeft, layout.detailTop, layout.detailRight, layout.detailBottom}
                    : new int[]{panel[0] + 12, panel[1] + 8, panel[2] - 12, panel[3] - 6};
            CategoryGrid grid = new CategoryGrid(box[0], box[1], box[2], box[3], box[1] + 36, COUNT);
            String at = width + "×" + height;
            check(grid.x >= box[0] && grid.x + grid.width == box[2] && grid.y >= box[1] && grid.y + grid.height <= box[3], "Window outside the details at " + at);
            check(grid.cell >= 100, "Cells too narrow for a name at " + at + ": " + grid.cell);
            for (int i = 0; i < COUNT; i++) {
                int x = grid.cellX(i), y = grid.cellY(i);
                check(x >= grid.x && x + grid.cell <= grid.x + grid.width && y >= grid.y + CategoryGrid.HEAD && y + CategoryGrid.ROW <= grid.y + grid.height - CategoryGrid.FOOT + 2, "Cell outside the window at " + at);
                check(grid.at(x, y) == i && grid.at(x + grid.cell - 1, y + CategoryGrid.ROW - 1) == i, "A cell does not answer to the mouse at " + at);
            }
            check(grid.at(grid.x, grid.y) == -1 && grid.contains(grid.x, grid.y) && !grid.contains(grid.x - 1, grid.y), "Edges at " + at);
        }
        // Pasha's windows: wide 1146×472 opens below the title; the launcher's 462×265 stays inside its own screen.
        MenuLayout wide = new MenuLayout(1146, 472);
        CategoryGrid grid = new CategoryGrid(wide.detailLeft, wide.detailTop, wide.detailRight, wide.detailBottom, wide.detailTop + 36, COUNT);
        check(grid.y == wide.detailTop + 36 && grid.x + grid.width == wide.detailRight, "Wide: under the title, at the right edge");
    }

    private static void arrows() {
        check(CategoryGrid.move(AdaptationProfile.PHYSICAL.ordinal(), 0, 2, COUNT) == AdaptationProfile.COLD.ordinal(), "Physical to Cold is two down, as on the design's screen");
        check(CategoryGrid.move(0, 1, 0, COUNT) == 1 && CategoryGrid.move(1, -1, 0, COUNT) == 0, "Left and right change the column");
        check(CategoryGrid.move(0, -1, 0, COUNT) == 0 && CategoryGrid.move(1, 1, 0, COUNT) == 1, "Not past the side, and not onto the next row");
        check(CategoryGrid.move(0, 0, -1, COUNT) == 0 && CategoryGrid.move(COUNT - 1, 0, 1, COUNT) == COUNT - 1, "Not past the top or the bottom");
        for (int i = 0; i < COUNT; i++) for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int moved = CategoryGrid.move(i, step[0], step[1], COUNT);
            check(moved >= 0 && moved < COUNT, "Off the grid from " + i);
            if (moved != i) check(CategoryGrid.move(moved, -step[0], -step[1], COUNT) == i, "A step back returns from " + i);
        }
        check(CategoryGrid.move(12, 0, 1, 13) == 12 && CategoryGrid.move(11, 0, 1, 13) == 11 && CategoryGrid.move(10, 0, 1, 13) == 12, "An odd grid stops at its last cell");
    }

    private static void texts() {
        for (String lang : List.of("ru_ru", "en_us")) {
            String text;
            try (InputStream in = CategoryGridTest.class.getResourceAsStream("/assets/adaptationwheel/lang/" + lang + ".json")) {
                check(in != null, "No lang file " + lang);
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failed) {
                throw new java.io.UncheckedIOException(failed);
            }
            // The first word of every reason the analyzer can give (analysis.adaptationwheel.reason.*).
            for (String reason : List.of("registry", "native", "name", "compat", "tag", "fallback", "online", "learned", "species"))
                check(text.contains("\"gui.adaptationwheel.category.reason." + reason + "\""), lang + ": no short reason " + reason);
            for (String key : List.of("gui.adaptationwheel.category.label", "gui.adaptationwheel.category.confidence", "gui.adaptationwheel.category.manual",
                    "gui.adaptationwheel.category.fix", "gui.adaptationwheel.category.restore", "gui.adaptationwheel.category.unsure", "gui.adaptationwheel.category.sure",
                    "gui.adaptationwheel.category.was", "gui.adaptationwheel.category.was_off", "gui.adaptationwheel.category.changes", "gui.adaptationwheel.category.changes_text",
                    "gui.adaptationwheel.category.picker", "gui.adaptationwheel.category.picker_keys", "gui.adaptationwheel.keys.k", "gui.adaptationwheel.keys.fix",
                    "gui.adaptationwheel.keys.restore", "gui.adaptationwheel.analysis.verdict_manual", "command.adaptationwheel.why.manual"))
                check(text.contains("\"" + key + "\""), lang + ": no text " + key);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
