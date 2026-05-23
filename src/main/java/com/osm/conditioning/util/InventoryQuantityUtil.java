package com.osm.conditioning.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InventoryQuantityUtil {

    private InventoryQuantityUtil() {
    }

    public static int ceilToInt(double value) {
        if (value <= 0) {
            return 0;
        }
        return (int) Math.ceil(value - 1e-9);
    }

    public static int ceilToInt(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return value.setScale(0, RoundingMode.CEILING).intValue();
    }
}
