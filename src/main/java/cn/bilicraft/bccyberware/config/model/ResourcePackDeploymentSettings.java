package cn.bilicraft.bccyberware.config.model;

public record ResourcePackDeploymentSettings(
        boolean generationEnabled,
        boolean generateOnStartup,
        String outputFile,
        String mergeDirectory,
        boolean oraxenIntegrationEnabled,
        boolean reloadOraxenAfterGeneration
) {
}
