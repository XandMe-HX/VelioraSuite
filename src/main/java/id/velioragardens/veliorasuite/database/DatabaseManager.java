package id.velioragardens.veliorasuite.database;

import id.velioragardens.veliorasuite.VelioraSuite;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {

    private final VelioraSuite plugin;
    private Connection connection;

    public DatabaseManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String storageType = plugin.getConfig().getString("storage.type", "sqlite");

        if (!storageType.equalsIgnoreCase("sqlite")) {
            plugin.getLogger().warning("Storage selain SQLite belum diaktifkan. Menggunakan SQLite.");
        }

        try {
            File databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "database.db"));
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

            this.connection = DriverManager.getConnection(url);
            plugin.getLogger().info("SQLite database connected.");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Gagal terhubung ke SQLite database: " + exception.getMessage());
        }
    }

    public void disconnect() {
        if (connection == null) {
            return;
        }

        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Gagal menutup database: " + exception.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }
}
