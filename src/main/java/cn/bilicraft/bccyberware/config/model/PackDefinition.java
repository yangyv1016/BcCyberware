package cn.bilicraft.bccyberware.config.model;

import java.util.List;

public record PackDefinition(
        String id,
        String namespace,
        String displayName,
        String version,
        String description,
        int priority,
        List<String> depends,
        List<String> softDepends,
        boolean allowOverrides,
        List<String> resourcePacks
) {
    public PackDefinition {
        depends = List.copyOf(depends);
        softDepends = List.copyOf(softDepends);
        resourcePacks = List.copyOf(resourcePacks);
    }
}

