package io.github.shortvincentman.mcroguelite.items;

import io.github.shortvincentman.mcroguelite.ChargeBarManager;
import io.github.shortvincentman.mcroguelite.Mcroguelite;
import io.github.shortvincentman.mcroguelite.tome.SpellTome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Spell Scrolls - consumable items that teach spells to tomes.
 * Uses PAPER as the base item with enchant glow.
 */
public class SpellScroll implements Listener {
    private final Mcroguelite plugin;
    private final SpellTome spellTome;
    private final ChargeBarManager chargeBarManager;
    
    private final NamespacedKey SCROLL_KEY;
    private final NamespacedKey SPELL_KEY;

    public SpellScroll(Mcroguelite plugin, SpellTome spellTome, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.spellTome = spellTome;
        this.chargeBarManager = chargeBarManager;
        this.SCROLL_KEY = new NamespacedKey(plugin, "spell_scroll");
        this.SPELL_KEY = new NamespacedKey(plugin, "scroll_spell");
    }

    /**
     * Create a spell scroll item for a specific spell.
     */
    public ItemStack createScroll(String spellName) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        String formattedName = formatSpellName(spellName);
        
        // Display name with magical styling
        meta.displayName(Component.text("Scroll of " + formattedName, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A magical scroll containing", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("the knowledge of " + formattedName + ".", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("Spell: ", NamedTextColor.YELLOW)
                .append(Component.text(formattedName, NamedTextColor.AQUA)));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click while holding a tome", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("to learn this spell.", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Requires: Mana Unlocked", NamedTextColor.RED));
        
        meta.lore(lore);

        // Add enchant glow without showing enchantment
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Store scroll data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(SCROLL_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(SPELL_KEY, PersistentDataType.STRING, spellName.toUpperCase());

        scroll.setItemMeta(meta);
        return scroll;
    }

    /**
     * Check if an item is a spell scroll.
     */
    public boolean isScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(SCROLL_KEY, PersistentDataType.BYTE);
    }

    /**
     * Get the spell name from a scroll.
     */
    public String getScrollSpell(ItemStack item) {
        if (!isScroll(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(SPELL_KEY, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Check if holding scroll in main hand and tome in off hand (or vice versa)
        ItemStack scroll = null;
        ItemStack tome = null;
        boolean scrollInMainHand = false;

        if (isScroll(mainHand) && spellTome.isTome(offHand)) {
            scroll = mainHand;
            tome = offHand;
            scrollInMainHand = true;
        } else if (isScroll(offHand) && spellTome.isTome(mainHand)) {
            scroll = offHand;
            tome = mainHand;
            scrollInMainHand = false;
        }

        if (scroll == null || tome == null) return;
        
        event.setCancelled(true);

        // Check if player has mana unlocked
        if (!chargeBarManager.hasManaUnlocked(player)) {
            player.sendMessage(Component.text("You must have mana unlocked to use spell scrolls!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String spellName = getScrollSpell(scroll);
        if (spellName == null) return;

        // Try to add spell to tome
        if (spellTome.addSpell(tome, spellName)) {
            // Success - consume scroll
            if (scrollInMainHand) {
                mainHand.setAmount(mainHand.getAmount() - 1);
            } else {
                offHand.setAmount(offHand.getAmount() - 1);
            }

            player.sendMessage(Component.text("§d✦ You learned " + formatSpellName(spellName) + "! ✦"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            
            // Particles
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.ENCHANT,
                    player.getLocation().add(0, 1.5, 0),
                    50, 0.5, 0.5, 0.5, 0.5
            );
        } else {
            // Failed - spell already known or tome full
            if (spellTome.getSpells(tome).contains(spellName.toUpperCase())) {
                player.sendMessage(Component.text("You already know this spell!", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Your tome is full! Use a higher tier tome.", NamedTextColor.RED));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private String formatSpellName(String spell) {
        if (spell == null || spell.isEmpty()) return "";
        String[] words = spell.toLowerCase().replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }
}
