package io.github.shortvincentman.mcroguelite;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ItemSpellListener implements Listener {

    private final SpellManager spellManager;

    public ItemSpellListener(org.bukkit.plugin.Plugin plugin, SpellManager spellManager) {
        this.spellManager = spellManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String spellId = data.get(spellManager.getSpellKey(), PersistentDataType.STRING);
        if (spellId == null || spellId.isEmpty()) return;

        // Optional: cancel normal interaction
        event.setCancelled(true);

        spellManager.castSpell(player, spellId);
    }
}
