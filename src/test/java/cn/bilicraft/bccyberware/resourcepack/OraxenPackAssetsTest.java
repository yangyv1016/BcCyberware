package cn.bilicraft.bccyberware.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OraxenPackAssetsTest {
    @TempDir
    Path directory;

    @Test
    void readsOnlyAssetFilesForOraxenInjection() throws Exception {
        Path zip = directory.resolve("pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(output, "pack.mcmeta", "metadata");
            entry(output, "pack.png", "icon");
            entry(output, "assets/bccyberware/models/item/heart.json", "model");
            entry(output, "assets/bccyberware/textures/item/heart.png", "texture");
        }

        Map<String, byte[]> assets = OraxenPackAssets.read(zip);

        assertEquals(2, assets.size());
        assertArrayEquals("model".getBytes(StandardCharsets.UTF_8),
                assets.get("assets/bccyberware/models/item/heart.json"));
    }

    @Test
    void rejectsZipWithoutAssets() throws Exception {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(output, "pack.mcmeta", "metadata");
        }

        assertThrows(IOException.class, () -> OraxenPackAssets.read(zip));
    }

    private static void entry(ZipOutputStream output, String name, String value) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
