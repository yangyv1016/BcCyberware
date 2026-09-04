package cn.bilicraft.bccyberware.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsProfileAndInstalledItemBlobs() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (SqliteRepository repository = new SqliteRepository(directory.resolve("players.db"))) {
            repository.initialize();
            StoredProfile empty = repository.load(playerId, "Tester").get();
            assertFalse(empty.initialized());
            assertTrue(empty.installedItems().isEmpty());

            byte[] item = new byte[]{1, 2, 3, 4};
            repository.save(playerId, "Tester", 12.5, true, Map.of("core:brain-core", item)).get();

            StoredProfile loaded = repository.load(playerId, "ChangedName").get();
            assertTrue(loaded.initialized());
            assertEquals("Tester", loaded.lastName());
            assertEquals(12.5, loaded.permanentCapacity(), 0.000_001);
            assertArrayEquals(item, loaded.installedItems().get("core:brain-core"));
        }
    }
}

