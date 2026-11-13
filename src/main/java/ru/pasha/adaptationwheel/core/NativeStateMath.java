package ru.pasha.adaptationwheel.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact integral state scaling; never route a native long capability through double. */
public final class NativeStateMath {
    private NativeStateMath() {}
    public static long scalePositive(long nativeValue, double remaining) {
        if (nativeValue <= 0 || !Double.isFinite(remaining) || remaining < 0 || remaining >= 1) return nativeValue;
        if (remaining == 0) return 0;
        return BigDecimal.valueOf(nativeValue).multiply(BigDecimal.valueOf(remaining))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
