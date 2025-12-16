package io.github.shortvincentman.mcroguelite;

import io.github.shortvincentman.mcroguelite.util.ManaColorUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CombatListener implements Listener {

    private final Mcroguelite plugin;
    private final ChargeBarManager chargeBarManager;
    
    // Mana disruption duration in milliseconds (2.5 seconds like wiki says ~2 seconds)
    private static final long MANA_DISRUPTION_MS = 2500;

    public CombatListener(Mcroguelite plugin, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.chargeBarManager = chargeBarManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Check for Tenebris vulnerability on the damaged entity
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        NamespacedKey vulnKey = new NamespacedKey(plugin, "tenebris_vulnerability");
        
        if (pdc.has(vulnKey, PersistentDataType.DOUBLE)) {
            Double multiplier = pdc.get(vulnKey, PersistentDataType.DOUBLE);
            if (multiplier != null && multiplier > 1.0) {
                // Increase damage taken
                double newDamage = event.getDamage() * multiplier;
                event.setDamage(newDamage);
                
                // Purple damage indicator particles
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0),
                        8, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(128, 0, 255), 1.0f));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        
        // Check if holding Scrupus sword
        if (heldItem.getType() == Material.STONE_SWORD && heldItem.hasItemMeta()) {
            ItemMeta meta = heldItem.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey hitsKey = new NamespacedKey(plugin, "scrupus_hits");
            
            if (pdc.has(hitsKey, PersistentDataType.INTEGER)) {
                Integer hitsLeft = pdc.get(hitsKey, PersistentDataType.INTEGER);
                if (hitsLeft != null) {
                    hitsLeft--;
                    
                    if (hitsLeft <= 0) {
                        // Sword shatters
                        player.getInventory().setItemInMainHand(null);
                        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0),
                                30, 0.4, 0.4, 0.4, 0.1, Material.STONE.createBlockData());
                        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                        player.sendMessage("§7Your Mana-Forged Stone Blade has shattered!");
                    } else {
                        // Update hits remaining
                        pdc.set(hitsKey, PersistentDataType.INTEGER, hitsLeft);
                        
                        // Update lore to show remaining hits
                        java.util.List<net.kyori.adventure.text.Component> lore = meta.lore();
                        if (lore != null && lore.size() >= 3) {
                            lore.set(2, net.kyori.adventure.text.Component.text(hitsLeft + " hits remaining", 
                                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                            meta.lore(lore);
                        }
                        
                        heldItem.setItemMeta(meta);
                        
                        // Stone crack sound on hit
                        player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 1.2f);
                    }
                }
                return; // Don't apply mana punch effects when using scrupus sword
            }
        }

        // Only unarmed for mana punch
        if (heldItem.getType() != Material.AIR) return;
        if (!chargeBarManager.hasManaUnlocked(player)) return;

        double charge = chargeBarManager.getChargeProgress(player);
        if (charge <= 0.0) return;

        double baseDamage = event.getDamage();
        double multiplier = 1.0 + 0.5 * charge; // up to +50% at full charge
        event.setDamage(baseDamage * multiplier);

        // MANA DISRUPTION: If target is a player, disrupt their mana!
        if (event.getEntity() instanceof Player target) {
            if (chargeBarManager.hasManaUnlocked(target)) {
                chargeBarManager.disruptMana(target, MANA_DISRUPTION_MS);
                target.sendMessage("§c§l✖ §cYour mana has been disrupted! §c§l✖");
                player.sendMessage("§aYou disrupted " + target.getName() + "'s mana!");
                
                // Extra disruption particles
                target.getWorld().spawnParticle(
                        Particle.SMOKE,
                        target.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0.05
                );
            }
        }

        // Colored particles based on full charge count
        int fullCharges = chargeBarManager.getFullChargeCount(player);
        Color color = ManaColorUtil.getColorForLevel(fullCharges);
        
        // Dust particles with mana color
        event.getEntity().getWorld().spawnParticle(
                Particle.DUST,
                event.getEntity().getLocation().add(0, 1.0, 0),
                20,
                0.3, 0.3, 0.3,
                0,
                ManaColorUtil.getDustOptions(fullCharges, 1.2f)
        );
        
        // Additional ENTITY_EFFECT particles for potion-style burst
        for (int i = 0; i < 10; i++) {
            event.getEntity().getWorld().spawnParticle(
                    Particle.ENTITY_EFFECT,
                    event.getEntity().getLocation().add(
                            (Math.random() - 0.5) * 0.6,
                            1.0 + Math.random() * 0.5,
                            (Math.random() - 0.5) * 0.6
                    ),
                    0,
                    color.getRed() / 255.0,
                    color.getGreen() / 255.0,
                    color.getBlue() / 255.0,
                    1.0
            );
        }
        
        event.getEntity().getWorld().playSound(
                event.getEntity().getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                1f,
                1.4f
        );
    }
}
