package io.mckenz.friendlydeathchest.service;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Responsible for finding appropriate chest locations
 */
public class LocationFinder {
    private final ConfigManager config;
    private ChestManager chestManager;
    
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    /**
     * Creates a new LocationFinder
     * 
     * @param config The configuration manager
     * @param chestManager The chest manager
     */
    public LocationFinder(ConfigManager config, ChestManager chestManager) {
        this.config = config;
        this.chestManager = chestManager;
    }

    /**
     * Finds a suitable location for a death chest
     * 
     * @param deathLocation The location where the player died
     * @return A suitable location, or null if none found
     */
    public Location findChestLocation(Location deathLocation) {
        return findChestLocation(deathLocation, null);
    }
    
    /**
     * Finds a suitable location for a death chest
     * 
     * @param deathLocation The location where the player died
     * @param player The player who died (for permission checks)
     * @return A suitable location, or null if none found
     */
    public Location findChestLocation(Location deathLocation, Player player) {
        World world = deathLocation.getWorld();
        if (world == null) {
            return null;
        }
        
        // Get the max search radius from config
        int maxRadius = config.getMaxSearchRadius();
        String invalidBehavior = config.getInvalidLocationBehavior();
        
        // Try the exact death location first
        Block block = deathLocation.getBlock();
        if (isValidChestLocation(block, player)) {
            return block.getLocation();
        }
        
        // If we shouldn't search for alternatives, return null
        if ("CANCEL".equalsIgnoreCase(invalidBehavior)) {
            return null;
        }
        
        // Search in a spiral pattern around the death location
        for (int radius = 1; radius <= maxRadius; radius++) {
            // Try locations in increasing radius
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only check the perimeter of the current radius
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    // Check this location
                    Block testBlock = block.getRelative(x, 0, z);
                    if (isValidChestLocation(testBlock, player)) {
                        return testBlock.getLocation();
                    }
                    
                    // Check one block above
                    testBlock = block.getRelative(x, 1, z);
                    if (isValidChestLocation(testBlock, player)) {
                        return testBlock.getLocation();
                    }
                    
                    // Check one block below
                    testBlock = block.getRelative(x, -1, z);
                    if (isValidChestLocation(testBlock, player)) {
                        return testBlock.getLocation();
                    }
                }
            }
        }
        
        // If we get here, no valid location was found within the radius
        return null;
    }
    
    /**
     * Checks if a block location is valid for placing a chest
     * 
     * @param block The block to check
     * @param player The player to check permissions for, or null to skip permission checks
     * @return true if the location is valid
     */
    private boolean isValidChestLocation(Block block, Player player) {
        // Must be air or a replaceable block
        if (!isReplaceable(block.getType())) {
            return false;
        }
        
        // Must have a solid block beneath
        if (!block.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return false;
        }
        
        // Check if player has permission to build here
        if (player != null && chestManager != null && 
            config.shouldRespectProtectionPlugins() && 
            !chestManager.canBuild(player, block.getLocation())) {
            return false;
        }
        
        // Check for chests nearby (can't place double chests)
        for (BlockFace face : HORIZONTAL_FACES) {
            if (block.getRelative(face).getType() == Material.CHEST) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if a material is replaceable
     * 
     * @param material The material to check
     * @return true if the material is replaceable
     */
    private boolean isReplaceable(Material material) {
        return material == Material.AIR || 
               material == Material.CAVE_AIR ||
               material == Material.VOID_AIR ||
               material == Material.WATER ||
               material == Material.GRASS_BLOCK ||
               material == Material.TALL_GRASS ||
               material == Material.SEAGRASS ||
               material == Material.SNOW ||
               material == Material.VINE ||
               material == Material.DEAD_BUSH;
    }

    public void setChestManager(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    private boolean isValidGround(Block block) {
        return block.getType().isSolid() && 
               block.getType() != Material.BARRIER &&
               block.getType() != Material.BEDROCK;
    }
} 