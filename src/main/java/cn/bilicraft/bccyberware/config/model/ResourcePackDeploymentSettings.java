package cn.bilicraft.bccyberware.config.model;

import java.util.UUID;

public record ResourcePackDeploymentSettings(
        boolean generationEnabled,
        boolean generateOnStartup,
        String outputFile,
        String mergeDirectory,
        boolean deploymentEnabled,
        ResourcePackDeploymentType type,
        String bindAddress,
        int port,
        String publicUrl,
        byte[] externalSha1,
        boolean autoSendEnabled,
        boolean sendOnUpdate,
        UUID uuid,
        boolean required,
        String prompt
) {
    public ResourcePackDeploymentSettings {
        externalSha1 = externalSha1.clone();
    }

    @Override
    public byte[] externalSha1() {
        return externalSha1.clone();
    }
}
