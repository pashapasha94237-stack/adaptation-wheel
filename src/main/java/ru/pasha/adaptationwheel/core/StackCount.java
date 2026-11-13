package ru.pasha.adaptationwheel.core;

/** Stack sizes after the full inventory pressure adaptation. */
public final class StackCount {
    /**
     * "No limit" in practice: a billion per slot. Two full stacks still add up below Integer.MAX_VALUE, so merging
     * code that sums counts before clamping cannot overflow into a negative (empty) stack.
     */
    public static final int LIMIT = 1_000_000_000;
    private StackCount() {}

    /** Slot label: exact below 1000, then 1.2k / 12k / 345k / 1.2M / 12M / 1B, never wider than four characters. */
    public static String format(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 1_000_000) return scaled(count, 1000, "k");
        if (count < 1_000_000_000) return scaled(count, 1_000_000, "M");
        return scaled(count, 1_000_000_000, "B");
    }
    private static String scaled(int count, int unit, String suffix) {
        int whole = count / unit;
        // Rounded down so a label never claims more than the slot holds.
        if (whole >= 10) return whole + suffix;
        int tenth = count % unit / (unit / 10);
        return tenth == 0 ? whole + suffix : whole + "." + tenth + suffix;
    }
    /** Tooltip form in groups of three: 12 345 678. */
    public static String grouped(int count) {
        String digits = Integer.toString(count);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
