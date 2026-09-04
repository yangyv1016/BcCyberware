package cn.bilicraft.bccyberware.item;

import java.util.UUID;

public record CyberwareIdentity(
        String definitionId,
        UUID instanceId,
        UUID originalOwnerId,
        String originalOwnerName
) {
    public boolean originalOrgan() {
        return originalOwnerId != null;
    }
}

