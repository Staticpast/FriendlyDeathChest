package io.mckenz.friendlydeathchest.listeners;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.model.ChestData;
import io.mckenz.friendlydeathchest.service.ChestManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for inventory interactions with death chests
 */
public class InventoryListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ChestManager chestManager;
    
    /**
     * Creates a new InventoryListener
     * 
     * @param plugin The plugin instance
     * @param config The configuration manager
     * @param chestManager The chest manager service
     */
    public InventoryListener(JavaPlugin plugin, ConfigManager config, ChestManager chestManager) {
        this.plugin = plugin;
        this.config = config;
        this.chestManager = chestManager;
    }
    
    /**
     * Handles inventory open events for death chests
     * 
     * @param event The inventory open event
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest)) {
            return;
        }

        Chest chest = (Chest) event.getInventory().getHolder();
        Block chestBlock = chest.getBlock();
        
        // Check if this is a death chest
        ChestData chestData = chestManager.getChestData(chestBlock.getLocation());
        if (chestData == null) {
            return;
        }
        
        // Check for stored experience
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            
            // Only give XP to the chest owner
            if (player.getUniqueId().equals(chestData.getOwnerUUID())) {
                chestManager.restoreExperience(chest, player);
            }
        }

        Location chestLoc = chest.getLocation().add(0.5, 0.5, 0.5);

        // Play discovery effects
        chestLoc.getWorld().spawnParticle(Particle.END_ROD, chestLoc, 20, 0.2, 0.2, 0.2, 0.05);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    /**
     * Handles inventory close events for death chests
     * 
     * @param event The inventory close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        
        if (!(inventory.getHolder() instanceof Chest)) {
            return;
        }
        
        Chest chest = (Chest) inventory.getHolder();
        Block chestBlock = chest.getBlock();
        
        // Check if this is a death chest
        if (!chestManager.isDeathChest(chestBlock.getLocation())) {
            return;
        }
        
        if (inventory.isEmpty()) {
            // Schedule the chest removal (must be done in the next tick)
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Get the player who closed the inventory
                    Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
                    
                    // Remove the chest, sign, and hologram
                    chestManager.removeChest(chestBlock, player);
                    
                    // Play additional effects to make it more noticeable
                    if (player != null) {
                        Location playerLoc = player.getLocation();
                        playerLoc.getWorld().playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    }
                    
                    if (config.isDebugEnabled()) {
                        plugin.getLogger().info("Death chest removed after being emptied at " + chestBlock.getLocation());
                    }
                }
            }.runTask(plugin);
        }
    }
} 