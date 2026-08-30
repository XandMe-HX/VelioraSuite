package id.velioragardens.veliorasuite.module.warp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WarpCommand implements CommandExecutor, TabCompleter {
    private final WarpManager manager;

    public WarpCommand(WarpManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("vgwarp")) {
            if (!(sender instanceof Player player)) return true;
            manager.teleport(player, label);
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                sender.sendMessage(manager.color(manager.message("list", "%prefix% &7Warp: &f%warps%")
                        .replace("%warps%", String.join(", ", manager.warpNames()))));
            } else help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("set", "delete", "list", "info", "alias", "import", "reload", "help").contains(sub)) {
            if (!(sender instanceof Player player)) return true;
            manager.teleport(player, sub);
            return true;
        }
        if (sub.equals("help")) { help(sender); return true; }
        if (sub.equals("list")) {
            sender.sendMessage(manager.color(manager.message("list", "%prefix% &7Warp: &f%warps%")
                    .replace("%warps%", manager.warpNames().isEmpty() ? "-" : String.join(", ", manager.warpNames()))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command admin warp harus dijalankan oleh player.");
            return true;
        }
        if (sub.equals("reload")) {
            if (!manager.hasReload(player)) return noPermission(player);
            manager.load();
            player.sendMessage(manager.color(manager.message("reload", "%prefix% &aKonfigurasi dan data warp dimuat ulang.")));
            return true;
        }
        if (!manager.hasAdmin(player)) return noPermission(player);

        if (sub.equals("import")) {
            if (args.length != 2 || !args[1].equalsIgnoreCase("essentials")) { player.sendMessage(manager.color("&cGunakan /vgwarp import essentials")); return true; }
            WarpManager.ImportResult result = manager.importEssentialsWarps();
            if (!result.issue().isBlank()) player.sendMessage(manager.color("&e" + result.issue()));
            else player.sendMessage(manager.color("&aImport Essentials selesai: &f" + result.added() + " &aditambah, &e" + result.skipped() + " &edilewati, &c" + result.invalid() + " &cworld tidak tersedia."));
            return true;
        }

        if (sub.equals("set")) {
            if (args.length < 2 || !manager.setWarp(player, args[1])) {
                player.sendMessage(manager.color(manager.message("invalid-name", "%prefix% &cNama warp harus 2-24 karakter: huruf kecil, angka, _ atau -.")));
                return true;
            }
            player.sendMessage(manager.color(manager.message("set", "%prefix% &aWarp &f%warp% &aberhasil diset.")
                    .replace("%warp%", args[1].toLowerCase(Locale.ROOT))));
            if (manager.hasExternalCommand(args[1])) {
                player.sendMessage(manager.color("&eCommand langsung /" + args[1] + " dipakai plugin lain; gunakan /vgwarp " + args[1] + "."));
            }
            return true;
        }
        if (sub.equals("delete")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                player.sendMessage(manager.color("&cGunakan /vgwarp delete <nama> confirm"));
                return true;
            }
            boolean deleted = manager.deleteWarp(args[1]);
            player.sendMessage(manager.color(deleted ? manager.message("deleted", "%prefix% &aWarp berhasil dihapus.")
                    : manager.message("not-found", "%prefix% &cWarp tidak ditemukan: &f%warp%").replace("%warp%", args[1])));
            return true;
        }
        if (sub.equals("info")) {
            if (args.length < 2) { player.sendMessage(manager.color("&cGunakan /vgwarp info <nama>")); return true; }
            WarpManager.WarpPoint point = manager.get(args[1]);
            if (point == null) { manager.teleport(player, "__missing__"); return true; }
            player.sendMessage(manager.color("&8[&bVelioraWarp&8] &f" + point.name()
                    + " &7world=&f" + point.worldName()
                    + " &7xyz=&f" + String.format(Locale.US, "%.1f %.1f %.1f", point.x(), point.y(), point.z())
                    + " &7alias=&f" + (point.aliases().isEmpty() ? "-" : String.join(",", point.aliases()))));
            return true;
        }
        if (sub.equals("alias")) {
            if (args.length < 4) {
                player.sendMessage(manager.color("&cGunakan /vgwarp alias <add|remove> <warp> <alias>"));
                return true;
            }
            boolean success = args[1].equalsIgnoreCase("add")
                    ? manager.addAlias(args[2], args[3])
                    : args[1].equalsIgnoreCase("remove") && manager.removeAlias(args[2], args[3]);
            player.sendMessage(manager.color(success ? "&aAlias warp diperbarui." : "&cAlias gagal: nama bentrok, tidak valid, atau tidak ditemukan."));
            return true;
        }
        help(sender);
        return true;
    }

    private boolean noPermission(Player player) {
        player.sendMessage(manager.color(manager.message("no-permission", "%prefix% &cKamu tidak punya izin.")));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(manager.color("&8&m---------- &bVelioraWarp &8&m----------"));
        sender.sendMessage(manager.color("&f/vgwarp <nama> &7- teleport"));
        sender.sendMessage(manager.color("&f/vgwarp set <nama> &7- simpan lokasi"));
        sender.sendMessage(manager.color("&f/vgwarp delete <nama> confirm"));
        sender.sendMessage(manager.color("&f/vgwarp list &8| &finfo <nama>"));
        sender.sendMessage(manager.color("&f/vgwarp alias <add|remove> <warp> <alias>"));
        sender.sendMessage(manager.color("&f/vgwarp import essentials &7- salin warp Essentials sekali arah"));
        sender.sendMessage(manager.color("&f/vgwarp reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("vgwarp")) return List.of();
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("set", "delete", "list", "info", "alias", "import", "reload", "help"));
            values.addAll(manager.warpNames());
            return values.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("delete", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            return manager.warpNames().stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("alias")) return List.of("add", "remove");
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) return List.of("essentials");
        if (args.length == 3 && args[0].equalsIgnoreCase("alias")) return new ArrayList<>(manager.warpNames());
        if (args.length == 3 && args[0].equalsIgnoreCase("delete")) return List.of("confirm");
        return List.of();
    }
}
