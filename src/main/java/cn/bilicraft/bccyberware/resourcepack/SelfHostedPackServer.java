package cn.bilicraft.bccyberware.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class SelfHostedPackServer implements AutoCloseable {
    private static final String DOWNLOAD_PREFIX = "/bccyberware/resourcepacks/";
    private static final int MAX_CACHED_PACKS = 4;
    private static final int MAX_CONCURRENT_REQUESTS = 64;
    private static final int REQUEST_QUEUE_WAIT_SECONDS = 30;
    private static final Pattern MANAGED_CACHE_FILE = Pattern.compile("[0-9a-f]{40}\\.zip");

    private final Logger logger;
    private final Path cacheDirectory;
    private final Map<String, HostedPack> hostedPacks = new ConcurrentHashMap<>();
    private final Set<DrainingServer> drainingServers = ConcurrentHashMap.newKeySet();
    private final Semaphore requestSlots = new Semaphore(MAX_CONCURRENT_REQUESTS);
    private HttpServer server;
    private ExecutorService executor;
    private String activeBindAddress;
    private int activePort = -1;

    SelfHostedPackServer(Logger logger, Path cacheDirectory) {
        this.logger = logger;
        this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
    }

    static String downloadPath(String sha1) {
        return DOWNLOAD_PREFIX + sha1 + ".zip";
    }

    synchronized void startPrepared(String bindAddress, int port, PreparedPack prepared) throws IOException {
        if (isRunning(bindAddress, port)) {
            try {
                publishPrepared(prepared);
            } catch (IOException | RuntimeException exception) {
                discardPrepared(prepared);
                throw exception;
            }
            return;
        }

        InetSocketAddress address = bindAddress.isBlank()
                ? new InetSocketAddress(port)
                : new InetSocketAddress(bindAddress, port);
        HttpServer replacement = null;
        ExecutorService replacementExecutor = null;
        try {
            replacement = HttpServer.create(address, 0);
            replacement.createContext(DOWNLOAD_PREFIX, this::handle);
            ThreadFactory threads = Thread.ofVirtual().name("BcCyberware-ResourcePack-HTTP-", 0).factory();
            replacementExecutor = Executors.newThreadPerTaskExecutor(threads);
            replacement.setExecutor(replacementExecutor);
            replacement.start();
            publishPrepared(prepared);
        } catch (IOException | RuntimeException exception) {
            if (replacement != null) {
                replacement.stop(0);
            }
            if (replacementExecutor != null) {
                replacementExecutor.shutdownNow();
            }
            discardPrepared(prepared);
            throw exception;
        }

        HttpServer previous = server;
        ExecutorService previousExecutor = executor;
        server = replacement;
        executor = replacementExecutor;
        activeBindAddress = bindAddress;
        activePort = port;
        if (previous != null) {
            DrainingServer draining = new DrainingServer(previous, previousExecutor);
            drainingServers.add(draining);
            Thread.startVirtualThread(() -> {
                try {
                    draining.server().stop(5);
                    if (draining.executor() != null) {
                        draining.executor().shutdown();
                    }
                } finally {
                    drainingServers.remove(draining);
                }
            });
        }
    }

    synchronized boolean isRunning(String bindAddress, int port) {
        return server != null && activePort == port && Objects.equals(activeBindAddress, bindAddress);
    }

    PreparedPack prepare(ResourcePackGenerator.GeneratedResourcePack generatedPack) throws IOException {
        String sha1 = generatedPack.sha1Hex();
        if (!sha1.matches("[0-9a-f]{40}")) {
            throw new IOException("生成资源包的 SHA-1 格式无效：" + sha1);
        }
        if (Files.isSymbolicLink(cacheDirectory)) {
            throw new IOException("SELFHOST 缓存目录不能是符号链接：" + cacheDirectory);
        }
        Files.createDirectories(cacheDirectory);
        if (Files.isSymbolicLink(cacheDirectory)) {
            throw new IOException("SELFHOST 缓存目录不能是符号链接：" + cacheDirectory);
        }

        Path cached = cacheDirectory.resolve(sha1 + ".zip");
        if (Files.isSymbolicLink(cached)) {
            throw new IOException("SELFHOST 缓存文件不能是符号链接：" + cached);
        }
        if (Files.isRegularFile(cached, LinkOption.NOFOLLOW_LINKS)
                && Files.size(cached) == generatedPack.size()
                && sha1.equals(digestSha1(cached))) {
            return new PreparedPack(cached, null, sha1, generatedPack.size());
        }

        Path temporary = Files.createTempFile(cacheDirectory, "." + sha1 + "-", ".zip.tmp");
        try {
            Files.copy(generatedPack.file(), temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temporary) != generatedPack.size() || !sha1.equals(digestSha1(temporary))) {
                throw new IOException("生成资源包在进入 SELFHOST 缓存前发生变化，已拒绝发布：" + generatedPack.file());
            }
            return new PreparedPack(cached, temporary, sha1, generatedPack.size());
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryFile(temporary);
            throw exception;
        }
    }

    synchronized void publishPrepared(PreparedPack prepared) throws IOException {
        if (prepared.temporary() != null) {
            validateTemporaryPath(prepared.temporary());
            if (Files.isSymbolicLink(prepared.target())) {
                throw new IOException("SELFHOST 缓存文件不能是符号链接：" + prepared.target());
            }
            try {
                Files.move(prepared.temporary(), prepared.target(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(prepared.temporary(), prepared.target(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!Files.isRegularFile(prepared.target(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("SELFHOST 待发布缓存文件不存在：" + prepared.target());
        }
        publish(new HostedPack(prepared.target(), prepared.sha1(), prepared.size()));
    }

    private void publish(HostedPack prepared) {
        HostedPack published = hostedPacks.putIfAbsent(prepared.sha1(), prepared);
        HostedPack current = published == null ? prepared : published;
        try {
            Files.setLastModifiedTime(current.file(), FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException exception) {
            logger.warning("无法更新 SELFHOST 缓存时间，但资源包仍可使用：" + exception.getMessage());
        }
        pruneCache(current.sha1());
    }

    synchronized void discardPrepared(PreparedPack prepared) {
        if (prepared.temporary() != null) {
            deleteTemporaryFile(prepared.temporary());
        }
    }

    private static String digestSha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 不可用", exception);
        }
    }

    private void validateTemporaryPath(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        String name = normalized.getFileName().toString();
        if (!cacheDirectory.equals(normalized.getParent())
                || !name.matches("\\.[0-9a-f]{40}-.+\\.zip\\.tmp")
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("无效的 SELFHOST 临时缓存路径：" + normalized);
        }
    }

    private void deleteTemporaryFile(Path file) {
        try {
            validateTemporaryPath(file);
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            logger.warning("清理 SELFHOST 临时缓存失败：" + exception.getMessage());
        }
    }

    private void pruneCache(String currentSha1) {
        ArrayList<CachedFile> managed = new ArrayList<>();
        try (var files = Files.list(cacheDirectory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!MANAGED_CACHE_FILE.matcher(name).matches()
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(file)) {
                    continue;
                }
                FileTime modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
                managed.add(new CachedFile(file, name.substring(0, 40), modified));
            }
        } catch (IOException exception) {
            logger.warning("扫描 SELFHOST 缓存失败，已跳过本轮清理：" + exception.getMessage());
            return;
        }
        managed.sort(Comparator.comparing(CachedFile::modified)
                .thenComparing(entry -> entry.file().getFileName().toString()));

        int removeCount = Math.max(0, managed.size() - MAX_CACHED_PACKS);
        for (CachedFile entry : managed) {
            if (removeCount == 0) {
                break;
            }
            if (entry.sha1().equals(currentSha1)) {
                continue;
            }
            HostedPack published = hostedPacks.remove(entry.sha1());
            if (published == null) {
                deleteManagedFile(entry.file());
            } else if (published.retire()) {
                deleteRetiredIfUnpublished(published);
            }
            removeCount--;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        boolean acquired;
        try {
            acquired = requestSlots.tryAcquire(REQUEST_QUEUE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            try (exchange) {
                exchange.getResponseHeaders().set("Retry-After", "1");
                exchange.sendResponseHeaders(503, -1);
            }
            return;
        }
        try (exchange) {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            if (!requestPath.startsWith(DOWNLOAD_PREFIX) || !requestPath.endsWith(".zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String sha1 = requestPath.substring(DOWNLOAD_PREFIX.length(), requestPath.length() - 4);
            if (!sha1.matches("[0-9a-f]{40}")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            HostedPack current = hostedPacks.get(sha1);
            if (current == null || !current.acquire()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            try {
                if (!Files.isRegularFile(current.file(), LinkOption.NOFOLLOW_LINKS)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                exchange.getResponseHeaders().set("ETag", '"' + current.sha1() + '"');
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                if (method.equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Long.toString(current.size()));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, current.size());
                try (OutputStream response = exchange.getResponseBody()) {
                    Files.copy(current.file(), response);
                }
            } finally {
                if (current.release()) {
                    deleteRetiredIfUnpublished(current);
                }
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "发送义体资源包失败：" + exception.getMessage());
            throw exception;
        } finally {
            requestSlots.release();
        }
    }

    private void deleteManagedFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        String name = normalized.getFileName().toString();
        if (!cacheDirectory.equals(parent) || !MANAGED_CACHE_FILE.matcher(name).matches()
                || Files.isSymbolicLink(normalized)) {
            logger.warning("已拒绝清理非 BcCyberware 管理的缓存路径：" + normalized);
            return;
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException exception) {
            logger.warning("清理旧 SELFHOST 缓存失败，将在下次生成时重试：" + exception.getMessage());
        }
    }

    private synchronized void deleteRetiredIfUnpublished(HostedPack retired) {
        if (hostedPacks.get(retired.sha1()) == null) {
            deleteManagedFile(retired.file());
        }
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        for (DrainingServer draining : drainingServers) {
            try {
                draining.server().stop(0);
            } catch (RuntimeException exception) {
                logger.fine("旧 SELFHOST 监听已结束：" + exception.getMessage());
            }
            if (draining.executor() != null) {
                draining.executor().shutdownNow();
            }
        }
        drainingServers.clear();
        activeBindAddress = null;
        activePort = -1;
        hostedPacks.clear();
    }

    private static final class HostedPack {
        private final Path file;
        private final String sha1;
        private final long size;
        private int readers;
        private boolean retired;

        private HostedPack(Path file, String sha1, long size) {
            this.file = file;
            this.sha1 = sha1;
            this.size = size;
        }

        Path file() {
            return file;
        }

        String sha1() {
            return sha1;
        }

        long size() {
            return size;
        }

        synchronized boolean acquire() {
            if (retired) {
                return false;
            }
            readers++;
            return true;
        }

        synchronized boolean release() {
            readers--;
            return retired && readers == 0;
        }

        synchronized boolean retire() {
            retired = true;
            return readers == 0;
        }
    }

    record PreparedPack(Path target, Path temporary, String sha1, long size) {
    }

    private record CachedFile(Path file, String sha1, FileTime modified) {
    }

    private record DrainingServer(HttpServer server, ExecutorService executor) {
    }
}
