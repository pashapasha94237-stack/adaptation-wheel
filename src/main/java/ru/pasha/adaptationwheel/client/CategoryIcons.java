package ru.pasha.adaptationwheel.client;

import ru.pasha.adaptationwheel.core.AdaptationProfile;

/**
 * 7×7 category icons drawn next to the category colour, so categories of almost the same colour (physical attacks,
 * projectiles, hunger) still tell apart. Pure data, unit-tested in {@code CategoryIconsTest}; drawn by
 * {@link WheelUi#icon}. Each row is a bit mask, bit 6 is the left pixel.
 */
final class CategoryIcons {
    static final int SIZE = 7;
    /** World mechanics ({@code environment:} keys) get a gear, the low FPS adaptation a screen. */
    static final int[] GEAR = rows(".#.#.#.", "#######", ".##.##.", "###.###", ".##.##.", "#######", ".#.#.#.");
    static final int[] SCREEN = rows("#######", "#.....#", "#.#.#.#", "#.###.#", "#######", "...#...", ".#####.");
    private static final int[][] PROFILE = new int[AdaptationProfile.values().length][];

    static {
        put(AdaptationProfile.FIRE, "..#....", "..##...", ".####..", ".#####.", "###.##.", "##..###", ".#####.");
        put(AdaptationProfile.PHYSICAL, ".....##", "....###", "...###.", "#.###..", ".###...", ".##....", "#..#...");
        put(AdaptationProfile.MAGIC, "...#...", "...#...", "..###..", "#######", "..###..", ".##.##.", ".#...#.");
        put(AdaptationProfile.EFFECT, "..###..", "...#...", "..###..", ".#...#.", "#######", "#######", ".#####.");
        put(AdaptationProfile.HUNGER, "....##.", "...####", "..#####", "..####.", ".##....", "##.....", "#......");
        put(AdaptationProfile.COLD, "...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#...");
        put(AdaptationProfile.DARKNESS, "..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###..");
        put(AdaptationProfile.ENVIRONMENT, "...####", "..#####", ".####.#", ".###.##", ".#.####", "..###..", "#......");
        put(AdaptationProfile.PROJECTILE, "...####", ".....##", "....#.#", "...#..#", "#.#....", ".#.....", "#.#....");
        put(AdaptationProfile.EXPLOSION, ".....#.", "....#.#", "..###..", ".#####.", "##.####", "#######", ".#####.");
        put(AdaptationProfile.LIGHT, "#..#..#", ".#...#.", "..###..", "#.###.#", "..###..", ".#...#.", "#..#..#");
        put(AdaptationProfile.ELECTRIC, "....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##....");
        put(AdaptationProfile.POISON, "...#...", "..###..", "..###..", ".#####.", "##.####", "#######", ".#####.");
        put(AdaptationProfile.SUFFOCATION, "....##.", "...#..#", "...#..#", "##..##.", "##.....", ".....#.", "....#.#");
    }

    private CategoryIcons() {
    }

    static int[] of(AdaptationProfile profile) {
        return PROFILE[profile.ordinal()];
    }

    /** The icon of an adaptation key: mechanics and the FPS adaptation have their own, the rest their category's. */
    static int[] forKey(String key, AdaptationProfile profile) {
        if (FpsAdaptation.KEY.equals(key)) return SCREEN;
        if (key != null && key.startsWith("environment:")) return GEAR;
        return of(profile);
    }

    private static void put(AdaptationProfile profile, String... rows) {
        PROFILE[profile.ordinal()] = rows(rows);
    }

    private static int[] rows(String... rows) {
        if (rows.length != SIZE) throw new IllegalArgumentException("An icon has " + SIZE + " rows");
        int[] bits = new int[SIZE];
        for (int y = 0; y < SIZE; y++) {
            if (rows[y].length() != SIZE) throw new IllegalArgumentException("An icon row has " + SIZE + " pixels");
            for (int x = 0; x < SIZE; x++) if (rows[y].charAt(x) == '#') bits[y] |= 1 << (SIZE - 1 - x);
        }
        return bits;
    }
}
