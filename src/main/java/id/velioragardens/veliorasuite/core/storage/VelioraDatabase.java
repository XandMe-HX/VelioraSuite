package id.velioragardens.veliorasuite.core.storage;

import id.velioragardens.veliorasuite.VelioraSuite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared SQLite access point for runtime data. Configuration stays in YAML;
 * player/runtime data can migrate module-by-module without mixed database files.
 */
public final class VelioraDatabase {
    private static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final VelioraSuite plugin;
    private final File databaseFile;
    private final ExecutorService executor;
    private volatile boolean available;

    public VelioraDatabase(VelioraSuite plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databaseFile = new File(plugin.getDataFolder(), "database/veliora.db");
        this.executor = Executors.newSingleThreadExecutor(new DatabaseThreadFactory());
    }

    public void initialize() {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Database VelioraSuite gagal membuat folder database.");
            return;
        }
        boolean firstDatabase = !databaseFile.exists();
        if (firstDatabase) backupLegacyYaml();

        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("CREATE TABLE IF NOT EXISTS veliorasuite_meta (key_name TEXT PRIMARY KEY, value_text TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS veliorasuite_module_state (module_id TEXT PRIMARY KEY, state_text TEXT NOT NULL, updated_at INTEGER NOT NULL)");
            migrate(connection);
            available = true;
            plugin.getLogger().info("SQLite VelioraSuite siap: database/veliora.db (WAL aktif).");
        } catch (SQLException exception) {
            available = false;
            plugin.getLogger().severe("SQLite VelioraSuite tidak aktif. Data YAML tetap digunakan. " + exception.getMessage());
        }
    }

    public boolean isAvailable() { return available; }
    public File getDatabaseFile() { return databaseFile; }

    public CompletableFuture<Void> executeAsync(Consumer<Connection> action) {
        return CompletableFuture.runAsync(() -> {
            if (!available) return;
            try (Connection connection = open()) {
                action.accept(connection);
            } catch (SQLException exception) {
                throw new IllegalStateException("Database VelioraSuite gagal menulis", exception);
            }
        }, executor);
    }

    public <T> CompletableFuture<T> queryAsync(Function<Connection, T> query) {
        return CompletableFuture.supplyAsync(() -> {
            if (!available) return null;
            try (Connection connection = open()) {
                return query.apply(connection);
            } catch (SQLException exception) {
                throw new IllegalStateException("Database VelioraSuite gagal membaca", exception);
            }
        }, executor);
    }

    public void shutdown() {
        available = false;
        executor.shutdown();
    }

    /** Startup-only read for a module's runtime state. */
    public String loadModuleStateNow(String moduleId) {
        if (!available) return null;
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT state_text FROM veliorasuite_module_state WHERE module_id = ?")) {
            statement.setString(1, moduleId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        } catch (SQLException exception) {
            plugin.getLogger().warning("SQLite: gagal membaca data " + moduleId + ": " + exception.getMessage());
            return null;
        }
    }

    public CompletableFuture<Void> saveModuleStateAsync(String moduleId, String state) {
        return executeAsync(connection -> saveModuleState(connection, moduleId, state));
    }

    /** Final write at shutdown; it avoids losing the last buffered snapshot. */
    public void saveModuleStateNow(String moduleId, String state) {
        if (!available) return;
        try (Connection connection = open()) { saveModuleState(connection, moduleId, state); }
        catch (SQLException exception) { plugin.getLogger().warning("SQLite: gagal menyimpan data " + moduleId + ": " + exception.getMessage()); }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private void migrate(Connection connection) throws SQLException {
        int installed = schemaVersion(connection);
        if (installed > SCHEMA_VERSION) {
            throw new SQLException("Database dibuat oleh VelioraSuite versi schema lebih baru: " + installed);
        }
        if (installed == 0) {
            // Tables are intentionally empty in foundation phase. Each module
            // adds its own migration before it starts reading from SQLite.
            setMeta(connection, "schema_version", Integer.toString(SCHEMA_VERSION));
            setMeta(connection, "created_at", Long.toString(System.currentTimeMillis()));
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value_text FROM veliorasuite_meta WHERE key_name = 'schema_version'");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return 0;
            try { return Integer.parseInt(result.getString(1)); }
            catch (NumberFormatException ignored) { return 0; }
        }
    }

    private void setMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO veliorasuite_meta(key_name, value_text) VALUES(?, ?) ON CONFLICT(key_name) DO UPDATE SET value_text = excluded.value_text")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void saveModuleState(Connection connection, String moduleId, String state) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO veliorasuite_module_state(module_id, state_text, updated_at) VALUES(?, ?, ?) ON CONFLICT(module_id) DO UPDATE SET state_text = excluded.state_text, updated_at = excluded.updated_at")) {
            statement.setString(1, moduleId);
            statement.setString(2, state == null ? "" : state);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) { throw new IllegalStateException("Gagal menyimpan state modul " + moduleId, exception); }
    }

    private void backupLegacyYaml() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.isDirectory()) return;
        List<File> legacyFiles = new ArrayList<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) for (File file : files) if (file.isFile()) legacyFiles.add(file);
        if (legacyFiles.isEmpty()) return;

        File backupFolder = new File(plugin.getDataFolder(), "database/backups/legacy-yaml-" + BACKUP_TIME.format(LocalDateTime.now()));
        if (!backupFolder.mkdirs()) {
            plugin.getLogger().warning("SQLite: backup YAML awal gagal dibuat; migrasi data nanti tidak boleh dijalankan sebelum backup tersedia.");
            return;
        }
        try {
            for (File legacy : legacyFiles) {
                Files.copy(legacy.toPath(), new File(backupFolder, legacy.getName()).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            plugin.getLogger().info("SQLite: backup data YAML dibuat di database/backups/ sebelum migrasi.");
        } catch (IOException exception) {
            plugin.getLogger().warning("SQLite: sebagian backup data YAML gagal: " + exception.getMessage());
        }
    }

    private static final class DatabaseThreadFactory implements ThreadFactory {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "VelioraSuite-SQLite");
            thread.setDaemon(true);
            return thread;
        }
    }
}
