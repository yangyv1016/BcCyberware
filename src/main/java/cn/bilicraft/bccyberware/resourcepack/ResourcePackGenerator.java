package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.PackDefinition;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackGenerator {
    private final Path dataDirectory;

    public ResourcePackGenerator(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    public GeneratedResourcePack generate(ConfigSnapshot snapshot) throws IOException {
        Path output = resolveInsideDataDirectory(snapshot.resourcePackDeployment().outputFile());
        Path staging = Files.createTempDirectory("bccyberware-resource-pack-");
        Path temporaryZip = null;

        try {
            List<PackDefinition> orderedPacks = orderPacks(snapshot.packs());
            for (PackDefinition pack : orderedPacks) {
                Path assets = dataDirectory.resolve("packs").resolve(pack.id()).resolve("Assets");
                copyTree(assets, staging);
            }

            Path mergeDirectory = resolveInsideDataDirectory(snapshot.resourcePackDeployment().mergeDirectory());
            copyTree(mergeDirectory, staging);
            validatePack(staging);
            PaperModelRouter.generate(staging, snapshot.items().values());

            Files.createDirectories(output.getParent());
            temporaryZip = Files.createTempFile(output.getParent(), ".bccyberware-resource-pack-", ".zip.tmp");
            writeZip(staging, temporaryZip);
            moveAtomically(temporaryZip, output);
            byte[] sha1 = digest(output, "SHA-1");
            return new GeneratedResourcePack(output, sha1, HexFormat.of().formatHex(sha1), Files.size(output));
        } finally {
            deleteTree(staging);
            if (temporaryZip != null) {
                Files.deleteIfExists(temporaryZip);
            }
        }
    }

    private static List<PackDefinition> orderPacks(Map<String, PackDefinition> packs) throws IOException {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> outgoing = new HashMap<>();
        for (PackDefinition pack : packs.values()) {
            indegree.put(pack.id(), 0);
            outgoing.put(pack.id(), new LinkedHashSet<>());
        }
        for (PackDefinition pack : packs.values()) {
            for (String dependency : pack.depends()) {
                if (!packs.containsKey(dependency)) {
                    throw new IOException("Pack " + pack.id() + " 缺少资源合并硬依赖：" + dependency);
                }
                addDependency(dependency, pack.id(), indegree, outgoing);
            }
            for (String dependency : pack.softDepends()) {
                if (packs.containsKey(dependency)) {
                    addDependency(dependency, pack.id(), indegree, outgoing);
                }
            }
        }

        Comparator<PackDefinition> comparator = Comparator.comparingInt(PackDefinition::priority)
                .thenComparing(PackDefinition::id);
        PriorityQueue<PackDefinition> ready = new PriorityQueue<>(comparator);
        packs.values().stream().filter(pack -> indegree.get(pack.id()) == 0).forEach(ready::add);
        List<PackDefinition> ordered = new java.util.ArrayList<>();
        while (!ready.isEmpty()) {
            PackDefinition pack = ready.remove();
            ordered.add(pack);
            for (String dependent : outgoing.get(pack.id())) {
                int next = indegree.merge(dependent, -1, Integer::sum);
                if (next == 0) {
                    ready.add(packs.get(dependent));
                }
            }
        }
        if (ordered.size() != packs.size()) {
            throw new IOException("Pack 资源合并依赖存在循环");
        }
        return ordered;
    }

    private static void addDependency(
            String dependency,
            String dependent,
            Map<String, Integer> indegree,
            Map<String, Set<String>> outgoing
    ) {
        if (outgoing.get(dependency).add(dependent)) {
            indegree.merge(dependent, 1, Integer::sum);
        }
    }

    public GeneratedResourcePack inspectExisting(ConfigSnapshot snapshot) throws IOException {
        Path output = resolveInsideDataDirectory(snapshot.resourcePackDeployment().outputFile());
        if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("资源包尚未生成：" + output);
        }
        byte[] sha1 = digest(output, "SHA-1");
        return new GeneratedResourcePack(output, sha1, HexFormat.of().formatHex(sha1), Files.size(output));
    }

    private Path resolveInsideDataDirectory(String configuredPath) throws IOException {
        Path resolved = dataDirectory.resolve(configuredPath).normalize();
        if (!resolved.startsWith(dataDirectory)) {
            throw new IOException("资源包路径越出插件数据目录：" + configuredPath);
        }
        return resolved;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("资源文件路径越界：" + relative);
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("资源包 Assets 不允许符号链接：" + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void validatePack(Path staging) throws IOException {
        if (!Files.isRegularFile(staging.resolve("pack.mcmeta"))) {
            throw new IOException("生成失败：所有启用 Pack 与 Generation/merge 中均没有 pack.mcmeta");
        }
        if (!Files.isDirectory(staging.resolve("assets"))) {
            throw new IOException("生成失败：合并结果中没有 assets 目录");
        }
    }

    private static void writeZip(Path root, Path output) throws IOException {
        try (OutputStream file = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(file))) {
            zip.setLevel(9);
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String name = root.relativize(path).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    private static byte[] digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " 不可用", exception);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    public record GeneratedResourcePack(Path file, byte[] sha1, String sha1Hex, long size) {
        public GeneratedResourcePack {
            sha1 = sha1.clone();
        }

        @Override
        public byte[] sha1() {
            return sha1.clone();
        }
    }
}
