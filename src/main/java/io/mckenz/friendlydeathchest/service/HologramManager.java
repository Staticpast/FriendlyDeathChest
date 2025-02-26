package io.mckenz.friendlydeathchest.service;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.model.ChestData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages holograms for death chests
 */
public class HologramManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ChestManager chestManager;
    
    private final Map<Location, ArmorStand> holograms = new HashMap<>();
    private BukkitRunnable updateTask;
    
    /**
     * Creates a new HologramManager
     * 
     * @param plugin The plugin instance
     * @param config The configuration manager
     * @param chestManager The chest manager
     */
    public HologramManager(JavaPlugin plugin, ConfigManager config, ChestManager chestManager) {
        this.plugin = plugin;
        this.config = config;
        this.chestManager = chestManager;
        
        // Only start the update task if holograms are enabled and time display is enabled
        if (config.isHologramEnabled() && config.shouldShowTimeRemaining()) {
            startUpdateTask();
        }
    }
    
    /**
     * Creates a hologram for a death chest
     * 
     * @param location The chest location
     * @param playerName The name of the player who died
     * @param expiryTime The time when the chest will expire
     */
    public void createHologram(Location location, String playerName, long expiryTime) {
        if (!config.isHologramEnabled()) {
            return;
        }
        
        // Remove any existing hologram at this location
        removeHologram(location);
        
        // Create a new hologram
        Location holoLoc = location.clone().add(0.5, 1.5, 0.5);
        
        ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(holoLoc, EntityType.ARMOR_STAND);
        hologram.setCustomName(formatHologramText(playerName, expiryTime));
        hologram.setCustomNameVisible(true);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setVisible(false);
        hologram.setSmall(true);
        hologram.setMarker(true);
        
        // Store the hologram
        holograms.put(location, hologram);
    }
    
    /**
     * Removes a hologram
     * 
     * @param location The chest location
     */
    public void removeHologram(Location location) {
        ArmorStand hologram = holograms.remove(location);
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
    }
    
    /**
     * Removes all holograms
     */
    public void removeAllHolograms() {
        for (ArmorStand hologram : holograms.values()) {
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
            }
        }
        holograms.clear();
    }
    
    /**
     * Formats the hologram text with placeholders
     * 
     * @param playerName The player name
     * @param expiryTime The expiry time
     * @return The formatted text
     */
    private String formatHologramText(String playerName, long expiryTime) {
        String text = config.getHologramText();
        
        // Replace color codes
        text = ChatColor.translateAlternateColorCodes('&', text);
        
        // Replace placeholders
        text = text.replace("{player}", playerName);
        
        if (config.shouldShowTimeRemaining() && expiryTime > 0) {
            long timeLeft = Math.max(0, (expiryTime - System.currentTimeMillis()) / 60000); // minutes
            text = text.replace("{time}", String.valueOf(timeLeft));
        }
        
        return text;
    }
    
    /**
     * Starts the hologram update task
     */
    private void startUpdateTask() {
        // Cancel any existing task
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        // Create a new update task
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Update each hologram with the current time remaining
                for (Map.Entry<Location, ArmorStand> entry : holograms.entrySet()) {
                    Location location = entry.getKey();
                    ArmorStand hologram = entry.getValue();
                    
                    // Skip if hologram or location is invalid
                    if (hologram == null || hologram.isDead() || location == null) {
                        continue;
                    }
                    
                    // Get chest data
                    ChestData chestData = chestManager.getChestData(location);
                    if (chestData == null) {
                        // Chest data is missing, remove hologram
                        removeHologram(location);
                        continue;
                    }
                    
                    // Get player name
                    UUID ownerUUID = chestData.getOwnerUUID();
                    String playerName = plugin.getServer().getOfflinePlayer(ownerUUID).getName();
                    if (playerName == null) {
                        playerName = "Unknown";
                    }
                    
                    // Update hologram text
                    hologram.setCustomName(formatHologramText(playerName, chestData.getExpiryTime()));
                }
            }
        };
        
        // Start the task (update every 10 seconds)
        updateTask.runTaskTimer(plugin, 20 * 10, 20 * 10);
    }
    
    /**
     * Stops the hologram update task
     */
    public void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }
    
    /**
     * Checks if a entity is a death chest hologram
     * 
     * @param entity The entity to check
     * @return true if the entity is a death chest hologram
     */
    public boolean isDeathChestHologram(Entity entity) {
        return entity instanceof ArmorStand && holograms.containsValue(entity);
    }
} 