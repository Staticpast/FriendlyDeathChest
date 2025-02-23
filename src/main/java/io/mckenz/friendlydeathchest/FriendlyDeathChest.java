package io.mckenz.friendlydeathchest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;
import java.util.ArrayList;

public class FriendlyDeathChest extends JavaPlugin implements Listener {
    private int searchRadius = 1;
    private String messageNoChest;
    private String messageChestCreated;
    private String messageChestRemoved;
    private String signLine1;
    private String signLine2;
    private String signLine3;
    private String messageSignProtected;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration
        loadConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FriendlyDeathChest has been enabled!");
    }

    private void loadConfig() {
        // Load search radius
        searchRadius = getConfig().getInt("search-radius", 1);
        
        // Load messages
        messageNoChest = getConfig().getString("messages.no-chest-location", 
            "§c[FriendlyDeathChest] Could not create a chest. Items dropped normally.");
        messageChestCreated = getConfig().getString("messages.chest-created", 
            "§6[FriendlyDeathChest] Your items are safe in a chest at: §e%d, %d, %d");
        messageChestRemoved = getConfig().getString("messages.chest-removed", 
            "§6[FriendlyDeathChest] Chest removed as it is now empty.");
        messageSignProtected = getConfig().getString("messages.sign-protected",
            "§c[FriendlyDeathChest] Cannot remove sign while chest contains items!");
            
        // Load sign text
        signLine1 = getConfig().getString("sign.line1", "Death Chest");
        signLine2 = getConfig().getString("sign.line2", "%s");
        signLine3 = getConfig().getString("sign.line3", "Rest in peace");
    }

    @Override
    public void onDisable() {
        getLogger().info("FriendlyDeathChest has been disabled!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        
        // Collect all items including armor and off-hand
        List<ItemStack> allItems = new ArrayList<>(event.getDrops()); // Regular drops
        
        // Add armor contents if keep inventory is false
        if (!event.getKeepInventory()) {
            ItemStack[] armorContents = player.getInventory().getArmorContents();
            for (ItemStack armor : armorContents) {
                if (armor != null && armor.getType() != Material.AIR) {
                    allItems.add(armor);
                }
            }
            
            // Add off-hand item
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() != Material.AIR) {
                allItems.add(offHand);
            }
            
            // Clear armor and off-hand to prevent double drops
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
        }

        // Don't do anything if there are no items to store
        if (allItems.isEmpty()) {
            return;
        }

        // Count total non-null items
        int itemCount = (int) allItems.stream()
            .filter(item -> item != null && item.getType() != Material.AIR)
            .count();

        // Determine if we need a double chest (27 slots in single, 54 in double)
        boolean needsDoubleChest = itemCount > 27;

        // Find a suitable location for the chest
        Block chestBlock = findSuitableLocation(deathLocation);
        if (chestBlock == null) {
            // If no suitable location found, let items drop normally
            player.sendMessage(messageNoChest);
            // Restore items to drops list
            event.getDrops().clear();
            event.getDrops().addAll(allItems);
            return;
        }

        // Place and fill the chest
        chestBlock.setType(Material.CHEST);
        if (needsDoubleChest) {
            // Find adjacent block for double chest
            Block secondChestBlock = findAdjacentSpace(chestBlock);
            if (secondChestBlock != null) {
                secondChestBlock.setType(Material.CHEST);
            } else {
                // If we can't make a double chest, items might be lost
                getLogger().warning("Could not create double chest - some items might be lost!");
            }
        }

        Chest chest = (Chest) chestBlock.getState();
        
        // Add sign on top of chest with custom text
        Block signBlock = chestBlock.getRelative(0, 1, 0);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof org.bukkit.block.Sign sign) {
            sign.setLine(0, signLine1);
            sign.setLine(1, String.format(signLine2, player.getName()));
            sign.setLine(2, signLine3);
            sign.update();
        }
        
        // Clear the drops and add all items to chest
        event.getDrops().clear();
        for (ItemStack item : allItems) {
            if (item != null) {
                chest.getInventory().addItem(item);
            }
        }

        // Play creation effects
        Location chestLoc = chestBlock.getLocation().add(0.5, 0.5, 0.5);
        chestLoc.getWorld().spawnParticle(Particle.FLAME, chestLoc, 50, 0.5, 0.5, 0.5, 0.1);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);

        // Send custom coordinates message to player
        String message = String.format(messageChestCreated,
                chestBlock.getX(), chestBlock.getY(), chestBlock.getZ());
        player.sendMessage(message);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest)) {
            return;
        }

        Chest chest = (Chest) event.getInventory().getHolder();
        Location chestLoc = chest.getLocation().add(0.5, 0.5, 0.5);

        // Play discovery effects
        chestLoc.getWorld().spawnParticle(Particle.END_ROD, chestLoc, 20, 0.2, 0.2, 0.2, 0.05);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        
        if (!(inventory.getHolder() instanceof Chest)) {
            return;
        }
        
        Chest chest = (Chest) inventory.getHolder();
        
        if (inventory.isEmpty()) {
            Location chestLoc = chest.getLocation().add(0.5, 0.5, 0.5);

            // Play disappearing effects
            chestLoc.getWorld().spawnParticle(Particle.SMOKE, chestLoc, 30, 0.2, 0.2, 0.2, 0.05);
            chestLoc.getWorld().spawnParticle(Particle.PORTAL, chestLoc, 20, 0.2, 0.2, 0.2, 0.5);
            chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);

            // Schedule the chest and sign removal
            getServer().getScheduler().runTask(this, () -> {
                // Remove sign first without dropping it
                Block signBlock = chest.getBlock().getRelative(0, 1, 0);
                if (signBlock.getType() == Material.OAK_SIGN) {
                    signBlock.setType(Material.AIR, false);
                }
                // Then remove chest without dropping it
                chest.getBlock().setType(Material.AIR, false);
                if (event.getPlayer() instanceof Player) {
                    ((Player) event.getPlayer()).sendMessage(messageChestRemoved);
                }
            });
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        // Handle chest break
        if (block.getType() == Material.CHEST) {
            Block signBlock = block.getRelative(0, 1, 0);
            if (signBlock.getType() == Material.OAK_SIGN) {
                // Remove sign without dropping it
                signBlock.setType(Material.AIR, false);
            }
        }
        
        // Handle sign break
        if (block.getType() == Material.OAK_SIGN) {
            Block chestBlock = block.getRelative(0, -1, 0);
            if (chestBlock.getType() == Material.CHEST) {
                Chest chest = (Chest) chestBlock.getState();
                if (!chest.getInventory().isEmpty()) {
                    // Cancel sign breaking if chest still has items
                    event.setCancelled(true);
                    if (event.getPlayer() instanceof Player) {
                        ((Player) event.getPlayer()).sendMessage(messageSignProtected);
                    }
                }
            }
        }
    }

    private Block findSuitableLocation(Location location) {
        Block block = location.getBlock();
        
        // Check the death location first
        if (isValidChestLocation(block)) {
            return block;
        }

        // Check nearby blocks in configurable radius
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block relative = block.getRelative(x, y, z);
                    if (isValidChestLocation(relative)) {
                        return relative;
                    }
                }
            }
        }

        return null;
    }

    private Block findAdjacentSpace(Block block) {
        // Check all four sides
        Block[] adjacentBlocks = {
            block.getRelative(1, 0, 0),
            block.getRelative(-1, 0, 0),
            block.getRelative(0, 0, 1),
            block.getRelative(0, 0, -1)
        };

        for (Block adjacent : adjacentBlocks) {
            if (adjacent.getType() == Material.AIR && 
                adjacent.getRelative(0, -1, 0).getType().isSolid() &&
                adjacent.getRelative(0, 1, 0).getType() == Material.AIR) {
                return adjacent;
            }
        }
        return null;
    }

    private boolean isValidChestLocation(Block block) {
        // Check the main chest location
        if (!(block.getType() == Material.AIR &&
            block.getRelative(0, -1, 0).getType().isSolid() &&
            block.getRelative(0, 1, 0).getType() == Material.AIR)) {
            return false;
        }

        // If we might need a double chest, check if at least one adjacent space is available
        Block adjacent = findAdjacentSpace(block);
        return adjacent != null;
    }
} 