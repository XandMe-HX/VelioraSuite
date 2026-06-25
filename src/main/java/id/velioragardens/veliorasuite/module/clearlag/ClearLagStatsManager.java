package id.velioragardens.veliorasuite.module.clearlag;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;

public final class ClearLagStatsManager {

    public String getMemoryLine() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory() / 1024L / 1024L;
        long total = runtime.totalMemory() / 1024L / 1024L;
        long free = runtime.freeMemory() / 1024L / 1024L;
        long used = total - free;
        return "Used: " + used + "MB | Free: " + free + "MB | Max: " + max + "MB";
    }

    public String getTpsLine() {
        try {
            Method method = Bukkit.class.getMethod("getTPS");
            Object value = method.invoke(null);
            if (value instanceof double[] tps && tps.length > 0) {
                return String.format("%.2f, %.2f, %.2f", tps[0], tps.length > 1 ? tps[1] : tps[0], tps.length > 2 ? tps[2] : tps[0]);
            }
        } catch (ReflectiveOperationException ignored) {
            // Paper exposes TPS; fallback for APIs that do not.
        }
        return "TPS tidak tersedia di API server ini.";
    }
}
