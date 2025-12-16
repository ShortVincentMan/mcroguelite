package io.github.shortvincentman.mcroguelite.commands;

import io.github.shortvincentman.mcroguelite.ChargeBarManager;
import io.github.shortvincentman.mcroguelite.gui.SpellSelectionGUI;
import io.github.shortvincentman.mcroguelite.items.SpellScroll;
import io.github.shortvincentman.mcroguelite.tome.SpellTome;
import io.github.shortvincentman.mcroguelite.tome.TomeSpellListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TomeCommands implements CommandExecutor, TabCompleter {
    private final SpellTome spellTome;
    private final TomeSpellListener tomeListener;
    private ChargeBarManager chargeBarManager;
    private SpellScroll spellScroll;
    private SpellSelectionGUI spellGUI;

    // Known spells for tab completion
    private static final List<String> ALL_SPELLS = Arrays.asList(
            "ignis", "hoppa", "ward", "unlock", "snap", 
            "fimbulvetr", "perflora", "snarvindur", "tenebris",
            "percutiens", "celeritas", "veil", "gelidus", "scrupus",
            "armis", "viribus", "trickstus"
    );

    public TomeCommands(SpellTome spellTome, TomeSpellListener tomeListener) {
        this.spellTome = spellTome;
        this.tomeListener = tomeListener;
    }
    
    // Set additional dependencies after construction
    public void setChargeBarManager(ChargeBarManager chargeBarManager) {
        this.chargeBarManager = chargeBarManager;
    }
    
    public void setSpellScroll(SpellScroll spellScroll) {
        this.spellScroll = spellScroll;
    }
    
    public void setSpellGUI(SpellSelectionGUI spellGUI) {
        this.spellGUI = spellGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "givetome":
                return handleGiveTome(sender, args);
            case "learnspell":
                return handleLearnSpell(sender, args);
            case "selectspell":
                return handleSelectSpell(sender, args);
            case "forgetspell":
                return handleForgetSpell(sender, args);
            case "givescroll":
                return handleGiveScroll(sender, args);
            case "teachspell":
                return handleTeachSpell(sender, args);
            case "unlockmana":
                return handleUnlockMana(sender, args);
            case "spellgui":
                return handleSpellGUI(sender, args);
            default:
                return false;
        }
    }

    private boolean handleGiveTome(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcroguelite.givetome")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        Player target;
        SpellTome.TomeTier tier = SpellTome.TomeTier.STARTER;
        
        if (args.length >= 1) {
            // First arg could be player or tier
            Player possibleTarget = Bukkit.getPlayer(args[0]);
            if (possibleTarget != null) {
                target = possibleTarget;
                if (args.length >= 2) {
                    try {
                        tier = SpellTome.TomeTier.valueOf(args[1].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text("Invalid tier! Options: STARTER, ADEPT, MASTER, ANCIENT", NamedTextColor.RED));
                        return true;
                    }
                }
            } else {
                // First arg is tier
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Console must specify a player!");
                    return true;
                }
                target = (Player) sender;
                try {
                    tier = SpellTome.TomeTier.valueOf(args[0].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid tier! Options: STARTER, ADEPT, MASTER, ANCIENT", NamedTextColor.RED));
                    return true;
                }
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        }

        target.getInventory().addItem(spellTome.createTome(tier));
        target.sendMessage(Component.text("Received " + tier.getDisplayName() + "!", tier.getColor()));
        if (sender != target) {
            sender.sendMessage(Component.text("Gave " + tier.getDisplayName() + " to " + target.getName(), NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleLearnSpell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /learnspell <spell>", NamedTextColor.RED));
            player.sendMessage(Component.text("Available: " + String.join(", ", ALL_SPELLS), NamedTextColor.GRAY));
            return true;
        }

        String spellName = String.join("_", args).toUpperCase();
        
        // Validate spell exists
        if (!ALL_SPELLS.contains(spellName.toLowerCase())) {
            player.sendMessage(Component.text("Unknown spell: " + spellName, NamedTextColor.RED));
            player.sendMessage(Component.text("Available: " + String.join(", ", ALL_SPELLS), NamedTextColor.GRAY));
            return true;
        }

        var tome = player.getInventory().getItemInMainHand();
        if (!spellTome.isTome(tome)) {
            tome = player.getInventory().getItemInOffHand();
        }

        if (!spellTome.isTome(tome)) {
            player.sendMessage(Component.text("You must be holding a tome!", NamedTextColor.RED));
            return true;
        }

        if (spellTome.addSpell(tome, spellName)) {
            player.sendMessage(Component.text("Learned " + formatSpellName(spellName) + "!", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        } else {
            player.sendMessage(Component.text("Couldn't learn spell (already known or tome full)", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleSelectSpell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /selectspell <spell>", NamedTextColor.RED));
            return true;
        }

        String spellName = String.join("_", args);
        tomeListener.selectSpell(player, spellName);
        return true;
    }

    private boolean handleForgetSpell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /forgetspell <spell>", NamedTextColor.RED));
            return true;
        }

        var tome = player.getInventory().getItemInMainHand();
        if (!spellTome.isTome(tome)) {
            tome = player.getInventory().getItemInOffHand();
        }

        if (!spellTome.isTome(tome)) {
            player.sendMessage(Component.text("You must be holding a tome!", NamedTextColor.RED));
            return true;
        }

        String spellName = String.join("_", args).toUpperCase();
        if (spellTome.removeSpell(tome, spellName)) {
            player.sendMessage(Component.text("Forgot " + formatSpellName(spellName), NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("You don't have that spell in your tome!", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleGiveScroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcroguelite.givescroll")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (spellScroll == null) {
            sender.sendMessage(Component.text("Spell scrolls not initialized!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /givescroll <spell> [player]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available: " + String.join(", ", ALL_SPELLS), NamedTextColor.GRAY));
            return true;
        }

        String spellName = args[0].toUpperCase();
        if (!ALL_SPELLS.contains(spellName.toLowerCase())) {
            sender.sendMessage(Component.text("Unknown spell: " + spellName, NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        }

        target.getInventory().addItem(spellScroll.createScroll(spellName));
        target.sendMessage(Component.text("Received Scroll of " + formatSpellName(spellName) + "!", NamedTextColor.LIGHT_PURPLE));
        if (sender != target) {
            sender.sendMessage(Component.text("Gave scroll of " + formatSpellName(spellName) + " to " + target.getName(), NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleTeachSpell(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcroguelite.teachspell")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /teachspell <player> <spell>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available: " + String.join(", ", ALL_SPELLS), NamedTextColor.GRAY));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
            return true;
        }

        String spellName = args[1].toUpperCase();
        if (!ALL_SPELLS.contains(spellName.toLowerCase())) {
            sender.sendMessage(Component.text("Unknown spell: " + spellName, NamedTextColor.RED));
            return true;
        }

        // Find player's tome
        var tome = target.getInventory().getItemInMainHand();
        if (!spellTome.isTome(tome)) {
            tome = target.getInventory().getItemInOffHand();
        }
        
        // Search entire inventory if not in hands
        if (!spellTome.isTome(tome)) {
            for (var item : target.getInventory().getContents()) {
                if (spellTome.isTome(item)) {
                    tome = item;
                    break;
                }
            }
        }

        if (!spellTome.isTome(tome)) {
            sender.sendMessage(Component.text(target.getName() + " doesn't have a tome!", NamedTextColor.RED));
            return true;
        }

        if (spellTome.addSpell(tome, spellName)) {
            target.sendMessage(Component.text("§d✦ You learned " + formatSpellName(spellName) + "! ✦"));
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            sender.sendMessage(Component.text("Taught " + formatSpellName(spellName) + " to " + target.getName(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Couldn't teach spell (already known or tome full)", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleUnlockMana(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcroguelite.unlockmana")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (chargeBarManager == null) {
            sender.sendMessage(Component.text("Mana system not initialized!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage: /unlockmana <player>");
                return true;
            }
            Player player = (Player) sender;
            chargeBarManager.unlockMana(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
            return true;
        }

        chargeBarManager.unlockMana(target);
        sender.sendMessage(Component.text("Unlocked mana for " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSpellGUI(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (spellGUI == null) {
            player.sendMessage(Component.text("Spell GUI not initialized!", NamedTextColor.RED));
            return true;
        }

        var tome = player.getInventory().getItemInMainHand();
        if (!spellTome.isTome(tome)) {
            tome = player.getInventory().getItemInOffHand();
        }

        if (!spellTome.isTome(tome)) {
            player.sendMessage(Component.text("You must be holding a tome!", NamedTextColor.RED));
            return true;
        }

        spellGUI.openGUI(player, tome);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        switch (command.getName().toLowerCase()) {
            case "givetome":
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    // Suggest players first
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList()));
                    // Also suggest tiers
                    completions.addAll(Arrays.stream(SpellTome.TomeTier.values())
                            .map(t -> t.name().toLowerCase())
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                } else if (args.length == 2) {
                    String partial = args[1].toLowerCase();
                    completions.addAll(Arrays.stream(SpellTome.TomeTier.values())
                            .map(t -> t.name().toLowerCase())
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                }
                break;
            case "learnspell":
            case "selectspell":
            case "forgetspell":
                if (args.length >= 1) {
                    String partial = String.join("_", args).toLowerCase();
                    completions.addAll(ALL_SPELLS.stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                }
                break;
            case "givescroll":
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    completions.addAll(ALL_SPELLS.stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                } else if (args.length == 2) {
                    String partial = args[1].toLowerCase();
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList()));
                }
                break;
            case "teachspell":
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList()));
                } else if (args.length == 2) {
                    String partial = args[1].toLowerCase();
                    completions.addAll(ALL_SPELLS.stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                }
                break;
            case "unlockmana":
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList()));
                }
                break;
        }

        return completions;
    }

    private String formatSpellName(String spell) {
        if (spell == null || spell.isEmpty()) return "";
        String[] words = spell.toLowerCase().replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }
}
