package io.mckenz.friendlydeathchest.listeners;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.model.ChestData;
import io.mckenz.friendlydeathchest.service.ChestManager;
import io.mckenz.friendlydeathchest.service.HologramManager;
import io.mckenz.friendlydeathchest.service.LocationFinder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles player death events
 */
public class PlayerDeathListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final LocationFinder locationFinder;
    private final ChestManager chestManager;
    private final HologramManager hologramManager;

    /**
     * Creates a new PlayerDeathListener
     *
     * @param plugin The plugin instance
     * @param config The configuration manager
     * @param locationFinder The location finder service
     * @param chestManager The chest manager service
     * @param hologramManager The hologram manager service (can be null if disabled)
     */
    public PlayerDeathListener(JavaPlugin plugin, ConfigManager config, LocationFinder locationFinder, 
                               ChestManager chestManager, HologramManager hologramManager) {
        this.plugin = plugin;
        this.config = config;
        this.locationFinder = locationFinder;
        this.chestManager = chestManager;
        this.hologramManager = hologramManager;
    }

    /**
     * Handles player death events
     *
     * @param event The player death event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Check if player has permission to have a death chest
        if (!player.hasPermission("friendlydeathchest.chest")) {
            return;
        }
        
        // Check if there are any items to store
        if (event.getDrops().isEmpty() && event.getDroppedExp() == 0) {
            return;
        }
        
        // Find a suitable location for the chest
        Location deathLoc = player.getLocation();
        Location chestLoc = locationFinder.findChestLocation(deathLoc);
        
        if (chestLoc == null) {
            // No valid location found
            if (config.sendInvalidLocationMessage()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getInvalidLocationMessage()));
            }
            return;
        }
        
        // Create the chest and store items
        createDeathChest(player, chestLoc, event.getDrops(), event.getDroppedExp());
        
        // Clear the drops
        event.getDrops().clear();
        event.setDroppedExp(0);
        
        // Send creation message
        if (config.sendCreationMessage()) {
            String message = config.getCreationMessage()
                    .replace("{location}", formatLocation(chestLoc))
                    .replace("{x}", String.valueOf(chestLoc.getBlockX()))
                    .replace("{y}", String.valueOf(chestLoc.getBlockY()))
                    .replace("{z}", String.valueOf(chestLoc.getBlockZ()));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    /**
     * Creates a death chest at the specified location
     *
     * @param player The player who died
     * @param location The location to create the chest
     * @param items The items to store in the chest
     * @param experience The experience to store in the chest
     */
    private void createDeathChest(Player player, Location location, List<ItemStack> items, int experience) {
        // Set the chest block
        Block block = location.getBlock();
        block.setType(Material.CHEST);
        
        // Get the chest inventory
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();
        
        // Create a copy of the items to prevent modification of the original list
        List<ItemStack> itemCopy = new ArrayList<>(items);
        
        // Fill the chest with the items
        for (ItemStack item : itemCopy) {
            if (item != null && item.getType() != Material.AIR) {
                // Try to add the item to the chest
                if (inventory.addItem(item).isEmpty()) {
                    // Item was added successfully, remove it from the original list
                    items.remove(item);
                }
            }
        }
        
        // Calculate expiry time (if applicable)
        long expiryTime = 0;
        if (config.getChestLifetime() > 0) {
            expiryTime = System.currentTimeMillis() + (config.getChestLifetime() * 60000L);
        }
        
        // Register the chest with the chest manager
        ChestData chestData = new ChestData(player.getUniqueId(), experience, expiryTime);
        chestManager.registerChest(location, chestData);
        
        // Add a sign if enabled
        if (config.isSignEnabled()) {
            createSign(location, player.getName());
        }
        
        // Create hologram if enabled
        if (hologramManager != null && config.isHologramEnabled()) {
            hologramManager.createHologram(location, player.getName(), expiryTime);
        }
    }
    
    /**
     * Creates a sign on the front of the chest
     *
     * @param chestLocation The location of the chest
     * @param playerName The name of the player who died
     */
    private void createSign(Location chestLocation, String playerName) {
        World world = chestLocation.getWorld();
        if (world == null) {
            return;
        }
        
        // Get the chest block
        Block chestBlock = chestLocation.getBlock();
        
        // Get the chest's facing direction if possible
        BlockFace chestFacing = null;
        if (chestBlock.getBlockData() instanceof org.bukkit.block.data.Directional) {
            chestFacing = ((org.bukkit.block.data.Directional) chestBlock.getBlockData()).getFacing();
        }
        
        // If we couldn't determine the chest's facing, use our priority order
        if (chestFacing == null) {
            // Determine the best direction to place the sign
            chestFacing = determineSignDirection(chestBlock);
            if (chestFacing == null) {
                // If no valid direction found, don't place a sign
                return;
            }
        }
        
        // Get the block in front of the chest
        Block signBlock = chestBlock.getRelative(chestFacing);
        if (signBlock.getType() != Material.AIR) {
            // If the block is not air, don't place a sign
            return;
        }
        
        // Set the wall sign block with correct orientation
        Material signMaterial = getWallSignMaterial();
        signBlock.setType(signMaterial);
        
        // Set the sign direction
        org.bukkit.block.data.type.WallSign signData = 
            (org.bukkit.block.data.type.WallSign) signBlock.getBlockData();
        
        // The sign should be attached to the chest and face outward
        // So the sign's facing direction should be the same as the chest's facing
        signData.setFacing(chestFacing);
        signBlock.setBlockData(signData);
        
        // Log debug information if enabled
        if (config.isDebugEnabled()) {
            plugin.getLogger().info("Placed death chest sign at " + signBlock.getLocation() + 
                " facing " + chestFacing + " in front of chest at " + chestLocation);
        }
        
        // Update the sign text
        if (signBlock.getState() instanceof Sign) {
            Sign sign = (Sign) signBlock.getState();
            
            // Get current date for the date placeholder
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            String currentDate = dateFormat.format(new Date());
            
            // Format the text
            String[] lines = config.getSignText();
            for (int i = 0; i < lines.length && i < 4; i++) {
                String line = lines[i]
                        .replace("{player}", playerName)
                        .replace("{date}", currentDate)
                        .replace("&", "§");
                sign.setLine(i, line);
            }
            
            sign.update();
        }
    }
    
    /**
     * Determines the best direction to place a sign based on surrounding blocks
     *
     * @param chestBlock The chest block
     * @return The best direction to place the sign, or null if no valid direction
     */
    private BlockFace determineSignDirection(Block chestBlock) {
        // First try to determine the chest's facing direction
        if (chestBlock.getBlockData() instanceof org.bukkit.block.data.Directional) {
            BlockFace chestFacing = ((org.bukkit.block.data.Directional) chestBlock.getBlockData()).getFacing();
            
            // Check if the block in front of the chest is air
            Block frontBlock = chestBlock.getRelative(chestFacing);
            if (frontBlock.getType() == Material.AIR) {
                return chestFacing;
            }
        }
        
        // If we couldn't use the chest's facing, use our priority order
        // Priority order for sign placement (south is most visible in default view)
        BlockFace[] priorityOrder = new BlockFace[]{
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST
        };
        
        // Check in priority order
        for (BlockFace face : priorityOrder) {
            Block adjacent = chestBlock.getRelative(face);
            if (adjacent.getType() == Material.AIR) {
                return face;
            }
        }
        
        // If no direction has air, return null
        return null;
    }
    
    /**
     * Gets the appropriate wall sign material
     *
     * @return The wall sign material
     */
    private Material getWallSignMaterial() {
        // Default to oak wall sign, but could be made configurable in the future
        try {
            // Try to get the configured sign material if it exists
            String signType = config.getSignMaterial();
            if (signType != null && !signType.isEmpty()) {
                Material material = Material.valueOf(signType + "_WALL_SIGN");
                if (material != null) {
                    return material;
                }
            }
        } catch (Exception e) {
            // If any error occurs, fall back to oak
            if (config.isDebugEnabled()) {
                plugin.getLogger().warning("Error getting sign material: " + e.getMessage());
            }
        }
        
        // Default to oak wall sign
        return Material.OAK_WALL_SIGN;
    }
    
    /**
     * Formats a location into a readable string
     *
     * @param location The location to format
     * @return A formatted string representing the location
     */
    private String formatLocation(Location location) {
        return String.format("x:%d, y:%d, z:%d in %s", 
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ(),
                location.getWorld().getName());
    }
} 