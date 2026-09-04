package cn.bilicraft.bccyberware.config.model;

import cn.bilicraft.bccyberware.util.Comparison;

import java.util.List;

public record ThresholdRule(
        String id,
        boolean enabled,
        String metric,
        Comparison comparison,
        double value,
        long intervalTicks,
        List<ActionSpec> actions
) {
    public ThresholdRule {
        actions = List.copyOf(actions);
    }
}

