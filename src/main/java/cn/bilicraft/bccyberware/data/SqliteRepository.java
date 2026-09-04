package cn.bilicraft.bccyberware.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SqliteRepository implements AutoCloseable {
    private final Path databasePath;
    private final ExecutorService executor;

    public SqliteRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BcCyberware-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(databasePath.getParent());
        } catch (ClassNotFoundException exception) {
            throw new SQLException("未能加载内置 SQLite JDBC 驱动", exception);
        } catch (Exception exception) {
            throw new SQLException("无法创建数据库目录 " + databasePath.getParent(), exception);
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        player_uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        permanent_capacity REAL NOT NULL DEFAULT 0,
                        initialized INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS installed_parts (
                        player_uuid TEXT NOT NULL,
                        slot_id TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        PRIMARY KEY (player_uuid, slot_id),
                        FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE
                    )
                    """);
        }
    }

    public CompletableFuture<StoredProfile> load(UUID playerId, String currentName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = open()) {
                String lastName = currentName;
                double permanentCapacity = 0.0;
                boolean initialized = false;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT last_name, permanent_capacity, initialized FROM player_profiles WHERE player_uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            lastName = result.getString("last_name");
                            permanentCapacity = result.getDouble("permanent_capacity");
                            initialized = result.getInt("initialized") != 0;
                        }
                    }
                }
                LinkedHashMap<String, byte[]> installed = new LinkedHashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT slot_id, item_data FROM installed_parts WHERE player_uuid = ? ORDER BY slot_id")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            installed.put(result.getString("slot_id"), result.getBytes("item_data"));
                        }
                    }
                }
                return new StoredProfile(playerId, lastName, permanentCapacity, initialized, installed);
            } catch (SQLException exception) {
                throw new RepositoryException("读取玩家 " + playerId + " 的义体数据失败", exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> save(
            UUID playerId,
            String lastName,
            double permanentCapacity,
            boolean initialized,
            Map<String, byte[]> installedItems
    ) {
        Map<String, byte[]> snapshot = new LinkedHashMap<>(installedItems);
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = open()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO player_profiles(player_uuid, last_name, permanent_capacity, initialized, updated_at)
                            VALUES(?, ?, ?, ?, ?)
                            ON CONFLICT(player_uuid) DO UPDATE SET
                                last_name = excluded.last_name,
                                permanent_capacity = excluded.permanent_capacity,
                                initialized = excluded.initialized,
                                updated_at = excluded.updated_at
                            """)) {
                        statement.setString(1, playerId.toString());
                        statement.setString(2, lastName);
                        statement.setDouble(3, permanentCapacity);
                        statement.setInt(4, initialized ? 1 : 0);
                        statement.setLong(5, System.currentTimeMillis());
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM installed_parts WHERE player_uuid = ?")) {
                        statement.setString(1, playerId.toString());
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO installed_parts(player_uuid, slot_id, item_data) VALUES(?, ?, ?)")) {
                        for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
                            statement.setString(1, playerId.toString());
                            statement.setString(2, entry.getKey());
                            statement.setBytes(3, entry.getValue());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException exception) {
                throw new RepositoryException("保存玩家 " + playerId + " 的义体数据失败", exception);
            }
        }, executor);
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public static final class RepositoryException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
