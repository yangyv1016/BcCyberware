package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.PackDefinition;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentSettings;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackGeneratorTest {
    @TempDir
    Path dataDirectory;

    @Test
    void mergesPackAssetsByPriorityAndAppliesGenerationMergeLast() throws Exception {
        write("packs/base/Assets/pack.mcmeta", "base metadata");
        write("packs/base/Assets/assets/bccyberware/base.txt", "base only");
        write("packs/base/Assets/assets/bccyberware/shared.txt", "base value");

        write("packs/extension/Assets/pack.mcmeta", "extension metadata");
        write("packs/extension/Assets/assets/bccyberware/extension.txt", "extension only");
        write("packs/extension/Assets/assets/bccyberware/shared.txt", "extension value");

        write("Generation/merge/pack.mcmeta", "server metadata");
        write("Generation/merge/assets/bccyberware/shared.txt", "server override");
        write("Generation/merge/assets/bccyberware/server.txt", "server only");

        Map<String, PackDefinition> packs = new LinkedHashMap<>();
        // Intentionally insert the higher-priority Pack first: merge order must use priority, not Map iteration order.
        packs.put("extension", pack("extension", 100));
        packs.put("base", pack("base", 0));

        ResourcePackGenerator.GeneratedResourcePack generated = generator().generate(snapshot(packs));
        Map<String, byte[]> entries = readZip(generated.file());

        assertEquals("server metadata", text(entries, "pack.mcmeta"));
        assertEquals("server override", text(entries, "assets/bccyberware/shared.txt"));
        assertEquals("base only", text(entries, "assets/bccyberware/base.txt"));
        assertEquals("extension only", text(entries, "assets/bccyberware/extension.txt"));
        assertEquals("server only", text(entries, "assets/bccyberware/server.txt"));
    }

    @Test
    void packDependenciesTakePrecedenceOverNumericPriority() throws Exception {
        write("packs/base/Assets/pack.mcmeta", "metadata");
        write("packs/base/Assets/assets/bccyberware/shared.txt", "base");
        write("packs/extension/Assets/assets/bccyberware/shared.txt", "extension");

        Map<String, PackDefinition> packs = Map.of(
                "base", pack("base", 100),
                "extension", pack("extension", 0, List.of("base"))
        );

        Map<String, byte[]> entries = readZip(generator().generate(snapshot(packs)).file());
        assertEquals("extension", text(entries, "assets/bccyberware/shared.txt"));
    }

    @Test
    void writesAValidZipAndReportsItsActualSha1SizeAndPath() throws Exception {
        write("packs/core/Assets/pack.mcmeta", "{\"pack\":{\"description\":\"test\"}}");
        write("packs/core/Assets/assets/bccyberware/models/item/test.json", "{\"parent\":\"item/generated\"}");
        ConfigSnapshot snapshot = snapshot(Map.of("core", pack("core", 0)));

        ResourcePackGenerator generator = generator();
        ResourcePackGenerator.GeneratedResourcePack generated = generator.generate(snapshot);
        Path expectedOutput = dataDirectory.resolve("Generation/resource_pack.zip").toAbsolutePath().normalize();
        byte[] expectedSha1 = sha1(expectedOutput);

        assertEquals(expectedOutput, generated.file());
        assertTrue(Files.isRegularFile(generated.file()));
        assertEquals(Files.size(expectedOutput), generated.size());
        assertArrayEquals(expectedSha1, generated.sha1());
        assertEquals(HexFormat.of().formatHex(expectedSha1), generated.sha1Hex());
        assertEquals(40, generated.sha1Hex().length());

        Map<String, byte[]> entries = readZip(generated.file());
        assertTrue(entries.containsKey("pack.mcmeta"));
        assertTrue(entries.containsKey("assets/bccyberware/models/item/test.json"));

        ResourcePackGenerator.GeneratedResourcePack inspected = generator.inspectExisting(snapshot);
        assertEquals(generated.file(), inspected.file());
        assertEquals(generated.size(), inspected.size());
        assertArrayEquals(generated.sha1(), inspected.sha1());
        assertEquals(generated.sha1Hex(), inspected.sha1Hex());

        byte[] firstZip = Files.readAllBytes(generated.file());
        ResourcePackGenerator.GeneratedResourcePack regenerated = generator.generate(snapshot);
        assertArrayEquals(firstZip, Files.readAllBytes(regenerated.file()));
        assertEquals(generated.sha1Hex(), regenerated.sha1Hex());
    }

    @Test
    void rejectsMergedOutputWithoutPackMetadata() throws Exception {
        write("packs/core/Assets/assets/bccyberware/test.txt", "asset");

        IOException exception = assertThrows(IOException.class,
                () -> generator().generate(snapshot(Map.of("core", pack("core", 0)))));

        assertTrue(exception.getMessage().contains("pack.mcmeta"));
        assertTrue(Files.notExists(dataDirectory.resolve("Generation/resource_pack.zip")));
    }

    @Test
    void rejectsMergedOutputWithoutAssetsDirectory() throws Exception {
        write("packs/core/Assets/pack.mcmeta", "metadata");

        IOException exception = assertThrows(IOException.class,
                () -> generator().generate(snapshot(Map.of("core", pack("core", 0)))));

        assertTrue(exception.getMessage().contains("assets"));
        assertTrue(Files.notExists(dataDirectory.resolve("Generation/resource_pack.zip")));
    }

    private ResourcePackGenerator generator() {
        return new ResourcePackGenerator(dataDirectory);
    }

    private ConfigSnapshot snapshot(Map<String, PackDefinition> packs) {
        ResourcePackDeploymentSettings deployment = new ResourcePackDeploymentSettings(
                true,
                true,
                "Generation/resource_pack.zip",
                "Generation/merge",
                true,
                ResourcePackDeploymentType.SELFHOST,
                "127.0.0.1",
                8168,
                "http://127.0.0.1:8168",
                new byte[0],
                true,
                true,
                UUID.fromString("b5c9a5c4-a607-4b35-92a3-81d0b40915a2"),
                true,
                "test"
        );
        return new ConfigSnapshot(
                1,
                true,
                20,
                10,
                40,
                "data/players.db",
                true,
                false,
                null,
                null,
                packs,
                Map.of(),
                Map.of(),
                deployment,
                false,
                List.of(),
                Map.of()
        );
    }

    private static PackDefinition pack(String id, int priority) {
        return pack(id, priority, List.of());
    }

    private static PackDefinition pack(String id, int priority, List<String> depends) {
        return new PackDefinition(
                id,
                id,
                id,
                "1.0.0",
                "",
                priority,
                depends,
                List.of(),
                false,
                List.of()
        );
    }

    private void write(String relative, String content) throws IOException {
        Path target = dataDirectory.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static Map<String, byte[]> readZip(Path file) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(file);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        assertTrue(entries.containsKey(name), () -> "ZIP 中缺少文件：" + name);
        return new String(entries.get(name), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
