package ru.pasha.adaptationwheel.core;

public final class NativeStateMathTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    public static void main(String[] args) { run(); }
    public static void run() {
        check(NativeStateMath.scalePositive(10, .8) == 8, "Meaningful partial reduction");
        check(NativeStateMath.scalePositive(1, .8) == 1, "Fractional integral states round toward native harm");
        check(NativeStateMath.scalePositive(Long.MAX_VALUE, 1) == Long.MAX_VALUE, "Disabled protection must preserve all long bits");
        check(NativeStateMath.scalePositive(Long.MAX_VALUE, .5) == 4_611_686_018_427_387_904L, "Half Long.MAX_VALUE must not lose precision or overflow");
        check(NativeStateMath.scalePositive(Long.MAX_VALUE, 0) == 0, "Full protection must handle the full positive native range");
        check(NativeStateMath.scalePositive(-1, 0) == -1, "Unknown negative schema fails open");
        check(NativeStateMath.scalePositive(17, Double.NaN) == 17, "Invalid factor fails open");
        System.out.println("NativeStateMathTest PASS: exact long scaling, rounding, complete native range and fail-open values");
    }
}
