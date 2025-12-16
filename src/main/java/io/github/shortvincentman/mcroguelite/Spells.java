package io.github.shortvincentman.mcroguelite;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

public class Spells {

    public enum SpellResult {
        SUCCESS,
        PERFECT,
        BACKFIRE,
        FIZZLE
    }

    // ==================== IGNIS ====================
    // Large fireball projectile that explodes and sets targets on fire
    public static SpellResult castIgnis(Player player, double timing, double windowStart, double windowEnd, ChargeBarManager barManager) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            backfire(player);
            player.sendMessage("§c§oYour Ignis backfires!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        Location startLoc = player.getEyeLocation().clone();
        World world = player.getWorld();
        Vector direction = startLoc.getDirection().normalize();
        final double finalAccuracy = accuracy;
        final double speed = 0.8;
        final double maxRange = 30.0;
        
        // Launch fireball projectile
        new BukkitRunnable() {
            Location loc = startLoc.clone();
            double traveled = 0;
            
            @Override
            public void run() {
                // Move fireball
                loc.add(direction.clone().multiply(speed));
                traveled += speed;
                
                // Large fireball particle effect
                world.spawnParticle(Particle.FLAME, loc, 15, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.LAVA, loc, 3, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.SMOKE, loc, 8, 0.25, 0.25, 0.25, 0.01);
                
                // Orange/red dust core
                world.spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 100, 0), 1.5f));
                
                // Check for hit on entity
                LivingEntity hit = findHitEntity(player, loc, 1.8);
                if (hit != null) {
                    explodeFireball(loc, player, finalAccuracy, barManager, world);
                    cancel();
                    return;
                }
                
                // Check for hit on block
                if (loc.getBlock().getType().isSolid()) {
                    explodeFireball(loc, player, finalAccuracy, barManager, world);
                    cancel();
                    return;
                }
                
