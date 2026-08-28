package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuideModule implements VelioraModule {

    private final VelioraSuite plugin;
    private GuideManager guideManager;
    private boolean enabled;

    public GuideModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "guide";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/guide.yml");
        guideManager = new GuideManager(plugin);
        guideManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerGuideCommand("velioraguide", "guide");
        registerGuideCommand("veliorarules", "rules");
        registerGuideCommand("velioraproduct", "product");
        registerGuideCommand("veliorarank", "rank");
        registerAdminCommand();
    }

    @Override
    public void disable() {
        enabled = false;
        registerDisabledCommand("velioraguide");
        registerDisabledCommand("veliorarules");
        registerDisabledCommand("velioraproduct");
        registerDisabledCommand("veliorarank");
        registerDisabledCommand("cmdadmin");
    }

    @Override
    public void reload() {
        if (guideManager != null) {
            guideManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerGuideCommand(String commandName, String sectionName) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            plugin.getLogger().warning("Command /" + commandName + " tidak ditemukan di plugin.yml.");
            return;
        }

        GuideCommand guideCommand = new GuideCommand(guideManager, sectionName);
        command.setExecutor(guideCommand);
        command.setTabCompleter(guideCommand);
    }

    private void registerAdminCommand() {
        PluginCommand command = plugin.getCommand("cmdadmin");
        if (command == null) {
            plugin.getLogger().warning("Command /cmdadmin tidak ditemukan di plugin.yml.");
            return;
        }
        AdminCommand adminCommand = new AdminCommand();
        command.setExecutor(adminCommand);
        command.setTabCompleter(adminCommand);
    }

    private void registerDisabledCommand(String commandName) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraGuide");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }

    private static final class AdminCommand implements CommandExecutor, TabCompleter {
        private static final int MAX_PAGE = 10;

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("veliorasuite.guide.admin") && !sender.isOp()) {
                send(sender, "&8[&6VelioraAdmin&8] &cKamu tidak punya izin.");
                return true;
            }
            int page = 1;
            if (args.length > 0) {
                try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) { page = 1; }
            }
            page = Math.max(1, Math.min(MAX_PAGE, page));
            sendPage(sender, label, page);
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> result = new ArrayList<>();
            if (args.length != 1) return result;
            if (!sender.hasPermission("veliorasuite.guide.admin") && !sender.isOp()) return result;
            String input = args[0].toLowerCase(Locale.ROOT);
            for (int i = 1; i <= MAX_PAGE; i++) {
                String value = String.valueOf(i);
                if (value.startsWith(input)) result.add(value);
            }
            return result;
        }

        private void sendPage(CommandSender sender, String label, int page) {
            send(sender, "");
            send(sender, "&8&m--------------------------------");
            switch (page) {
                case 1 -> {
                    send(sender, "&6&lAdmin Command Guide");
                    send(sender, "&8- &b/cmdadmin <page> &7- buka panduan admin.");
                    send(sender, "&8- &b/veliorasuite reload &7- reload semua module.");
                    send(sender, "&8- &b/vguide reload &7- reload guide player.");
                    send(sender, "&8- &b/vchat reload &7- reload chat dan AI reply.");
                    send(sender, "&8- &b/vsecurity status &7- cek security server.");
                    send(sender, "&8- &b/valt help &7- panduan AltGuard.");
                    send(sender, "&8- &b/vxray help &7- panduan OreWatch.");
                }
                case 2 -> {
                    send(sender, "&6&lLogin Security");
                    send(sender, "&8- &b/loginsecurity status &7- cek akun login.");
                    send(sender, "&8- &b/loginsecurity reload &7- reload login security.");
                    send(sender, "&8- &b/risetpw <player> &7- reset password player.");
                    send(sender, "&8- &b/cpowner <player> <pass> &7- ubah password player.");
                    send(sender, "&8- &7Jika player stuck gelap: &b/effect clear <player> blindness&7.");
                }
                case 3 -> {
                    send(sender, "&6&lBoss Admin");
                    send(sender, "&8- &b/boss set <nama> &7- set lokasi boss.");
                    send(sender, "&8- &b/boss spawn <boss> &7- spawn boss manual.");
                    send(sender, "&8- &b/boss list &7- list boss tersedia.");
                    send(sender, "&8- &b/boss info <boss> &7- detail boss.");
                    send(sender, "&8- &b/boss top &7- top damage boss aktif.");
                    send(sender, "&8- &b/boss stop &7- hentikan boss aktif.");
                    send(sender, "&8- &b/boss reload &7- reload config boss.");
                }
                case 4 -> {
                    send(sender, "&6&lQuest, Skill, Pet");
                    send(sender, "&8- &b/skills &7- dikelola oleh plugin AuraSkills.");
                    send(sender, "&8- &b/skills &7- buka menu AuraSkills.");
                    send(sender, "&8- &b/pet reload &7- reload pet.");
                    send(sender, "&8- &b/pet give <player> <pet> &7- kasih pet.");
                }
                case 5 -> {
                    send(sender, "&6&lReport dan Moderasi");
                    send(sender, "&8- &b/reports list &7- lihat laporan.");
                    send(sender, "&8- &b/reports view <id> &7- detail laporan.");
                    send(sender, "&8- &b/reports close <id> &7- tutup laporan.");
                    send(sender, "&8- &b/reports reopen <id> &7- buka lagi laporan.");
                    send(sender, "&8- &b/report bug <alasan> &7- test laporan bug.");
                    send(sender, "&8- &7Jika private/penting, arahkan player ke Owner.");
                }
                case 6 -> {
                    send(sender, "&6&lKits, Team, Trader, Fishing");
                    send(sender, "&8- &b/kits reload &7- reload kits.");
                    send(sender, "&8- &b/kits preview <kit> &7- cek isi kit.");
                    send(sender, "&8- &b/team reload &7- reload team.");
                    send(sender, "&8- &b/trader spawn &7- spawn trader.");
                    send(sender, "&8- &b/trader reload &7- reload trader.");
                    send(sender, "&8- &b/fish reload &7- reload fishing.");
                }
                case 7 -> {
                    send(sender, "&6&lLuckPerms Cepat");
                    send(sender, "&8- &b/lp user <player> parent set <rank> &7- set rank.");
                    send(sender, "&8- &b/lp group default permission set <perm> true &7- izin default.");
                    send(sender, "&8- &b/lp user <player> permission set <perm> true &7- izin player.");
                    send(sender, "&8- &b/lp editor &7- buka editor web.");
                    send(sender, "&8- &7Hati-hati jangan kasih &cop&7 sembarang player.");
                }
                case 8 -> {
                    send(sender, "&6&lVelioraAltGuard");
                    send(sender, "&8- &b/valt help &7- panduan akun ganda.");
                    send(sender, "&8- &b/valt list &7- IP dengan 2+ akun.");
                    send(sender, "&8- &b/valt check <player> &7- cek akun utama dan alt.");
                    send(sender, "&8- &b/valt alerts &7- alert akun ganda dan /pay.");
                    send(sender, "&8- &b/valt trust <player> &7- whitelist rumah/keluarga.");
                    send(sender, "&8- &b/valt untrust <player> &7- hapus whitelist.");
                    send(sender, "&8- &7Rule: 1 normal, 2 alert, 3 ban, 5 ban-ip.");
                }
                case 9 -> {
                    send(sender, "&6&lOreWatch / Xray Review");
                    send(sender, "&8- &b/vxray help &7- panduan xray monitor.");
                    send(sender, "&8- &b/vxray alerts &7- alert mining mencurigakan.");
                    send(sender, "&8- &b/vxray review <player> &7- check + logs + pertanyaan.");
                    send(sender, "&8- &b/vxray check <player> &7- angka ore 5/15/60 menit.");
                    send(sender, "&8- &b/vxray logs <player> &7- ore terakhir.");
                    send(sender, "&8- &b/vxray clear-log <no|all> &7- hapus alert selesai.");
                    send(sender, "&8- &7Beli/shop/reward/teman tidak dihitung, hanya mining ore.");
                }
                case 10 -> {
                    send(sender, "&6&lChecklist Admin Saat Ada Masalah");
                    send(sender, "&8- &f1. &7Buka &b/cmdadmin &7untuk command terkait.");
                    send(sender, "&8- &f2. &7Cek &b/valt alerts &7dan &b/vxray alerts&7.");
                    send(sender, "&8- &f3. &7Cek console error merah.");
                    send(sender, "&8- &f4. &7Reload module terkait, jangan /reload server.");
                    send(sender, "&8- &f5. &7Kalau masih error, restart full server.");
                    send(sender, "&8- &f6. &7Kirim log jelas ke Owner/Developer.");
                    send(sender, "&8- &7Admin/OP akan dapat notif cepat saat join.");
                }
                default -> { }
            }
            send(sender, "&8&m--------------------------------");
            send(sender, "&7Page &f" + page + "&7/&f" + MAX_PAGE + " &8| &7Bedrock: &f/" + label + " <page>");
            send(sender, "");
        }

        private static void send(CommandSender sender, String text) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        }
    }
}
