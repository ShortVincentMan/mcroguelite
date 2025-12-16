package io.github.shortvincentman.mcroguelite;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainCommand implements CommandExecutor {

    private final ChargeBarManager chargeBarManager;
    private final ManaManager manaManager;
    private final ClimbListener climbListener;

    public MainCommand(ChargeBarManager chargeBarManager,
                       ManaManager manaManager,
                       ClimbListener climbListener) {
        this.chargeBarManager = chargeBarManager;
        this.manaManager = manaManager;
        this.climbListener = climbListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6McRogueLite commands:");
            sender.sendMessage("§e/mcroguelite help §7- Show this help");
            sender.sendMessage("§e/mcroguelite stats §7- Show your mana and climb stats");
            sender.sendMessage("§e/spell ignis §7- Fire bolt spell");
            sender.sendMessage("§e/spell hoppa §7- Leap spell");
            sender.sendMessage("§e/spell ward §7- Knockback wave");
            sender.sendMessage("§e/spell unlock §7- Unlock another player's mana (test)");
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            int manaLevel = manaManager.getManaLevel(player);
            int climbLevel = climbListener.getClimbLevel(player);
            double charge = chargeBarManager.getChargeProgress(player);

            player.sendMessage("§6McRogueLite stats:");
            player.sendMessage("§7Mana Level: §b" + manaLevel);
            player.sendMessage("§7Current Mana Charge: §b" + Math.round(charge * 100) + "%");
            player.sendMessage("§7Climb Level: §a" + climbLevel);
            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use /mcroguelite help");
        return true;
    }
}
