package io.github.shortvincentman.mcroguelite.tome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpellTome {
    private final Plugin plugin;
    private final NamespacedKey TOME_KEY;
    private final NamespacedKey SPELLS_KEY;
    private final NamespacedKey TOME_TIER_KEY;
    private final NamespacedKey SELECTED_SPELL_KEY;

    public enum TomeTier {
        STARTER("Weathered Tome", NamedTextColor.GRAY, 3),
        ADEPT("Adept's Tome", NamedTextColor.GREEN, 5),
        MASTER("Master's Tome", NamedTextColor.GOLD, 8),
        ANCIENT("Ancient Tome", NamedTextColor.DARK_PURPLE, -1); // Unlimited

        private final String displayName;
        private final NamedTextColor color;
        private final int maxSpells;

        TomeTier(String displayName, NamedTextColor color, int maxSpells) {
            this.displayName = displayName;
            this.color = color;
            this.maxSpells = maxSpells;
        }

        public String getDisplayName() { return displayName; }
        public NamedTextColor getColor() { return color; }
        public int getMaxSpells() { return maxSpells; }
    }

    public SpellTome(Plugin plugin) {
        this.plugin = plugin;
        this.TOME_KEY = new NamespacedKey(plugin, "spell_tome");
        this.SPELLS_KEY = new NamespacedKey(plugin, "tome_spells");
        this.TOME_TIER_KEY = new NamespacedKey(plugin, "tome_tier");
        this.SELECTED_SPELL_KEY = new NamespacedKey(plugin, "selected_spell");
    }

    public ItemStack createTome(TomeTier tier) {
        ItemStack tome = new ItemStack(Material.BOOK);
        ItemMeta meta = tome.getItemMeta();

        meta.displayName(Component.text(tier.getDisplayName(), tier.getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A tome for casting spells", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("Spells: ", NamedTextColor.YELLOW)
                .append(Component.text("None", NamedTextColor.GRAY)));
        lore.add(Component.empty());
        if (tier.getMaxSpells() > 0) {
            lore.add(Component.text("Capacity: 0/" + tier.getMaxSpells(), NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("Capacity: Unlimited", NamedTextColor.LIGHT_PURPLE));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to open spell menu", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Sneak + Right-click to cast selected", NamedTextColor.DARK_GRAY));

        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TOME_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(TOME_TIER_KEY, PersistentDataType.STRING, tier.name());
        pdc.set(SPELLS_KEY, PersistentDataType.STRING, "");
        pdc.set(SELECTED_SPELL_KEY, PersistentDataType.STRING, "");

        tome.setItemMeta(meta);
        return tome;
    }

    public boolean isTome(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(TOME_KEY, PersistentDataType.BYTE);
    }

    public TomeTier getTomeTier(ItemStack item) {
        if (!isTome(item)) return null;
        String tierName = item.getItemMeta().getPersistentDataContainer()
                .get(TOME_TIER_KEY, PersistentDataType.STRING);
        try {
            return TomeTier.valueOf(tierName);
        } catch (Exception e) {
            return TomeTier.STARTER;
        }
    }

    public List<String> getSpells(ItemStack item) {
        if (!isTome(item)) return new ArrayList<>();
        String spellsStr = item.getItemMeta().getPersistentDataContainer()
                .get(SPELLS_KEY, PersistentDataType.STRING);
        if (spellsStr == null || spellsStr.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(spellsStr.split(",")));
    }

    public String getSelectedSpell(ItemStack item) {
        if (!isTome(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(SELECTED_SPELL_KEY, PersistentDataType.STRING);
    }

    public void setSelectedSpell(ItemStack item, String spellName) {
        if (!isTome(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SELECTED_SPELL_KEY, PersistentDataType.STRING, 
                spellName != null ? spellName.toUpperCase() : "");
        updateTomeLore(meta, getTomeTier(item), getSpells(item), spellName);
        item.setItemMeta(meta);
    }

    public boolean addSpell(ItemStack item, String spellName) {
        if (!isTome(item)) return false;

        TomeTier tier = getTomeTier(item);
        List<String> spells = getSpells(item);

        if (spells.contains(spellName.toUpperCase())) return false;
        if (tier.getMaxSpells() > 0 && spells.size() >= tier.getMaxSpells()) return false;

        spells.add(spellName.toUpperCase());

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SPELLS_KEY, PersistentDataType.STRING,
                String.join(",", spells));

        String selected = getSelectedSpell(item);
        updateTomeLore(meta, tier, spells, selected);
        item.setItemMeta(meta);
        return true;
    }

    public boolean removeSpell(ItemStack item, String spellName) {
        if (!isTome(item)) return false;

        List<String> spells = getSpells(item);
        if (!spells.remove(spellName.toUpperCase())) return false;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SPELLS_KEY, PersistentDataType.STRING,
                String.join(",", spells));

        String selected = getSelectedSpell(item);
        if (selected != null && selected.equalsIgnoreCase(spellName)) {
            meta.getPersistentDataContainer().set(SELECTED_SPELL_KEY, PersistentDataType.STRING, "");
            selected = null;
        }

        updateTomeLore(meta, getTomeTier(item), spells, selected);
        item.setItemMeta(meta);
        return true;
    }

    private void updateTomeLore(ItemMeta meta, TomeTier tier, List<String> spells, String selectedSpell) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A tome for casting spells", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());

        if (spells.isEmpty()) {
            lore.add(Component.text("Spells: ", NamedTextColor.YELLOW)
                    .append(Component.text("None", NamedTextColor.GRAY)));
        } else {
            lore.add(Component.text("Spells:", NamedTextColor.YELLOW));
            for (String spell : spells) {
                Component spellLine = Component.text("  • " + formatSpellName(spell), NamedTextColor.AQUA);
                if (spell.equalsIgnoreCase(selectedSpell)) {
                    spellLine = spellLine.append(Component.text(" ◄", NamedTextColor.GREEN));
                }
                lore.add(spellLine);
            }
        }

        lore.add(Component.empty());
        if (tier.getMaxSpells() > 0) {
            lore.add(Component.text("Capacity: " + spells.size() + "/" + tier.getMaxSpells(), NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("Capacity: Unlimited", NamedTextColor.LIGHT_PURPLE));
        }
        
        if (selectedSpell != null && !selectedSpell.isEmpty()) {
            lore.add(Component.text("Selected: " + formatSpellName(selectedSpell), NamedTextColor.GREEN));
        }
        
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to open spell menu", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Sneak + Right-click to cast selected", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
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
