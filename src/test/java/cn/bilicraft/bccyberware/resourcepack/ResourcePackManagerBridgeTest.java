package cn.bilicraft.bccyberware.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePackManagerBridgeTest {
    @TempDir
    Path directory;

    @Test
    void createsPortablePathRelativeToPluginsDirectory() throws Exception {
        Path plugins = directory.resolve("plugins");
        Path pack = plugins.resolve("BcCyberware/Generation/resource_pack.zip");

        assertEquals("BcCyberware/Generation/resource_pack.zip",
                ResourcePackManagerBridge.pluginRelativePath(plugins, pack));
    }

    @Test
    void rejectsPackOutsidePluginsDirectory() {
        Path plugins = directory.resolve("plugins");
        Path pack = directory.resolve("resource_pack.zip");

        assertThrows(IOException.class,
                () -> ResourcePackManagerBridge.pluginRelativePath(plugins, pack));
    }
}
