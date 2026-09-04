package cn.bilicraft.bccyberware.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class OraxenPackAssets {
    private OraxenPackAssets() {
    }

    static Map<String, byte[]> read(Path zipFile) throws IOException {
        LinkedHashMap<String, byte[]> assets = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(zipFile);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName().replace('\\', '/');
                if (!entry.isDirectory() && isSafeAssetPath(path)) {
                    assets.put(path, zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        if (assets.isEmpty()) {
            throw new IOException("生成的 ZIP 中没有 assets/ 文件，无法注入 Oraxen");
        }
        return assets;
    }

    private static boolean isSafeAssetPath(String path) {
        return path.startsWith("assets/")
                && !path.endsWith("/")
                && !path.contains("../")
                && !path.contains("/..")
                && !path.startsWith("/");
    }
}
