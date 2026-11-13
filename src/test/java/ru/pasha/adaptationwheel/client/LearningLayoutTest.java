package ru.pasha.adaptationwheel.client;

/** The learning screen's layout (variant C tiles) at every GUI size Minecraft allows, and at the two Pasha plays at. */
public final class LearningLayoutTest {
    public static void run() {
        LearningLayout ultrawide = new LearningLayout(1146, 472), launcher = new LearningLayout(462, 265), qa = new LearningLayout(480, 320);
        check(!ultrawide.compact && ultrawide.right - ultrawide.left == 660 && ultrawide.cardW == 208, "A maximised ultrawide window gets three tiles side by side");
        check(ultrawide.studyW == 312 && ultrawide.tileW == 153 && ultrawide.numberX(1) + ultrawide.tileW == ultrawide.textX + ultrawide.textW,
                "Study panel on the left half, two number tiles per row reach the right edge");
        check(launcher.compact && qa.compact, "The launcher's window and the QA window get the list and one tile");
        check(launcher.ruleY == launcher.top + 134 && launcher.detailX == launcher.left + 178 && launcher.listW == 160, "The compact list and tile keep the 4.35 places at 462×265");
        check(new LearningLayout(640, 339).compact && !new LearningLayout(640, 340).compact && new LearningLayout(559, 600).compact, "Thresholds");
        check(ultrawide.cardsY(2, true) - ultrawide.cardsY(2, false) == LearningLayout.strip(), "The server strip pushes the tiles down");

        check(LearningLayout.group(1184).equals("1 184") && LearningLayout.group(1650).equals("1 650") && LearningLayout.group(335).equals("335")
                && LearningLayout.group(0).equals("0") && LearningLayout.group(1_000_000).equals("1 000 000"), "Thousands are split by a space");
        check(LearningLayout.genitiveOne(1) && LearningLayout.genitiveOne(21) && !LearningLayout.genitiveOne(8) && !LearningLayout.genitiveOne(11)
                && !LearningLayout.genitiveOne(32), "«до 1 определения», «до 8 определений»");
        check(LearningLayout.plural(1) == LearningLayout.Plural.ONE && LearningLayout.plural(21) == LearningLayout.Plural.ONE
                && LearningLayout.plural(11) == LearningLayout.Plural.MANY && LearningLayout.plural(2) == LearningLayout.Plural.FEW
                && LearningLayout.plural(34) == LearningLayout.Plural.FEW && LearningLayout.plural(12) == LearningLayout.Plural.MANY
                && LearningLayout.plural(0) == LearningLayout.Plural.MANY && LearningLayout.plural(312) == LearningLayout.Plural.MANY
                && LearningLayout.plural(114) == LearningLayout.Plural.MANY && LearningLayout.plural(102) == LearningLayout.Plural.FEW,
                "1 наблюдение, 2 наблюдения, 11/312 наблюдений, 102 наблюдения");
        check(LearningLayout.study(true, 3, 10) == LearningLayout.Study.RUNNING && LearningLayout.study(false, 10, 10) == LearningLayout.Study.COMPLETE
                && LearningLayout.study(false, 3, 10) == LearningLayout.Study.STOPPED && LearningLayout.study(false, 0, 0) == LearningLayout.Study.STOPPED,
                "Study status: running, complete or stopped (grey)");
        check(LearningLayout.progress(1184, 1650) > 0.71f && LearningLayout.progress(1184, 1650) < 0.72f && LearningLayout.progress(5, 0) == 0
                && LearningLayout.progress(20, 10) == 1, "Study progress");
        check(LearningLayout.percent(1184, 1650) == 71 && LearningLayout.percent(1649, 1650) == 99 && LearningLayout.percent(1650, 1650) == 100
                && LearningLayout.percent(5, 0) == 0 && LearningLayout.percent(0, 10) == 0, "Per cent: 100 only when everything is inspected");
        check(LearningLayout.segments(1188, 1650) == 14 && LearningLayout.segments(1649, 1650) == 19 && LearningLayout.segments(1650, 1650) == 20
                && LearningLayout.segments(0, 1650) == 0 && LearningLayout.segments(3, 0) == 0, "72% lights 14 of 20 segments as in the mock-up");
        eta(1184, 1650, 8, "1", LearningLayout.Unit.MINUTES);   // 466 left at 8/s = 59 s: «≈ 1 мин» as in the mock-up
        eta(0, 1650, 8, "3,5", LearningLayout.Unit.MINUTES);    // 207 s: «≈ 3,5 мин» as in variant D
        eta(1600, 1650, 8, "7", LearningLayout.Unit.SECONDS);
        eta(1649, 1650, 32, "1", LearningLayout.Unit.SECONDS);
        eta(0, 9000, 8, "19", LearningLayout.Unit.MINUTES);     // 1125 s = 18.75 min, whole minutes from ten up
        eta(0, 100_000, 1, "28", LearningLayout.Unit.HOURS);
        eta(0, 60_000, 8, "2", LearningLayout.Unit.HOURS);      // 7500 s = 125 min
        check(LearningLayout.remaining(1650, 1650, 8) == null && LearningLayout.remaining(10, 20, 0) == null && LearningLayout.remaining(30, 20, 8) == null,
                "No time left when everything is inspected or the speed is unknown");
        check(LearningLayout.step(0, -1) == 0 && LearningLayout.step(2, 1) == 2 && LearningLayout.step(1, 1) == 2 && LearningLayout.step(1, -1) == 0,
                "←→ stop at the first and the last tile");

        int checked = 0;
        for (int width = 320; width <= 1920; width += 7)
            for (int height = 240; height <= 1080; height += 11) {
                verify(new LearningLayout(width, height), width, height);
                checked++;
            }
        System.out.println("LearningLayoutTest: " + checked + " GUI sizes keep the three modes, the study panel, the number tiles and the buttons apart; 1146×472 has "
                + ultrawide.cardW + " px tiles, 462×265 a " + launcher.detailW + " px tile next to the list and " + launcher.smallTileW + " px number tiles.");
    }

