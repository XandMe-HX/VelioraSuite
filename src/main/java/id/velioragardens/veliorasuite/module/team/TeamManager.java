package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.team.model.Team;
import id.velioragardens.veliorasuite.module.team.model.TeamInvite;
import id.velioragardens.veliorasuite.module.team.model.TeamMember;
import id.velioragardens.veliorasuite.module.team.model.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TeamManager {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VelioraSuite plugin;
    private final TeamConfigManager configManager;
    private final TeamDataManager dataManager;
    private final TeamEconomyManager economyManager;
    private final TeamInviteManager inviteManager;
    private final TeamChatManager chatManager;
    private final TeamUpgradeManager upgradeManager;
    private final TeamTagManager tagManager;
    private final Map<UUID, Long> ownerLeaveConfirmations = new HashMap<>();

    public TeamManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new TeamConfigManager(plugin);
        this.dataManager = new TeamDataManager(plugin);
        this.economyManager = new TeamEconomyManager(plugin);
        this.inviteManager = new TeamInviteManager();
        this.chatManager = new TeamChatManager(configManager);
        this.upgradeManager = new TeamUpgradeManager(configManager);
        this.tagManager = new TeamTagManager(configManager, dataManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        economyManager.load();
        plugin.getLogger().info("VelioraTeam loaded with " + dataManager.getTeams().size() + " team(s).");
    }

    public void reload() {
        configManager.load();
        economyManager.load();
    }

    public void shutdown() {
        inviteManager.clear();
        ownerLeaveConfirmations.clear();
    }

    public TeamConfigManager getConfigManager() {
        return configManager;
    }

    public TeamDataManager getDataManager() {
        return dataManager;
    }

    public TeamTagManager getTagManager() {
        return tagManager;
    }

    public boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission(configManager.getUsePermission()) || hasAdminPermission(sender);
    }

    public boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getAdminPermission()) || sender.isOp();
    }

    public boolean hasReloadPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getReloadPermission()) || hasAdminPermission(sender);
    }

    public boolean hasSetOwnerPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getSetOwnerPermission()) || hasAdminPermission(sender);
    }

    public boolean hasDeletePermission(CommandSender sender) {
        return sender.hasPermission(configManager.getDeletePermission()) || hasAdminPermission(sender);
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraTeam",
                "&f/team create <nama> &7- Membuat team.",
                "&f/team invite <player> &7- Invite player.",
                "&f/team accept &7- Terima invite.",
                "&f/team leave &7- Keluar dari team.",
                "&f/team list &7- Lihat daftar team.",
                "&f/team chat <pesan> &7- Chat khusus team.",
                "&f/team upgrade &7- Upgrade kapasitas team.",
                "&f/team setowner <team> <player> &7- Pindah owner team.",
                "&f/team delete <team> &7- Hapus team. Admin only.",
                "&f/team info <team> &7- Lihat detail team. Admin only.",
                "&f/team reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void createTeam(Player player, String rawName) {
        if (!checkEnabled(player)) return;

        if (dataManager.getTeamByPlayer(player.getUniqueId()) != null) {
            send(player, "already-in-team", "%prefix% &cKamu sudah punya team.", Map.of());
            return;
        }

        String name = rawName == null ? "" : rawName.trim();
        if (!validateTeamName(player, name)) return;

        double cost = configManager.getCreateCost();
        if (!charge(player, cost, "not-enough-money-create", "%prefix% &cUang kamu kurang untuk membuat team. Butuh &f$%cost%&c.")) return;

        int id = dataManager.nextId();
        Team team = new Team(id, name, name, player.getUniqueId(), player.getName(), configManager.getDefaultMaxMembers(), false, now(), now());
        team.addMember(new TeamMember(player.getUniqueId(), player.getName(), TeamRole.OWNER, now()));
        dataManager.saveTeam(team);
        send(player, "create-success", "%prefix% &aTeam &f%team% &aberhasil dibuat. Biaya: &f$%cost%&a.", teamPlaceholders(team, Map.of("%cost%", formatPrice(cost))));
    }

    public void invite(Player inviter, Player target) {
        if (!checkEnabled(inviter)) return;

        Team team = requireTeam(inviter);
        if (team == null) return;

        if (!team.isOwner(inviter.getUniqueId())) {
            send(inviter, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of());
            return;
        }

        if (target == null) {
            send(inviter, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-"));
            return;
        }

        if (dataManager.getTeamByPlayer(target.getUniqueId()) != null) {
            send(inviter, "target-already-in-team", "%prefix% &cPlayer itu sudah punya team.", Map.of());
            return;
        }

        if (team.isFull() && !inviter.hasPermission(configManager.getBypassLimitPermission()) && !hasAdminPermission(inviter)) {
            send(inviter, "team-full", "%prefix% &cTeam sudah penuh.", Map.of());
            return;
        }

        long expiresAt = System.currentTimeMillis() + (configManager.getInviteTimeoutSeconds() * 1000L);
        inviteManager.createInvite(new TeamInvite(team.getName(), target.getUniqueId(), target.getName(), inviter.getUniqueId(), inviter.getName(), expiresAt));
        send(inviter, "invite-sent", "%prefix% &aInvite team dikirim ke &f%player%&a.", Map.of("%player%", target.getName(), "%team%", team.getDisplayName()));
        send(target, "invite-received", "%prefix% &aKamu diundang ke team &f%team%&a. Ketik &f/team accept &auntuk bergabung.", Map.of("%team%", team.getDisplayName(), "%player%", inviter.getName()));
    }

    public void acceptInvite(Player player) {
        if (!checkEnabled(player)) return;

        if (dataManager.getTeamByPlayer(player.getUniqueId()) != null) {
            send(player, "already-in-team", "%prefix% &cKamu sudah punya team.", Map.of());
            return;
        }

        TeamInvite invite = inviteManager.removeInvite(player.getUniqueId());
        if (invite == null) {
            send(player, "no-invite", "%prefix% &cKamu tidak punya invite team.", Map.of());
            return;
        }

        if (invite.isExpired()) {
            send(player, "invite-expired", "%prefix% &cInvite team sudah expired.", Map.of());
            return;
        }

        Team team = dataManager.getTeam(invite.getTeamName());
        if (team == null) {
            send(player, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", invite.getTeamName()));
            return;
        }

        if (team.isFull() && !player.hasPermission(configManager.getBypassLimitPermission()) && !hasAdminPermission(player)) {
            send(player, "team-full", "%prefix% &cTeam sudah penuh.", Map.of());
            return;
        }

        team.addMember(new TeamMember(player.getUniqueId(), player.getName(), TeamRole.MEMBER, now()));
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(player, "accept-success", "%prefix% &aKamu bergabung ke team &f%team%&a.", teamPlaceholders(team, Map.of()));
        notifyTeam(team, "member-joined", "%prefix% &f%player% &abergabung ke team.", Map.of("%player%", player.getName()), player.getUniqueId());
    }

    public void leave(Player player, boolean confirm) {
        Team team = requireTeam(player);
        if (team == null) return;

        if (team.isOwner(player.getUniqueId())) {
            handleOwnerLeave(player, team, confirm);
            return;
        }

        team.removeMember(player.getUniqueId());
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(player, "leave-success", "%prefix% &aKamu keluar dari team &f%team%&a.", teamPlaceholders(team, Map.of()));
    }

    public void listTeams(CommandSender sender) {
        sendLines(sender, configManager.getFormatList("list-header", List.of("&8&m--------------------------------", "&b&lDaftar Team")), Map.of());

        String format = configManager.getFormat("list-format", "&7- &b%team% &8| &7Owner: &f%owner% &8| &7Member: &f%members%&7/&f%max_members% &8| &7Upgrade: &f%upgraded%");
        for (Team team : dataManager.getTeams()) {
            sender.sendMessage(configManager.color(apply(format, teamPlaceholders(team, Map.of()))));
        }

        sendLines(sender, configManager.getFormatList("list-footer", List.of("&8&m--------------------------------")), Map.of());
    }

    public void teamChat(Player player, String message) {
        Team team = requireTeam(player);
        if (team == null) return;
        chatManager.sendTeamChat(player, team, message);
        team.setLastActive(now());
        dataManager.saveTeam(team);
    }

    public void upgrade(Player player) {
        Team team = requireTeam(player);
        if (team == null) return;

        if (!team.isOwner(player.getUniqueId())) {
            send(player, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of());
            return;
        }

        if (!upgradeManager.canUpgrade(team)) {
            send(player, "already-upgraded", "%prefix% &cTeam kamu sudah mencapai upgrade maksimal.", teamPlaceholders(team, Map.of()));
            return;
        }

        double cost = configManager.getUpgradeCost();
        if (!charge(player, cost, "not-enough-money-upgrade", "%prefix% &cUang kamu kurang untuk upgrade team. Butuh &f$%cost%&c.")) return;

        team.setMaxMembers(upgradeManager.getNextMaxMembers(team));
        team.setUpgraded(true);
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(player, "upgrade-success", "%prefix% &aTeam berhasil diupgrade. Maksimal member sekarang &f%max_members%&a.", teamPlaceholders(team, Map.of("%cost%", formatPrice(cost))));
    }

    public void setOwner(CommandSender sender, String teamName, Player target) {
        if (!hasSetOwnerPermission(sender)) {
            send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
            return;
        }

        Team team = dataManager.getTeam(teamName);
        if (team == null) {
            send(sender, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", teamName));
            return;
        }

        if (target == null) {
            send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-"));
            return;
        }

        if (!team.isMember(target.getUniqueId()) && !configManager.isAllowSetOwnerNonMember()) {
            send(sender, "target-not-member", "%prefix% &cPlayer itu bukan member team.", Map.of("%player%", target.getName(), "%team%", team.getDisplayName()));
            return;
        }

        if (!team.isMember(target.getUniqueId())) {
            if (team.isFull() && !sender.hasPermission(configManager.getBypassLimitPermission()) && !hasAdminPermission(sender)) {
                send(sender, "team-full", "%prefix% &cTeam sudah penuh.", Map.of());
                return;
            }
            team.addMember(new TeamMember(target.getUniqueId(), target.getName(), TeamRole.MEMBER, now()));
        }

        TeamMember oldOwner = team.getMembers().get(team.getOwnerUuid());
        if (oldOwner != null) oldOwner.setRole(TeamRole.MEMBER);

        TeamMember newOwner = team.getMembers().get(target.getUniqueId());
        if (newOwner != null) newOwner.setRole(TeamRole.OWNER);
        team.setOwnerUuid(target.getUniqueId());
        team.setOwnerName(target.getName());
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(sender, "setowner-success", "%prefix% &aOwner team &f%team% &aberhasil dipindahkan ke &f%player%&a.", teamPlaceholders(team, Map.of("%player%", target.getName())));
        send(target, "setowner-received", "%prefix% &aKamu sekarang menjadi owner team &f%team%&a.", teamPlaceholders(team, Map.of()));
    }

    public void deleteTeam(CommandSender sender, String teamName) {
        if (!hasDeletePermission(sender)) {
            send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
            return;
        }

        Team team = dataManager.getTeam(teamName);
        if (team == null) {
            send(sender, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", teamName));
            return;
        }

        dataManager.deleteTeam(team);
        send(sender, "team-deleted", "%prefix% &aTeam &f%team% &aberhasil dihapus.", teamPlaceholders(team, Map.of()));
    }

    public void infoTeam(CommandSender sender, String teamName) {
        if (!hasAdminPermission(sender)) {
            send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
            return;
        }

        Team team = dataManager.getTeam(teamName);
        if (team == null) {
            send(sender, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", teamName));
            return;
        }

        send(sender, "team-info", "%prefix% &b%team% &7Owner: &f%owner% &7Member: &f%members%/%max_members% &7Upgrade: &f%upgraded%", teamPlaceholders(team, Map.of()));
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraTeam berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    public void sendPlayerOnly(CommandSender sender) {
        send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of());
    }

    public void sendUsage(CommandSender sender) {
        send(sender, "invalid-usage", "%prefix% &cGunakan: &f/team help", Map.of());
    }

    private void handleOwnerLeave(Player player, Team team, boolean confirm) {
        if (!confirm) {
            long expires = System.currentTimeMillis() + (configManager.getOwnerLeaveConfirmTimeoutSeconds() * 1000L);
            ownerLeaveConfirmations.put(player.getUniqueId(), expires);
            send(player, "owner-leave-confirm", "%prefix% &cKamu adalah owner. Jika keluar, team akan dihapus. Ketik &f/team leave confirm &cdalam &f%time%s &cuntuk lanjut.", teamPlaceholders(team, Map.of("%time%", String.valueOf(configManager.getOwnerLeaveConfirmTimeoutSeconds()))));
            return;
        }

        long expiresAt = ownerLeaveConfirmations.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() > expiresAt) {
            ownerLeaveConfirmations.remove(player.getUniqueId());
            send(player, "owner-leave-cancelled", "%prefix% &cKonfirmasi keluar owner sudah expired.", Map.of());
            return;
        }

        ownerLeaveConfirmations.remove(player.getUniqueId());
        notifyTeam(team, "team-disbanded", "%prefix% &cTeam &f%team% &ctelah dihapus karena owner keluar.", teamPlaceholders(team, Map.of()), null);
        dataManager.deleteTeam(team);
    }

    private boolean validateTeamName(Player player, String name) {
        if (name.length() < configManager.getMinTeamNameLength()) {
            send(player, "team-name-too-short", "%prefix% &cNama team terlalu pendek. Minimal &f%min% &ckarakter.", Map.of("%min%", String.valueOf(configManager.getMinTeamNameLength())));
            return false;
        }
        if (name.length() > configManager.getMaxTeamNameLength()) {
            send(player, "team-name-too-long", "%prefix% &cNama team terlalu panjang. Maksimal &f%max% &ckarakter.", Map.of("%max%", String.valueOf(configManager.getMaxTeamNameLength())));
            return false;
        }
        if (!configManager.getTeamNamePattern().matcher(name).matches()) {
            send(player, "team-name-invalid", "%prefix% &cNama team tidak valid. Gunakan huruf, angka, atau underscore.", Map.of());
            return false;
        }
        for (String blocked : configManager.getBlockedNames()) {
            if (blocked.equalsIgnoreCase(name)) {
                send(player, "blocked-name", "%prefix% &cNama team ini tidak boleh dipakai.", Map.of());
                return false;
            }
        }
        if (dataManager.teamExists(name)) {
            send(player, "team-name-exists", "%prefix% &cNama team &f%team% &csudah dipakai.", Map.of("%team%", name));
            return false;
        }
        return true;
    }

    private Team requireTeam(Player player) {
        Team team = dataManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team", "%prefix% &cKamu belum punya team.", Map.of());
            return null;
        }
        return team;
    }

    private boolean charge(Player player, double cost, String notEnoughPath, String notEnoughFallback) {
        if (cost <= 0 || player.hasPermission(configManager.getBypassCostPermission()) || hasAdminPermission(player)) {
            return true;
        }
        if (!economyManager.hasEconomy()) {
            send(player, "economy-not-found", "%prefix% &cEconomy/Vault tidak tersedia.", Map.of());
            return false;
        }
        if (!economyManager.hasEnough(player, cost)) {
            send(player, notEnoughPath, notEnoughFallback, Map.of("%cost%", formatPrice(cost)));
            return false;
        }
        return economyManager.withdraw(player, cost);
    }

    private boolean checkEnabled(CommandSender sender) {
        if (!configManager.isEnabled()) {
            send(sender, "disabled", "%prefix% &cModule team sedang dimatikan.", Map.of());
            return false;
        }
        return true;
    }

    private void notifyTeam(Team team, String messagePath, String fallback, Map<String, String> placeholders, UUID except) {
        Map<String, String> finalPlaceholders = teamPlaceholders(team, placeholders);
        for (UUID uuid : team.getMembers().keySet()) {
            if (except != null && except.equals(uuid)) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                send(member, messagePath, fallback, finalPlaceholders);
            }
        }
    }

    private Map<String, String> teamPlaceholders(Team team, Map<String, String> extra) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%team%", team == null ? "-" : team.getDisplayName());
        placeholders.put("%team_id%", team == null ? "-" : String.valueOf(team.getId()));
        placeholders.put("%owner%", team == null ? "-" : team.getOwnerName());
        placeholders.put("%members%", team == null ? "0" : String.valueOf(team.getMembers().size()));
        placeholders.put("%max_members%", team == null ? "0" : String.valueOf(team.getMaxMembers()));
        placeholders.put("%upgraded%", team == null ? "false" : String.valueOf(team.isUpgraded()));
        placeholders.putAll(extra);
        return placeholders;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.getMessage(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            sender.sendMessage(configManager.color(apply(line, placeholders)));
        }
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text.replace("%prefix%", configManager.getPrefix());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
}
