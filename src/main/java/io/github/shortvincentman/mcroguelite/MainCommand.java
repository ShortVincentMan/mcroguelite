package io.github.shortvincentman.mcroguelite;

import io.github.shortvincentman.mcroguelite.util.ManaColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainCommand implements CommandExecutor {

    private final Mcroguelite plugin;
    private final ChargeBarManager chargeBarManager;

    public MainCommand(Mcroguelite plugin, ChargeBarManager chargeBarManager) {
        this.plugin = plugin;
        this.chargeBarManager = chargeBarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6McRogueLite commands:");
            sender.sendMessage("§e/mcroguelite help §7- Show this help");
            sender.sendMessage("§e/mcroguelite stats §7- Show your mana stats");
            sender.sendMessage("§e/mcroguelite grantrun <player> §7- Grant Mana Run ability");
            sender.sendMessage("§e/mcroguelite grantclimb <player> §7- Grant Mana Climb ability");
            sender.sendMessage("§e/mcroguelite grantdash <player> §7- Grant Mana Dash ability");
            sender.sendMessage("§e/mcroguelite setcharges <player> <count> §7- Set full charge count");
            sender.sendMessage("§e/givetome [tier] §7- Get a spell tome");
            sender.sendMessage("§e/learnspell <spell> §7- Learn a spell");
            sender.sendMessage("§e/selectspell <spell> §7- Select a spell");
            sender.sendMessage("§e/spellgui §7- Open spell selection GUI");
            sender.sendMessage("§e/givescroll <spell> §7- Get a spell scroll");
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            double charge = chargeBarManager.getChargeProgress(player);
            boolean manaUnlocked = chargeBarManager.hasManaUnlocked(player);
            int fullCharges = chargeBarManager.getFullChargeCount(player);
            boolean runUnlocked = chargeBarManager.hasManaRunUnlocked(player);
            boolean climbUnlocked = chargeBarManager.hasManaClimbUnlocked(player);
            boolean dashUnlocked = chargeBarManager.hasManaDashUnlocked(player);
            String tier = ManaColorUtil.getTierName(fullCharges);
            String tierColor = ManaColorUtil.getChatColor(fullCharges);

            player.sendMessage("§6McRogueLite stats:");
            player.sendMessage("§7Mana Unlocked: " + (manaUnlocked ? "§aYes" : "§cNo"));
            player.sendMessage("§7Current Mana Charge: §b" + Math.round(charge * 100) + "%");
            player.sendMessage("§7Full Charges: §b" + fullCharges + " " + tierColor + "(" + tier + ")");
            player.sendMessage("§7Mana Run: " + (runUnlocked ? "§aUnlocked" : "§c" + fullCharges + "/" + ChargeBarManager.MANA_RUN_THRESHOLD));
            player.sendMessage("§7Mana Climb: " + (climbUnlocked ? "§aUnlocked" : "§c" + fullCharges + "/" + ChargeBarManager.MANA_CLIMB_THRESHOLD));
            player.sendMessage("§7Mana Dash: " + (dashUnlocked ? "§aUnlocked" : "§c" + fullCharges + "/" + ChargeBarManager.MANA_DASH_THRESHOLD));
            
            // Show climb training stats if climb is unlocked
            if (climbUnlocked && plugin.getClimbingSystem() != null) {
                int climbLvl = plugin.getClimbingSystem().getClimbLevel(player);
                int climbCount = plugin.getClimbingSystem().getClimbUseCount(player);
                int toNext = plugin.getClimbingSystem().getClimbsToNextLevel(player);
                double drainReduction = Math.min(0.60, climbLvl * 0.04) * 100;
                player.sendMessage("§7Climb Level: §b" + climbLvl + " §7(" + climbCount + " climbs, " + (toNext > 0 ? toNext + " to next" : "MAX") + ")");
                player.sendMessage("§7  Drain reduction: §a-" + String.format("%.0f", drainReduction) + "%");
            }
            return true;
        }
        
        // Admin commands for granting abilities
        if (args[0].equalsIgnoreCase("grantrun")) {
            if (!sender.hasPermission("mcroguelite.admin")) {
                sender.sendMessage("§cNo permission!");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /mcroguelite grantrun <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
            chargeBarManager.grantManaRun(target);
            sender.sendMessage("§aGranted Mana Run to " + target.getName());
            return true;
        }
        
        if (args[0].equalsIgnoreCase("grantclimb")) {
            if (!sender.hasPermission("mcroguelite.admin")) {
                sender.sendMessage("§cNo permission!");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /mcroguelite grantclimb <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
            chargeBarManager.grantManaClimb(target);
            sender.sendMessage("§aGranted Mana Climb to " + target.getName());
            return true;
        }
        
        if (args[0].equalsIgnoreCase("grantdash")) {
            if (!sender.hasPermission("mcroguelite.admin")) {
                sender.sendMessage("§cNo permission!");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /mcroguelite grantdash <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
            chargeBarManager.grantManaDash(target);
            sender.sendMessage("§aGranted Mana Dash to " + target.getName());
            return true;
        }
        
        if (args[0].equalsIgnoreCase("setcharges")) {
            if (!sender.hasPermission("mcroguelite.admin")) {
                sender.sendMessage("§cNo permission!");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /mcroguelite setcharges <player> <count>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
            try {
                int count = Integer.parseInt(args[2]);
                chargeBarManager.setFullChargeCount(target, count);
                sender.sendMessage("§aSet " + target.getName() + "'s full charge count to " + count);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid number!");
            }
            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use /mcroguelite help");
        return true;
    }
}
