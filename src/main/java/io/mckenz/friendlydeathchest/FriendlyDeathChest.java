package io.mckenz.friendlydeathchest;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.listeners.BlockListener;
import io.mckenz.friendlydeathchest.listeners.InventoryListener;
import io.mckenz.friendlydeathchest.listeners.PlayerDeathListener;
import io.mckenz.friendlydeathchest.service.ChestManager;
import io.mckenz.friendlydeathchest.service.HologramManager;
import io.mckenz.friendlydeathchest.service.LocationFinder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main class for the FriendlyDeathChest plugin
 */
public class FriendlyDeathChest extends JavaPlugin {
    private ConfigManager configManager;
    private LocationFinder locationFinder;
    private ChestManager chestManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Check if plugin is enabled in config
        if (!configManager.isEnabled()) {
            getLogger().info("Plugin disabled in config.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize services in correct order
        locationFinder = new LocationFinder(configManager, null); // Temporarily pass null for ChestManager
        chestManager = new ChestManager(this, configManager, locationFinder);
        // Update LocationFinder with ChestManager reference
        ((LocationFinder)locationFinder).setChestManager(chestManager);
        
        // Initialize hologram manager if enabled
        if (configManager.isHologramEnabled()) {
            hologramManager = new HologramManager(this, configManager, chestManager);
            getLogger().info("Hologram feature enabled.");
        }

        // Register event listeners
        registerEventListeners();

        // Log debug status
        if (configManager.isDebugEnabled()) {
            getLogger().info("Debug mode enabled.");
            getLogger().setLevel(Level.FINE);
        }

        getLogger().info("FriendlyDeathChest has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up resources
        if (chestManager != null) {
            chestManager.saveChests();
            chestManager.cancelExpirationTask();
            chestManager.cancelExpiryWarningTask();
        }
        
        if (hologramManager != null) {
            hologramManager.removeAllHolograms();
            hologramManager.stopUpdateTask();
        }
        
        getLogger().info("FriendlyDeathChest has been disabled!");
    }

    /**
     * Registers all event listeners
     */
    private void registerEventListeners() {
        PluginManager pm = getServer().getPluginManager();
        
        // Register death listener if chest creation is enabled
        if (configManager.shouldCreateChest()) {
            PlayerDeathListener deathListener = new PlayerDeathListener(this, configManager, locationFinder, chestManager, hologramManager);
            pm.registerEvents(deathListener, this);
            getLogger().info("Death chest creation enabled.");
        }
        
        // Register inventory listener
        InventoryListener inventoryListener = new InventoryListener(this, configManager, chestManager);
        pm.registerEvents(inventoryListener, this);
        
        // Register block listener if chest protection is enabled
        if (configManager.shouldProtectChest()) {
            BlockListener blockListener = new BlockListener(configManager, chestManager);
            pm.registerEvents(blockListener, this);
            getLogger().info("Death chest protection enabled.");
        }
    }

    /**
     * Gets the chest manager
     * 
     * @return The chest manager
     */
    public ChestManager getChestManager() {
        return chestManager;
    }
    
    /**
     * Gets the hologram manager
     * 
     * @return The hologram manager, or null if holograms are disabled
     */
    public HologramManager getHologramManager() {
        return hologramManager;
    }
} 