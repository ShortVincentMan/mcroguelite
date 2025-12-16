package io.github.shortvincentman.mcroguelite.tome;

import io.github.shortvincentman.mcroguelite.ChargeBarManager;
import io.github.shortvincentman.mcroguelite.Mcroguelite;
import io.github.shortvincentman.mcroguelite.Spells;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TomeSpellListener implements Listener {
    private final Mcroguelite plugin;
    private final SpellTome spellTome;
    private final ChargeBarManager chargeBarManager;

    private final Map<UUID, CastingState> castingPlayers = new HashMap<>();
    private final Map<UUID, BossBar> castBars = new HashMap<>();

    // Cast time in ticks for each spell
    private static final Map<String, Integer> CAST_TIMES = new HashMap<>() {{
        put("IGNIS", 20);           // 1 second
        put("HOPPA", 10);           // 0.5 seconds
        put("WARD", 30);            // 1.5 seconds
        put("UNLOCK", 40);          // 2 seconds
        put("SNAP", 15);            // 0.75 seconds
        put("FIMBULVETR", 40);      // 2 seconds - God Spell
        put("PERFLORA", 50);        // 2.5 seconds
        put("SNARVINDUR", 25);      // 1.25 seconds - Wind knockback
        put("TENEBRIS", 60);        // 3 seconds - Shadow mark trap
        put("PERCUTIENS", 30);      // 1.5 seconds - Lightning
        put("CELERITAS", 15);       // 0.75 seconds - Speed buff
        put("VEIL", 25);            // 1.25 seconds - Invisibility
        put("GELIDUS", 30);         // 1.5 seconds - Ice cone
        put("SCRUPUS", 20);         // 1 second - Stone sword
        put("ARMIS", 20);           // 1 second - Disarm bolt
        put("VIRIBUS", 35);         // 1.75 seconds - Stone pillars
        put("TRICKSTUS", 30);       // 1.5 seconds - Confusion
    }};

    // Mana costs for spells (consumed on cast)
    private static final Map<String, Double> MANA_COSTS = new HashMap<>() {{
        put("IGNIS", 0.20);
        put("HOPPA", 0.15);
        put("WARD", 0.25);
        put("UNLOCK", 0.10);
        put("SNAP", 0.30);
        put("FIMBULVETR", 0.35);
        put("PERFLORA", 0.25);
        put("SNARVINDUR", 0.30);
        put("TENEBRIS", 0.35);
        put("PERCUTIENS", 0.30);
        put("CELERITAS", 0.15);
        put("VEIL", 0.25);
        put("GELIDUS", 0.25);
        put("SCRUPUS", 0.20);
        put("ARMIS", 0.25);
        put("VIRIBUS", 0.35);
        put("TRICKSTUS", 0.30);
    }};

    // Mana bar windows (start, end) - 0.0 to 1.0
    // This is the mana % you need to have when casting for perfect timing
    // Based on wiki: Ignis 85-95%, Fimbulvetr 90-95%, Celeritas 70-99%, Gelidus 80-100%
    private static final Map<String, double[]> MANA_WINDOWS = new HashMap<>() {{
        put("IGNIS", new double[]{0.85, 0.95});       // Wiki: 85-95%
        put("HOPPA", new double[]{0.45, 0.55});
        put("WARD", new double[]{0.35, 0.65});
        put("UNLOCK", new double[]{0.40, 0.60});
        put("SNAP", new double[]{0.45, 0.55});
        put("FIMBULVETR", new double[]{0.90, 0.95}); // Wiki: 90-95% (God Spell - narrow)
        put("PERFLORA", new double[]{0.35, 0.65});
        put("SNARVINDUR", new double[]{0.40, 0.60});
        put("TENEBRIS", new double[]{0.45, 0.55});
        put("PERCUTIENS", new double[]{0.75, 0.95});  // Narrow - powerful spell
        put("CELERITAS", new double[]{0.70, 0.99});   // Wiki: 70-99% (wide window)
        put("VEIL", new double[]{0.60, 0.90});        // Medium window
        put("GELIDUS", new double[]{0.80, 1.00});     // Wiki: 80-100%
        put("SCRUPUS", new double[]{0.01, 1.00});     // Wiki: 1-100% (very forgiving)
        put("ARMIS", new double[]{0.50, 0.70});       // Medium window
        put("VIRIBUS", new double[]{0.60, 0.85});     // Moderate difficulty
        put("TRICKSTUS", new double[]{0.40, 0.60});   // Medium window
    }};

    private static class CastingState {
        String spell;
        int totalTicks;
        BukkitRunnable task;
        ItemStack tome;
        double manaAtCastStart; // The mana % when casting started - this is used for timing

        CastingState(String spell, int totalTicks, ItemStack tome, double manaAtCastStart) {
            this.spell = spell;
            this.totalTicks = totalTicks;
            this.tome = tome;
            this.manaAtCastStart = manaAtCastStart;
        }
    }

    public TomeSpellListener(Mcroguelite plugin, SpellTome spellTome, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.spellTome = spellTome;
        this.chargeBarManager = chargeBarManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!spellTome.isTome(item)) return;
        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // Start casting selected spell
                startCasting(player, item);
            } else {
                // Open spell selection menu
                openSpellMenu(player, item);
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!castingPlayers.containsKey(player.getUniqueId())) return;

        // Cancel casting when player takes damage
        cancelCasting(player);
        player.sendMessage(Component.text("Casting interrupted!", NamedTextColor.RED));
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        cancelCasting(event.getPlayer());
    }

    private void startCasting(Player player, ItemStack tome) {
        UUID uuid = player.getUniqueId();
        String spell = spellTome.getSelectedSpell(tome);

        if (spell == null || spell.isEmpty()) {
            player.sendMessage(Component.text("No spell selected! Right-click to open menu.", NamedTextColor.RED));
            return;
        }

        List<String> knownSpells = spellTome.getSpells(tome);
        if (!knownSpells.contains(spell)) {
            player.sendMessage(Component.text("You don't have this spell in your tome!", NamedTextColor.RED));
            return;
        }

        if (!chargeBarManager.hasManaUnlocked(player)) {
            player.sendMessage(Component.text("Your mana is not yet unlocked!", NamedTextColor.RED));
            return;
        }

        double manaCost = MANA_COSTS.getOrDefault(spell, 0.20);
        double currentMana = chargeBarManager.getChargeProgress(player);
        
        // Check if player has minimum mana to cast
        if (currentMana < manaCost) {
            player.sendMessage(Component.text("Not enough mana! Need " + (int)(manaCost * 100) + "%", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Cancel any existing cast
        cancelCasting(player);

        int castTime = CAST_TIMES.getOrDefault(spell, 30);
        
        // Store mana % at cast start - this is the timing value!
        CastingState state = new CastingState(spell, castTime, tome, currentMana);

        // Get the mana window to show in cast bar
        double[] window = MANA_WINDOWS.getOrDefault(spell, new double[]{0.4, 0.6});
        int windowStartPercent = (int)(window[0] * 100);
        int windowEndPercent = (int)(window[1] * 100);
        int manaPercent = (int)(currentMana * 100);

        // Create cast bar - shows the spell being cast and required mana %
        BarColor barColor;
        if (currentMana >= window[0] && currentMana <= window[1]) {
            barColor = BarColor.GREEN; // In perfect window
        } else {
            barColor = BarColor.RED; // Outside window - will backfire
        }

        BossBar castBar = Bukkit.createBossBar(
                formatSpellName(spell) + " [" + manaPercent + "% mana - need " + windowStartPercent + "-" + windowEndPercent + "%]",
                barColor,
                BarStyle.SOLID
        );
        castBar.setProgress(0);
        castBar.addPlayer(player);
        castBars.put(uuid, castBar);

        // Start casting sound
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 0.5f);

        state.task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !castingPlayers.containsKey(uuid)) {
                    cancelCasting(player);
                    return;
                }

                tick++;
                double progress = (double) tick / castTime;
                castBar.setProgress(Math.min(1.0, progress));

                // Charging sound every few ticks
                if (tick % 5 == 0) {
                    float pitch = 0.5f + (float) progress;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, pitch);
                }

                if (tick >= castTime) {
                    // Cast completes - use mana % from when casting started
                    finishCasting(player);
                    cancel();
                }
            }
        };

        state.task.runTaskTimer(plugin, 0L, 1L);
        castingPlayers.put(uuid, state);
    }

    private void finishCasting(Player player) {
        UUID uuid = player.getUniqueId();
        CastingState state = castingPlayers.remove(uuid);
        if (state == null) return;

        if (state.task != null) {
            state.task.cancel();
        }

        BossBar bar = castBars.remove(uuid);
        if (bar != null) bar.removeAll();

        String spell = state.spell;
        double manaCost = MANA_COSTS.getOrDefault(spell, 0.20);
        double manaAtCast = state.manaAtCastStart; // Use mana % from when cast started

        // Consume the mana cost
        chargeBarManager.consumeCharge(player, manaCost);

        // Cast the spell using mana % at cast start as the timing value
        Spells.SpellResult result = executeSpell(player, spell, manaAtCast);

        // Get window info for feedback
        double[] window = MANA_WINDOWS.getOrDefault(spell, new double[]{0.4, 0.6});
        int manaPercent = (int)(manaAtCast * 100);
        int windowStartPercent = (int)(window[0] * 100);
        int windowEndPercent = (int)(window[1] * 100);

        // Feedback based on result
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200));
        
        switch (result) {
            case PERFECT:
                player.showTitle(Title.title(
                        Component.empty(),
                        Component.text("Perfect! (" + manaPercent + "%)", NamedTextColor.GOLD),
                        times
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
                break;
            case SUCCESS:
                player.showTitle(Title.title(
                        Component.empty(),
                        Component.text("Cast! (" + manaPercent + "%)", NamedTextColor.GREEN),
                        times
                ));
                break;
            case BACKFIRE:
                player.showTitle(Title.title(
                        Component.empty(),
                        Component.text("Backfire! (" + manaPercent + "% - need " + windowStartPercent + "-" + windowEndPercent + "%)", NamedTextColor.RED),
                        times
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
                break;
            case FIZZLE:
                player.showTitle(Title.title(
                        Component.empty(),
                        Component.text("Fizzle...", NamedTextColor.GRAY),
                        times
                ));
                break;
        }
    }

    public void cancelCasting(Player player) {
        UUID uuid = player.getUniqueId();
        CastingState state = castingPlayers.remove(uuid);
        if (state != null && state.task != null) {
            state.task.cancel();
        }

        BossBar bar = castBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }

    private void openSpellMenu(Player player, ItemStack tome) {
        List<String> spells = spellTome.getSpells(tome);
        String selected = spellTome.getSelectedSpell(tome);

        if (spells.isEmpty()) {
            player.sendMessage(Component.text("Your tome has no spells! Visit a trainer to learn some.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("═══════ Spell Selection ═══════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Click a spell to select it!", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        
        for (int i = 0; i < spells.size(); i++) {
            String spell = spells.get(i);
            String formattedName = formatSpellName(spell);
            boolean isSelected = spell.equalsIgnoreCase(selected);

            // Build clickable spell component
            Component spellName = Component.text(formattedName, isSelected ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/selectspell " + spell.toLowerCase()))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to select ", NamedTextColor.YELLOW)
                                    .append(Component.text(formattedName, NamedTextColor.AQUA))
                                    .append(Component.text("\n" + getSpellDescription(spell), NamedTextColor.GRAY))
                    ));
            
            if (isSelected) {
                spellName = spellName.decorate(TextDecoration.BOLD);
            }

            Component spellLine = Component.text((i + 1) + ". ", NamedTextColor.GRAY)
                    .append(spellName);

            if (isSelected) {
                spellLine = spellLine.append(Component.text(" ◄", NamedTextColor.GREEN));
            }

            player.sendMessage(spellLine);
        }
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD));
    }
    
    private String getSpellDescription(String spell) {
        return switch (spell.toUpperCase()) {
            case "IGNIS" -> "Large fireball with explosion";
            case "HOPPA" -> "Leap forward";
            case "WARD" -> "Defensive shield";
            case "UNLOCK" -> "Unlock target's mana";
            case "SNAP" -> "Teleport to cursor";
            case "FIMBULVETR" -> "Ice storm with freezing";
            case "PERFLORA" -> "Healing aura";
            case "SNARVINDUR" -> "Wind knockback wave";
            case "TENEBRIS" -> "Shadow trap (purple mark, +damage taken)";
            case "PERCUTIENS" -> "Lightning strike";
            case "CELERITAS" -> "Speed burst";
            case "VEIL" -> "Channeled invisibility (drains mana)";
            case "GELIDUS" -> "Channeled frost breath (drains mana)";
            case "SCRUPUS" -> "Summon stone sword";
            case "ARMIS" -> "Disarm target (drop held item)";
            case "VIRIBUS" -> "Stone pillars with knockback";
            case "TRICKSTUS" -> "Confuse target's controls";
            default -> "A magical spell";
        };
    }

    public void selectSpell(Player player, String spellName) {
        ItemStack tome = player.getInventory().getItemInMainHand();
        if (!spellTome.isTome(tome)) {
            tome = player.getInventory().getItemInOffHand();
        }

        if (!spellTome.isTome(tome)) {
            player.sendMessage(Component.text("You must be holding a tome!", NamedTextColor.RED));
            return;
        }

        List<String> spells = spellTome.getSpells(tome);
        String normalized = spellName.toUpperCase().replace(" ", "_").replace("'", "");

        // Try to find matching spell
        String found = null;
        for (String s : spells) {
            if (s.equalsIgnoreCase(normalized) || s.replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                found = s;
                break;
            }
        }

        if (found == null) {
            player.sendMessage(Component.text("You don't have that spell in your tome!", NamedTextColor.RED));
            return;
        }

        spellTome.setSelectedSpell(tome, found);
        player.sendMessage(Component.text("Selected: " + formatSpellName(found), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
    }

    private Spells.SpellResult executeSpell(Player player, String spell, double manaPercent) {
        double[] window = MANA_WINDOWS.getOrDefault(spell, new double[]{0.4, 0.6});
        
        switch (spell.toUpperCase()) {
            case "IGNIS":
                return Spells.castIgnis(player, manaPercent, window[0], window[1], chargeBarManager);
            case "HOPPA":
                return Spells.castHoppa(player, manaPercent, window[0], window[1]);
            case "WARD":
                return Spells.castWard(player, manaPercent, window[0], window[1]);
            case "UNLOCK":
                return Spells.castUnlock(player, manaPercent, window[0], window[1], chargeBarManager);
            case "SNAP":
                return Spells.castSnap(player, manaPercent, window[0], window[1]);
            case "FIMBULVETR":
                return Spells.castFimbulvetr(player, manaPercent, window[0], window[1], chargeBarManager, plugin);
            case "PERFLORA":
                return Spells.castPerflora(player, manaPercent, window[0], window[1], plugin);
            case "SNARVINDUR":
                return Spells.castSnarvindur(player, manaPercent, window[0], window[1]);
            case "TENEBRIS":
                return Spells.castTenebris(player, manaPercent, window[0], window[1], chargeBarManager, plugin);
            case "PERCUTIENS":
                return Spells.castPercutiens(player, manaPercent, window[0], window[1]);
            case "CELERITAS":
                return Spells.castCeleritas(player, manaPercent, window[0], window[1]);
            case "VEIL":
                return Spells.castVeil(player, manaPercent, window[0], window[1], chargeBarManager, plugin);
            case "GELIDUS":
                return Spells.castGelidus(player, manaPercent, window[0], window[1], chargeBarManager, plugin);
            case "SCRUPUS":
                return Spells.castScrupus(player, manaPercent, window[0], window[1], plugin);
            case "ARMIS":
                return Spells.castArmis(player, manaPercent, window[0], window[1], plugin);
            case "VIRIBUS":
                return Spells.castViribus(player, manaPercent, window[0], window[1], plugin);
            case "TRICKSTUS":
                return Spells.castTrickstus(player, manaPercent, window[0], window[1], chargeBarManager, plugin);
            default:
                return Spells.SpellResult.FIZZLE;
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

    public void cleanup() {
        for (BossBar bar : castBars.values()) {
            bar.removeAll();
        }
        castBars.clear();
        for (CastingState state : castingPlayers.values()) {
            if (state.task != null) state.task.cancel();
        }
        castingPlayers.clear();
    }
}
