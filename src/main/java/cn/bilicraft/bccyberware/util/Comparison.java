package cn.bilicraft.bccyberware.util;

public enum Comparison {
    GTE,
    GT,
    LTE,
    LT,
    EQ;

    public boolean test(double actual, double expected) {
        return switch (this) {
            case GTE -> actual >= expected;
            case GT -> actual > expected;
            case LTE -> actual <= expected;
            case LT -> actual < expected;
            case EQ -> Math.abs(actual - expected) < 0.000_001;
        };
    }
}

