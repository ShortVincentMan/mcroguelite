package io.github.shortvincentman.mcroguelite;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatListener implements Listener {

    private final ChargeBarManager chargeBarManager;

    public CombatListener(ChargeBarManager chargeBarManager) {
        this.chargeBarManager = chargeBarManager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) return;

        // Only unarmed
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        if (!chargeBarManager.hasManaUnlocked(player)) return;

        double charge = chargeBarManager.getChargeProgress(player);
        if (charge <= 0.0) return;

        double baseDamage = event.getDamage();
        double multiplier = 1.0 + 0.5 * charge; // up to +50% at full charge
        event.setDamage(baseDamage * multiplier);

        // Visual / audio feedback for mana punch
        event.getEntity().getWorld().spawnParticle(
                Particle.CRIT,
                event.getEntity().getLocation().add(0, 1.0, 0),
                20,
                0.3, 0.3, 0.3,
                0.1
        );
        event.getEntity().getWorld().playSound(
                event.getEntity().getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                1f,
                1.4f
        );
    }
}
