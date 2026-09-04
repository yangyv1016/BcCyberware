package cn.bilicraft.bccyberware.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfHostedPackServerTest {
    @TempDir
    Path directory;

    @Test
    void keepsOnlyFourManagedGenerationsAndPreservesUnknownFiles() throws Exception {
        Path source = directory.resolve("resource_pack.zip");
        Path cache = directory.resolve("cache");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve("keep-me.zip"), "server owner file");

        String latestSha1 = null;
        try (SelfHostedPackServer server = new SelfHostedPackServer(Logger.getAnonymousLogger(), cache)) {
            for (int value = 0; value < 6; value++) {
                Files.writeString(source, "test generation " + value);
                latestSha1 = sha1(source);
                server.publishPrepared(server.prepare(generated(source)));
            }
        }

        try (var files = Files.list(cache)) {
            assertEquals(4, files.filter(path -> path.getFileName().toString().matches("[0-9a-f]{40}\\.zip")).count());
        }
        assertTrue(Files.isRegularFile(cache.resolve("keep-me.zip")));
        assertTrue(Files.isRegularFile(cache.resolve(latestSha1 + ".zip")));
    }

    @Test
    void failedEndpointReplacementDoesNotPublishCandidateOrBreakActivePack() throws Exception {
        Path source = directory.resolve("resource_pack.zip");
        byte[] activeBytes = "active pack".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, activeBytes);
        String activeSha1 = sha1(source);
        ResourcePackGenerator.GeneratedResourcePack active = generated(source);

        int livePort = findFreePort();
        try (SelfHostedPackServer server = new SelfHostedPackServer(
                Logger.getAnonymousLogger(), directory.resolve("cache"))) {
            server.startPrepared("127.0.0.1", livePort, server.prepare(active));
            Files.writeString(source, "candidate pack");
            String candidateSha1 = sha1(source);
            ResourcePackGenerator.GeneratedResourcePack candidate = generated(source);

            try (ServerSocket occupied = new ServerSocket(0)) {
                assertThrows(IOException.class,
                        () -> server.startPrepared(
                                "127.0.0.1", occupied.getLocalPort(), server.prepare(candidate)));
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<byte[]> activeResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + livePort
                            + SelfHostedPackServer.downloadPath(activeSha1))).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            HttpResponse<byte[]> candidateResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + livePort
                            + SelfHostedPackServer.downloadPath(candidateSha1))).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, activeResponse.statusCode());
            assertArrayEquals(activeBytes, activeResponse.body());
            assertEquals(404, candidateResponse.statusCode());
            assertTrue(Files.notExists(directory.resolve("cache").resolve(candidateSha1 + ".zip")));
        }
    }

    @Test
    void staleServiceDiscardCannotDeletePackPublishedByNewService() throws Exception {
        Path source = directory.resolve("resource_pack.zip");
        Path cache = directory.resolve("cache");
        Files.writeString(source, "same generation");
        ResourcePackGenerator.GeneratedResourcePack generated = generated(source);

        try (SelfHostedPackServer oldService = new SelfHostedPackServer(Logger.getAnonymousLogger(), cache);
             SelfHostedPackServer newService = new SelfHostedPackServer(Logger.getAnonymousLogger(), cache)) {
            SelfHostedPackServer.PreparedPack stale = oldService.prepare(generated);
            newService.publishPrepared(newService.prepare(generated));
            oldService.discardPrepared(stale);

            assertTrue(Files.isRegularFile(cache.resolve(generated.sha1Hex() + ".zip")));
        }
    }

    private ResourcePackGenerator.GeneratedResourcePack generated(Path source) throws Exception {
        String sha1 = sha1(source);
        return new ResourcePackGenerator.GeneratedResourcePack(
                source,
                HexFormat.of().parseHex(sha1),
                sha1,
                Files.size(source)
        );
    }

    private static String sha1(Path source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
