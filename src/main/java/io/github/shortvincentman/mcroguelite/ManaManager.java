package io.github.shortvincentman.mcroguelite;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ManaManager {

    private final NamespacedKey manaLevelKey;

    public ManaManager(Plugin plugin) {
        this.manaLevelKey = new NamespacedKey(plugin, "mana_level");
    }

    public int getManaLevel(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(manaLevelKey, PersistentDataType.INTEGER, 0);
    }

    public void addManaLevel(Player player, int amount) {
        int current = getManaLevel(player);
        int newLevel = Math.max(0, current + amount);
        player.getPersistentDataContainer()
                .set(manaLevelKey, PersistentDataType.INTEGER, newLevel);
    }
}
