package io.mckenz.friendlydeathchest.listeners;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.model.ChestData;
import io.mckenz.friendlydeathchest.service.ChestManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listener for block interactions with death chests
 */
public class BlockListener implements Listener {
    private final ConfigManager config;
    private final ChestManager chestManager;
    
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };
    
    /**
     * Creates a new BlockListener
     * 
     * @param config The configuration manager
     * @param chestManager The chest manager service
     */
    public BlockListener(ConfigManager config, ChestManager chestManager) {
        this.config = config;
        this.chestManager = chestManager;
    }
    
    /**
     * Handles block break events for death chests and their signs
     * 
     * @param event The block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        // Handle chest break
        if (block.getType() == Material.CHEST) {
            // Check if this is a death chest
            ChestData chestData = chestManager.getChestData(block.getLocation());
            if (chestData != null && config.shouldProtectChest()) {
                // Check if player has permission to break this chest
                Player player = event.getPlayer();
                if (!chestManager.canAccessChest(player, chestData)) {
                    event.setCancelled(true);
                    player.sendMessage(config.getMessageNoPermission());
                    return;
                }
            }
            
            if (config.isSignEnabled()) {
                // Check all horizontal directions for attached wall signs
                for (BlockFace face : HORIZONTAL_FACES) {
                    Block adjacentBlock = block.getRelative(face);
                    if (isWallSign(adjacentBlock)) {
                        WallSign signData = (WallSign) adjacentBlock.getBlockData();
                        // Check if the sign is facing towards our chest
                        if (signData.getFacing() == face) {
                            // Remove sign without dropping it
                            adjacentBlock.setType(Material.AIR, false);
                        }
                    }
                }
            }
            
            // Remove from tracking if it was a death chest
            if (chestData != null) {
                chestManager.removeChest(block, event.getPlayer());
                // Cancel the event to prevent the chest from dropping
                event.setCancelled(true);
            }
        }
        
        // Handle wall sign break
        if (isWallSign(block) && config.isSignEnabled()) {
            WallSign signData = (WallSign) block.getBlockData();
            // The sign is facing towards the chest, so we need to get the block it's facing
            BlockFace signFace = signData.getFacing();
            Block chestBlock = block.getRelative(signFace);
            
            if (chestBlock.getType() == Material.CHEST) {
                // Check if this is a death chest
                ChestData chestData = chestManager.getChestData(chestBlock.getLocation());
                if (chestData != null) {
                    Chest chest = (Chest) chestBlock.getState();
                    if (!chest.getInventory().isEmpty()) {
                        // Cancel sign breaking if chest still has items
                        event.setCancelled(true);
                        if (event.getPlayer() instanceof Player) {
                            ((Player) event.getPlayer()).sendMessage(config.getMessageSignProtected());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a block is a wall sign
     * 
     * @param block The block to check
     * @return true if the block is a wall sign
     */
    private boolean isWallSign(Block block) {
        return block.getType() == Material.OAK_WALL_SIGN ||
               block.getType() == Material.SPRUCE_WALL_SIGN ||
               block.getType() == Material.BIRCH_WALL_SIGN ||
               block.getType() == Material.JUNGLE_WALL_SIGN ||
               block.getType() == Material.ACACIA_WALL_SIGN ||
               block.getType() == Material.DARK_OAK_WALL_SIGN ||
               block.getType() == Material.CRIMSON_WALL_SIGN ||
               block.getType() == Material.WARPED_WALL_SIGN ||
               block.getType() == Material.MANGROVE_WALL_SIGN ||
               block.getType() == Material.CHERRY_WALL_SIGN ||
               block.getType() == Material.BAMBOO_WALL_SIGN;
    }
} 