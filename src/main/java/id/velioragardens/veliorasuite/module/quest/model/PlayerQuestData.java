package id.velioragardens.veliorasuite.module.quest.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerQuestData {

    private final UUID uuid;
    private String name;
    private boolean claimLand;
    private boolean setHome;
    private boolean starterKit;
    private boolean starterCompleted;
    private long lastReminder;
    private final Map<QuestCategory, PlayerCategoryProgress> categories = new EnumMap<>(QuestCategory.class);

    public PlayerQuestData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isClaimLand() { return claimLand; }
    public void setClaimLand(boolean claimLand) { this.claimLand = claimLand; }
    public boolean isSetHome() { return setHome; }
    public void setSetHome(boolean setHome) { this.setHome = setHome; }
    public boolean isStarterKit() { return starterKit; }
    public void setStarterKit(boolean starterKit) { this.starterKit = starterKit; }
    public boolean isStarterCompleted() { return starterCompleted; }
    public void setStarterCompleted(boolean starterCompleted) { this.starterCompleted = starterCompleted; }
    public long getLastReminder() { return lastReminder; }
    public void setLastReminder(long lastReminder) { this.lastReminder = Math.max(0L, lastReminder); }
    public Map<QuestCategory, PlayerCategoryProgress> getCategories() { return categories; }

    public PlayerCategoryProgress getCategoryProgress(QuestCategory category) {
        return categories.get(category);
    }

    public void putCategoryProgress(PlayerCategoryProgress progress) {
        categories.put(progress.getCategory(), progress);
    }

    public boolean isStarterDone() {
        return claimLand && setHome && starterKit;
    }
}
