package io.github.shortvincentman.mcroguelite;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpellCommand implements CommandExecutor {

    private final SpellManager spellManager;

    public SpellCommand(SpellManager spellManager) {
        this.spellManager = spellManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: /spell <ignis|hoppa|ward|unlock>");
            return true;
        }

        spellManager.castSpell(player, args[0]);
        return true;
    }
}
