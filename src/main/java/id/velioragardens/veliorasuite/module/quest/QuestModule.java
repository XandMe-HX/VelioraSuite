package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import id.velioragardens.veliorasuite.module.quest.listener.QuestBlockListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestCommandTrackListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestCookingListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestFarmListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestFishingListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestKillListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class QuestModule implements VelioraModule {

    private final VelioraSuite plugin;
    private QuestManager manager;
    private QuestReminderTask reminderTask;
    private boolean enabled;

    public QuestModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "quest";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/quest.yml");
        manager = new QuestManager(plugin);
        manager.load();
        reminderTask = new QuestReminderTask(plugin, manager);
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListeners();
        reminderTask.start();
    }

    @Override
    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(plugin);
        if (reminderTask != null) reminderTask.stop();
        if (manager != null) manager.shutdown();
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
        if (reminderTask != null) reminderTask.start();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public QuestManager getQuestManager() {
        return manager;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("quest");
        if (command == null) {
            plugin.getLogger().warning("Command /quest tidak ditemukan di plugin.yml.");
            return;
        }
        QuestCommand questCommand = new QuestCommand(manager);
        command.setExecutor(questCommand);
        command.setTabCompleter(questCommand);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(manager.getGuiManager(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestBlockListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestFarmListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestCookingListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestKillListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestFishingListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestCommandTrackListener(manager), plugin);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("quest");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraQuest");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
