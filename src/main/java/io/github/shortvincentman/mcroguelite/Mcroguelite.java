package io.github.shortvincentman.mcroguelite;

import io.github.shortvincentman.mcroguelite.climbing.ClimbingSystem;
import io.github.shortvincentman.mcroguelite.commands.TomeCommands;
import io.github.shortvincentman.mcroguelite.gui.SpellSelectionGUI;
import io.github.shortvincentman.mcroguelite.items.SpellScroll;
import io.github.shortvincentman.mcroguelite.tome.SpellTome;
import io.github.shortvincentman.mcroguelite.tome.TomeSpellListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Mcroguelite extends JavaPlugin {

    private ChargeBarManager chargeBarManager;
    private SpellManager spellManager;
    private ItemSpellListener itemSpellListener;
    
    // New systems
    private SpellTome spellTome;
    private TomeSpellListener tomeSpellListener;
    private ManaRunListener manaRunListener;
    private ClimbingSystem climbingSystem;
    private SpellScroll spellScroll;
    private SpellSelectionGUI spellGUI;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        this.chargeBarManager = new ChargeBarManager(this);
        this.spellManager = new SpellManager(this, chargeBarManager);
        this.itemSpellListener = new ItemSpellListener(this, spellManager);
        
        // Initialize new systems
        this.spellTome = new SpellTome(this);
        this.tomeSpellListener = new TomeSpellListener(this, spellTome, chargeBarManager);
        this.manaRunListener = new ManaRunListener(this, chargeBarManager);
        this.climbingSystem = new ClimbingSystem(this, chargeBarManager);
        this.spellScroll = new SpellScroll(this, spellTome, chargeBarManager);
        this.spellGUI = new SpellSelectionGUI(spellTome, tomeSpellListener);

        // Events - Core
        Bukkit.getPluginManager().registerEvents(chargeBarManager, this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this, chargeBarManager), this);
        Bukkit.getPluginManager().registerEvents(itemSpellListener, this);
        
        // Events - New systems
        Bukkit.getPluginManager().registerEvents(tomeSpellListener, this);
        // ManaRunListener is now a passive tick-based system, not an event listener
        Bukkit.getPluginManager().registerEvents(climbingSystem, this);
        Bukkit.getPluginManager().registerEvents(spellScroll, this);
        Bukkit.getPluginManager().registerEvents(spellGUI, this);

        // Commands - Original
        getCommand("spell").setExecutor(new SpellCommand(spellManager));
        getCommand("mcroguelite").setExecutor(new MainCommand(this, chargeBarManager));
        
        // Commands - Tome and new commands
        TomeCommands tomeCommands = new TomeCommands(spellTome, tomeSpellListener);
        tomeCommands.setChargeBarManager(chargeBarManager);
        tomeCommands.setSpellScroll(spellScroll);
        tomeCommands.setSpellGUI(spellGUI);
        
        getCommand("givetome").setExecutor(tomeCommands);
        getCommand("givetome").setTabCompleter(tomeCommands);
        getCommand("learnspell").setExecutor(tomeCommands);
        getCommand("learnspell").setTabCompleter(tomeCommands);
        getCommand("selectspell").setExecutor(tomeCommands);
        getCommand("selectspell").setTabCompleter(tomeCommands);
        getCommand("forgetspell").setExecutor(tomeCommands);
        getCommand("forgetspell").setTabCompleter(tomeCommands);
        getCommand("givescroll").setExecutor(tomeCommands);
        getCommand("givescroll").setTabCompleter(tomeCommands);
        getCommand("teachspell").setExecutor(tomeCommands);
        getCommand("teachspell").setTabCompleter(tomeCommands);
        getCommand("unlockmana").setExecutor(tomeCommands);
        getCommand("unlockmana").setTabCompleter(tomeCommands);
        getCommand("spellgui").setExecutor(tomeCommands);

        // Tick charge bars: 0.02 charge / 0.03 drain per tick (~0.1s)
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> chargeBarManager.tickAllBars(0.02, 0.03),
                0L,
                2L
        );
        
        getLogger().info("MCRogueLite enabled with Tome System, Mana Run, Climbing, Spell Scrolls, and GUI!");
    }

    @Override
    public void onDisable() {
        // Save all player data before clearing
        if (chargeBarManager != null) {
            chargeBarManager.saveAllPlayerData();
            chargeBarManager.clearAll();
        }
        if (tomeSpellListener != null) {
            tomeSpellListener.cleanup();
        }
        if (manaRunListener != null) {
            manaRunListener.cleanup();
        }
        if (climbingSystem != null) {
            climbingSystem.cleanup();
        }
    }
    
    // Getters for other classes
    public ChargeBarManager getChargeBarManager() {
        return chargeBarManager;
    }
    
    public SpellTome getSpellTome() {
        return spellTome;
    }
    
    public ClimbingSystem getClimbingSystem() {
        return climbingSystem;
    }
    
    public SpellScroll getSpellScroll() {
        return spellScroll;
    }
}
