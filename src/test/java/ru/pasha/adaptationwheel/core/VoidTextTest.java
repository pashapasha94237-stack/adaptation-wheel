package ru.pasha.adaptationwheel.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The Infinite Void's text: stage numbers as on the mock-up, letters that settle for good, and lang files in clean UTF-8. */
public final class VoidTextTest {
    private static final String SAMPLE = "Домен Годжо Сатору над головой: чёрный шар пустоты в искрах звёзд, вокруг него голубая сфера.";

    public static void run() {
        numbers();
        letters();
        runs();
        langFilesAreClean();
        System.out.println("VoidTextTest: the stage, the settling letters and both lang files passed.");
    }

    private static void numbers() {
        check(VoidText.STAGE_TICKS == 20 * 20, "The stage lasts 20 seconds");
        check(VoidText.percent(180) == 45 && VoidText.secondsLeft(180) == 11, "The mock-up's row: 45 %, 11 s left");
        check(VoidText.percent(0) == 0 && VoidText.secondsLeft(0) == 20, "Nothing done yet");
        check(VoidText.percent(399) == 99 && VoidText.secondsLeft(399) == 1, "100 % and 0 s only when the stage is done");
        check(VoidText.percent(400) == 100 && VoidText.secondsLeft(400) == 0 && VoidText.progress(400) == 1, "The stage is done");
        check(VoidText.percent(9999) == 100 && VoidText.percent(-5) == 0 && VoidText.progress(-5) == 0, "Bad numbers stay in range");
    }

    private static void letters() {
        int before = 0;
        for (double progress = 0; progress <= 1.0001; progress += 0.05) {
            int settled = 0;
            for (int i = 0; i < 300; i++) {
                boolean now = VoidText.settled(i, progress);
                if (now) settled++;
                // a letter that has settled never starts flickering again
                check(!VoidText.settled(i, Math.max(0, progress - 0.05)) || now, "Letter " + i + " came unsettled at " + progress);
            }
            check(settled >= before, "The settled letters only grow");
            before = settled;
        }
        check(before == 300, "All letters settle at the end");
        int half = 0;
        for (int i = 0; i < 300; i++) if (VoidText.settled(i, 0.45)) half++;
        check(half > 300 * 0.35 && half < 300 * 0.55, "About 45 % of the letters settle at 45 %: " + half);
        // the mock-up's formula, JS arithmetic, for the first letters
        check(!VoidText.settled(0, 0) && VoidText.settled(0, 0.001), "The first letter's place is 0");
        check(VoidText.settled(1, 0.762) && !VoidText.settled(1, 0.761), "The second letter's place is 0.761");
    }

    private static void runs() {
        check(VoidText.runs("", 0, 0.5).isEmpty(), "An empty line has no stretches");
        check(VoidText.runs(SAMPLE, 0, 0).equals(List.of(new VoidText.Run(0, SAMPLE.length(), false))), "At the start the line is all noise");
        check(VoidText.runs(SAMPLE, 0, 1).equals(List.of(new VoidText.Run(0, SAMPLE.length(), true))), "At the end the line is all settled");
        for (int offset : new int[]{0, 7, 120}) {
            List<VoidText.Run> runs = VoidText.runs(SAMPLE, offset, 0.45);
            check(runs.size() > 2, "A half-settled line changes along the way");
            int at = 0;
            for (VoidText.Run run : runs) {
                check(run.start() == at && run.end() > run.start(), "The stretches cover the line in order");
                at = run.end();
                for (int i = run.start(); i < run.end(); i++)
                    if (SAMPLE.charAt(i) != ' ') check(VoidText.settled(offset + i, 0.45) == run.settled(), "Letter " + i + " is in the wrong stretch");
            }
            check(at == SAMPLE.length(), "The stretches reach the end of the line");
        }
    }

    /** 4.39.0 shipped its look's strings double-encoded (UTF-8 read as Latin-1), so these bytes are looked for directly. */
    private static void langFilesAreClean() {
        for (String lang : List.of("ru_ru", "en_us")) {
            String text;
            try (InputStream in = VoidText.class.getResourceAsStream("/assets/adaptationwheel/lang/" + lang + ".json")) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            for (int i = 0; i + 1 < text.length(); i++) {
                char c = text.charAt(i), next = text.charAt(i + 1);
                boolean broken = (c == 'Ð' || c == 'Ñ' || c == 'Â' || c == 'â') && next >= 0x80 && next <= 0xBF;
                check(!broken, lang + ": double-encoded text at " + text.substring(Math.max(0, i - 40), i + 2));
            }
            for (String key : List.of("void.adapt", "void.adapt.short", "void.adapt.hint", "void.adapt.unworn", "void.adapt.off", "void.progress", "void.progress.short",
                    "void.paused.unworn", "void.paused.off", "void.paused.short", "void.read", "void.read.short"))
                check(text.contains("\"skin.adaptationwheel." + key + "\""), lang + ": no text for " + key);
        }
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
