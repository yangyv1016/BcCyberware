package cn.bilicraft.bccyberware.config.model;

import java.util.List;

public record CapacitySettings(
        boolean enabled,
        double base,
        boolean includePlayerPermanent,
        List<ValueSourceSpec> sources,
        List<ThresholdRule> thresholds
) {
    public CapacitySettings {
        sources = List.copyOf(sources);
        thresholds = List.copyOf(thresholds);
    }
}

