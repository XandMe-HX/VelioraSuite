package id.velioragardens.veliorasuite.module.gacha;

import org.bukkit.inventory.ItemStack;

record GachaOffer(String crateId, String keyId, String displayName, ItemStack icon, long price, boolean virtualKey) { }
