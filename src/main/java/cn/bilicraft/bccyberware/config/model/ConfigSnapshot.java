package cn.bilicraft.bccyberware.config.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ConfigSnapshot(
        int schemaVersion,
        boolean createDefaultOrgans,
        long effectEngineTick,
        long saveDebounceTicks,
        String databaseFile,
        boolean warnOnSourceFailure,
        boolean debugEffects,
        GuiSettings gui,
        CapacitySettings capacity,
        Map<String, PackDefinition> packs,
        Map<String, SlotDefinition> slots,
        Map<String, ItemDefinition> items,
        ResourcePackDeploymentSettings resourcePackDeployment,
        Map<String, String> messages
) {
    public ConfigSnapshot {
        packs = Map.copyOf(packs);
        slots = Map.copyOf(slots);
        items = Map.copyOf(items);
        messages = Map.copyOf(messages);
    }

    public ConfigSnapshot withMessageFallbacks(Map<String, String> fallbackMessages) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(fallbackMessages);
        merged.putAll(messages);
        return new ConfigSnapshot(
                schemaVersion,
                createDefaultOrgans,
                effectEngineTick,
                saveDebounceTicks,
                databaseFile,
                warnOnSourceFailure,
                debugEffects,
                gui,
                capacity,
                packs,
                slots,
                items,
                resourcePackDeployment,
                merged
        );
    }
}
