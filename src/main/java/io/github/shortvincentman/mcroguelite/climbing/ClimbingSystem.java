package io.github.shortvincentman.mcroguelite.climbing;

import io.github.shortvincentman.mcroguelite.ChargeBarManager;
import io.github.shortvincentman.mcroguelite.Mcroguelite;
import io.github.shortvincentman.mcroguelite.util.ManaColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Mana-based climbing system.
 * Right-click a wall with empty hands to start climbing.
 * Look up/down to climb, sneak to rest, right-click again or run out of mana to stop.
 * Climbing drains mana - higher climb level = less drain and faster speed.
 */
public class ClimbingSystem implements Listener {
    private final Mcroguelite plugin;
    private final ChargeBarManager chargeBarManager;

    private final Map<UUID, ClimbState> climbingPlayers = new HashMap<>();
    private final Map<UUID, Integer> climbLevel = new HashMap<>();
    private final Map<UUID, Integer> climbUseCount = new HashMap<>(); // Track number of climbs

    // Mana drain per tick (base rate, reduced by level)
    private static final double BASE_MANA_DRAIN = 0.008; // 0.8% per tick base (slower drain)
    private static final double RESTING_DRAIN_MULTIPLIER = 0.3; // 70% reduction when resting
    private static final double LEVEL_DRAIN_REDUCTION = 0.04; // 4% reduction per level
    private static final double MAX_DRAIN_REDUCTION = 0.60; // Cap at 60% reduction (level 15)
    
    // Climb speed similar to ladder (0.1 is roughly ladder speed)
    private static final double CLIMB_SPEED_BASE = 0.10;
    private static final double CLIMB_SPEED_PER_LEVEL = 0.008; // +0.008 per level
    
    // Leveling thresholds (climbs needed for each level)
    private static final int[] LEVEL_THRESHOLDS = {
        0,    // Level 1 (starting)
        10,   // Level 2
        25,   // Level 3
        45,   // Level 4
        70,   // Level 5
        100,  // Level 6
        140,  // Level 7
        190,  // Level 8
        250,  // Level 9
        320,  // Level 10
        400,  // Level 11
        500,  // Level 12
        620,  // Level 13
        760,  // Level 14
        920   // Level 15 (max benefits)
    };

    private static class ClimbState {
        BlockFace wallFace;
        long startTime;
        boolean isResting;

        ClimbState(BlockFace wallFace) {
            this.wallFace = wallFace;
            this.startTime = System.currentTimeMillis();
            this.isResting = false;
        }
    }

    public ClimbingSystem(Mcroguelite plugin, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.chargeBarManager = chargeBarManager;
        startClimbingTask();
    }

