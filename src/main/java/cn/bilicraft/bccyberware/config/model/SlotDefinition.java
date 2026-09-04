package cn.bilicraft.bccyberware.config.model;

import java.util.List;

public record SlotDefinition(
        String id,
        String type,
        String displayName,
        int guiSlot,
        String defaultOrganId,
        List<TriggerSpec> emptyEffects
) {
    public SlotDefinition {
        emptyEffects = List.copyOf(emptyEffects);
    }
}

