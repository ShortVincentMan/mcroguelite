package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChargeBarManager implements Listener {

    private final Plugin plugin;

    private static class PlayerChargeState {
        BossBar bar;
        boolean charging;
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

        state.charging = event.isSneaking();

        if (state.bar != null) {
            state.bar.setVisible(true);
        }
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
                BarStyle.SOLID,
                BarFlag.CREATE_FOG
        );
        bar.addPlayer(player);
        bar.setVisible(false);
        bar.setProgress(0.0);

        state.bar = bar;
        state.charging = false;

        return state;
    }

    public void tickAllBars(double chargeRate, double drainRate) {
        for (Map.Entry<UUID, PlayerChargeState> entry : states.entrySet()) {
            UUID id = entry.getKey();
            PlayerChargeState state = entry.getValue();


            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                if (state.bar != null) {
                    state.bar.removeAll();
                }
                continue;
            }

            BossBar bar = state.bar;
            if (bar == null) continue;

            double progress = bar.getProgress();
            if (state.charging && player.isSneaking()) {
                progress += chargeRate;
            } else {
                progress -= drainRate;
            }

            if (progress < 0.0) progress = 0.0;
            if (progress > 1.0) progress = 1.0;


            bar.setProgress(progress);

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
}