                // Max range reached
                if (traveled >= maxRange) {
                    explodeFireball(loc, player, finalAccuracy, barManager, world);
                    cancel();
                }
            }
        }.runTaskTimer(barManager.getPlugin(), 0L, 1L);

        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }
    
    private static void explodeFireball(Location loc, Player caster, double accuracy, ChargeBarManager barManager, World world) {
        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, loc, 50, 1.5, 1.5, 1.5, 0.1);
        world.spawnParticle(Particle.LAVA, loc, 20, 1.0, 1.0, 1.0, 0);
        world.spawnParticle(Particle.SMOKE, loc, 30, 1.2, 1.2, 1.2, 0.05);
        
        // Explosion sound
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        world.playSound(loc, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.8f);
        
        // Damage and burn entities in blast radius
        double radius = 3.0 + (accuracy * 1.5); // 3-4.5 block radius
        double damage = 5.0 * accuracy;
        int fireTicks = (int)(100 * accuracy); // 2.5-5 seconds of burn
        
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity le && entity != caster) {
                // Distance falloff
                double distance = le.getLocation().distance(loc);
                double falloff = 1.0 - (distance / (radius + 1));
                if (falloff <= 0) continue;
                
                le.damage(damage * falloff, caster);
                le.setFireTicks(Math.max(le.getFireTicks(), (int)(fireTicks * falloff)));
                
                // Knockback from explosion
                Vector knockback = le.getLocation().toVector().subtract(loc.toVector()).normalize();
                knockback.setY(0.3);
                knockback.multiply(0.6 * falloff);
                le.setVelocity(le.getVelocity().add(knockback));
                
                // Unlock mana on hit
                if (le instanceof Player target) {
                    barManager.unlockMana(target);
                    target.sendMessage("§5Your mana awakens from the burn.");
                }
            }
        }
    }

    // Legacy method for backwards compatibility
    public static void castIgnis(Player player, double charge, ChargeBarManager barManager) {
        castIgnis(player, charge, 0.4, 0.8, barManager);
    }

    // ==================== HOPPA ====================
    // Mobility leap spell - launches high into the air with fall damage immunity
    public static SpellResult castHoppa(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - launch up but WITH NO fall damage protection!
            Vector velocity = new Vector(0, 3.0, 0); // Launch straight up high
            player.setVelocity(velocity);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 20, 0.3, 0.2, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
            player.sendMessage("§c§oYour Hoppa misfires - no fall protection!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Launch high into the air
        double launchPower = 2.0 + (accuracy * 1.5); // 2.0 to 3.5 blocks/tick upward
        Vector dir = player.getLocation().getDirection().normalize();
        Vector velocity = dir.multiply(0.5 + accuracy * 0.5); // Slight forward momentum
        velocity.setY(launchPower);
        
        player.setVelocity(velocity);
        
        // Grant fall damage immunity via resistance effect
        // Duration long enough to cover the fall (10 seconds)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                200, // 10 seconds
                4,   // Resistance V = immunity to all damage
                true, // ambient
                false, // no particles
                false // no icon
        ));
        
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0.3, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 20, 0.3, 0.2, 0.3, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // Legacy
    public static void castHoppa(Player player, double charge, ChargeBarManager barManager) {
        castHoppa(player, charge, 0.3, 0.7);
    }

    // ==================== WARD ====================
    // Defensive shield spell
    public static SpellResult castWard(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            player.sendMessage("§c§oYour Ward collapses on you!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        int duration = (int) (100 * accuracy);
        int amplifier = accuracy > 0.8 ? 1 : 0;

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0));

        // Also knockback nearby enemies
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);
        double radius = 3.0 + (accuracy * 2);

        for (LivingEntity le : world.getLivingEntities()) {
            if (le.equals(player)) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;

            Vector knock = le.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.5 * accuracy);
            knock.setY(0.3);
            le.setVelocity(knock);
        }

        world.spawnParticle(Particle.ENCHANT, center, 30, 0.5, 0.5, 0.5, 0.5);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // Legacy
    public static void castWard(Player player, double charge, ChargeBarManager barManager) {
        castWard(player, charge, 0.1, 0.6);
    }

    // ==================== UNLOCK ====================
    // Utility spell to unlock another player's mana
    public static SpellResult castUnlock(Player player, double timing, double windowStart, double windowEnd, ChargeBarManager barManager) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
            player.sendMessage("§c§oThe unlock magic fizzles!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

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
            return SpellResult.FIZZLE;
        }

        barManager.unlockMana(target);
        target.sendMessage("§dYou feel a strange mana surge.");
        player.sendMessage("§aYou unlock " + target.getName() + "'s mana.");

        world.spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.02);
        world.playSound(target.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // Legacy
    public static void castUnlock(Player player, double charge, ChargeBarManager barManager) {
        castUnlock(player, charge, 0.2, 1.0, barManager);
    }

    // ==================== SNAP ====================
    // Teleport spell
    public static SpellResult castSnap(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_HURT, 1.0f, 0.5f);
            player.sendMessage("§c§oReality tears at you - failed teleport!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        double maxRange = 20 + (accuracy * 15); // 20-35 blocks based on accuracy
        Location targetLoc = player.getLocation().clone();
        
        // Raycast to where cursor is pointing
        var rayResult = player.rayTraceBlocks(maxRange);
        
        if (rayResult != null && rayResult.getHitBlock() != null) {
            // Hit a block - teleport to the face we hit
            Location hitLoc = rayResult.getHitPosition().toLocation(player.getWorld());
            org.bukkit.block.BlockFace face = rayResult.getHitBlockFace();
            
            // Offset based on hit face to land on top/beside block
            if (face != null) {
                hitLoc.add(face.getModX() * 0.6, face.getModY() * 0.6, face.getModZ() * 0.6);
            }
            
            // Find safe spot (2 blocks of air for player)
            Location check = hitLoc.clone();
            Block footBlock = check.getBlock();
            Block headBlock = check.clone().add(0, 1, 0).getBlock();
            
            // If landing in solid, try to find nearby air
            if (!footBlock.getType().isAir() || !headBlock.getType().isAir()) {
                // Try going up
                for (int i = 0; i < 3; i++) {
                    check.add(0, 1, 0);
                    if (check.getBlock().getType().isAir() && check.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                        hitLoc = check;
                        break;
                    }
                }
            }
            
            targetLoc = hitLoc;
        } else {
            // No block hit - teleport to max range in that direction
            targetLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(maxRange));
        }
        
        // Keep original orientation
        targetLoc.setYaw(player.getLocation().getYaw());
        targetLoc.setPitch(player.getLocation().getPitch());

        // Effects at origin
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        // Teleport
        player.teleport(targetLoc);

        // Effects at destination
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== FIMBULVETR ====================
    // Ice projectile spell - God Spell with lightning and ice blocks
    public static SpellResult castFimbulvetr(Player player, double timing, double windowStart, double windowEnd, ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - freeze yourself and take half your max health as damage!
            double halfMaxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() / 2.0;
            player.damage(halfMaxHealth);
            player.setFreezeTicks(300); // 15 seconds frozen
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            player.sendMessage("§b§oThe ice consumes you!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Lightning effect at casting spot
        player.getWorld().strikeLightningEffect(player.getLocation());
        
        Vector direction = player.getLocation().getDirection().normalize();
        Location loc = player.getEyeLocation().clone();
        final double finalAccuracy = accuracy;
        final ChargeBarManager finalChargeBarManager = chargeBarManager;

        new BukkitRunnable() {
            Location current = loc.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40) {
                    explodeIceWithBlocks(current, player, finalAccuracy, finalChargeBarManager, plugin);
                    cancel();
                    return;
                }

                current.add(direction.clone().multiply(1.5));
                current.getWorld().spawnParticle(Particle.SNOWFLAKE, current, 5, 0.1, 0.1, 0.1, 0.02);

                // Check for entity hit
                for (Entity entity : current.getWorld().getNearbyEntities(current, 1, 1, 1)) {
                    if (entity instanceof LivingEntity && entity != player) {
                        explodeIceWithBlocks(current, player, finalAccuracy, finalChargeBarManager, plugin);
                        cancel();
                        return;
                    }
                }

                // Check for block hit
                if (!current.getBlock().getType().isAir()) {
                    explodeIceWithBlocks(current, player, finalAccuracy, finalChargeBarManager, plugin);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Ice casting sound alongside lightning
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    private static void explodeIceWithBlocks(Location loc, Player caster, double accuracy, ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        World world = loc.getWorld();
        
        // Particles
        world.spawnParticle(Particle.SNOWFLAKE, loc, 50, 2, 2, 2, 0.1);
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);

        // Damage nearby entities
        Collection<Entity> nearby = world.getNearbyEntities(loc, 4, 4, 4);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && entity != caster) {
                LivingEntity target = (LivingEntity) entity;
                target.damage(6.0 * accuracy, caster);
                target.setFreezeTicks((int) (140 * accuracy));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (80 * accuracy), 1));
                
                // Unlock mana on player hit (curse spell)
                if (target instanceof Player hitPlayer) {
                    chargeBarManager.unlockMana(hitPlayer);
                    hitPlayer.sendMessage("§b§oThe frost awakens something within you...");
                }
            }
        }
        
        // Place 7x5x7 ice structure (7 wide, 5 up, 7 deep)
        Location center = loc.getBlock().getLocation();
        java.util.List<Block> placedIce = new java.util.ArrayList<>();
        
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    // Only replace air blocks to avoid griefing
                    if (block.getType().isAir()) {
                        block.setType(org.bukkit.Material.ICE);
                        placedIce.add(block);
                    }
                }
            }
        }
        
        // Schedule ice removal after 3 seconds
        if (!placedIce.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Block block : placedIce) {
                        if (block.getType() == org.bukkit.Material.ICE) {
                            block.setType(org.bukkit.Material.AIR);
                            block.getWorld().spawnParticle(Particle.BLOCK, 
                                    block.getLocation().add(0.5, 0.5, 0.5), 
                                    8, 0.3, 0.3, 0.3, 0.05,
                                    org.bukkit.Material.ICE.createBlockData());
                        }
                    }
                    // Play ice break sound once for all blocks
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
                }
            }.runTaskLater(plugin, 180L); // 9 seconds = 180 ticks
        }
    }

    private static void explodeIce(Location loc, Player caster, double accuracy) {
        loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 50, 2, 2, 2, 0.1);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);

        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 4, 4, 4);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && entity != caster) {
                LivingEntity target = (LivingEntity) entity;
                target.damage(6.0 * accuracy, caster);
                target.setFreezeTicks((int) (140 * accuracy));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (80 * accuracy), 1));
            }
        }
    }

    // ==================== PERFLORA ====================
    // Healing nature spell
    public static SpellResult castPerflora(Player player, double timing, double windowStart, double windowEnd, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.5f);
            player.sendMessage("§c§oThe nature magic turns toxic!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        int duration = (int) (100 * accuracy);
        int regenAmp = accuracy > 0.8 ? 1 : 0;

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, regenAmp));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0));

        // Heal nearby allies if good timing
        if (accuracy > 0.6) {
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (entity instanceof Player ally && ally != player) {
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration / 2, 0));
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                }
            }
        }

        // Flower particles
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1.0f, 1.2f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== SNARVINDUR (Wind Knockback) ====================
    // Wind knockback spell with snap/clap sound
    public static SpellResult castSnarvindur(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            Vector knockback = player.getLocation().getDirection().multiply(-3.0);
            knockback.setY(0.8);
            player.setVelocity(knockback);
            player.damage(3.0);
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);
            player.sendMessage("§c§oThe wind blasts you backwards!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        Location center = player.getLocation();
        double radius = 5 + (accuracy * 5); // 5-10 block radius (increased)
        double force = 1.8 + (accuracy * 2.2); // Much stronger knockback (1.8-4.0)

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity le) {
                Vector direction = entity.getLocation().toVector().subtract(center.toVector()).normalize();
                direction.setY(0.6); // Higher vertical component
                direction.multiply(force);
                entity.setVelocity(direction);
                le.damage(3.0 * accuracy, player);
            }
        }

        // Wind effects
        player.getWorld().spawnParticle(Particle.CLOUD, center.add(0, 1, 0), 60, 3, 1.5, 3, 0.15);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 15, 3, 1, 3, 0);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0);
        
        // Snap/clap sounds
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== TENEBRIS (Shadow Mark) ====================
    // Marks target with purple glow and makes them take more damage for 1 minute
    public static SpellResult castTenebris(Player player, double timing, double windowStart, double windowEnd, ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0)); // YOU get marked instead
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
            player.sendMessage("§5§oThe shadow marks YOU instead!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Raycast to find target location
        Location trapLoc;
        var result = player.rayTraceBlocks(15.0);
        if (result != null && result.getHitBlock() != null) {
            trapLoc = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
        } else {
            trapLoc = player.getLocation().add(player.getLocation().getDirection().multiply(5));
        }

        final Location finalTrapLoc = trapLoc;
        final double finalAccuracy = accuracy;
        final ChargeBarManager finalChargeBarManager = chargeBarManager;

        // Visual indicator - purple particles
        player.getWorld().spawnParticle(Particle.DUST, trapLoc, 20, 0.5, 0.1, 0.5, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 255), 1.2f));

        // Trap duration task - waits up to 1 minute for trigger
        new BukkitRunnable() {
            int ticks = 0;
            boolean triggered = false;

            @Override
            public void run() {
                if (ticks++ > 1200 || triggered) { // 60 second trap duration (1 minute)
                    cancel();
                    return;
                }

                // Ambient purple particles
                if (ticks % 15 == 0) {
                    finalTrapLoc.getWorld().spawnParticle(Particle.DUST, finalTrapLoc, 5, 0.4, 0.2, 0.4, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 255), 1.0f));
                    finalTrapLoc.getWorld().spawnParticle(Particle.WITCH, finalTrapLoc, 2, 0.3, 0.1, 0.3, 0.01);
                }

                // Check for entities
                for (Entity entity : finalTrapLoc.getWorld().getNearbyEntities(finalTrapLoc, 1.5, 2, 1.5)) {
                    if (entity instanceof LivingEntity le && entity != player) {
                        // Apply Tenebris mark - lasts 1 minute
                        int markDuration = (int)(1200 * finalAccuracy); // Up to 60 seconds
                        
                        // Purple glow via team color
                        applyPurpleGlow(le, markDuration, plugin);
                        
                        // Weakness makes them take more damage (via reduced resistance)
                        // Note: There's no "vulnerability" in vanilla, so we use a custom approach
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, markDuration, 0));
                        
                        // Initial damage
                        le.damage(3.0 * finalAccuracy, player);
                        
                        // Apply damage vulnerability via attribute modifier
                        applyDamageVulnerability(le, markDuration, finalAccuracy, plugin);

                        // Trigger effects
                        finalTrapLoc.getWorld().spawnParticle(Particle.DUST, finalTrapLoc, 40, 1.5, 1.5, 1.5, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 255), 1.5f));
                        finalTrapLoc.getWorld().spawnParticle(Particle.WITCH, finalTrapLoc, 30, 1, 1, 1, 0.1);
                        finalTrapLoc.getWorld().playSound(finalTrapLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                        finalTrapLoc.getWorld().playSound(finalTrapLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.5f);
                        
                        if (le instanceof Player target) {
                            // Unlock mana (curse spell)
                            finalChargeBarManager.unlockMana(target);
                            target.sendMessage(net.kyori.adventure.text.Component.text("You've been marked by Tenebris! Your mana awakens...", 
                                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
                        }
                        
                        triggered = true;
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.5f, 1.5f);
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }
    
    private static void applyPurpleGlow(LivingEntity target, int duration, Mcroguelite plugin) {
        // Create or get the purple team for glow effect
        org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team purpleTeam = scoreboard.getTeam("tenebris_mark");
        
        if (purpleTeam == null) {
            purpleTeam = scoreboard.registerNewTeam("tenebris_mark");
            purpleTeam.color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE);
            purpleTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, 
                    org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }
        
        // Add entity to team
        String entryName = target instanceof Player p ? p.getName() : target.getUniqueId().toString();
        purpleTeam.addEntry(entryName);
        
        // Apply glowing
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, false));
        
        // Schedule removal from team
        final org.bukkit.scoreboard.Team finalTeam = purpleTeam;
        new BukkitRunnable() {
            @Override
            public void run() {
                finalTeam.removeEntry(entryName);
            }
        }.runTaskLater(plugin, duration);
    }
    
    private static void applyDamageVulnerability(LivingEntity target, int duration, double accuracy, Mcroguelite plugin) {
        // Increase damage taken by reducing armor effectiveness via negative armor modifier
        // Or we can track marked entities and multiply damage in a listener
        // For simplicity, we'll use the health boost removal approach - give negative health
        // Actually, the cleanest way is to give them weakness which affects their attack AND
        // use a scheduled task to apply periodic "vulnerability" damage ticks
        
        // Purple particle trail while marked
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                
                // Subtle purple particles around marked target
                if (ticks % 10 == 0) {
                    target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 
                            3, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 255), 0.8f));
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Store the vulnerability on the entity using PDC (Persistent Data Container)
        // This can be checked in a damage listener to multiply damage taken
        org.bukkit.persistence.PersistentDataContainer pdc = target.getPersistentDataContainer();
        org.bukkit.NamespacedKey vulnKey = new org.bukkit.NamespacedKey(plugin, "tenebris_vulnerability");
        pdc.set(vulnKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 1.0 + (0.5 * accuracy)); // 25-50% more damage
        
        // Schedule removal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isValid()) {
                    target.getPersistentDataContainer().remove(vulnKey);
                }
            }
        }.runTaskLater(plugin, duration);
    }

    // ==================== HELPERS ====================

    /**
     * Check if timing is within the valid window. Spells MUST be cast within the window.
     */
    private static boolean isWithinWindow(double timing, double windowStart, double windowEnd) {
        return timing >= windowStart && timing <= windowEnd;
    }

    /**
     * Calculate accuracy based on how close to center of window the timing is.
     * Returns 0.7-1.0 for valid casts within window.
     * Returns 0 if outside window (should backfire).
     */
    private static double calculateAccuracy(double timing, double windowStart, double windowEnd) {
        // If outside window, return 0 - this will trigger backfire
        if (timing < windowStart || timing > windowEnd) {
            return 0.0;
        }

        // Inside window - calculate based on distance from center
        double windowCenter = (windowStart + windowEnd) / 2.0;
        double windowSize = windowEnd - windowStart;
        double distanceFromCenter = Math.abs(timing - windowCenter);
        double maxDistance = windowSize / 2.0;
        return 1.0 - (distanceFromCenter / maxDistance) * 0.3; // 0.7 to 1.0 inside window
    }

    private static void backfire(Player player) {
        player.damage(2.0);
        player.setFireTicks(20);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                player.getLocation().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
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

    // ==================== PERCUTIENS (Lightning) ====================
    // Lightning strike spell - wiki mentions lightning attacks
    public static SpellResult castPercutiens(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - strike yourself!
            player.getWorld().strikeLightningEffect(player.getLocation());
            player.damage(8.0);
            player.setFireTicks(60);
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
            player.sendMessage("§e§oLightning strikes YOU!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Raycast to find target location
        Location targetLoc;
        var result = player.rayTraceBlocks(20.0);
        if (result != null && result.getHitBlock() != null) {
            targetLoc = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
        } else {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(15));
        }

        // Clap sound effect when casting
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        
        // Strike lightning
        player.getWorld().strikeLightningEffect(targetLoc);

        // Damage nearby entities
        double radius = 3 + (accuracy * 2);
        double damage = 8 * accuracy;
        for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, radius, radius, radius)) {
            if (entity instanceof LivingEntity le && entity != player) {
                le.damage(damage, player);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(40 * accuracy), 1));
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== CELERITAS (Speed) ====================
    // Speed enhancement spell - wiki: 70-99% cast range, buffs speed
    public static SpellResult castCeleritas(Player player, double timing, double windowStart, double windowEnd) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - severe slowness
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 128)); // Jump boost 128 = can't jump
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            player.sendMessage("§c§oYour legs feel like lead!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        int duration = (int)(200 * accuracy); // Up to 10 seconds
        int amplifier = accuracy > 0.8 ? 2 : 1; // Speed II or III

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
        
        // Also give jump boost for mobility
        if (accuracy > 0.6) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 0));
        }

        // Speed particles
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 20, 0.5, 0.2, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.5f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== VEIL (Channeled Invisibility) ====================
    // Invisibility spell - lasts as long as mana remains, drains until empty
    public static SpellResult castVeil(Player player, double timing, double windowStart, double windowEnd,
                                       ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - glowing and blindness instead
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);
            player.sendMessage("§c§oYou become VISIBLE to everyone!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        final double drainPerTick = 0.008; // 0.8% per tick - slower than Gelidus
        
        // Start channeled invisibility
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                // Check if player still has mana
                double currentMana = chargeBarManager.getChargeProgress(player);
                if (currentMana <= 0.01) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                    player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
                    cancel();
                    return;
                }
                
                // Check if player is still valid
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                
                // Drain mana
                chargeBarManager.consumeCharge(player, drainPerTick);
                
                // Keep invisibility active (refresh every second)
                if (ticks % 20 == 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, false));
                }
                
                // Subtle particles occasionally
                if (ticks % 40 == 0) {
                    player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.01);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Initial effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, false));
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);

        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== GELIDUS (Channeled Frost Breath) ====================
    // Channeled frost cloud that continuously drains mana until empty
    // Applies frostbite damage and slows enemies in AOE
    public static SpellResult castGelidus(Player player, double timing, double windowStart, double windowEnd, 
                                          ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - freeze yourself with powder snow freezing damage
            // Set freeze ticks high (triggers freezing damage over time like powder snow)
            player.setFreezeTicks(player.getMaxFreezeTicks() + 100);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 3));
            // Deal damage (freeze ticks will apply additional freezing damage naturally)
            player.damage(4.0);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 
                    30, 0.5, 0.5, 0.5, 0.05);
            player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1.0f, 0.5f);
            player.sendMessage("§b§oThe frost consumes you!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        final double finalAccuracy = accuracy;
        final double drainPerTick = 0.015; // 1.5% mana per tick (drains fast)
        
        // Start channeled frost breath
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                // Check if player still has mana
                double currentMana = chargeBarManager.getChargeProgress(player);
                if (currentMana <= 0.01) {
                    player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.5f);
                    cancel();
                    return;
                }
                
                // Check if player switched items or is no longer valid
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                
                // Drain mana
                chargeBarManager.consumeCharge(player, drainPerTick);
                
                // Get current look direction for aiming
                Location eyeLoc = player.getEyeLocation();
                World world = player.getWorld();
                Vector dir = eyeLoc.getDirection().normalize();
                
                // Spawn frost cloud/breath in a cone in front of player
                double range = 6.0 + (2.0 * finalAccuracy); // 6-8 block range based on accuracy
                
                for (double d = 1.5; d < range; d += 0.8) {
                    double spread = d * 0.4; // Cone spread
                    Location center = eyeLoc.clone().add(dir.clone().multiply(d));
                    
                    // Large snow/frost particles
                    world.spawnParticle(Particle.SNOWFLAKE, center, 8, spread, spread * 0.5, spread, 0.05);
                    world.spawnParticle(Particle.CLOUD, center, 3, spread * 0.5, spread * 0.3, spread * 0.5, 0.02);
                    
                    // White dust for dense frost effect
                    world.spawnParticle(Particle.DUST, center, 5, spread, spread * 0.5, spread, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 240, 255), 2.0f));
                    
                    // Damage entities in cone
                    for (Entity entity : world.getNearbyEntities(center, spread + 0.8, spread * 0.6 + 0.8, spread + 0.8)) {
                        if (entity instanceof LivingEntity le && entity != player) {
                            // Frostbite damage - use freezeTicks mechanic
                            le.setFreezeTicks(Math.min(le.getFreezeTicks() + 30, 200)); // Stack freeze ticks
                            
                            // Deal freeze damage every few ticks (not every tick to prevent spam)
                            if (ticks % 4 == 0) {
                                // Minecraft freeze damage
                                le.damage(1.5 * finalAccuracy, player);
                                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                                
                                // Unlock mana on player hit (curse spell) - only once per cast
                                if (le instanceof Player hitPlayer && !chargeBarManager.hasManaUnlocked(hitPlayer)) {
                                    chargeBarManager.unlockMana(hitPlayer);
                                    hitPlayer.sendMessage("§b§oThe frost awakens something within you...");
                                }
                            }
                        }
                    }
                }
                
                // Sound effects (not every tick)
                if (ticks % 3 == 0) {
                    world.playSound(eyeLoc, Sound.BLOCK_POWDER_SNOW_STEP, 0.6f, 1.2f);
                }
                if (ticks % 8 == 0) {
                    world.playSound(eyeLoc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.4f, 1.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.playSound(player.getLocation(), Sound.ENTITY_SNOW_GOLEM_AMBIENT, 1.0f, 0.8f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== SCRUPUS (Stone Sword) ====================
    // Summons an enchanted stone sword that deals 7 damage, disappears after 10 hits
    public static SpellResult castScrupus(Player player, double timing, double windowStart, double windowEnd, Mcroguelite plugin) {
        // MUST be within window or backfire (note: Scrupus has 1-100% window so very forgiving)
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - stone spike hits you
            player.damage(5.0);
            player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 
                    20, 0.3, 0.3, 0.3, 0.1, org.bukkit.Material.STONE.createBlockData());
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
            player.sendMessage("§7§oA stone shard stabs you!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        World world = player.getWorld();
        
        // Create enchanted stone sword
        org.bukkit.inventory.ItemStack sword = new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("Mana-Forged Stone Blade", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            
            // Add lore
            java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("Conjured by Scrupus magic", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
            lore.add(net.kyori.adventure.text.Component.text("Deals 7 damage", net.kyori.adventure.text.format.NamedTextColor.AQUA));
            lore.add(net.kyori.adventure.text.Component.text("10 hits remaining", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            meta.lore(lore);
            
            // Add attack damage attribute to deal exactly 7 damage
            // Custom modifiers replace default - base player damage is 1, so +6 = 7 total
            org.bukkit.attribute.AttributeModifier damageModifier = new org.bukkit.attribute.AttributeModifier(
                    org.bukkit.NamespacedKey.minecraft("scrupus_damage"),
                    6.0, // +6 damage (base 1 + 6 = 7 total)
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE, damageModifier);
            
            // Also add attack speed so it feels like a normal sword
            org.bukkit.attribute.AttributeModifier speedModifier = new org.bukkit.attribute.AttributeModifier(
                    org.bukkit.NamespacedKey.minecraft("scrupus_speed"),
                    -2.4, // Standard sword attack speed (4 - 2.4 = 1.6)
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED, speedModifier);
            
            // Track hits remaining using PDC
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new org.bukkit.NamespacedKey(plugin, "scrupus_hits"), org.bukkit.persistence.PersistentDataType.INTEGER, 10);
            
            // Make it glow
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            
            sword.setItemMeta(meta);
        }
        
        // Give to player or drop if inventory full
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(sword);
        } else {
            world.dropItemNaturally(player.getLocation(), sword);
        }
        
        // Effects
        world.spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1,
                org.bukkit.Material.STONE.createBlockData());
        world.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== ARMIS (Disarm Bolt) ====================
    // Shoots a blue bolt that disarms the target (makes them drop their held item)
    public static SpellResult castArmis(Player player, double timing, double windowStart, double windowEnd, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - drop your own item AND main hand item
            org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInMainHand();
            org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
            if (mainHand.getType() != org.bukkit.Material.AIR) {
                player.getWorld().dropItemNaturally(player.getLocation(), mainHand.clone());
                player.getInventory().setItemInMainHand(null);
            }
            if (offHand.getType() != org.bukkit.Material.AIR) {
                player.getWorld().dropItemNaturally(player.getLocation(), offHand.clone());
                player.getInventory().setItemInOffHand(null);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            player.sendMessage("§c§oYou fumble and drop everything!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        Vector direction = player.getLocation().getDirection().normalize();
        Location loc = player.getEyeLocation().clone();
        World world = player.getWorld();
        final double finalAccuracy = accuracy;

        // Blue bolt projectile
        new BukkitRunnable() {
            Location current = loc.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40) { // 2 second max flight
                    cancel();
                    return;
                }

                current.add(direction.clone().multiply(1.5));
                
                // Blue particles
                world.spawnParticle(Particle.DUST, current, 10, 0.15, 0.15, 0.15, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 150, 255), 1.2f));
                world.spawnParticle(Particle.ENCHANT, current, 5, 0.1, 0.1, 0.1, 0.02);

                // Check for entity hit
                for (Entity entity : current.getWorld().getNearbyEntities(current, 1.2, 1.2, 1.2)) {
                    if (entity instanceof LivingEntity le && entity != player) {
                        // Disarm effect
                        if (le instanceof Player target) {
                            org.bukkit.inventory.ItemStack heldItem = target.getInventory().getItemInMainHand();
                            if (heldItem.getType() != org.bukkit.Material.AIR) {
                                // Drop item with limited launch distance (max 3 blocks)
                                Location dropLoc = target.getLocation().add(0, 1, 0);
                                org.bukkit.entity.Item droppedItem = world.dropItem(dropLoc, heldItem.clone());
                                
                                // Set velocity to launch away from caster but limited to ~3 blocks
                                Vector launchDir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                                launchDir.setY(0.3); // Slight upward arc
                                launchDir.multiply(0.4); // Limited velocity for ~3 block max distance
                                droppedItem.setVelocity(launchDir);
                                
                                // Prevent pickup for 5 seconds (100 ticks)
                                droppedItem.setPickupDelay(100);
                                // Also set owner to prevent others from picking it up easily
                                droppedItem.setOwner(target.getUniqueId());
                                
                                target.getInventory().setItemInMainHand(null);
                                target.sendMessage(net.kyori.adventure.text.Component.text("You've been disarmed!", net.kyori.adventure.text.format.NamedTextColor.RED));
                            }
                        } else {
                            // For mobs, deal some damage and knockback
                            le.damage(3.0 * finalAccuracy, player);
                        }
                        
                        // Hit effects
                        world.spawnParticle(Particle.DUST, current, 30, 0.5, 0.5, 0.5, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 180, 255), 1.5f));
                        world.playSound(current, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
                        world.playSound(current, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.2f);
                        cancel();
                        return;
                    }
                }

                // Check for block hit
                if (!current.getBlock().getType().isAir()) {
                    world.spawnParticle(Particle.DUST, current, 15, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 180, 255), 1.0f));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== VIRIBUS (Stone Pillars) ====================
    // Summons stone pillars that deal suffocation damage and knockback
    public static SpellResult castViribus(Player player, double timing, double windowStart, double windowEnd, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - pillar spawns under you and launches you up hard
            player.setVelocity(new Vector(0, 2.5, 0));
            player.damage(5.0);
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
            player.sendMessage("§7§oA pillar erupts beneath you!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Raycast to find target location
        Location targetLoc;
        var result = player.rayTraceBlocks(20.0);
        if (result != null && result.getHitBlock() != null) {
            targetLoc = result.getHitBlock().getLocation().add(0, 1, 0);
        } else {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(10));
        }

        World world = player.getWorld();
        final Location finalTargetLoc = targetLoc;
        final double finalAccuracy = accuracy;
        int pillarCount = (int)(3 + accuracy * 3); // 3-6 pillars based on accuracy
        
        // Spawn pillars in a pattern around target
        java.util.List<Location> pillarLocations = new java.util.ArrayList<>();
        for (int i = 0; i < pillarCount; i++) {
            double angle = (2 * Math.PI / pillarCount) * i;
            double radius = 2.0 + (Math.random() * 1.5);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location pillarLoc = finalTargetLoc.clone().add(x, 0, z);
            
            // Find ground level
            while (!pillarLoc.getBlock().getType().isSolid() && pillarLoc.getY() > world.getMinHeight()) {
                pillarLoc.subtract(0, 1, 0);
            }
            pillarLoc.add(0, 1, 0);
            pillarLocations.add(pillarLoc);
        }

        // Spawn pillars with delay for dramatic effect
        new BukkitRunnable() {
            int index = 0;
            java.util.List<Block> allPillarBlocks = new java.util.ArrayList<>();
            
            @Override
            public void run() {
                if (index >= pillarLocations.size()) {
                    // Schedule removal of all pillars
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (Block block : allPillarBlocks) {
                                if (block.getType() == org.bukkit.Material.STONE || 
                                    block.getType() == org.bukkit.Material.COBBLESTONE) {
                                    block.setType(org.bukkit.Material.AIR);
                                    world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                                            10, 0.3, 0.3, 0.3, 0.05, org.bukkit.Material.STONE.createBlockData());
                                }
                            }
                            world.playSound(finalTargetLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                        }
                    }.runTaskLater(plugin, 80L); // 4 seconds
                    cancel();
                    return;
                }
                
                Location pillarLoc = pillarLocations.get(index);
                int height = 3 + (int)(finalAccuracy * 2); // 3-5 blocks tall
                
                // Create pillar
                java.util.List<Block> pillarBlocks = new java.util.ArrayList<>();
                for (int y = 0; y < height; y++) {
                    Block block = pillarLoc.clone().add(0, y, 0).getBlock();
                    if (block.getType().isAir()) {
                        block.setType(y == height - 1 ? org.bukkit.Material.COBBLESTONE : org.bukkit.Material.STONE);
                        pillarBlocks.add(block);
                        allPillarBlocks.add(block);
                    }
                }
                
                // Check for entities in pillar path and apply effects
                for (Entity entity : world.getNearbyEntities(pillarLoc, 1, height, 1)) {
                    if (entity instanceof LivingEntity le && entity != player) {
                        // Launch upward with strong knockback
                        Vector launch = new Vector(
                                (Math.random() - 0.5) * 1.5,
                                1.0 + finalAccuracy,
                                (Math.random() - 0.5) * 1.5
                        );
                        le.setVelocity(launch);
                        le.damage(4.0 * finalAccuracy, player);
                        
                        // Brief suffocation effect via blindness
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                    }
                }
                
                // Sound and particles
                world.playSound(pillarLoc, Sound.BLOCK_STONE_PLACE, 1.2f, 0.7f);
                world.spawnParticle(Particle.BLOCK, pillarLoc.clone().add(0.5, height/2.0, 0.5), 
                        20, 0.3, height/2.0, 0.3, 0.1, org.bukkit.Material.STONE.createBlockData());
                
                index++;
            }
        }.runTaskTimer(plugin, 0L, 3L); // Spawn each pillar 3 ticks apart

        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.6f);
        world.playSound(targetLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }

    // ==================== TRICKSTUS (Control Confusion) ====================
    // Confuses target's controls for a duration
    public static SpellResult castTrickstus(Player player, double timing, double windowStart, double windowEnd, ChargeBarManager chargeBarManager, Mcroguelite plugin) {
        // MUST be within window or backfire
        if (!isWithinWindow(timing, windowStart, windowEnd)) {
            // Backfire - confuse yourself badly
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.5f);
            player.sendMessage("§c§oYour mind shatters with confusion!");
            return SpellResult.BACKFIRE;
        }

        double accuracy = calculateAccuracy(timing, windowStart, windowEnd);

        // Raycast to find target
        var result = player.rayTraceEntities(15);
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            player.sendMessage(net.kyori.adventure.text.Component.text("No target found!", net.kyori.adventure.text.format.NamedTextColor.RED));
            return SpellResult.FIZZLE;
        }

        World world = player.getWorld();
        final double finalAccuracy = accuracy;
        int duration = (int)(80 + accuracy * 80); // 4-8 seconds

        // Apply confusion effects
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0));
        
        // For players, also mess with their movement via random velocity nudges
        if (target instanceof Player targetPlayer) {
            // Unlock mana (curse spell)
            chargeBarManager.unlockMana(targetPlayer);
            targetPlayer.sendMessage(net.kyori.adventure.text.Component.text("Your mind is scrambled... but something awakens!", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            
            // Periodic random velocity adjustments
            new BukkitRunnable() {
                int ticks = 0;
                
                @Override
                public void run() {
                    if (ticks >= duration || !targetPlayer.isOnline() || targetPlayer.isDead()) {
                        cancel();
                        return;
                    }
                    
                    // Every 10 ticks, nudge their velocity slightly in random direction
                    if (ticks % 10 == 0) {
                        Vector randomNudge = new Vector(
                                (Math.random() - 0.5) * 0.3 * finalAccuracy,
                                0,
                                (Math.random() - 0.5) * 0.3 * finalAccuracy
                        );
                        targetPlayer.setVelocity(targetPlayer.getVelocity().add(randomNudge));
                    }
                    
                    // Visual indicator
                    if (ticks % 15 == 0) {
                        world.spawnParticle(Particle.WITCH, targetPlayer.getLocation().add(0, 2, 0), 
                                5, 0.3, 0.2, 0.3, 0.02);
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        // Cast effects
        world.spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.5);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        world.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 1.2f);
        
        return accuracy > 0.7 ? SpellResult.PERFECT : SpellResult.SUCCESS;
    }
}
