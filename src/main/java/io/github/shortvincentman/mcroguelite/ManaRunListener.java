package io.github.shortvincentman.mcroguelite;

import io.github.shortvincentman.mcroguelite.util.ManaColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Simple mana run system:
 * - While player has mana in their bar, they get Speed I
 * - Particles match their mana color (based on full charge count)
 * - No activation needed - passive effect while mana > 0
 */
public class ManaRunListener {
    private final Mcroguelite plugin;
    private final ChargeBarManager chargeBarManager;
    private BukkitRunnable tickTask;

    public ManaRunListener(Mcroguelite plugin, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.chargeBarManager = chargeBarManager;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        };
        tickTask.runTaskTimer(plugin, 0L, 10L); // Every 0.5 seconds
    }

    private void processPlayer(Player player) {
        // Must have mana unlocked and Mana Run trained
        if (!chargeBarManager.hasManaUnlocked(player)) return;
        if (!chargeBarManager.hasManaRunUnlocked(player)) return;

        double mana = chargeBarManager.getChargeProgress(player);
        
        // If player has mana, give speed effect and particles
        if (mana > 0.01) {
            // Apply Speed I effect (lasts 15 ticks = 0.75s, refreshes every 10 ticks)
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    15, // duration in ticks
                    0,  // Speed I (amplifier 0)
                    true,  // ambient (less intrusive particles)
                    false, // no default particles
                    true   // show icon
            ));

            // Spawn mana-colored particles at feet while moving
            if (player.isSprinting()) {
                int fullCharges = chargeBarManager.getFullChargeCount(player);
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        player.getLocation().add(0, 0.1, 0),
                        3, 0.15, 0.05, 0.15, 0,
                        ManaColorUtil.getDustOptions(fullCharges, 0.8f)
                );
            }
        }
    }

    public boolean isRunning(Player player) {
        double mana = chargeBarManager.getChargeProgress(player);
        return mana > 0.01 && chargeBarManager.hasManaRunUnlocked(player);
    }

    public void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
        }
    }
}
