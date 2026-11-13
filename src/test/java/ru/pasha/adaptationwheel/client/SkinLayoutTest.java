package ru.pasha.adaptationwheel.client;

/** The look menu's layout at every GUI size Minecraft allows, and at the two Pasha plays at. */
public final class SkinLayoutTest {
    public static void run() {
        SkinLayout ultrawide = new SkinLayout(1146, 472), small = new SkinLayout(462, 265), qa = new SkinLayout(480, 320);
        check(!ultrawide.compact && ultrawide.py1 - ultrawide.py0 == 205 && ultrawide.descriptionLines() >= 8, "A maximised ultrawide window gets the full layout");
        check(ultrawide.noteFits(2), "The note under the list fits the ultrawide window");
        check(small.compact && qa.compact, "The launcher's window and the QA window get the compact layout");
        check(SkinLayout.CARDS == ru.pasha.adaptationwheel.core.WheelSkin.values().length, "One card per look");
        // 4.39.0 added the ninth look: the launcher's window gets 23 px cards that still hold name and tagline
        check(small.row == 23 && small.card == 21 && small.compactTagline() && small.compactNameY(0) == small.listY + 1, "462×265 has 23 px cards with taglines");
        check(new SkinLayout(640, SkinLayout.WIDE_MIN_HEIGHT).descriptionLines() >= 3, "The lowest wide window keeps three description lines");
        // the void's description is 5-7 lines at the widths the wide layout has
        check(new SkinLayout(640, SkinLayout.WIDE_MIN_HEIGHT).voidLines() >= 7, "The lowest wide window holds the void's description above its button");
        check(ultrawide.adaptY(5) == ultrawide.descY + 66, "The button sits where the mock-up has it under five lines");
        int checked = 0;
        for (int width = 320; width <= 1920; width += 7)
            for (int height = 240; height <= 1080; height += 11) {
                verify(new SkinLayout(width, height), width, height);
                checked++;
            }
        System.out.println("SkinLayoutTest: " + checked + " GUI sizes keep the " + SkinLayout.CARDS + " looks, preview, description and buttons apart; 462×265 is compact with a "
                + (small.px1 - small.px0) + "×" + (small.py1 - small.py0) + " preview.");
    }

    private static void verify(SkinLayout l, int width, int height) {
        String at = " at " + width + "×" + height;
        check(l.left >= 0 && l.right <= width && l.top >= 0 && l.bottom <= height, "Panel outside the screen" + at);
        check(l.backX >= l.left + 4 && l.backX + SkinLayout.CLOSE < l.titleX && l.backY + SkinLayout.CLOSE <= l.listY - 4, "Back icon" + at);
        check(l.chipRight <= l.right - 8 && l.chipY + 11 <= l.listY - 4, "Chip" + at);
        if (!l.compact) check(l.subtitleY >= l.titleY + 12 && l.subtitleY + 9 <= l.listY - 4, "Subtitle" + at);
        int listEnd = l.cardY(SkinLayout.CARDS - 1) + l.card;
        check(l.listX >= l.left + 8 && l.listX + l.listW + 6 <= l.px0, "List and preview overlap" + at);
        check(listEnd <= l.statusY - 2 && (l.compact || listEnd <= l.ruleBottom), "The looks do not fit" + at);
        check(l.thumb <= l.card && l.thumb >= 15, "Thumbnail size" + at);
        // a compact card holds its name and tagline (two 9 px lines, 10 px apart)
        if (l.compact) for (int i = 0; i < SkinLayout.CARDS; i++)
            check(l.compactNameY(i) >= l.cardY(i) + 1 && l.compactNameY(i) + (l.compactTagline() ? 19 : 9) <= l.cardY(i) + l.card, "Card text" + at);
        check(l.px1 <= l.right - 8 && l.px1 - l.px0 >= 120 && l.py1 - l.py0 >= 120 && l.py0 == l.listY, "Preview too small" + at);
        check(l.clickX >= l.px0 + 30 && l.clickX + l.clickW <= l.px1 && l.clickY >= l.py0 && l.clickY + l.clickH <= l.py1, "Stage click button" + at);
        check(l.descY >= l.py1 + 5, "Description runs into the preview" + at);
        if (l.compact) check(l.descY + 11 + 9 <= l.buttonY, "Compact tagline runs into the buttons" + at);
        else check(l.descriptionLines() >= 3 && l.descBottom <= l.buttonY, "Description" + at);
        // the void's adapt button, its stage bar and the buttons below
        if (l.compact) {
            check(l.adaptY(0) >= l.py1 + 2 && l.adaptY(0) + l.adaptH() <= l.descY + 11, "Compact adapt button" + at);
            check(l.compactBarY() >= l.descY + 11 + 9 && l.compactBarY() + 2 <= l.buttonY, "Compact stage bar" + at);
        } else {
            check(l.voidLines() >= 7 && l.voidLines() <= l.descriptionLines(), "The void's description" + at);
            check(l.adaptY(l.voidLines()) >= l.descY + 13 + l.voidLines() * 10 && l.adaptY(l.voidLines()) + l.adaptH() <= l.descBottom, "Adapt button" + at);
        }
        check(l.wearX + l.wearW + 4 <= l.backButtonX && l.backButtonX + l.backButtonW <= l.right - 8, "Buttons overlap" + at);
        check(l.buttonY >= listEnd + 2 && l.buttonY + l.buttonH <= l.bottom - 2 && l.buttonY >= l.py1, "Buttons" + at);
        check(l.statusY + 9 <= l.bottom && l.wearX - l.listX - 8 >= 100, "Status line" + at);
        if (!l.compact && l.noteFits(2)) check(l.noteY >= listEnd + 2 && l.noteY + 20 <= l.statusY - 1, "Note" + at);
        for (int i = 0; i < SkinLayout.CARDS; i++) {
            int y = l.cardY(i);
            check(l.cardAt(l.listX + 1, y) == i && l.cardAt(l.listX + l.listW - 1, y + l.card - 1) == i, "Card hit test" + at);
            check(l.cardAt(l.listX + 5, y + l.card) == -1, "The gap between cards is not a card" + at);
        }
        check(l.cardAt(l.listX - 1, l.listY + 2) == -1 && l.cardAt(l.listX + 5, listEnd + l.row) == -1, "Outside the list" + at);
        check(l.inPreview(l.px0 + 1, l.py0 + 1) && !l.inPreview(l.px1, l.py0 + 1), "Preview hit test" + at);
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
