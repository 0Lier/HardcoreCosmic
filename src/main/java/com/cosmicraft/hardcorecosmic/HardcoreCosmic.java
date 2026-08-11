package com.cosmicraft.hardcorecosmic;

import com.cosmicraft.hardcorecosmic.commands.HardcoreCommand;
import com.cosmicraft.hardcorecosmic.listeners.HardcoreListener;
import com.cosmicraft.hardcorecosmic.managers.HardcoreManager;
import com.cosmicraft.hardcorecosmic.managers.NetheriteDowngradeManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcoreCosmic extends JavaPlugin {

    private static HardcoreCosmic instance;
    private HardcoreManager hardcoreManager;
    private NetheriteDowngradeManager netheriteDowngradeManager;

    @Override
    public void onEnable() {
        instance = this;

        hardcoreManager = new HardcoreManager(this);
        netheriteDowngradeManager = new NetheriteDowngradeManager(this);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(netheriteDowngradeManager, this);
        getServer().getPluginManager().registerEvents(new HardcoreListener(this), this);

        if (getCommand("hardcore") != null) {
            getCommand("hardcore").setExecutor(new HardcoreCommand(this));
        } else {
            getLogger().warning("[HardcoreCosmic] No se pudo registrar el comando /hardcore.");
        }

        hardcoreManager.loadPersistedState();
        netheriteDowngradeManager.restoreStateOnEnable();

        getLogger().info("[HardcoreCosmic] Plugin habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[HardcoreCosmic] Plugin deshabilitado.");
    }

    public static HardcoreCosmic getInstance() { return instance; }
    public HardcoreManager getHardcoreManager() { return hardcoreManager; }
    public NetheriteDowngradeManager getNetheriteDowngradeManager() { return netheriteDowngradeManager; }
}