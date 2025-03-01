package io.mckenz.friendlydeathchest.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages configuration for the FriendlyDeathChest plugin
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    
    // General settings
    private boolean enabled;
    
    // Death chest settings
    private boolean createChest;
    private int chestLifetime;
    private String invalidLocationBehavior;
    private int maxSearchRadius;
    private int searchRadius;
    private boolean protectChest;
    private boolean namedChest;
    
    // Hologram settings
    private boolean enableHologram;
    private String hologramText;
    private boolean showTimeRemaining;
    
    // Sign settings
    private boolean enableSign;
    private String signLine1;
    private String signLine2;
    private String signLine3;
    private String signLine4;
    private String signMaterial;
    
    // Item handling settings
    private String overflowBehavior;
    private boolean storeExperience;
    private Set<String> excludedItems;
    
    // Messages
    private String messageNoValidLocation;
    private String deathMessage;
    private String collectionMessage;
    private String messageSignProtected;
    private String messageNoPermission;
    private String messageExpiryWarning;
    private int expiryWarningTime;
    
    // Advanced settings
    private boolean persistentStorage;
    private boolean respectProtectionPlugins;
    private boolean debug;
    
    // Update checker settings
    private boolean updateCheckerEnabled;
    private int updateCheckerResourceId;
    private boolean updateCheckerNotifyAdmins;
    
    /**
     * Creates a new ConfigManager instance
     * 
     * @param plugin The plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        
        // Ensure the default config is saved
        plugin.saveDefaultConfig();
        
        // Load the configuration
        loadConfig();
    }
    
    /**
     * Reloads the configuration from disk
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }
    
    /**
     * Loads all configuration values from the config file
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        // Load general settings
        enabled = config.getBoolean("enabled", true);
        
        // Load death chest settings
        createChest = config.getBoolean("create-chest", true);
        chestLifetime = config.getInt("chest-lifetime", 15);
        invalidLocationBehavior = config.getString("invalid-location-behavior", "CLOSEST_VALID");
        maxSearchRadius = config.getInt("max-search-radius", 10);
        protectChest = config.getBoolean("protect-chest", true);
        namedChest = config.getBoolean("named-chest", true);
        
        // Load hologram settings
        enableHologram = config.getBoolean("enable-hologram", true);
        hologramText = config.getString("hologram-text", "&c{player}'s Death Chest");
        showTimeRemaining = config.getBoolean("show-time-remaining", true);
        
        // Load sign settings
        enableSign = config.getBoolean("enable-sign", true);
        signLine1 = config.getString("sign.line1", "Death Chest");
        signLine2 = config.getString("sign.line2", "{player}");
        signLine3 = config.getString("sign.line3", "Rest in peace");
        signLine4 = config.getString("sign.line4", "");
        
        // Load sign material (added in v1.0.4)
        String configSignMaterial = config.getString("sign-material", "OAK");
        // Validate the material
        try {
            Material.valueOf(configSignMaterial + "_WALL_SIGN");
            signMaterial = configSignMaterial;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sign material: " + configSignMaterial + ". Using OAK instead.");
            signMaterial = "OAK";
        }
        
        // Load item handling settings
        overflowBehavior = config.getString("overflow-behavior", "DOUBLE_CHEST");
        storeExperience = config.getBoolean("store-experience", true);
        excludedItems = new HashSet<>(config.getStringList("excluded-items"));
        
        // Load search radius
        searchRadius = config.getInt("search-radius", 1);
        
        // Load messages
        messageNoValidLocation = config.getString("messages.no-valid-location", 
            "&c[FriendlyDeathChest] Could not create a chest. Items dropped normally.");
        deathMessage = config.getString("death-message", 
            "&c[FriendlyDeathChest] &fYour items have been stored in a chest at &e{location}");
        collectionMessage = config.getString("collection-message", 
            "&aYou have collected all items from your death chest!");
        messageSignProtected = config.getString("messages.sign-protected",
            "&c[FriendlyDeathChest] Cannot remove sign while chest contains items!");
        messageNoPermission = "&c[FriendlyDeathChest] This is not your death chest!";
        messageExpiryWarning = config.getString("expiry-warning-message", 
            "&eYour death chest at &f{location} &ewill disappear in &f{time}&e!");
        expiryWarningTime = config.getInt("expiry-warning-time", 5);
        
        // Load advanced settings
        persistentStorage = config.getBoolean("persistent-storage", true);
        respectProtectionPlugins = config.getBoolean("respect-protection-plugins", true);
        debug = config.getBoolean("debug", false);
        
        // Load update checker settings
        updateCheckerEnabled = config.getBoolean("update-checker.enabled", true);
        updateCheckerResourceId = config.getInt("update-checker.resource-id", 0);
        updateCheckerNotifyAdmins = config.getBoolean("update-checker.notify-admins", true);
        
        // Log debug information if enabled
        if (debug) {
            plugin.getLogger().info("Debug mode enabled");
            plugin.getLogger().info("Loaded configuration: enabled=" + enabled);
            plugin.getLogger().info("Death chest settings: createChest=" + createChest + ", lifetime=" + chestLifetime);
        }
    }
    
    // Getters for all configuration values
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean shouldCreateChest() {
        return createChest;
    }
    
    public int getChestLifetime() {
        return chestLifetime;
    }
    
    public String getInvalidLocationBehavior() {
        return invalidLocationBehavior;
    }
    
    public int getMaxSearchRadius() {
        return maxSearchRadius;
    }
    
    public int getSearchRadius() {
        return searchRadius;
    }
    
    public boolean shouldProtectChest() {
        return protectChest;
    }
    
    public boolean shouldNameChest() {
        return namedChest;
    }
    
    public boolean isHologramEnabled() {
        return enableHologram;
    }
    
    public String getHologramText() {
        return hologramText;
    }
    
    public boolean shouldShowTimeRemaining() {
        return showTimeRemaining;
    }
    
    public boolean isSignEnabled() {
        return enableSign;
    }
    
    public String getSignLine1() {
        return signLine1;
    }
    
    public String getSignLine2() {
        return signLine2;
    }
    
    public String getSignLine3() {
        return signLine3;
    }
    
    public String getSignLine4() {
        return signLine4;
    }
    
    public String getOverflowBehavior() {
        return overflowBehavior;
    }
    
    public boolean shouldStoreExperience() {
        return storeExperience;
    }
    
    public Set<String> getExcludedItems() {
        return excludedItems;
    }
    
    public String getMessageNoChest() {
        return messageNoValidLocation;
    }
    
    public String getMessageChestCreated() {
        return deathMessage;
    }
    
    public String getMessageChestRemoved() {
        return collectionMessage;
    }
    
    public String getMessageSignProtected() {
        return messageSignProtected;
    }
    
    public String getMessageNoPermission() {
        return messageNoPermission;
    }
    
    public String getMessageExpiryWarning() {
        return messageExpiryWarning;
    }
    
    public int getExpiryWarningTime() {
        return expiryWarningTime;
    }
    
    /**
     * Gets whether persistent storage is enabled
     * 
     * @return true if persistent storage is enabled
     */
    public boolean isPersistentStorageEnabled() {
        return persistentStorage;
    }
    
    public boolean shouldRespectProtectionPlugins() {
        return respectProtectionPlugins;
    }
    
    public boolean isDebugEnabled() {
        return debug;
    }
    
    /**
     * Gets whether the update checker is enabled
     * 
     * @return true if the update checker is enabled
     */
    public boolean isUpdateCheckerEnabled() {
        return updateCheckerEnabled;
    }
    
    /**
     * Gets the SpigotMC resource ID for the update checker
     * 
     * @return the resource ID
     */
    public int getUpdateCheckerResourceId() {
        return updateCheckerResourceId;
    }
    
    /**
     * Gets whether to notify admins about updates
     * 
     * @return true if admins should be notified
     */
    public boolean shouldNotifyAdminsAboutUpdates() {
        return updateCheckerNotifyAdmins;
    }
    
    /**
     * Gets the plugin instance
     * 
     * @return The plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    /**
     * Gets the invalid location message
     * 
     * @return The invalid location message 
     */
    public String getInvalidLocationMessage() {
        return messageNoValidLocation;
    }
    
    /**
     * Checks if an invalid location message should be sent
     * 
     * @return true if the message should be sent
     */
    public boolean sendInvalidLocationMessage() {
        return !messageNoValidLocation.isEmpty();
    }
    
    /**
     * Gets the chest creation message
     * 
     * @return The chest creation message
     */
    public String getCreationMessage() {
        return deathMessage;
    }
    
    /**
     * Checks if a creation message should be sent
     *
     * @return true if the message should be sent
     */
    public boolean sendCreationMessage() {
        return !deathMessage.isEmpty();
    }
    
    /**
     * Gets the sign text lines
     * 
     * @return Array of sign text lines
     */
    public String[] getSignText() {
        return new String[]{signLine1, signLine2, signLine3, signLine4};
    }
    
    /**
     * Gets the expiry warning message
     * 
     * @return The expiry warning message
     */
    public String getExpiryWarningMessage() {
        return messageExpiryWarning;
    }
    
    /**
     * Gets the sign material type (without the _WALL_SIGN suffix)
     * 
     * @return The sign material type (e.g., "OAK", "SPRUCE", etc.)
     */
    public String getSignMaterial() {
        return signMaterial;
    }
} 