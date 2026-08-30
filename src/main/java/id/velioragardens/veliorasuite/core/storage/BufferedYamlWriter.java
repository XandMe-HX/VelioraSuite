package id.velioragardens.veliorasuite.core.storage;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/** Batches frequently-changing YAML data and moves disk I/O off the server thread. */
public final class BufferedYamlWriter {
    private static final long AUTOSAVE_TICKS = 20L * 60L;

    private final VelioraSuite plugin;
    private final File file;
    private final FileConfiguration data;
    private final String displayName;
    private final Object fileLock = new Object();
    private final AtomicBoolean writing = new AtomicBoolean(false);
    private BukkitTask autosaveTask;
    private boolean dirty;

    public BufferedYamlWriter(VelioraSuite plugin, File file, FileConfiguration data, String displayName) {
        this.plugin = plugin;
        this.file = file;
        this.data = data;
        this.displayName = displayName;
    }

    public void start() {
        stopTask();
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushAsync, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushAsync() {
        if (!dirty || !writing.compareAndSet(false, true)) return;
        String snapshot = data.saveToString();
        dirty = false;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (fileLock) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null) Files.createDirectories(parent.toPath());
                    Files.writeString(file.toPath(), snapshot, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    plugin.getLogger().warning("Gagal menyimpan " + displayName);
                    plugin.getServer().getScheduler().runTask(plugin, () -> dirty = true);
                } finally {
                    writing.set(false);
                }
            }
        });
    }

    public void shutdown() {
        stopTask();
        if (!dirty && !writing.get()) return;
        synchronized (fileLock) {
            try {
                data.save(file);
                dirty = false;
            } catch (IOException exception) {
                plugin.getLogger().warning("Gagal menyimpan " + displayName);
            }
        }
    }

    private void stopTask() {
        if (autosaveTask != null) autosaveTask.cancel();
        autosaveTask = null;
    }
}
