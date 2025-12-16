package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Mcroguelite extends JavaPlugin {

    private ChargeBarManager chargeBarManager;
    private ManaManager manaManager;
    private ClimbListener climbListener;
    private SpellManager spellManager;
    private ItemSpellListener itemSpellListener;

    @Override
    public void onEnable() {
        this.chargeBarManager = new ChargeBarManager(this);
        this.manaManager = new ManaManager(this);
        this.climbListener = new ClimbListener(this, chargeBarManager);
        this.spellManager = new SpellManager(this, chargeBarManager);
        this.itemSpellListener = new ItemSpellListener(this, spellManager);

        // Events
        Bukkit.getPluginManager().registerEvents(chargeBarManager, this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(chargeBarManager), this);
        Bukkit.getPluginManager().registerEvents(climbListener, this);
        Bukkit.getPluginManager().registerEvents(new ManaTrainingListener(this, chargeBarManager, manaManager), this);
        Bukkit.getPluginManager().registerEvents(itemSpellListener, this);

        // Commands
        getCommand("spell").setExecutor(new SpellCommand(spellManager));
        getCommand("mcroguelite").setExecutor(new MainCommand(chargeBarManager, manaManager, climbListener));

        // Tick charge bars: 0.02 charge / 0.03 drain per tick (~0.1s)
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> chargeBarManager.tickAllBars(0.02, 0.03),
                0L,
                2L
        );
    }

    @Override
    public void onDisable() {
        if (chargeBarManager != null) {
            chargeBarManager.clearAll();
        }
    }
}
