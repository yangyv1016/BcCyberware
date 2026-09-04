package cn.bilicraft.bccyberware.config.model;

import java.util.List;

public record TriggerSpec(
        String key,
        TriggerType type,
        long intervalTicks,
        double chance,
        long cooldownTicks,
        List<ConditionSpec> conditions,
        List<ActionSpec> actions
) {
    public TriggerSpec {
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }
}

