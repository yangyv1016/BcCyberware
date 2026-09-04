package cn.bilicraft.bccyberware.config.model;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConfigSnapshot(
        int schemaVersion,
        boolean createDefaultOrgans,
        long resourcePackDelayTicks,
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
        boolean resourcePacksEnabled,
        List<ResourcePackSpec> resourcePacks,
        Map<String, String> messages
) {
    public ConfigSnapshot {
        packs = Map.copyOf(packs);
        slots = Map.copyOf(slots);
        items = Map.copyOf(items);
        resourcePacks = List.copyOf(resourcePacks);
        messages = Map.copyOf(messages);
    }

    public ConfigSnapshot withMessageFallbacks(Map<String, String> fallbackMessages) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(fallbackMessages);
        merged.putAll(messages);
        return new ConfigSnapshot(
                schemaVersion,
                createDefaultOrgans,
                resourcePackDelayTicks,
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
                resourcePacksEnabled,
                resourcePacks,
                merged
        );
    }
}
