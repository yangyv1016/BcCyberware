package cn.bilicraft.bccyberware.config.model;

import org.bukkit.Material;

import java.util.List;

public record ItemDefinition(
        String id,
        String slotType,
        Material material,
        String itemModel,
        Integer customModelData,
        double capacityCost,
        boolean originalOrgan,
        String displayName,
        List<String> lore,
        List<TriggerSpec> triggers
) {
    public ItemDefinition {
        lore = List.copyOf(lore);
        triggers = List.copyOf(triggers);
    }
}

