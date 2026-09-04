package cn.bilicraft.bccyberware.data;

import java.util.Map;
import java.util.UUID;

public record StoredProfile(
        UUID playerId,
        String lastName,
        double permanentCapacity,
        boolean initialized,
        Map<String, byte[]> installedItems
) {
    public StoredProfile {
        installedItems = Map.copyOf(installedItems);
    }
}

