package id.velioragardens.veliorasuite.module.kits.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class KitReward {

    private final double money;
    private final int exp;
    private final List<ItemStack> items;
    private final List<String> commands;

    public KitReward(double money, int exp, List<ItemStack> items, List<String> commands) {
        this.money = Math.max(0, money);
        this.exp = Math.max(0, exp);
        this.items = items == null ? List.of() : List.copyOf(items);
        this.commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public double getMoney() {
        return money;
    }

    public int getExp() {
        return exp;
    }

    public List<ItemStack> getItems() {
        return items.stream().map(ItemStack::clone).toList();
    }

    public List<String> getCommands() {
        return commands;
    }
}
