package id.velioragardens.veliorasuite.module.team.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Team {

    private final int id;
    private String name;
    private String displayName;
    private UUID ownerUuid;
    private String ownerName;
    private int maxMembers;
    private boolean upgraded;
    private final String createdAt;
    private String lastActive;
    private String description = "";
    private String tag = "";
    private String color = "&b";
    private final Set<UUID> bannedMembers = new LinkedHashSet<>();
    private boolean open;
    private boolean pvpEnabled;
    private double balance;
    private long score;
    private int rank;
    private String homeWorld = "";
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;
    private final Map<UUID, TeamMember> members = new LinkedHashMap<>();

    public Team(int id, String name, String displayName, UUID ownerUuid, String ownerName, int maxMembers, boolean upgraded, String createdAt, String lastActive) {
        this.id = id;
        this.name = name;
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "Unknown" : ownerName;
        this.maxMembers = Math.max(1, maxMembers);
        this.upgraded = upgraded;
        this.createdAt = createdAt == null || createdAt.isBlank() ? "-" : createdAt;
        this.lastActive = lastActive == null ? "" : lastActive;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? this.ownerName : ownerName;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(1, maxMembers);
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLastActive() {
        return lastActive;
    }

    public void setLastActive(String lastActive) {
        this.lastActive = lastActive == null ? "" : lastActive;
    }

    public Map<UUID, TeamMember> getMembers() {
        return members;
    }

    public boolean isOwner(UUID uuid) {
        return uuid != null && uuid.equals(ownerUuid);
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && members.containsKey(uuid);
    }

    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public String getTag() { return tag.isBlank() ? name : tag; }
    public void setTag(String tag) { this.tag = tag == null ? "" : tag; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color == null || color.isBlank() ? "&b" : color; }
    public Set<UUID> getBannedMembers() { return bannedMembers; }
    public boolean isBanned(UUID uuid) { return uuid != null && bannedMembers.contains(uuid); }
    public void ban(UUID uuid) { if (uuid != null) bannedMembers.add(uuid); }
    public void unban(UUID uuid) { if (uuid != null) bannedMembers.remove(uuid); }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = Math.max(0D, balance); }
    public long getScore() { return score; }
    public void setScore(long score) { this.score = Math.max(0L, score); }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = Math.max(0, rank); }

    public void setHome(Location location) {
        if (location == null || location.getWorld() == null) return;
        homeWorld = location.getWorld().getName();
        homeX = location.getX(); homeY = location.getY(); homeZ = location.getZ();
        homeYaw = location.getYaw(); homePitch = location.getPitch();
    }

    public void clearHome() { homeWorld = ""; }
    public boolean hasHome() { return !homeWorld.isBlank(); }
    public Location getHome() {
        World world = homeWorld.isBlank() ? null : Bukkit.getWorld(homeWorld);
        return world == null ? null : new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }
    public String getHomeWorld() { return homeWorld; }
    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public float getHomeYaw() { return homeYaw; }
    public float getHomePitch() { return homePitch; }
    public void loadHome(String world, double x, double y, double z, float yaw, float pitch) {
        homeWorld = world == null ? "" : world;
        homeX = x; homeY = y; homeZ = z; homeYaw = yaw; homePitch = pitch;
    }

    public TeamRole getRole(UUID uuid) {
        TeamMember member = uuid == null ? null : members.get(uuid);
        return member == null ? null : member.getRole();
    }

    public void addMember(TeamMember member) {
        if (member != null && member.getUuid() != null) {
            members.put(member.getUuid(), member);
        }
    }

    public void removeMember(UUID uuid) {
        if (uuid != null) {
            members.remove(uuid);
        }
    }
}