    private static void eta(int inspected, int total, int perSecond, String number, LearningLayout.Unit unit) {
        var eta = LearningLayout.remaining(inspected, total, perSecond);
        check(eta != null && eta.unit() == unit && eta.number(",").equals(number),
                "Time left for " + inspected + "/" + total + " at " + perSecond + "/s: " + (eta == null ? "null" : eta.number(",") + " " + eta.unit()));
    }

    private static void verify(LearningLayout l, int width, int height) {
        String at = " at " + width + "×" + height;
        check(l.left >= 0 && l.right <= width && l.top >= 0 && l.bottom <= height, "Panel outside the screen" + at);
        check(l.backX >= l.left + 4 && l.backX + LearningLayout.CLOSE < l.titleX, "Back icon" + at);
        check(l.buttonX >= l.left && l.buttonX + l.buttonW <= l.right - 8 && l.buttonY + l.buttonH <= l.bottom - 2, "«Назад» button" + at);
        check(l.hintsWidth() >= 100 && l.hintsY + 9 <= l.bottom, "Key hints" + at);
        if (!l.compact) {
            check(l.cardW >= 160, "Tiles too narrow" + at);
            check(l.cardX(LearningLayout.MODES - 1) + l.cardW <= l.textX + l.textW && l.textX + l.textW <= l.right - 12, "Tiles outside the panel" + at);
            for (int i = 0; i + 1 < LearningLayout.MODES; i++) check(l.cardX(i) + l.cardW < l.cardX(i + 1), "Tiles overlap" + at);
            check(l.studyW >= 200 && l.tileW >= 100 && l.textX + l.studyW < l.numberX(0) && l.numberX(0) + l.tileW < l.numberX(1)
                    && l.numberX(1) + l.tileW <= l.textX + l.textW, "Study panel and number tiles" + at);
            check(LearningLayout.numberY(3) + LearningLayout.TILE_H <= LearningLayout.STUDY_H && LearningLayout.numberY(1) == 0
                    && LearningLayout.numberY(2) >= LearningLayout.TILE_H, "Two rows of number tiles next to the study panel" + at);
            int y = l.cardsY(2, false), h = 150;
            for (int i = 0; i < LearningLayout.MODES; i++) {
                check(l.cardAt(l.cardX(i), y, y, h) == i && l.cardAt(l.cardX(i) + l.cardW - 1, y + h - 1, y, h) == i, "Tile hit test" + at);
                check(l.cardAt(l.cardX(i) + l.cardW, y + 5, y, h) == -1, "The gap after a tile is not a tile" + at);
            }
            check(l.cardAt(l.textX - 1, y + 5, y, h) == -1 && l.cardAt(l.textX + 5, y + h, y, h) == -1, "Outside the tiles" + at);
            check(l.leadY >= l.titleY + 12 && l.cardsY(2, true) + 160 <= l.buttonY, "Header and tiles" + at);
        } else {
            check(l.listX >= l.left + 8 && l.listX + l.listW + 6 <= l.detailX && l.detailW >= 100 && l.detailX + l.detailW <= l.right - 8, "List and tile" + at);
            check(l.rowY(LearningLayout.MODES - 1) + l.row <= l.lockedY && l.lockedY + 13 <= l.ruleY - 4, "The list and the server strip fit above the rule" + at);
            check(l.ruleY - l.detailY >= 90, "The tile of a mode has room" + at);
            check(l.smallTileW >= 60 && l.numberX(3) + l.smallTileW <= l.right - 10 && l.numberX(0) == l.listX, "Four number tiles in a row" + at);
            check(l.statsY >= l.ruleY + 4 && l.statsY + l.smallTileH + 2 <= l.studyY && l.studyY + 10 <= l.barY && l.barY + 4 <= l.changeY
                    && l.changeY + 9 <= l.buttonY - 2, "Number tiles, study line, bar and change line" + at);
            for (int i = 0; i < LearningLayout.MODES; i++) {
                int y = l.rowY(i);
                check(l.rowAt(l.listX, y) == i && l.rowAt(l.listX + l.listW - 1, y + l.row - 1) == i, "Row hit test" + at);
                check(l.rowAt(l.listX + 5, y + l.row) == -1, "The gap between rows is not a row" + at);
            }
            check(l.rowAt(l.listX - 1, l.listY + 2) == -1 && l.rowAt(l.detailX + 2, l.listY + 2) == -1, "Outside the list" + at);
        }
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
