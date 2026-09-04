package cn.bilicraft.bccyberware.config;

import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir
    Path directory;

    @Test
    void loadsBundledCorePackWithIndependentLimbs() throws Exception {
        ConfigSnapshot snapshot = new ConfigLoader(Path.of("src/main/resources")).load();

        assertEquals(1, snapshot.packs().size());
        assertEquals(10, snapshot.slots().size());
        assertEquals(18, snapshot.items().size());
        assertEquals("LEFT_LEG", snapshot.slots().get("core:left-leg").type());
        assertEquals("RIGHT_LEG", snapshot.slots().get("core:right-leg").type());
        assertTrue(snapshot.resourcePackDeployment().generationEnabled());
        assertFalse(snapshot.resourcePackDeployment().deploymentEnabled());
        assertEquals(ResourcePackDeploymentType.RESOURCE_PACK_MANAGER, snapshot.resourcePackDeployment().type());
        assertEquals(8168, snapshot.resourcePackDeployment().port());
        assertEquals("", snapshot.resourcePackDeployment().publicUrl());
    }

    @Test
    void loadsAndNamespacesAPack() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", """
                id: core
                namespace: core
                enabled: true
                priority: 0
                depends: []
                soft-depends: []
                """);
        write("packs/core/slots.yml", """
                heart:
                  type: CIRCULATORY
                  gui-slot: 4
                  default-organ: native-heart
                """);
        write("packs/core/organs/organs.yml", """
                items:
                  native-heart:
                    slot-type: CIRCULATORY
                    material: PAPER
                    original-organ: true
                    display-name: Native heart
                    triggers: []
                """);

        ConfigSnapshot snapshot = new ConfigLoader(directory).load();
        assertTrue(snapshot.slots().containsKey("core:heart"));
        assertTrue(snapshot.items().containsKey("core:native-heart"));
        assertEquals("core:native-heart", snapshot.slots().get("core:heart").defaultOrganId());
    }

    @Test
    void rejectsDuplicateGuiSlotsAcrossPacks() throws Exception {
        writeBaseFiles();
        write("packs/one/pack.yml", "id: one\nenabled: true\npriority: 0\n");
        write("packs/one/slots.yml", "first:\n  type: ARM\n  gui-slot: 1\n");
        write("packs/two/pack.yml", "id: two\nenabled: true\npriority: 1\n");
        write("packs/two/slots.yml", "second:\n  type: LEG\n  gui-slot: 1\n");

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("已被其他槽位占用"));
    }

    @Test
    void rejectsOneSlotTypeMappedToTwoBodyPositions() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("packs/core/slots.yml", """
                left:
                  type: ARM
                  gui-slot: 1
                right:
                  type: ARM
                  gui-slot: 2
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("重复类型：ARM"));
    }

    @Test
    void rejectsGeneratedZipInsidePackAssets() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: true
                  output: packs/core/Assets/resource_pack.zip
                  merge-directory: Generation/merge
                deployment:
                  enabled: false
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("不能放在 packs 目录内"), exception::getMessage);
    }

    @Test
    void rejectsGeneratedZipInsideRuntimeCache() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: true
                  output: .runtime/resource_pack.zip
                  merge-directory: Generation/merge
                deployment:
                  enabled: false
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains(".runtime"), exception::getMessage);
    }

    @Test
    void requiresSha1ForEnabledExternalDeployment() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: false
                deployment:
                  enabled: true
                  type: EXTERNAL
                  auto-send:
                    public-url: https://cdn.example.test/resource_pack.zip
                    sha1: ""
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("EXTERNAL 部署需要填写"), exception::getMessage);
    }

    @Test
    void rejectsSelfHostBaseUrlWithPathOrQuery() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: true
                deployment:
                  enabled: true
                  type: SELFHOST
                  auto-send:
                    public-url: https://packs.example.test/proxy?token=unsafe
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("不能包含路径或查询参数"), exception::getMessage);
    }

    @Test
    void resourcePackManagerDoesNotRequirePublicUrl() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: true
                deployment:
                  enabled: true
                  type: RESOURCE_PACK_MANAGER
                  auto-send:
                    public-url: ""
                """);

        ConfigSnapshot snapshot = new ConfigLoader(directory).load();
        assertTrue(snapshot.resourcePackDeployment().deploymentEnabled());
        assertEquals(ResourcePackDeploymentType.RESOURCE_PACK_MANAGER,
                snapshot.resourcePackDeployment().type());
    }

    @Test
    void rejectsLegacyExternalPackList() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", "id: core\nenabled: true\npriority: 0\n");
        write("resources.yml", """
                generation:
                  enabled: true
                deployment:
                  enabled: false
                external-packs:
                  enabled: true
                  packs:
                    - id: old-pack
                      uuid: 2dde193f-9e6c-4d02-87f1-21eb126d93f9
                      url: https://cdn.example.test/old.zip
                      sha1: 0123456789012345678901234567890123456789
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("不再逐包下发"), exception::getMessage);
    }

    @Test
    void rejectsPackResourcePackReferences() throws Exception {
        writeBaseFiles();
        write("packs/core/pack.yml", """
                id: core
                enabled: true
                priority: 0
                resource-packs: [legacy]
                """);

        ConfigException exception = assertThrows(ConfigException.class, () -> new ConfigLoader(directory).load());
        assertTrue(exception.getMessage().contains("字段已移除"), exception::getMessage);
    }

    private void writeBaseFiles() throws IOException {
        write("config.yml", """
                schema-version: 1
                general:
                  resource-pack-delay: 1s
                  effect-engine-tick: 10t
                  save-debounce: 2s
                gui:
                  rows: 6
                  selector-page-size: 45
                  filler:
                    material: BLACK_STAINED_GLASS_PANE
                database:
                  type: SQLITE
                  file: data/players.db
                capacity:
                  enabled: true
                  base: 30
                  sources: []
                  thresholds: []
                """);
        write("resources.yml", "enabled: false\npacks: []\n");
        write("messages.yml", "prefix: '[test] '\n");
    }

    private void write(String relative, String content) throws IOException {
        Path target = directory.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
