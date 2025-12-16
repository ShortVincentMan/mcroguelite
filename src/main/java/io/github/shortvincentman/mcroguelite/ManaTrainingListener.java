package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaTrainingListener implements Listener {

    private final ChargeBarManager chargeBarManager;
    private final ManaManager manaManager;

    private final Map<UUID, Double> lastProgress = new HashMap<>();

    public ManaTrainingListener(Plugin plugin, ChargeBarManager chargeBarManager, ManaManager manaManager) {
        this.chargeBarManager = chargeBarManager;
        this.manaManager = manaManager;

        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickTraining,
                0L,
                4L // ~0.2s
        );
    }

    private void tickTraining() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            double current = chargeBarManager.getChargeProgress(player);
            double last = lastProgress.getOrDefault(player.getUniqueId(), 0.0);

            // Full bar reached while sneaking → training "tick"
            if (last < 1.0 && current >= 1.0 && player.isSneaking()) {
                manaManager.addManaLevel(player, 1);
                player.sendMessage("§bYour mana attunement grows. Level: " + manaManager.getManaLevel(player));
                chargeBarManager.resetCharge(player);
            }

            lastProgress.put(player.getUniqueId(), current);
        }
    }
}
