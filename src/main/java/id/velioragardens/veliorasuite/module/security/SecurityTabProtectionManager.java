package id.velioragardens.veliorasuite.module.security;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class SecurityTabProtectionManager {

    private final SecurityConfigManager configManager;
    private final SecurityCommandProtectionManager commandProtectionManager;

    public SecurityTabProtectionManager(SecurityConfigManager configManager, SecurityCommandProtectionManager commandProtectionManager) {
        this.configManager = configManager;
        this.commandProtectionManager = commandProtectionManager;
    }

    public void filter(Player player, Set<String> commands) {
        if (player == null || commands == null) return;
        if (!configManager.isEnabled() || !configManager.isTabProtectionEnabled() || !configManager.isHideBlockedCommands()) return;
        if (configManager.hasBypass(player)) return;

        Set<String> blocked = new HashSet<>();
        for (String command : commandProtectionManager.blockedCommands()) {
            String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
            blocked.add(withoutSlash);
            if (withoutSlash.contains(":")) blocked.add(withoutSlash.substring(withoutSlash.indexOf(':') + 1));
        }
        commands.removeIf(value -> blocked.contains(value.toLowerCase()));
    }
}
