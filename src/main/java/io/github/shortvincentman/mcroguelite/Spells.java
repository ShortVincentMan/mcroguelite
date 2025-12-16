package io.github.shortvincentman.mcroguelite;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Spells {

    // Fire bolt that burns and can unlock mana on hit
    public static void castIgnis(Player player, double charge, ChargeBarManager barManager) {
        double min = 0.4;
        double max = 0.8;

        if (charge < min || charge > max) {
            backfire(player, barManager);
            return;
        }

        Location loc = player.getEyeLocation().clone();
        World world = player.getWorld();
        Vector dir = loc.getDirection().normalize();

        double range = 12.0;
        double step = 0.75;
        for (double d = 0; d < range; d += step) {
            loc.add(dir.clone().multiply(step));
            world.spawnParticle(Particle.FLAME, loc, 4, 0.1, 0.1, 0.1, 0.01);

            LivingEntity hit = findHitEntity(player, loc, 1.0);
            if (hit != null) {
                hit.damage(4.0, player);
                hit.setFireTicks(40);
                world.playSound(hit.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 1.2f);
                if (hit instanceof Player target) {
                    barManager.unlockMana(target);
                    target.sendMessage("§5Your mana awakens from the burn.");
                }
                break;
            }
        }

        barManager.resetCharge(player);
        player.sendMessage("§6Ignis cast.");
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.2f);
    }

    // Mobility leap spell
    public static void castHoppa(Player player, double charge, ChargeBarManager barManager) {
        double min = 0.3;
        double max = 0.7;

        if (charge < min || charge > max) {
            backfire(player, barManager);
            return;
        }

        Vector dir = player.getLocation().getDirection().normalize();
        player.setVelocity(dir.multiply(1.8).setY(0.8));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.2, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);

        barManager.resetCharge(player);
    }

    // Radial knockback wave spell
    public static void castWard(Player player, double charge, ChargeBarManager barManager) {
        double min = 0.1;
        double max = 0.6;

        if (charge < min || charge > max) {
            backfire(player, barManager);
            return;
        }

        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);

        double radius = 5.0;
        for (LivingEntity le : world.getLivingEntities()) {
            if (le.equals(player)) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;

            Vector knock = le.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.6);
            knock.setY(0.4);
            le.setVelocity(knock);
        }

        world.spawnParticle(Particle.SWEEP_ATTACK, center, 20, 1.5, 0.5, 1.5, 0.01);
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.2f);

        barManager.resetCharge(player);
    }

    // Utility spell to unlock another player's mana (for testing)
    public static void castUnlock(Player player, double charge, ChargeBarManager barManager) {
        double min = 0.2;
        double max = 1.0;

        if (charge < min || charge > max) {
            backfire(player, barManager);
            return;
        }

        Location loc = player.getEyeLocation();
        World world = player.getWorld();
        Vector dir = loc.getDirection().normalize();

        Player target = null;
        double bestDist = Double.MAX_VALUE;

        for (Player other : world.getPlayers()) {
            if (other.equals(player)) continue;
            double dist = other.getLocation().distance(player.getLocation());
            if (dist > 10) continue;

            Vector to = other.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).normalize();
            if (dir.dot(to) < 0.5) continue;

            if (dist < bestDist) {
                bestDist = dist;
                target = other;
            }
        }

        if (target == null) {
            player.sendMessage("§7No one to unlock.");
            return;
        }

        barManager.unlockMana(target);
        target.sendMessage("§dYou feel a strange mana surge.");
        player.sendMessage("§aYou unlock " + target.getName() + "'s mana.");

        world.spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.02);
        world.playSound(target.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

        barManager.resetCharge(player);
    }

    // -------- Helpers --------

    private static void backfire(Player player, ChargeBarManager barManager) {
        player.sendMessage("§4The spell backfires!");
        player.damage(2.0);
        player.setFireTicks(20);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                player.getLocation().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        barManager.resetCharge(player);
    }

    private static LivingEntity findHitEntity(Player caster, Location point, double radius) {
        double r2 = radius * radius;
        LivingEntity best = null;
        for (LivingEntity le : point.getWorld().getLivingEntities()) {
            if (le.equals(caster)) continue;
            double dist2 = le.getLocation().distanceSquared(point);
            if (dist2 <= r2) {
                best = le;
                r2 = dist2;
            }
        }
        return best;
    }
}
