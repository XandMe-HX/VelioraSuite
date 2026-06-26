package id.velioragardens.veliorasuite.module.boss;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BossDamageTracker {

    private final Map<UUID, Double> damage = new LinkedHashMap<>();

    public void add(Player player, double amount) {
        if (player == null || amount <= 0.0D) return;
        damage.merge(player.getUniqueId(), amount, Double::sum);
    }

    public void clear() { damage.clear(); }
    public double get(UUID uuid) { return damage.getOrDefault(uuid, 0.0D); }

    public List<Entry> top() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : damage.entrySet()) entries.add(new Entry(entry.getKey(), entry.getValue()));
        entries.sort(Comparator.comparingDouble(Entry::damage).reversed());
        return entries;
    }

    public String topText(int limit) {
        List<Entry> entries = top();
        if (entries.isEmpty()) return "-";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            Entry entry = entries.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            String name = player == null ? entry.uuid().toString().substring(0, 8) : player.getName();
            builder.append(i + 1).append(". ").append(name).append(" - ").append(String.format("%.1f", entry.damage())).append(" dmg");
            if (i + 1 < Math.min(limit, entries.size())) builder.append("\n");
        }
        return builder.toString();
    }

    public record Entry(UUID uuid, double damage) {}
}
