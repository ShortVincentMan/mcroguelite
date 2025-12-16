package io.github.shortvincentman.mcroguelite;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SpellManager {

    private final ChargeBarManager chargeBarManager;
    private final NamespacedKey spellKey;

    public SpellManager(Plugin plugin, ChargeBarManager chargeBarManager) {
        this.chargeBarManager = chargeBarManager;
        this.spellKey = new NamespacedKey(plugin, "spell_id");
    }

    public void castSpell(Player player, String spellId) {
        spellId = spellId.toLowerCase();

        if (!chargeBarManager.hasManaUnlocked(player)) {
            player.sendMessage("§cYou have not unlocked your mana yet.");
            return;
        }

        double charge = chargeBarManager.getChargeProgress(player);

        switch (spellId) {
            case "ignis" -> Spells.castIgnis(player, charge, chargeBarManager);
            case "hoppa" -> Spells.castHoppa(player, charge, chargeBarManager);
            case "ward" -> Spells.castWard(player, charge, chargeBarManager);
            case "unlock" -> Spells.castUnlock(player, charge, chargeBarManager);
            default -> player.sendMessage("§cYou don't know that spell.");
        }
    }

    public NamespacedKey getSpellKey() {
        return spellKey;
    }

    /** Create a spell item representing this spell. */
    public ItemStack createSpellItem(String spellId, Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.getPersistentDataContainer().set(spellKey, PersistentDataType.STRING, spellId.toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }
}
