package id.velioragardens.veliorasuite.module.security;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SecurityCommand implements CommandExecutor, TabCompleter {

    private final SecurityManager manager;

    public SecurityCommand(SecurityManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("vxray") || label.equalsIgnoreCase("vxray") || label.equalsIgnoreCase("vorewatch") || label.equalsIgnoreCase("orecheck")) {
            return handleOreCommand(sender, args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!manager.getConfigManager().hasAdmin(sender)) {
                manager.sendNoPermission(sender);
                return true;
            }
            manager.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!manager.getConfigManager().hasAdmin(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendStatus(sender);
                return true;
            }
            case "alerts" -> {
                if (!manager.getConfigManager().hasAlerts(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendAlerts(sender);
                return true;
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            default -> {
                manager.sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleOreCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            manager.sendOreHelp(sender);
            sender.sendMessage("§7Tambahan: §f/vxray clear-log <no|all> §7hapus alert yang sudah dilihat.");
            sender.sendMessage("§7Review banding: §f/vxray review <player> §7tampilkan check + logs sekaligus.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> manager.sendOreStatus(sender);
            case "alerts" -> manager.sendOreAlerts(sender);
            case "suspects", "top" -> manager.sendOreSuspects(sender);
            case "allreport" -> manager.sendOreAllReport(sender);
            case "review", "evidence", "bukti" -> {
                if (args.length < 2) sender.sendMessage("§8[§cVelioraOreWatch§8] §cGunakan §f/vxray review <player>§c.");
                else {
                    sender.sendMessage("§8&m--------------------------------");
                    sender.sendMessage("§c§lVelioraOreWatch Review");
                    sender.sendMessage("§7Data ini untuk mencocokkan cerita player dengan hasil sistem.");
                    sender.sendMessage("§8&m--------------------------------");
                    manager.sendOreCheck(sender, args[1]);
                    manager.sendOreLogs(sender, args[1]);
                    sender.sendMessage("§8&m--------------------------------");
                    sender.sendMessage("§ePertanyaan banding yang disarankan:");
                    sender.sendMessage("§7- World dan lokasi mining terakhir di mana?");
                    sender.sendMessage("§7- Pakai tools/enchant apa?");
                    sender.sendMessage("§7- Ada beacon/potion atau tidak?");
                    sender.sendMessage("§7- Mining sendiri atau bersama siapa?");
                    sender.sendMessage("§7- Rute mining dari mana ke mana?");
                    sender.sendMessage("§8&m--------------------------------");
                }
            }
            case "clear-log", "clearlog", "clear" -> {
                if (args.length < 2) sender.sendMessage("§8[§cVelioraOreWatch§8] §cGunakan §f/vxray clear-log <no|all>§c. Nomor mengikuti urutan §f/vxray alerts§c dari atas ke bawah.");
                else clearOreAlert(sender, args[1]);
            }
            case "check" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.sendOreCheck(sender, args[1]);
            }
            case "logs" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.sendOreLogs(sender, args[1]);
            }
            case "reset" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.resetOre(sender, args[1]);
            }
            case "exempt" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.exemptOre(sender, args[1], true);
            }
            case "unexempt" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.exemptOre(sender, args[1], false);
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) manager.sendNoPermission(sender);
                else {
                    manager.reload();
                    manager.sendReloadSuccess(sender);
                }
            }
            default -> manager.sendOreHelp(sender);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void clearOreAlert(CommandSender sender, String input) {
        if (!manager.getConfigManager().hasAdmin(sender)) {
            manager.sendNoPermission(sender);
            return;
        }
        try {
            Field field = SecurityManager.class.getDeclaredField("oreAlerts");
            field.setAccessible(true);
            List<Object> alerts = (List<Object>) field.get(manager);
            if (input.equalsIgnoreCase("all")) {
                int amount = alerts.size();
                alerts.clear();
                sender.sendMessage("§8[§cVelioraOreWatch§8] §aBerhasil menghapus §f" + amount + " §aalert yang sudah dilihat.");
                return;
            }
            int number;
            try {
                number = Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§8[§cVelioraOreWatch§8] §cNomor tidak valid. Contoh: §f/vxray clear-log 1§c.");
                return;
            }
            List<Object> sorted = new ArrayList<>(alerts);
            sorted.sort(Comparator.comparingInt(this::scoreOf).reversed());
            if (number < 1 || number > sorted.size()) {
                sender.sendMessage("§8[§cVelioraOreWatch§8] §cAlert nomor §f" + number + " §ctidak ada. Cek §f/vxray alerts§c dulu.");
                return;
            }
            Object removed = sorted.get(number - 1);
            alerts.remove(removed);
            sender.sendMessage("§8[§cVelioraOreWatch§8] §aAlert nomor §f" + number + " §auntuk §f" + nameOf(removed) + " §asudah dihapus.");
        } catch (Exception exception) {
            sender.sendMessage("§8[§cVelioraOreWatch§8] §cGagal menghapus alert: §f" + exception.getMessage());
        }
    }

    private int scoreOf(Object report) {
        try {
            Method method = report.getClass().getDeclaredMethod("score");
            method.setAccessible(true);
            return (int) method.invoke(report);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String nameOf(Object report) {
        try {
            Method method = report.getClass().getDeclaredMethod("name");
            method.setAccessible(true);
            return String.valueOf(method.invoke(report));
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("vxray") || alias.equalsIgnoreCase("vxray") || alias.equalsIgnoreCase("vorewatch") || alias.equalsIgnoreCase("orecheck")) {
            if (!manager.getConfigManager().hasAdmin(sender)) return new ArrayList<>();
            if (args.length == 1) return filter(new ArrayList<>(Arrays.asList("help", "status", "check", "logs", "review", "evidence", "suspects", "top", "alerts", "allreport", "clear-log", "reset", "exempt", "unexempt", "reload")), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("clear-log")) return filter(new ArrayList<>(Arrays.asList("1", "2", "3", "all")), args[1]);
            return new ArrayList<>();
        }

        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>();
        if (manager.getConfigManager().hasAdmin(sender)) options.addAll(Arrays.asList("help", "status"));
        if (manager.getConfigManager().hasAlerts(sender)) options.add("alerts");
        if (manager.getConfigManager().hasReload(sender)) options.add("reload");
        return filter(options, args[0]);
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) result.add(option);
        }
        return result;
    }
}
