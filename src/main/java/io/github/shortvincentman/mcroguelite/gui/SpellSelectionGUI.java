package io.github.shortvincentman.mcroguelite.gui;

import io.github.shortvincentman.mcroguelite.tome.SpellTome;
import io.github.shortvincentman.mcroguelite.tome.TomeSpellListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for selecting spells from a tome.
 * Uses Bukkit's built-in inventory system.
 */
public class SpellSelectionGUI implements Listener {
    private final SpellTome spellTome;
    private final TomeSpellListener tomeListener;
    
    // Track which players have the GUI open and their tome
    private final Map<UUID, ItemStack> openGUIs = new HashMap<>();

    // Spell icons mapping
    private static final Map<String, Material> SPELL_ICONS = new HashMap<>() {{
        put("IGNIS", Material.FIRE_CHARGE);
        put("HOPPA", Material.FEATHER);
        put("WARD", Material.SHIELD);
        put("UNLOCK", Material.TRIPWIRE_HOOK);
        put("SNAP", Material.ENDER_PEARL);
        put("FIMBULVETR", Material.BLUE_ICE);
        put("PERFLORA", Material.PINK_PETALS);
        put("SNARVINDUR", Material.WIND_CHARGE);
        put("TENEBRIS", Material.COBWEB);
        put("PERCUTIENS", Material.LIGHTNING_ROD);
        put("CELERITAS", Material.SUGAR);
        put("VEIL", Material.GLASS);
        put("GELIDUS", Material.SNOWBALL);
        put("SCRUPUS", Material.POINTED_DRIPSTONE);
    }};

    // Mana costs for display (percentages)
    private static final Map<String, Integer> MANA_COSTS = new HashMap<>() {{
        put("IGNIS", 20);
        put("HOPPA", 15);
        put("WARD", 25);
        put("UNLOCK", 10);
        put("SNAP", 30);
        put("FIMBULVETR", 35);
        put("PERFLORA", 25);
        put("SNARVINDUR", 30);
        put("TENEBRIS", 35);
        put("PERCUTIENS", 30);      // Lightning strike
        put("CELERITAS", 15);       // Speed buff - low cost
        put("VEIL", 25);            // Invisibility
        put("GELIDUS", 25);         // Ice cone
        put("SCRUPUS", 20);         // Stone projectile
    }};

    public SpellSelectionGUI(SpellTome spellTome, TomeSpellListener tomeListener) {
        this.spellTome = spellTome;
        this.tomeListener = tomeListener;
    }

    /**
     * Open the spell selection GUI for a player.
     */
    public void openGUI(Player player, ItemStack tome) {
        List<String> spells = spellTome.getSpells(tome);
        String selectedSpell = spellTome.getSelectedSpell(tome);

        if (spells.isEmpty()) {
            player.sendMessage(Component.text("Your tome has no spells! Use spell scrolls to learn.", NamedTextColor.YELLOW));
            return;
        }

        // Create inventory (9 slots for up to 9 spells, 18 for more)
        int size = spells.size() <= 9 ? 9 : 18;
        Inventory gui = Bukkit.createInventory(new SpellGUIHolder(), size, 
                Component.text("Select Spell", NamedTextColor.DARK_PURPLE));

        // Add spell icons
        for (int i = 0; i < spells.size() && i < size; i++) {
            String spell = spells.get(i);
            boolean isSelected = spell.equalsIgnoreCase(selectedSpell);
            gui.setItem(i, createSpellIcon(spell, isSelected));
        }

        openGUIs.put(player.getUniqueId(), tome);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    private ItemStack createSpellIcon(String spellName, boolean isSelected) {
        Material icon = SPELL_ICONS.getOrDefault(spellName.toUpperCase(), Material.PAPER);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        String formattedName = formatSpellName(spellName);
        NamedTextColor nameColor = isSelected ? NamedTextColor.GREEN : NamedTextColor.AQUA;

        meta.displayName(Component.text(formattedName, nameColor)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, isSelected));

        List<Component> lore = new ArrayList<>();
        
        lore.add(getSpellDescription(spellName.toUpperCase()));
        lore.add(Component.empty());

        if (isSelected) {
            lore.add(Component.text("✓ SELECTED", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));
        } else {
            lore.add(Component.text("Click to select", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component getSpellDescription(String spell) {
        return switch (spell) {
            case "IGNIS" -> Component.text("Shoots a fire bolt", NamedTextColor.GRAY);
            case "HOPPA" -> Component.text("Leap forward", NamedTextColor.GRAY);
            case "WARD" -> Component.text("Defensive shield", NamedTextColor.GRAY);
            case "UNLOCK" -> Component.text("Unlock target's mana", NamedTextColor.GRAY);
            case "SNAP" -> Component.text("Short-range teleport", NamedTextColor.GRAY);
            case "FIMBULVETR" -> Component.text("Ice projectile, freezes", NamedTextColor.GRAY);
            case "PERFLORA" -> Component.text("Healing aura", NamedTextColor.GRAY);
            case "SNARVINDUR" -> Component.text("Wind knockback wave", NamedTextColor.GRAY);
            case "TENEBRIS" -> Component.text("Place a trap", NamedTextColor.GRAY);
            case "PERCUTIENS" -> Component.text("Lightning strike", NamedTextColor.GRAY);
            case "CELERITAS" -> Component.text("Speed burst", NamedTextColor.GRAY);
            case "VEIL" -> Component.text("Turn invisible", NamedTextColor.GRAY);
            case "GELIDUS" -> Component.text("Cone freeze attack", NamedTextColor.GRAY);
            case "SCRUPUS" -> Component.text("Earth spike damage", NamedTextColor.GRAY);
            default -> Component.text("A magical spell", NamedTextColor.GRAY);
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellGUIHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack tome = openGUIs.get(player.getUniqueId());
        if (tome == null) return;

        // Get spell name from slot
        List<String> spells = spellTome.getSpells(tome);
        int slot = event.getSlot();
        if (slot < 0 || slot >= spells.size()) return;

        String selectedSpell = spells.get(slot);
        
        // Set selected spell on tome
        spellTome.setSelectedSpell(tome, selectedSpell);
        
        player.sendMessage(Component.text("Selected: ", NamedTextColor.GRAY)
                .append(Component.text(formatSpellName(selectedSpell), NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        
        // Close GUI
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellGUIHolder)) return;
        openGUIs.remove(event.getPlayer().getUniqueId());
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

    /**
     * Marker class for identifying our GUI inventory.
     */
    private static class SpellGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
