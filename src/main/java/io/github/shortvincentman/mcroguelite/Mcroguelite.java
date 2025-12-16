package io.github.shortvincentman.mcroguelite;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public final class Mcroguelite extends JavaPlugin {
    private ChargeBarManager chargeBarManager;

    @Override
    public void onEnable() {
        this.chargeBarManager = new ChargeBarManager(this);

        Bukkit.getPluginManager().registerEvents(chargeBarManager, this);

        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> chargeBarManager.tickAllBars(0.02, 0.03),
                1L,
                2L
        );
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (chargeBarManager != null) {
            chargeBarManager.clearAll();
        }
    }
}
