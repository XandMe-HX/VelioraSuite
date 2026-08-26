package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import id.velioragardens.veliorasuite.module.quest.listener.QuestBlockListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestBossBarListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestCommandTrackListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestCookingListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestFarmListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestFishingListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestKillListener;
import id.velioragardens.veliorasuite.module.quest.listener.QuestProgressionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public final class QuestModule implements VelioraModule {

    private final VelioraSuite plugin;
    private final List<Listener> listeners = new ArrayList<>();
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
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
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
        QuestCommand questCommand = new QuestCommand(manager);
        for (String name : skillCommands()) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml.");
                continue;
            }
            command.setExecutor(questCommand);
            command.setTabCompleter(questCommand);
        }
    }

    private void registerListeners() {
        listeners.clear();
        listeners.add(manager.getGuiManager());
        listeners.add(new QuestBlockListener(manager));
        listeners.add(new QuestFarmListener(manager));
        listeners.add(new QuestCookingListener(manager));
        listeners.add(new QuestKillListener(manager));
        listeners.add(new QuestProgressionListener(manager));
        listeners.add(new QuestFishingListener(manager));
        listeners.add(new QuestCommandTrackListener(manager));
        listeners.add(new QuestBossBarListener(manager));
        for (Listener listener : listeners) plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    private void registerDisabledCommand() {
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraQuest");
        for (String name : skillCommands()) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) continue;
            command.setExecutor(disabledCommand);
            command.setTabCompleter(disabledCommand);
        }
    }

    private List<String> skillCommands() {
        return List.of("quests", "skills", "stats", "skilltop", "skillrank", "mining", "farmer", "chef", "agility", "alchemy", "archery");
    }
}
