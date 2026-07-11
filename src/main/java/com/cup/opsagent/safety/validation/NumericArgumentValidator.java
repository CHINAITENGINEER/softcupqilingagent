package com.cup.opsagent.safety.validation;

import java.math.BigInteger;
import java.util.Optional;

public final class NumericArgumentValidator {

    private NumericArgumentValidator() {
    }

    public static Optional<Integer> integerInRange(Object rawValue, int min, int max) {
        if (rawValue instanceof BigInteger bigInteger) {
            BigInteger minValue = BigInteger.valueOf(min);
            BigInteger maxValue = BigInteger.valueOf(max);
            if (bigInteger.compareTo(minValue) < 0 || bigInteger.compareTo(maxValue) > 0) {
                return Optional.empty();
            }
            return Optional.of(bigInteger.intValue());
        }
        if (!(rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long)) {
            return Optional.empty();
        }
        long value = ((Number) rawValue).longValue();
        if (value < min || value > max) {
            return Optional.empty();
        }
        return Optional.of((int) value);
    }
}
