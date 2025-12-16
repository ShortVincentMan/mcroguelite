package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ChargeBarManager implements Listener {

    private final Plugin plugin;

    static class PlayerChargeState {
        BossBar bar;
        boolean charging;
        boolean manaUnlocked;
        long lastChargeSound;
    }

    private final Map<UUID, PlayerChargeState> states = new HashMap<>();

    public ChargeBarManager(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        PlayerChargeState state = states.computeIfAbsent(id, k -> createState(player));

        if (!state.manaUnlocked) {
            return;
        }

        state.charging = event.isSneaking();

        if (event.isSneaking()) {
            if (state.bar != null) {
                state.bar.setVisible(true);
            }
        }
        // bar hides only when fully drained in tickAllBars()
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
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
        state.manaUnlocked = true; // set to false later if you want "must be hit by spell"
        state.lastChargeSound = 0L;

        return state;
    }

    public void tickAllBars(double chargeRate, double drainRate) {
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

            double oldProgress = bar.getProgress();
            double progress = oldProgress;

            boolean chargingNow = state.charging && player.isSneaking();

            if (chargingNow) {
                progress += chargeRate;
            } else {
                progress -= drainRate;
            }

            if (progress < 0.0) progress = 0.0;
            if (progress > 1.0) progress = 1.0;

            // Charging sound (soft chime) while bar is actually increasing
            if (chargingNow && progress > oldProgress) {
                long now = System.currentTimeMillis();
                if (now - state.lastChargeSound >= 500L) { // every 0.5s max
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.6f,
                            1.6f
                    );
                    state.lastChargeSound = now;
                }
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
        state.manaUnlocked = true;
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
}
