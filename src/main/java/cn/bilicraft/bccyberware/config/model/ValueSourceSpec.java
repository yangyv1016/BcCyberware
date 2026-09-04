package cn.bilicraft.bccyberware.config.model;

import java.util.Map;

public record ValueSourceSpec(
        String id,
        boolean enabled,
        String type,
        String operation,
        String formula,
        double min,
        double max,
        double fallback,
        long refreshTicks,
        Map<String, Object> values
) {
    public ValueSourceSpec {
        values = Map.copyOf(values);
    }
}

