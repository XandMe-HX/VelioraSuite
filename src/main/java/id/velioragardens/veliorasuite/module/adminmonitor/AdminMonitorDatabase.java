package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminMonitorDatabase {
    private final VelioraSuite plugin;
    private final File file;

    public AdminMonitorDatabase(VelioraSuite plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "adminmonitor.db");
    }

    public void init() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Gagal membuat folder data VelioraSuite.");
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS adminmonitor_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    time INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    clock TEXT NOT NULL,
                    player TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    world TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER
                )
                """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_adminmonitor_date_time ON adminmonitor_logs(date, time DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_adminmonitor_player_date ON adminmonitor_logs(player, date)");
        } catch (SQLException exception) {
            plugin.getLogger().warning("Gagal menyiapkan SQLite AdminMonitor: " + exception.getMessage());
        }
    }

    public void insertBatch(List<Map<String, Object>> entries) throws SQLException {
        if (entries.isEmpty()) return;
        String sql = """
            INSERT INTO adminmonitor_logs
            (time, date, clock, player, uuid, type, detail, world, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Map<String, Object> entry : entries) {
                statement.setLong(1, number(entry.get("time")));
                statement.setString(2, text(entry.get("date")));
                statement.setString(3, text(entry.get("clock")));
                statement.setString(4, text(entry.get("player")));
                statement.setString(5, text(entry.get("uuid")));
                statement.setString(6, text(entry.get("type")));
                statement.setString(7, text(entry.get("detail")));
                statement.setString(8, nullableText(entry.get("world")));
                setNullableInt(statement, 9, entry.get("x"));
                setNullableInt(statement, 10, entry.get("y"));
                setNullableInt(statement, 11, entry.get("z"));
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }

    public List<Map<?, ?>> entries(LocalDate date) {
        return query("SELECT * FROM adminmonitor_logs WHERE date = ? ORDER BY time DESC LIMIT 250", date.toString());
    }

    public List<Map<?, ?>> entries(LocalDate date, String player) {
        return query("SELECT * FROM adminmonitor_logs WHERE date = ? AND LOWER(player) = LOWER(?) ORDER BY time DESC LIMIT 250", date.toString(), player);
    }

    public void prune(LocalDate cutoff) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement("DELETE FROM adminmonitor_logs WHERE date < ?")) {
            statement.setString(1, cutoff.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Gagal menghapus log AdminMonitor lama: " + exception.getMessage());
        }
    }

    private List<Map<?, ?>> query(String sql, String... values) {
        List<Map<?, ?>> entries = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("time", result.getLong("time"));
                    entry.put("date", result.getString("date"));
                    entry.put("clock", result.getString("clock"));
                    entry.put("player", result.getString("player"));
                    entry.put("uuid", result.getString("uuid"));
                    entry.put("type", result.getString("type"));
                    entry.put("detail", result.getString("detail"));
                    String world = result.getString("world");
                    if (world != null) {
                        entry.put("world", world);
                        entry.put("x", result.getInt("x"));
                        entry.put("y", result.getInt("y"));
                        entry.put("z", result.getInt("z"));
                    }
                    entries.add(entry);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Gagal membaca log AdminMonitor: " + exception.getMessage());
        }
        return entries;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    private static void setNullableInt(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Number number) statement.setInt(index, number.intValue());
        else statement.setObject(index, null);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
