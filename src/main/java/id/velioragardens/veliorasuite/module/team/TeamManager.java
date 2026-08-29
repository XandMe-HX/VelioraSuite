package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.team.model.Team;
import id.velioragardens.veliorasuite.module.team.model.TeamInvite;
import id.velioragardens.veliorasuite.module.team.model.TeamMember;
import id.velioragardens.veliorasuite.module.team.model.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<UUID> chatSpy = ConcurrentHashMap.newKeySet();
    private TeamGuiManager guiManager;
    private final Map<UUID, Long> activityScoreCooldown = new HashMap<>();

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
        dataManager.shutdown();
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

    public void setGuiManager(TeamGuiManager guiManager) { this.guiManager = guiManager; }
    public void openGui(Player player) { if (guiManager != null) guiManager.openMain(player); }
    public Team getPlayerTeam(UUID playerId) { return dataManager.getTeamByPlayer(playerId); }

    public void sendAdminHelp(CommandSender sender) {
        sendLines(sender, List.of(
                "&8&m--------------------------------",
                "&c&lAdmin Team Veliora",
                "&f/teama reload &7- Memuat ulang konfigurasi team.",
                "&f/teama invite <team> <player> &7- Mengirim undangan team.",
                "&f/teama join <team> <player> &7- Memasukkan player ke team.",
                "&f/teama leave <player> &7- Mengeluarkan player dari team.",
                "&f/teama promote <player> &7- Member menjadi admin team.",
                "&f/teama demote <player> &7- Admin menjadi member team.",
                "&f/teama setowner <player> &7- Menjadikan member sebagai owner.",
                "&f/teama disband <team> &7- Membubarkan team.",
                "&7Pengaturan skor, saldo, tag, warna, dan chatspy masuk Progress 3 bersama GUI.",
                "&8&m--------------------------------"), Map.of());
    }

    public void sendAdminUsage(CommandSender sender) {
        send(sender, "admin-invalid-usage", "%prefix% &cFormat salah. Lihat &f/teama help&c.", Map.of());
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
        celebrate(player, Particle.END_ROD, Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public void invite(Player inviter, Player target) {
        if (!checkEnabled(inviter)) return;

        Team team = requireTeam(inviter);
        if (team == null) return;

        if (!canManageMembers(team, inviter.getUniqueId())) {
            send(inviter, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of());
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
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65F, 1.2F);
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
        celebrate(player, Particle.HAPPY_VILLAGER, Sound.ENTITY_PLAYER_LEVELUP);
    }

    public void leave(Player player) {
        Team team = requireTeam(player);
        if (team == null) return;

        if (team.isOwner(player.getUniqueId())) {
            send(player, "owner-must-transfer", "%prefix% &cOwner tidak dapat keluar langsung. Pindahkan owner terlebih dahulu dengan &f/team setowner <team> <player>&c.", teamPlaceholders(team, Map.of()));
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

    public void teamInfo(Player player, String query) {
        Team team = query == null || query.isBlank() ? requireTeam(player) : dataManager.getTeam(query);
        if (team == null) { if (query != null && !query.isBlank()) send(player, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", query)); return; }
        send(player, "team-info", "%prefix% &b%team% &7Owner: &f%owner% &7Member: &f%members%/%max_members% &7Status: %open%", teamPlaceholders(team, Map.of("%open%", team.isOpen() ? "&aTERBUKA" : "&eUNDANGAN")));
    }

    public void disbandOwnedTeam(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!team.isOwner(player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; }
        notifyTeam(team, "team-disbanded", "%prefix% &cTeam &f%team% &ctelah dibubarkan oleh owner.", teamPlaceholders(team, Map.of()), player.getUniqueId());
        dataManager.deleteTeam(team);
        send(player, "team-disbanded", "%prefix% &cTeam &f%team% &ctelah dibubarkan.", teamPlaceholders(team, Map.of()));
    }

    public void joinOpenTeam(Player player, String teamName) {
        if (dataManager.getTeamByPlayer(player.getUniqueId()) != null) { send(player, "already-in-team", "%prefix% &cKamu sudah punya team.", Map.of()); return; }
        Team team = dataManager.getTeam(teamName);
        if (team == null) { send(player, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", teamName)); return; }
        if (team.isBanned(player.getUniqueId())) { send(player, "team-banned", "%prefix% &cKamu dilarang bergabung ke team ini.", teamPlaceholders(team, Map.of())); return; }
        if (!team.isOpen()) { send(player, "team-closed", "%prefix% &cTeam ini hanya menerima anggota melalui undangan.", teamPlaceholders(team, Map.of())); return; }
        if (team.isFull()) { send(player, "team-full", "%prefix% &cTeam sudah penuh.", Map.of()); return; }
        team.addMember(new TeamMember(player.getUniqueId(), player.getName(), TeamRole.MEMBER, now()));
        team.setLastActive(now()); dataManager.saveTeam(team);
        send(player, "accept-success", "%prefix% &aKamu bergabung ke team &f%team%&a.", teamPlaceholders(team, Map.of()));
        notifyTeam(team, "member-joined", "%prefix% &f%player% &abergabung ke team.", Map.of("%player%", player.getName()), player.getUniqueId());
    }

    public void setTeamHome(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!canManageMembers(team, player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of()); return; }
        team.setHome(player.getLocation()); dataManager.saveTeam(team);
        send(player, "team-home-set", "%prefix% &aHome team berhasil ditetapkan.", teamPlaceholders(team, Map.of()));
    }

    public void deleteTeamHome(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!canManageMembers(team, player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of()); return; }
        if (!team.hasHome()) { send(player, "team-home-missing", "%prefix% &cTeam belum memiliki home.", Map.of()); return; }
        team.clearHome(); dataManager.saveTeam(team);
        send(player, "team-home-deleted", "%prefix% &aHome team berhasil dihapus.", teamPlaceholders(team, Map.of()));
    }

    public void teleportTeamHome(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!team.hasHome() || team.getHome() == null) { send(player, "team-home-missing", "%prefix% &cTeam belum memiliki home yang tersedia.", Map.of()); return; }
        player.teleportAsync(team.getHome()).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) send(player, "team-home-teleported", "%prefix% &aKamu berhasil menuju home team &f%team%&a.", teamPlaceholders(team, Map.of()));
            else send(player, "team-home-failed", "%prefix% &cTeleport ke home team gagal.", Map.of());
        }));
    }

    public void toggleTeamChat(Player player) {
        if (requireTeam(player) == null) return;
        boolean enabled = chatManager.toggle(player);
        send(player, "team-chat-toggle", "%prefix% &aMode chat team: %state%&a.", Map.of("%state%", enabled ? "&aAKTIF" : "&cNONAKTIF"));
    }

    public boolean isTeamChatMode(Player player) { return player != null && dataManager.getTeamByPlayer(player.getUniqueId()) != null && chatManager.isEnabled(player.getUniqueId()); }

    public void toggleTeamPvp(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!canManageMembers(team, player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of()); return; }
        team.setPvpEnabled(!team.isPvpEnabled()); dataManager.saveTeam(team);
        send(player, "team-pvp-toggle", "%prefix% &aPvP antar anggota team: %state%&a.", teamPlaceholders(team, Map.of("%state%", team.isPvpEnabled() ? "&aAKTIF" : "&cNONAKTIF")));
    }

    public void toggleTeamOpen(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        if (!team.isOwner(player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; }
        team.setOpen(!team.isOpen()); dataManager.saveTeam(team);
        send(player, "team-open-toggle", "%prefix% &aStatus masuk team: %state%&a.", teamPlaceholders(team, Map.of("%state%", team.isOpen() ? "&aTERBUKA" : "&eUNDANGAN SAJA")));
    }

    public void manageMember(Player actor, String targetName, String action) {
        Team team = requireTeam(actor); if (team == null) return;
        if (!canManageMembers(team, actor.getUniqueId())) { send(actor, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of()); return; }
        UUID targetId = dataManager.findMemberUuid(team, targetName);
        if (targetId == null) { send(actor, "target-not-member", "%prefix% &cPlayer itu bukan member team ini.", Map.of()); return; }
        TeamMember target = team.getMembers().get(targetId);
        TeamRole actorRole = team.getRole(actor.getUniqueId());
        if (targetId.equals(actor.getUniqueId()) || !actorRole.isHigherThan(target.getRole())) { send(actor, "role-protected", "%prefix% &cKamu tidak dapat mengelola anggota dengan jabatan setara atau lebih tinggi.", Map.of()); return; }
        switch (action) {
            case "kick" -> { team.removeMember(targetId); chatManager.disable(targetId); send(actor, "member-kicked", "%prefix% &a%player% dikeluarkan dari team.", Map.of("%player%", target.getName())); }
            case "ban" -> { team.removeMember(targetId); team.ban(targetId); chatManager.disable(targetId); send(actor, "member-banned", "%prefix% &a%player% dilarang bergabung kembali ke team ini.", Map.of("%player%", target.getName())); }
            case "promote" -> { if (!team.isOwner(actor.getUniqueId())) { send(actor, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; } target.setRole(TeamRole.ADMIN); send(actor, "member-promoted", "%prefix% &a%player% sekarang menjadi admin team.", Map.of("%player%", target.getName())); }
            case "demote" -> { if (!team.isOwner(actor.getUniqueId())) { send(actor, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; } target.setRole(TeamRole.MEMBER); send(actor, "member-demoted", "%prefix% &a%player% sekarang menjadi member team.", Map.of("%player%", target.getName())); }
            default -> { return; }
        }
        team.setLastActive(now()); dataManager.saveTeam(team);
    }

    public void unbanMember(Player actor, String targetName) {
        Team team = requireTeam(actor); if (team == null) return;
        if (!canManageMembers(team, actor.getUniqueId())) { send(actor, "only-owner", "%prefix% &cHanya owner atau admin team yang bisa melakukan ini.", Map.of()); return; }
        UUID targetId = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        if (!team.isBanned(targetId)) { send(actor, "target-not-banned", "%prefix% &cPlayer tersebut tidak diban dari team ini.", Map.of()); return; }
        team.unban(targetId); dataManager.saveTeam(team);
        send(actor, "member-unbanned", "%prefix% &a%player% boleh bergabung lagi ke team.", Map.of("%player%", targetName));
    }

    public void setTeamText(Player player, String type, String value) {
        Team team = requireTeam(player); if (team == null) return;
        if (!team.isOwner(player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; }
        if (value == null || value.isBlank() || value.length() > 48) { send(player, "invalid-team-text", "%prefix% &cTeks tidak valid atau terlalu panjang.", Map.of()); return; }
        if (type.equals("description")) team.setDescription(value);
        else if (type.equals("tag")) team.setTag(value.replace("&", ""));
        else if (type.equals("color") && value.matches("&[0-9a-fk-orA-FK-OR]")) team.setColor(value.toLowerCase(Locale.ROOT));
        else { send(player, "invalid-team-text", "%prefix% &cNilai tidak valid.", Map.of()); return; }
        dataManager.saveTeam(team);
        send(player, "team-text-updated", "%prefix% &aPengaturan team berhasil diperbarui.", teamPlaceholders(team, Map.of()));
    }

    public void renameOwnedTeam(Player player, String newName) {
        Team team = requireTeam(player); if (team == null) return;
        if (!team.isOwner(player.getUniqueId())) { send(player, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of()); return; }
        String value = newName == null ? "" : newName.trim();
        if (!value.matches("^[A-Z]+$") || !configManager.getTeamNamePattern().matcher(value).matches() || dataManager.teamExists(value)) {
            send(player, "team-name-invalid", "%prefix% &cNama team tidak valid atau sudah dipakai.", Map.of()); return;
        }
        if (!dataManager.renameTeam(team, value)) { send(player, "team-name-invalid", "%prefix% &cNama team tidak dapat diubah.", Map.of()); return; }
        send(player, "team-renamed", "%prefix% &aNama team berhasil diubah menjadi &f%team%&a.", teamPlaceholders(team, Map.of()));
    }

    public boolean isFriendlyFireBlocked(Player attacker, Player victim) {
        Team first = dataManager.getTeamByPlayer(attacker.getUniqueId());
        return first != null && first == dataManager.getTeamByPlayer(victim.getUniqueId()) && !first.isPvpEnabled();
    }

    public void sendFriendlyFireBlocked(Player player) { send(player, "team-friendly-fire", "%prefix% &cPvP antar anggota team sedang dinonaktifkan.", Map.of()); }

    public void teamBalance(Player player) {
        Team team = requireTeam(player); if (team == null) return;
        send(player, "team-balance", "%prefix% &7Saldo team &f%team%&7: &a$%balance%", teamPlaceholders(team, Map.of("%balance%", formatPrice(team.getBalance()))));
    }

    /** Safe team progression: invoked only by vetted sources such as AuraSkills gathering XP and completed guild quests. */
    public void addActivityScore(Player player, long amount, String source) {
        if (player == null || amount <= 0L) return;
        Team team = dataManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;
        long now = System.currentTimeMillis();
        long previous = activityScoreCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 2_000L) return;
        activityScoreCooldown.put(player.getUniqueId(), now);
        long gained = Math.min(5L, amount);
        team.setScore(team.getScore() + gained);
        team.setLastActive(this.now());
        dataManager.saveTeam(team);
        if (team.getScore() % 25L < gained) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 8, 0.25, 0.35, 0.25, 0.01);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.35F);
            send(player, "team-score-gained", "%prefix% &aTeam memperoleh &e%amount% skor &adari %source%.", Map.of("%amount%", String.valueOf(gained), "%source%", source));
        }
    }

    public void addQuestScore(Team team, long amount) {
        if (team == null || amount <= 0L) return;
        team.setScore(team.getScore() + amount);
        team.setLastActive(now());
        dataManager.saveTeam(team);
    }

    public void rankings(CommandSender sender, boolean balance) {
        List<Team> teams = dataManager.getTeams().stream().sorted(balance ? Comparator.comparingDouble(Team::getBalance).reversed() : Comparator.comparingLong(Team::getScore).reversed()).limit(10).toList();
        sender.sendMessage(configManager.color("&8&m--------------------------------"));
        sender.sendMessage(configManager.color(balance ? "&6&lTeam Terkaya" : "&b&lPeringkat Team"));
        int rank = 1;
        for (Team team : teams) sender.sendMessage(configManager.color("&7#" + rank++ + " &f" + team.getDisplayName() + " &8- &e" + (balance ? "$" + formatPrice(team.getBalance()) : team.getScore())));
        sender.sendMessage(configManager.color("&8&m--------------------------------"));
    }

    public void teamChat(Player player, String message) {
        Team team = requireTeam(player);
        if (team == null) return;
        chatManager.sendTeamChat(player, team, message);
        for (UUID spyId : chatSpy) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy != null && spy.isOnline() && !team.isMember(spyId)) spy.sendMessage(configManager.color("&8[&cSpy " + team.getDisplayName() + "&8] &f" + player.getName() + "&7: &f" + message));
        }
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
        celebrate(player, Particle.TOTEM_OF_UNDYING, Sound.UI_TOAST_CHALLENGE_COMPLETE);
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

    public void toggleChatSpy(Player player) {
        if (chatSpy.remove(player.getUniqueId())) send(player, "chat-spy", "%prefix% &cTeam chat spy dinonaktifkan.", Map.of());
        else { chatSpy.add(player.getUniqueId()); send(player, "chat-spy", "%prefix% &aTeam chat spy diaktifkan.", Map.of()); }
    }

    public void setChatSpy(Player player, boolean enabled) {
        if (enabled) chatSpy.add(player.getUniqueId()); else chatSpy.remove(player.getUniqueId());
        send(player, "chat-spy", enabled ? "%prefix% &aTeam chat spy diaktifkan." : "%prefix% &cTeam chat spy dinonaktifkan.", Map.of());
    }

    public void adminCreate(CommandSender sender, String name) {
        String value = name == null ? "" : name.trim();
        if (!value.matches("^[A-Z]+$") || !configManager.getTeamNamePattern().matcher(value).matches() || dataManager.teamExists(value)) { send(sender, "team-name-invalid", "%prefix% &cNama team tidak valid atau sudah dipakai.", Map.of()); return; }
        Team team = new Team(dataManager.nextId(), value, value, null, "-", configManager.getDefaultMaxMembers(), false, now(), now());
        dataManager.saveTeam(team);
        send(sender, "admin-created", "%prefix% &aTeam kosong &f%team% &aberhasil dibuat.", teamPlaceholders(team, Map.of()));
    }

    public void adminSetText(CommandSender sender, String teamName, String type, String value) {
        Team team = getTeamOrMessage(sender, teamName); if (team == null) return;
        if (value == null || value.isBlank() || value.length() > 48) { send(sender, "invalid-team-text", "%prefix% &cTeks tidak valid atau terlalu panjang.", Map.of()); return; }
        if (type.equals("description")) team.setDescription(value);
        else if (type.equals("tag")) team.setTag(value.replace("&", ""));
        else if (type.equals("color") && value.matches("&[0-9a-fk-orA-FK-OR]")) team.setColor(value.toLowerCase(Locale.ROOT));
        else { send(sender, "invalid-team-text", "%prefix% &cNilai tidak valid.", Map.of()); return; }
        dataManager.saveTeam(team); send(sender, "team-text-updated", "%prefix% &aPengaturan team berhasil diperbarui.", teamPlaceholders(team, Map.of()));
    }

    public void adminRename(CommandSender sender, String oldName, String newName) {
        Team team = getTeamOrMessage(sender, oldName); if (team == null) return;
        String value = newName == null ? "" : newName.trim();
        if (!value.matches("^[A-Z]+$") || !configManager.getTeamNamePattern().matcher(value).matches() || dataManager.teamExists(value) || !dataManager.renameTeam(team, value)) { send(sender, "team-name-invalid", "%prefix% &cNama team tidak valid atau sudah dipakai.", Map.of()); return; }
        send(sender, "team-renamed", "%prefix% &aNama team berhasil diubah menjadi &f%team%&a.", teamPlaceholders(team, Map.of()));
    }

    public void adminSetRank(CommandSender sender, String teamName, int rank) {
        Team team = getTeamOrMessage(sender, teamName); if (team == null) return;
        team.setRank(rank); dataManager.saveTeam(team);
        send(sender, "admin-rank", "%prefix% &aRank team &f%team% &adiatur menjadi &f%rank%&a.", teamPlaceholders(team, Map.of("%rank%", String.valueOf(team.getRank()))));
    }

    public void adminChangeNumber(CommandSender sender, String operation, String subject, String target, double amount, boolean money) {
        Team team = dataManager.getTeam(target);
        if (team == null && subject.equalsIgnoreCase("player")) team = dataManager.getTeamByMemberName(target);
        if (team == null) { send(sender, "team-not-found", "%prefix% &cTeam atau player tidak ditemukan.", Map.of("%team%", target)); return; }
        if (amount < 0D || Double.isNaN(amount) || Double.isInfinite(amount)) { send(sender, "invalid-team-text", "%prefix% &cNilai tidak valid.", Map.of()); return; }
        if (money) {
            double next = operation.equals("set") ? amount : operation.equals("add") ? team.getBalance() + amount : Math.max(0D, team.getBalance() - amount);
            team.setBalance(next);
        } else {
            long raw = Math.round(amount);
            long next = operation.equals("set") ? raw : operation.equals("add") ? team.getScore() + raw : Math.max(0L, team.getScore() - raw);
            team.setScore(next);
        }
        dataManager.saveTeam(team);
        send(sender, "admin-number", "%prefix% &aData &f%team% &aberhasil diperbarui.", teamPlaceholders(team, Map.of()));
    }

    public void adminPurgeScores(CommandSender sender) {
        for (Team team : dataManager.getTeams()) { team.setScore(0L); dataManager.saveTeam(team); }
        send(sender, "admin-purge", "%prefix% &aSemua skor team direset ke 0.", Map.of());
    }

    public void transferOwner(Player owner, Player target) {
        Team team = requireTeam(owner);
        if (team == null) return;
        if (!team.isOwner(owner.getUniqueId())) {
            send(owner, "only-owner", "%prefix% &cHanya owner team yang bisa melakukan ini.", Map.of());
            return;
        }
        if (target == null || !team.isMember(target.getUniqueId())) {
            send(owner, "target-not-member", "%prefix% &cPlayer itu bukan member team ini.", Map.of());
            return;
        }
        TeamMember oldOwner = team.getMembers().get(owner.getUniqueId());
        TeamMember newOwner = team.getMembers().get(target.getUniqueId());
        oldOwner.setRole(TeamRole.ADMIN);
        newOwner.setRole(TeamRole.OWNER);
        team.setOwnerUuid(target.getUniqueId());
        team.setOwnerName(target.getName());
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(owner, "setowner-success", "%prefix% &aOwner team &f%team% &aberhasil dipindahkan ke &f%player%&a.", teamPlaceholders(team, Map.of("%player%", target.getName())));
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

    public void adminInvite(CommandSender sender, String teamName, Player target) {
        Team team = getTeamOrMessage(sender, teamName);
        if (team == null || target == null) {
            if (target == null) send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-"));
            return;
        }
        if (dataManager.getTeamByPlayer(target.getUniqueId()) != null) {
            send(sender, "target-already-in-team", "%prefix% &cPlayer itu sudah punya team.", Map.of());
            return;
        }
        long expiresAt = System.currentTimeMillis() + (configManager.getInviteTimeoutSeconds() * 1000L);
        inviteManager.createInvite(new TeamInvite(team.getName(), target.getUniqueId(), target.getName(), null, sender.getName(), expiresAt));
        send(sender, "invite-sent", "%prefix% &aInvite team dikirim ke &f%player%&a.", teamPlaceholders(team, Map.of("%player%", target.getName())));
        send(target, "invite-received", "%prefix% &aKamu diundang ke team &f%team%&a. Ketik &f/team accept &auntuk bergabung.", teamPlaceholders(team, Map.of()));
    }

    public void forceJoin(CommandSender sender, String teamName, Player target) {
        Team team = getTeamOrMessage(sender, teamName);
        if (team == null || target == null) {
            if (target == null) send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-"));
            return;
        }
        Team oldTeam = dataManager.getTeamByPlayer(target.getUniqueId());
        if (oldTeam != null && oldTeam.isOwner(target.getUniqueId())) {
            send(sender, "admin-owner-protected", "%prefix% &cOwner harus memindahkan owner terlebih dahulu.", teamPlaceholders(oldTeam, Map.of()));
            return;
        }
        if (team.isFull()) { send(sender, "team-full", "%prefix% &cTeam sudah penuh.", Map.of()); return; }
        if (oldTeam != null) { oldTeam.removeMember(target.getUniqueId()); dataManager.saveTeam(oldTeam); }
        team.addMember(new TeamMember(target.getUniqueId(), target.getName(), TeamRole.MEMBER, now()));
        team.setLastActive(now());
        dataManager.saveTeam(team);
        send(sender, "admin-force-join", "%prefix% &a%player% dimasukkan ke team &f%team%&a.", teamPlaceholders(team, Map.of("%player%", target.getName())));
        send(target, "accept-success", "%prefix% &aKamu bergabung ke team &f%team%&a.", teamPlaceholders(team, Map.of()));
    }

    public void forceLeave(CommandSender sender, Player target) {
        if (target == null) { send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-")); return; }
        Team team = dataManager.getTeamByPlayer(target.getUniqueId());
        if (team == null) { send(sender, "not-in-team", "%prefix% &cPlayer tersebut belum punya team.", Map.of()); return; }
        if (team.isOwner(target.getUniqueId())) { send(sender, "admin-owner-protected", "%prefix% &cOwner tidak bisa dikeluarkan. Pindahkan owner dahulu.", teamPlaceholders(team, Map.of())); return; }
        team.removeMember(target.getUniqueId());
        dataManager.saveTeam(team);
        send(sender, "admin-force-leave", "%prefix% &a%player% dikeluarkan dari team &f%team%&a.", teamPlaceholders(team, Map.of("%player%", target.getName())));
    }

    public void changeRole(CommandSender sender, Player target, boolean promote) {
        if (target == null) { send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-")); return; }
        Team team = dataManager.getTeamByPlayer(target.getUniqueId());
        TeamMember member = team == null ? null : team.getMembers().get(target.getUniqueId());
        if (member == null) { send(sender, "target-not-member", "%prefix% &cPlayer itu bukan member team.", Map.of()); return; }
        if (team.isOwner(target.getUniqueId())) { send(sender, "role-owner-protected", "%prefix% &cRole owner hanya dipindahkan memakai &f/teama setowner <player>&c.", teamPlaceholders(team, Map.of())); return; }
        member.setRole(promote ? TeamRole.ADMIN : TeamRole.MEMBER);
        dataManager.saveTeam(team);
        send(sender, "admin-role-changed", "%prefix% &aRole %player% diubah menjadi &f%role%&a.", teamPlaceholders(team, Map.of("%player%", target.getName(), "%role%", promote ? "ADMIN" : "MEMBER")));
    }

    public void setOwnerForMember(CommandSender sender, Player target) {
        if (target == null) { send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", "-")); return; }
        Team team = dataManager.getTeamByPlayer(target.getUniqueId());
        if (team == null) { send(sender, "target-not-member", "%prefix% &cPlayer itu bukan member team.", Map.of()); return; }
        setOwner(sender, team.getName(), target);
    }

    private void celebrate(Player player, Particle particle, Sound sound) {
        player.getWorld().spawnParticle(particle, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.42D, 0.6D, 0.42D, 0.025D);
        player.playSound(player.getLocation(), sound, 0.7F, 1.15F);
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

    public boolean canManageMembers(Team team, UUID playerId) {
        TeamRole role = team == null ? null : team.getRole(playerId);
        return role != null && role.canManageMembers();
    }

    private boolean validateTeamName(Player player, String name) {
        if (!name.matches("^[A-Z]+$")) {
            send(player, "team-name-invalid", "%prefix% &cNama team tidak valid. Gunakan huruf besar A-Z saja, tanpa angka/simbol/emoji. Contoh: &fSHDW", Map.of());
            return false;
        }
        if (name.length() < configManager.getMinTeamNameLength()) {
            send(player, "team-name-too-short", "%prefix% &cNama team terlalu pendek. Minimal &f%min% &churuf.", Map.of("%min%", String.valueOf(configManager.getMinTeamNameLength())));
            return false;
        }
        if (name.length() > configManager.getMaxTeamNameLength()) {
            send(player, "team-name-too-long", "%prefix% &cNama team terlalu panjang. Maksimal &f%max% &churuf.", Map.of("%max%", String.valueOf(configManager.getMaxTeamNameLength())));
            return false;
        }
        if (!configManager.getTeamNamePattern().matcher(name).matches()) {
            send(player, "team-name-invalid", "%prefix% &cNama team tidak valid. Gunakan huruf besar A-Z saja, tanpa angka/simbol/emoji. Contoh: &fSHDW", Map.of());
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

    private Team getTeamOrMessage(CommandSender sender, String name) {
        Team team = dataManager.getTeam(name);
        if (team == null) {
            send(sender, "team-not-found", "%prefix% &cTeam &f%team% &ctidak ditemukan.", Map.of("%team%", name));
        }
        return team;
    }

    private boolean charge(Player player, double amount, String messageKey, String fallback) {
        if (amount <= 0) return true;
        if (player.hasPermission(configManager.getBypassCostPermission())) return true;

        // 1. Cek apakah uangnya cukup
        if (!economyManager.hasEnough(player, amount)) {
            send(player, messageKey, fallback, Map.of("%cost%", formatPrice(amount)));
            return false;
        }

        // 2. POTONG UANGNYA DI SINI! (Ini yang krusial)
        if (!economyManager.withdraw(player, amount)) {
            player.sendMessage(configManager.color(configManager.getPrefix() + "&cGagal memproses penarikan uang dari Vault. Silakan hubungi staff."));
            return false;
        }

        return true;
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
