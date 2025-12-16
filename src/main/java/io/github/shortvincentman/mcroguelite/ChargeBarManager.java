package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ChargeBarManager implements Listener {

    private final Plugin plugin;

    // Training thresholds (full mana charges required)
    public static final int MANA_RUN_THRESHOLD = 25;
    public static final int MANA_CLIMB_THRESHOLD = 90;
    public static final int MANA_DASH_THRESHOLD = 150;
    
    // Scaling constants based on full charge count
    private static final int MAX_SCALING_CHARGES = 200; // Caps scaling benefits at 200 charges
    
    // Base cooldown after full charge (ms) - scales down with mana level
    private static final long BASE_COOLDOWN_MS = 1500L;
    private static final long MIN_COOLDOWN_MS = 300L;

    static class PlayerChargeState {
        BossBar bar;
        boolean charging;
        boolean manaUnlocked;
        boolean hasReceivedManaMessage;
        long lastChargeSound;
        
        // Training system
        int fullChargeCount = 0;
        boolean manaRunUnlocked = false;
        boolean manaClimbUnlocked = false;
        boolean manaDashUnlocked = false;
        
        // Mana disruption (from mana punch)
        long manaDisruptedUntil = 0;
        
        // Cooldown after full charge (prevents immediate re-charging)
        long chargeCooldownUntil = 0;
        boolean reachedFullCharge = false;
    }

    private final Map<UUID, PlayerChargeState> states = new HashMap<>();
    private final File playerDataFolder;

    public ChargeBarManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }
    
    // -------- Data Persistence --------
    
    private File getPlayerFile(UUID playerId) {
        return new File(playerDataFolder, playerId.toString() + ".yml");
    }
    
    /**
     * Save a player's mana data to disk.
     */
    public void savePlayerData(UUID playerId) {
        PlayerChargeState state = states.get(playerId);
        if (state == null) return;
        
        File file = getPlayerFile(playerId);
        YamlConfiguration config = new YamlConfiguration();
        
        config.set("manaUnlocked", state.manaUnlocked);
        config.set("hasReceivedManaMessage", state.hasReceivedManaMessage);
        config.set("fullChargeCount", state.fullChargeCount);
        config.set("manaRunUnlocked", state.manaRunUnlocked);
        config.set("manaClimbUnlocked", state.manaClimbUnlocked);
        config.set("manaDashUnlocked", state.manaDashUnlocked);
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data for " + playerId + ": " + e.getMessage());
        }
    }
    
    /**
     * Load a player's mana data from disk into their state.
     */
    private void loadPlayerData(UUID playerId, PlayerChargeState state) {
        File file = getPlayerFile(playerId);
        if (!file.exists()) return;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        state.manaUnlocked = config.getBoolean("manaUnlocked", false);
        state.hasReceivedManaMessage = config.getBoolean("hasReceivedManaMessage", false);
        state.fullChargeCount = config.getInt("fullChargeCount", 0);
        state.manaRunUnlocked = config.getBoolean("manaRunUnlocked", false);
        state.manaClimbUnlocked = config.getBoolean("manaClimbUnlocked", false);
        state.manaDashUnlocked = config.getBoolean("manaDashUnlocked", false);
        
        plugin.getLogger().info("Loaded mana data for player " + playerId + " (charges: " + state.fullChargeCount + ")");
    }
    
    /**
     * Save all online players' data (call on plugin disable).
     */
    public void saveAllPlayerData() {
        for (UUID playerId : states.keySet()) {
            savePlayerData(playerId);
        }
        plugin.getLogger().info("Saved mana data for " + states.size() + " players.");
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        PlayerChargeState state = states.computeIfAbsent(id, k -> createState(player));

        if (!state.manaUnlocked) {
            return;
        }
        
        // Check if mana is disrupted
        if (System.currentTimeMillis() < state.manaDisruptedUntil) {
            if (event.isSneaking()) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§c✖ Mana Disrupted! ✖"));
            }
            return;
        }
        
        // Check if on charge cooldown (just hit full charge)
        long now = System.currentTimeMillis();
        if (now < state.chargeCooldownUntil) {
            // Can't charge during cooldown, but bar stays visible and drains
            state.charging = false;
            if (state.bar != null) {
                state.bar.setVisible(true);
            }
            return;
        }
        
        // Reset the full charge flag when starting a new charge cycle
        if (event.isSneaking() && state.bar != null && state.bar.getProgress() < 0.5) {
            state.reachedFullCharge = false;
        }

        state.charging = event.isSneaking();

        if (event.isSneaking()) {
            if (state.bar != null) {
                state.bar.setVisible(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Pre-load player data when they join
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        PlayerChargeState state = states.computeIfAbsent(id, k -> createState(player));
        loadPlayerData(id, state);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Save player data before removing their state
        savePlayerData(id);
        PlayerChargeState state = states.remove(id);
        if (state != null && state.bar != null) {
            state.bar.removeAll();
        }
    }

    private PlayerChargeState createState(Player player) {
        PlayerChargeState state = new PlayerChargeState();

        BossBar bar = Bukkit.createBossBar(
                "Mana",
                BarColor.BLUE,
                BarStyle.SEGMENTED_10
        );
        bar.addPlayer(player);
        bar.setVisible(false);
        bar.setProgress(0.0);

        state.bar = bar;
        state.charging = false;
        state.manaUnlocked = plugin.getConfig().getBoolean("mana.auto-unlock", false);
        state.hasReceivedManaMessage = false;
        state.lastChargeSound = 0L;

        return state;
    }

    public void tickAllBars(double baseChargeRate, double baseDrainRate) {
        Iterator<Map.Entry<UUID, PlayerChargeState>> it = states.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, PlayerChargeState> entry = it.next();
            UUID id = entry.getKey();
            PlayerChargeState state = entry.getValue();

            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                if (state.bar != null) {
                    state.bar.removeAll();
                }
                it.remove();
                continue;
            }

            BossBar bar = state.bar;
            if (bar == null) continue;

            if (!state.manaUnlocked) {
                bar.setProgress(0.0);
                bar.setVisible(false);
                continue;
            }
            
            // Get full charge count for scaling (capped at MAX_SCALING_CHARGES)
            int charges = Math.min(state.fullChargeCount, MAX_SCALING_CHARGES);
            
            // Scale charge rate: +0.5% per charge (200 charges = 100% faster charging)
            double chargeMultiplier = 1.0 + (charges * 0.005);
            double chargeRate = baseChargeRate * chargeMultiplier;
            
            // Scale drain rate: -0.3% per charge (200 charges = 60% slower drain, capped at 70% reduction)
            double drainMultiplier = Math.max(0.3, 1.0 - (charges * 0.003));
            double drainRate = baseDrainRate * drainMultiplier;

            double oldProgress = bar.getProgress();
            double progress = oldProgress;

            long now = System.currentTimeMillis();
            boolean onCooldown = now < state.chargeCooldownUntil;
            boolean chargingNow = state.charging && player.isSneaking() && !onCooldown;

            if (chargingNow && !state.reachedFullCharge) {
                progress += chargeRate;
            } else {
                // Always drain when not actively charging (including during cooldown)
                progress -= drainRate;
            }

            if (progress < 0.0) progress = 0.0;
            if (progress > 1.0) progress = 1.0;

            // Charging sound (soft chime) while bar is actually increasing
            if (chargingNow && progress > oldProgress && !state.reachedFullCharge) {
                now = System.currentTimeMillis();
                if (now - state.lastChargeSound >= 200L) { // every 0.2s for smoother sound
                    // Pitch increases as charge fills
                    float pitch = 0.8f + (float)(progress * 1.2f);
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_CHIME,
                            0.4f,
                            pitch
                    );
                    state.lastChargeSound = now;
                }
            }
            
            // Sound when charge is full
            if (progress >= 1.0 && !state.reachedFullCharge) {
                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.8f,
                        2.0f
                );
                
                // Training system - count full charges
                state.fullChargeCount++;
                checkAbilityUnlocks(player, state);
                
                // Set cooldown - scales down with full charge count
                // 0 charges = 1500ms, 20 charges = ~900ms, 40+ charges = 300ms min
                charges = Math.min(state.fullChargeCount, MAX_SCALING_CHARGES);
                long cooldown = Math.max(MIN_COOLDOWN_MS, BASE_COOLDOWN_MS - (charges * 30L));
                
                state.reachedFullCharge = true;
                state.chargeCooldownUntil = System.currentTimeMillis() + cooldown;
                state.charging = false;
            }

            bar.setProgress(progress);

            // stays visible until drained
            if (progress <= 0.0 && !state.charging) {
                bar.setVisible(false);
            }
        }
    }

    public void clearAll() {
        for (PlayerChargeState state : states.values()) {
            if (state.bar != null) {
                state.bar.removeAll();
            }
        }
        states.clear();
    }

    // -------- Public API --------

    public void unlockMana(Player player) {
        PlayerChargeState state = states.computeIfAbsent(player.getUniqueId(), k -> createState(player));
        boolean wasUnlocked = state.manaUnlocked;
        state.manaUnlocked = true;
        
        // Only show message once when first unlocked
        if (!wasUnlocked && !state.hasReceivedManaMessage) {
            state.hasReceivedManaMessage = true;
            player.sendMessage(net.kyori.adventure.text.Component.text("§d✦ Your mana has been awakened! ✦"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    public boolean hasManaUnlocked(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        return state != null && state.manaUnlocked;
    }

    public double getChargeProgress(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (state == null || state.bar == null || !state.manaUnlocked) {
            return 0.0;
        }
        return state.bar.getProgress();
    }

    public void resetCharge(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (state == null || state.bar == null) return;
        state.bar.setProgress(0.0);
        if (!state.charging) {
            state.bar.setVisible(false);
        }
    }

    /** Consume [amount] charge; returns false if not enough. */
    public boolean consumeCharge(Player player, double amount) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (state == null || state.bar == null || !state.manaUnlocked) return false;

        double progress = state.bar.getProgress();
        if (progress < amount) return false;

        progress -= amount;
        if (progress < 0.0) progress = 0.0;

        state.bar.setProgress(progress);
        if (progress <= 0.0 && !state.charging) {
            state.bar.setVisible(false);
        }
        return true;
    }

    // -------- Mana Disruption --------
    
    /**
     * Disrupt a player's mana for the specified duration (milliseconds).
     * Prevents them from charging mana.
     */
    public void disruptMana(Player player, long durationMs) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (state == null) return;
        
        state.manaDisruptedUntil = System.currentTimeMillis() + durationMs;
        state.charging = false;
        
        // Force stop charging visually
        if (state.bar != null) {
            state.bar.setTitle("§c✖ DISRUPTED ✖");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state.bar != null) {
                    state.bar.setTitle("Mana");
                }
            }, durationMs / 50); // Convert ms to ticks
        }
    }
    
    public boolean isManaDisrupted(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (state == null) return false;
        return System.currentTimeMillis() < state.manaDisruptedUntil;
    }

    // -------- Training System --------
    
    private void checkAbilityUnlocks(Player player, PlayerChargeState state) {
        // Check Mana Run unlock
        if (!state.manaRunUnlocked && state.fullChargeCount >= MANA_RUN_THRESHOLD) {
            state.manaRunUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY UNLOCKED: §b§lMANA RUN §6§l✦");
            player.sendMessage("§7Sprint while releasing sneak with 30%+ mana to dash forward!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            plugin.getLogger().info(player.getName() + " unlocked Mana Run! (" + state.fullChargeCount + " charges)");
        }
        
        // Check Mana Climb unlock
        if (!state.manaClimbUnlocked && state.fullChargeCount >= MANA_CLIMB_THRESHOLD) {
            state.manaClimbUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY UNLOCKED: §a§lMANA CLIMB §6§l✦");
            player.sendMessage("§7Sprint into a wall while sneaking to climb!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            plugin.getLogger().info(player.getName() + " unlocked Mana Climb! (" + state.fullChargeCount + " charges)");
        }
        
        // Check Mana Dash unlock
        if (!state.manaDashUnlocked && state.fullChargeCount >= MANA_DASH_THRESHOLD) {
            state.manaDashUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY UNLOCKED: §d§lMANA DASH §6§l✦");
            player.sendMessage("§7Double-tap sneak while moving to dash!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            plugin.getLogger().info(player.getName() + " unlocked Mana Dash! (" + state.fullChargeCount + " charges)");
        }
    }
    
    public boolean hasManaRunUnlocked(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        // If training is disabled in config, always return true
        if (!plugin.getConfig().getBoolean("training.enabled", true)) return true;
        return state != null && state.manaRunUnlocked;
    }
    
    public boolean hasManaClimbUnlocked(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("training.enabled", true)) return true;
        return state != null && state.manaClimbUnlocked;
    }
    
    public boolean hasManaDashUnlocked(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("training.enabled", true)) return true;
        return state != null && state.manaDashUnlocked;
    }
    
    public int getFullChargeCount(Player player) {
        PlayerChargeState state = states.get(player.getUniqueId());
        return state != null ? state.fullChargeCount : 0;
    }
    
    public void setFullChargeCount(Player player, int count) {
        PlayerChargeState state = states.computeIfAbsent(player.getUniqueId(), k -> createState(player));
        state.fullChargeCount = Math.max(0, count);
        // Check ability unlocks based on new count
        checkAbilityUnlocks(player, state);
    }
    
    // -------- Admin Grant Methods --------
    
    public void grantManaRun(Player player) {
        PlayerChargeState state = states.computeIfAbsent(player.getUniqueId(), k -> createState(player));
        if (!state.manaRunUnlocked) {
            state.manaRunUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY GRANTED: §b§lMANA RUN §6§l✦");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    public void grantManaClimb(Player player) {
        PlayerChargeState state = states.computeIfAbsent(player.getUniqueId(), k -> createState(player));
        if (!state.manaClimbUnlocked) {
            state.manaClimbUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY GRANTED: §a§lMANA CLIMB §6§l✦");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    public void grantManaDash(Player player) {
        PlayerChargeState state = states.computeIfAbsent(player.getUniqueId(), k -> createState(player));
        if (!state.manaDashUnlocked) {
            state.manaDashUnlocked = true;
            player.sendMessage("§6§l✦ ABILITY GRANTED: §d§lMANA DASH §6§l✦");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    public Plugin getPlugin() {
        return plugin;
    }
}