    private void startClimbingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    if (climbingPlayers.containsKey(uuid)) {
                        processClimbingPlayer(player, uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void processClimbingPlayer(Player player, UUID uuid) {
        ClimbState state = climbingPlayers.get(uuid);
        
        // Check if still against wall
        if (!isAgainstWall(player, state.wallFace)) {
            stopClimbing(player, false);
            return;
        }
        
        // Calculate mana drain based on climb level
        int level = climbLevel.getOrDefault(uuid, 1);
        int fullCharges = chargeBarManager.getFullChargeCount(player);
        
        // Drain reduction: 4% per climb level, capped at 60%
        double levelReduction = Math.min(MAX_DRAIN_REDUCTION, level * LEVEL_DRAIN_REDUCTION);
        double drain = BASE_MANA_DRAIN * (1.0 - levelReduction);
        
        // Resting reduces drain by 70%
        if (state.isResting) {
            drain *= RESTING_DRAIN_MULTIPLIER;
        }
        
        // Try to consume mana
        double currentMana = chargeBarManager.getChargeProgress(player);
        if (currentMana < drain) {
            // Out of mana - fall!
            stopClimbing(player, true);
            return;
        }
        
        chargeBarManager.consumeCharge(player, drain);
        
        // Handle climbing movement in the task (more reliable than move event)
        handleClimbMovement(player, state, level);
        
        // Spawn colored particles while climbing (not resting)
        if (!state.isResting && player.getTicksLived() % 5 == 0) {
            spawnClimbingParticles(player, state, fullCharges);
        }
    }

    private void spawnClimbingParticles(Player player, ClimbState state, int fullCharges) {
        Block wallBlock = player.getLocation().getBlock().getRelative(state.wallFace);
        Location particleLoc = player.getLocation().add(0, 0.5, 0);
        
        // Block particles from wall
        if (wallBlock.getType().isSolid()) {
            player.getWorld().spawnParticle(Particle.BLOCK,
                    particleLoc,
                    3, 0.1, 0.1, 0.1, 0,
                    wallBlock.getBlockData());
        }
        
        // Colored mana dust particles
        player.getWorld().spawnParticle(
                Particle.DUST,
                particleLoc,
                5, 0.2, 0.3, 0.2, 0,
                ManaColorUtil.getDustOptions(fullCharges, 0.8f)
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // If already climbing, right-click stops climbing
        if (climbingPlayers.containsKey(uuid)) {
            stopClimbing(player, false);
            return;
        }
        
        // Must have empty main hand to start climbing
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        
        // Must have mana unlocked AND mana climb trained
        if (!chargeBarManager.hasManaUnlocked(player) || !chargeBarManager.hasManaClimbUnlocked(player)) {
            return;
        }
        
        // Must have some mana to start climbing
        if (chargeBarManager.getChargeProgress(player) < BASE_MANA_DRAIN * 5) {
            player.sendActionBar(Component.text("Not enough mana to climb!", NamedTextColor.RED));
            return;
        }
        
        // Check if the clicked block is climbable
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !isClimbable(clickedBlock.getType())) {
            return;
        }
        
        // Get the face of the block we're climbing
        BlockFace clickedFace = event.getBlockFace();
        // We want the opposite face (we're facing the wall)
        BlockFace wallFace = clickedFace.getOppositeFace();
        
        // Only allow climbing on vertical faces
        if (wallFace == BlockFace.UP || wallFace == BlockFace.DOWN) {
            return;
        }
        
        event.setCancelled(true);
        startClimbing(player, wallFace);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (climbingPlayers.containsKey(uuid)) {
            ClimbState state = climbingPlayers.get(uuid);
            state.isResting = event.isSneaking();

            if (event.isSneaking()) {
                player.sendActionBar(Component.text("Resting... (reduced mana drain)", NamedTextColor.YELLOW));
            } else {
                player.sendActionBar(Component.text("Climbing...", NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        climbingPlayers.remove(uuid);
    }

    private void startClimbing(Player player, BlockFace wallFace) {
        UUID uuid = player.getUniqueId();

        climbingPlayers.put(uuid, new ClimbState(wallFace));

        int level = climbLevel.getOrDefault(uuid, 1);
        double drainReduction = Math.min(MAX_DRAIN_REDUCTION, level * LEVEL_DRAIN_REDUCTION) * 100;
        
        player.playSound(player.getLocation(), Sound.BLOCK_LADDER_STEP, 0.5f, 1.0f);
        player.sendActionBar(Component.text("Climbing! (Sneak to rest, right-click to drop) [-" + 
                String.format("%.0f", drainReduction) + "% drain]", NamedTextColor.GREEN));
    }

    private void stopClimbing(Player player, boolean ranOutOfMana) {
        UUID uuid = player.getUniqueId();
        ClimbState state = climbingPlayers.remove(uuid);

        if (state == null) return;

        if (ranOutOfMana) {
            // Softer feedback - just a thud sound instead of hurt sound
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_STEP, 0.8f, 0.5f);
            player.sendActionBar(Component.text("Out of mana!", NamedTextColor.RED));
            // Still count the climb attempt
            incrementClimbCount(player);
        } else {
            // Successful climb - count it and check for level up
            incrementClimbCount(player);
            player.playSound(player.getLocation(), Sound.BLOCK_LADDER_STEP, 0.3f, 1.2f);
        }
    }

    private void incrementClimbCount(Player player) {
        UUID uuid = player.getUniqueId();
        int count = climbUseCount.getOrDefault(uuid, 0) + 1;
        climbUseCount.put(uuid, count);
        
        int currentLevel = climbLevel.getOrDefault(uuid, 1);
        int newLevel = calculateLevelFromCount(count);
        
        if (newLevel > currentLevel) {
            climbLevel.put(uuid, newLevel);
            // Level up tracked silently - check /mcroguelite stats
        }
    }
    
    private int calculateLevelFromCount(int count) {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (count >= LEVEL_THRESHOLDS[i]) {
                return i + 1; // Level is 1-indexed
            }
        }
        return 1;
    }

    private void handleClimbMovement(Player player, ClimbState state, int level) {
        if (state.isResting) {
            // Hold position - negate gravity
            Vector velocity = player.getVelocity();
            velocity.setY(0);
            player.setVelocity(velocity);
            player.setFallDistance(0);
            return;
        }

        double climbSpeed = CLIMB_SPEED_BASE + (level * CLIMB_SPEED_PER_LEVEL);

        // Look up = climb up, look down = climb down
        float pitch = player.getLocation().getPitch();
        double verticalSpeed = 0;
        if (pitch < -15) {
            verticalSpeed = climbSpeed;
        } else if (pitch > 15) {
            verticalSpeed = -climbSpeed * 0.8;
        }
        
        // Apply velocity - override to prevent gravity from pulling down
        Vector velocity = new Vector(0, verticalSpeed, 0);
        
        // Allow slight horizontal movement towards wall
        Vector wallDir = new Vector(state.wallFace.getModX(), 0, state.wallFace.getModZ()).multiply(0.02);
        velocity.add(wallDir);

        player.setVelocity(velocity);
        player.setFallDistance(0);

        // Climbing sound
        if (Math.abs(verticalSpeed) > 0.05 && player.getTicksLived() % 12 == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_LADDER_STEP, 0.3f, 1.0f);
        }
    }

    private BlockFace getAdjacentWall(Player player) {
        Location loc = player.getLocation();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        Vector dir = player.getLocation().getDirection();
        dir.setY(0).normalize();

        List<BlockFace> sortedFaces = new ArrayList<>(Arrays.asList(faces));
        sortedFaces.sort((a, b) -> {
            Vector va = new Vector(a.getModX(), 0, a.getModZ());
            Vector vb = new Vector(b.getModX(), 0, b.getModZ());
            return Double.compare(vb.dot(dir), va.dot(dir));
        });

        for (BlockFace face : sortedFaces) {
            Block block = loc.getBlock().getRelative(face);
            Block blockAbove = block.getRelative(BlockFace.UP);
            
            if (block.getType().isSolid() && isClimbable(block.getType())) {
                if (blockAbove.getType().isSolid() || block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    return face;
                }
            }
        }
        return null;
    }

    private boolean isAgainstWall(Player player, BlockFace wallFace) {
        Block wallBlock = player.getLocation().getBlock().getRelative(wallFace);
        return wallBlock.getType().isSolid();
    }

    private boolean isClimbable(Material material) {
        return material.isSolid() &&
                material != Material.ICE &&
                material != Material.PACKED_ICE &&
                material != Material.BLUE_ICE &&
                material != Material.SLIME_BLOCK &&
                material != Material.HONEY_BLOCK &&
                !material.name().contains("GLASS");
    }

    // Public API
    public int getClimbLevel(Player player) {
        return climbLevel.getOrDefault(player.getUniqueId(), 1);
    }

    public int getClimbUseCount(Player player) {
        return climbUseCount.getOrDefault(player.getUniqueId(), 0);
    }
    
    public int getClimbsToNextLevel(Player player) {
        int count = climbUseCount.getOrDefault(player.getUniqueId(), 0);
        int level = climbLevel.getOrDefault(player.getUniqueId(), 1);
        if (level >= LEVEL_THRESHOLDS.length) {
            return 0; // Max level
        }
        return LEVEL_THRESHOLDS[level] - count;
    }

    public boolean isClimbing(Player player) {
        return climbingPlayers.containsKey(player.getUniqueId());
    }

    public void cleanup() {
        climbingPlayers.clear();
    }
}
