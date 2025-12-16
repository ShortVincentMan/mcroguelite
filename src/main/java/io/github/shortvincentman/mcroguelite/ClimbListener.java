package io.github.shortvincentman.mcroguelite;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClimbListener implements Listener {

    private final ChargeBarManager chargeBarManager;
    private final NamespacedKey climbLevelKey;

    private static class ClimbState {
        long startTimeMillis;
        boolean climbing;
    }

    private final Map<UUID, ClimbState> climbs = new HashMap<>();

    // extra mana drain while climbing (on top of global drain)
    private static final double CLIMB_EXTRA_DRAIN = 0.03;

    public ClimbListener(Plugin plugin, ChargeBarManager chargeBarManager) {
        this.chargeBarManager = chargeBarManager;
        this.climbLevelKey = new NamespacedKey(plugin, "climb_level");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        ClimbState state = climbs.get(id);

        // If already climbing, handle climb motion
        if (state != null && state.climbing) {
            handleOngoingClimb(player, state);
            return;
        }

        // Not climbing: check for activation
        if (!player.isSprinting()) return;
        if (!chargeBarManager.hasManaUnlocked(player)) return;

        double charge = chargeBarManager.getChargeProgress(player);
        if (charge < 0.10) return; // need at least 10% mana

        if (!isAgainstWall(player)) return;

        // Start climb
        state = climbs.computeIfAbsent(id, k -> new ClimbState());
        state.climbing = true;
        state.startTimeMillis = System.currentTimeMillis();
        player.sendMessage("§aYou begin climbing.");
    }

    private boolean isAgainstWall(Player player) {
        Vector dir = player.getLocation().getDirection().normalize();
        Block front = player.getLocation().add(dir.multiply(0.5)).getBlock();
        return isSolid(front);
    }

    private boolean isSolid(Block block) {
        return block.getType() != Material.AIR && block.getType().isSolid();
    }

    public int getClimbLevel(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(climbLevelKey, PersistentDataType.INTEGER, 1);
    }

    private void addClimbXP(Player player, int climbsDone) {
        int level = getClimbLevel(player);
        int newLevel = Math.max(1, level + climbsDone);
        player.getPersistentDataContainer()
                .set(climbLevelKey, PersistentDataType.INTEGER, newLevel);
        player.sendMessage("§aYour climbing improves. Level: " + newLevel);
    }

    private void handleOngoingClimb(Player player, ClimbState state) {
        // Stop if not still against a wall
        if (!isAgainstWall(player)) {
            endClimb(player, state, true);
            return;
        }

        long elapsed = System.currentTimeMillis() - state.startTimeMillis;
        int level = getClimbLevel(player);

        // Climb duration scales with level
        long maxDuration = 600L + (long) level * 100L;

        if (elapsed > maxDuration) {
            endClimb(player, state, true);
            return;
        }

        // Extra mana drain while climbing
        if (!chargeBarManager.consumeCharge(player, CLIMB_EXTRA_DRAIN)) {
            // Out of mana → fall, no XP
            endClimb(player, state, false);
            return;
        }

        // Push upward and slightly into the wall
        Vector dir = player.getLocation().getDirection().normalize();
        Vector climbVel = new Vector(dir.getX() * 0.1, 0.32, dir.getZ() * 0.1);
        player.setVelocity(climbVel);
        player.setFallDistance(0f);
    }

    private void endClimb(Player player, ClimbState state, boolean reward) {
        state.climbing = false;
        if (reward) {
            addClimbXP(player, 1);
        }
    }
}
