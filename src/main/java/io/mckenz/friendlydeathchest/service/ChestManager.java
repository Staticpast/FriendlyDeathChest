package io.mckenz.friendlydeathchest.service;

import io.mckenz.friendlydeathchest.config.ConfigManager;
import io.mckenz.friendlydeathchest.model.ChestData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import static org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.TileState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.bukkit.World;

/**
 * Manages death chest creation, tracking, and cleanup
 */
public class ChestManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final LocationFinder locationFinder;
    
    private final Map<Location, ChestData> deathChests = new HashMap<>();
    private final NamespacedKey ownerKey;
    private final NamespacedKey expiryTimeKey;
    private final NamespacedKey experienceKey;
    
    private BukkitRunnable expiryWarningTask;
    private boolean persistentStorage;
    private boolean respectProtectionPlugins;
    
    /**
     * Creates a new ChestManager
     * 
     * @param plugin The plugin instance
     * @param config The configuration manager
     * @param locationFinder The location finder service
     */
    public ChestManager(JavaPlugin plugin, ConfigManager config, LocationFinder locationFinder) {
        this.plugin = plugin;
        this.config = config;
        this.locationFinder = locationFinder;
        
        // Create namespaced keys for persistent data
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.expiryTimeKey = new NamespacedKey(plugin, "expiry-time");
        this.experienceKey = new NamespacedKey(plugin, "stored-experience");
        
        this.persistentStorage = config.isPersistentStorageEnabled();
        this.respectProtectionPlugins = config.shouldRespectProtectionPlugins();
        
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        this.loadChests();
        
        // Start tasks
        if (config.getChestLifetime() > 0) {
            startExpirationTask();
        }
        this.startExpiryWarningTask();
    }
    
    /**
     * Creates a death chest for a player
     * 
     * @param player The player who died
     * @param location The death location
     * @param items The items to store in the chest
     * @param experiencePoints The experience points to store (0 if not storing)
     * @return A list of items that couldn't be stored, or an empty list if all were stored
     */
    public List<ItemStack> createDeathChest(Player player, Location location, List<ItemStack> items, int experiencePoints) {
        // Find a suitable location for the chest
        Location chestLocation = locationFinder.findChestLocation(location, player);
        
        if (chestLocation == null) {
            // If no suitable location found, return all items
            return new ArrayList<>(items);
        }
        
        Block chestBlock = chestLocation.getBlock();
        
        // Count total non-null items
        int itemCount = (int) items.stream().filter(item -> item != null && item.getType() != Material.AIR).count();
        
        // Determine if we need a double chest (27 slots in single, 54 in double)
        boolean needsDoubleChest = itemCount > 27 && "DOUBLE_CHEST".equalsIgnoreCase(config.getOverflowBehavior());
        
        // Place the chest
        chestBlock.setType(Material.CHEST);
        
        // Set up chest data
        long expiryTime = config.getChestLifetime() > 0 ? 
            System.currentTimeMillis() + (config.getChestLifetime() * 60 * 1000) : 0;
        
        // Store chest data for tracking
        ChestData chestData = new ChestData(player.getUniqueId(), experiencePoints, expiryTime);
        deathChests.put(chestBlock.getLocation(), chestData);
        
        // Store owner data and experience in the chest's persistent data container
        if (chestBlock.getState() instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            container.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            if (expiryTime > 0) {
                container.set(expiryTimeKey, PersistentDataType.LONG, expiryTime);
            }
            if (experiencePoints > 0) {
                container.set(experienceKey, PersistentDataType.INTEGER, experiencePoints);
            }
            tileState.update();
        }
        
        Block secondChestBlock = null;
        if (needsDoubleChest) {
            // Try to find an adjacent block for double chest
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                Block adjacent = chestBlock.getRelative(face);
                if (adjacent.getType() == Material.AIR && adjacent.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    secondChestBlock = adjacent;
                    break;
                }
            }
            
            if (secondChestBlock != null) {
                secondChestBlock.setType(Material.CHEST);
                
                // Connect the chests to form a double chest
                connectChests(chestBlock, secondChestBlock);
                
                // Store owner data in the second chest too
                if (secondChestBlock.getState() instanceof TileState tileState) {
                    PersistentDataContainer container = tileState.getPersistentDataContainer();
                    container.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    if (expiryTime > 0) {
                        container.set(expiryTimeKey, PersistentDataType.LONG, expiryTime);
                    }
                    tileState.update();
                }
                
                // Track the second chest too
                deathChests.put(secondChestBlock.getLocation(), 
                    new ChestData(player.getUniqueId(), 0, expiryTime));
            }
        }
        
        Chest chest = (Chest) chestBlock.getState();
        
        // Set custom name if enabled
        if (config.shouldNameChest()) {
            chest.setCustomName(player.getName() + "'s Death Chest");
            chest.update();
            
            if (secondChestBlock != null) {
                Chest secondChest = (Chest) secondChestBlock.getState();
                secondChest.setCustomName(player.getName() + "'s Death Chest");
                secondChest.update();
            }
        }
        
        // List to track items that couldn't be stored
        List<ItemStack> leftoverItems = new ArrayList<>();
        
        // Fill the chest(s) with the items, excluding items in the excluded list
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                // Skip excluded items
                if (config.getExcludedItems().contains(item.getType().getKey().toString())) {
                    leftoverItems.add(item);
                    continue;
                }
                
                // Try to add the item to the chest
                HashMap<Integer, ItemStack> leftovers = chest.getInventory().addItem(item);
                
                // If there are leftovers and we have a second chest, add to that
                if (!leftovers.isEmpty() && secondChestBlock != null) {
                    Chest secondChest = (Chest) secondChestBlock.getState();
                    leftovers = secondChest.getInventory().addItem(leftovers.values().toArray(new ItemStack[0]));
                }
                
                // If there are still leftovers, track them
                if (!leftovers.isEmpty()) {
                    leftoverItems.addAll(leftovers.values());
                }
            }
        }
        
        // Play creation effects
        Location chestLoc = chestBlock.getLocation().add(0.5, 0.5, 0.5);
        chestLoc.getWorld().spawnParticle(Particle.FLAME, chestLoc, 50, 0.5, 0.5, 0.5, 0.1);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        chestLoc.getWorld().playSound(chestLoc, Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);
        
        // Send custom coordinates message to player
        String message = config.getMessageChestCreated()
            .replace("{x}", String.valueOf(chestBlock.getX()))
            .replace("{y}", String.valueOf(chestBlock.getY()))
            .replace("{z}", String.valueOf(chestBlock.getZ()));
        player.sendMessage(message);
        
        return leftoverItems;
    }
    
    /**
     * Removes a death chest when it's empty
     * 
     * @param chestBlock The chest block to remove
     * @param player The player who emptied the chest, or null if expired
     */
    public void removeChest(Block chestBlock, Player player) {
        if (chestBlock.getType() != Material.CHEST) {
            return;
        }
        
        Location chestLoc = chestBlock.getLocation().add(0.5, 0.5, 0.5);
        
        // Play disappearing effects
        chestLoc.getWorld().spawnParticle(Particle.SMOKE, chestLoc, 30, 0.2, 0.2, 0.2, 0.05);
        chestLoc.getWorld().spawnParticle(Particle.PORTAL, chestLoc, 20, 0.2, 0.2, 0.2, 0.5);
        chestLoc.getWorld().playSound(chestLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
        
        // Remove sign first without dropping it
        if (config.isSignEnabled()) {
            Block signBlock = chestBlock.getRelative(0, 1, 0);
            if (signBlock.getType() == Material.OAK_SIGN) {
                signBlock.setType(Material.AIR, false);
            }
        }
        
        // Check if this is part of a double chest
        org.bukkit.block.data.type.Chest chestData = 
            (org.bukkit.block.data.type.Chest) chestBlock.getBlockData();
        
        if (chestData.getType() != Type.SINGLE) {
            // Find the other half of the chest
            Block otherHalf = getOtherHalfOfChest(chestBlock, chestData);
            
            // Remove the other half if it exists and is empty
            if (otherHalf != null && otherHalf.getType() == Material.CHEST) {
                Chest otherChest = (Chest) otherHalf.getState();
                if (otherChest.getInventory().isEmpty()) {
                    otherHalf.setType(Material.AIR, false);
                    deathChests.remove(otherHalf.getLocation());
                }
            }
        }
        
        // Then remove chest without dropping it
        chestBlock.setType(Material.AIR, false);
        deathChests.remove(chestBlock.getLocation());
        
        // Notify player if provided
        if (player != null) {
            player.sendMessage(config.getMessageChestRemoved());
        }
    }
    
    /**
     * Gets the other half of a double chest
     * 
     * @param chestBlock The chest block
     * @param chestData The chest block data
     * @return The other half of the chest, or null if not found
     */
    private Block getOtherHalfOfChest(Block chestBlock, org.bukkit.block.data.type.Chest chestData) {
        if (chestData.getType() == Type.SINGLE) {
            return null;
        }
        
        if (chestData.getType() == Type.LEFT) {
            if (chestData.getFacing() == org.bukkit.block.BlockFace.NORTH) {
                return chestBlock.getRelative(-1, 0, 0);
            } else if (chestData.getFacing() == org.bukkit.block.BlockFace.SOUTH) {
                return chestBlock.getRelative(1, 0, 0);
            } else if (chestData.getFacing() == org.bukkit.block.BlockFace.EAST) {
                return chestBlock.getRelative(0, 0, 1);
            } else {
                return chestBlock.getRelative(0, 0, -1);
            }
        } else { // RIGHT
            if (chestData.getFacing() == org.bukkit.block.BlockFace.NORTH) {
                return chestBlock.getRelative(1, 0, 0);
            } else if (chestData.getFacing() == org.bukkit.block.BlockFace.SOUTH) {
                return chestBlock.getRelative(-1, 0, 0);
            } else if (chestData.getFacing() == org.bukkit.block.BlockFace.EAST) {
                return chestBlock.getRelative(0, 0, -1);
            } else {
                return chestBlock.getRelative(0, 0, 1);
            }
        }
    }
    
    /**
     * Starts the expiration task for death chests
     */
    private void startExpirationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                
                // Create a copy of the keys to avoid concurrent modification
                List<Location> locationsToCheck = new ArrayList<>(deathChests.keySet());
                
                for (Location loc : locationsToCheck) {
                    ChestData data = deathChests.get(loc);
                    if (data != null && data.getExpiryTime() > 0 && currentTime > data.getExpiryTime()) {
                        // Chest has expired, remove it
                        Block block = loc.getBlock();
                        if (block.getType() == Material.CHEST) {
                            // Remove chest without notifying player
                            removeChest(block, null);
                            
                            // Notify player if they're online
                            Player owner = plugin.getServer().getPlayer(data.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage("§c[FriendlyDeathChest] Your death chest at §f" + 
                                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + 
                                    " §chas expired!");
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // Check every minute
    }
    
    /**
     * Checks if a chest is a death chest
     * 
     * @param location The location to check
     * @return true if the location contains a death chest
     */
    public boolean isDeathChest(Location location) {
        return deathChests.containsKey(location);
    }
    
    /**
     * Gets the chest data for a location
     * 
     * @param location The location to check
     * @return The chest data, or null if not a death chest
     */
    public ChestData getChestData(Location location) {
        return deathChests.get(location);
    }
    
    /**
     * Restores experience to a player from a chest
     * 
     * @param chest The chest containing the experience
     * @param player The player to give experience to
     * @return true if experience was restored
     */
    public boolean restoreExperience(Chest chest, Player player) {
        if (!config.shouldStoreExperience()) {
            return false;
        }
        
        // Try to get stored experience
        if (chest.getBlock().getState() instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            if (container.has(experienceKey, PersistentDataType.INTEGER)) {
                int storedExp = container.get(experienceKey, PersistentDataType.INTEGER);
                
                // Only restore if there is experience to restore
                if (storedExp > 0) {
                    player.giveExp(storedExp);
                    player.sendMessage("§a[FriendlyDeathChest] You recovered " + storedExp + " experience points!");
                    
                    // Remove the stored experience to prevent getting it multiple times
                    container.remove(experienceKey);
                    tileState.update();
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Calculates total experience points from a player's level and progress
     * 
     * @param player The player
     * @return The total experience points
     */
    public int calculateTotalExperience(Player player) {
        int level = player.getLevel();
        float exp = player.getExp();
        int currentExp;
        
        if (level <= 16) {
            currentExp = (int) (level * level + 6 * level);
        } else if (level <= 31) {
            currentExp = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            currentExp = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        
        // Add partial level progress
        currentExp += Math.round(exp * player.getExpToLevel());
        
        return currentExp;
    }
    
    /**
     * Checks if a player can access a death chest
     * 
     * @param player The player trying to access
     * @param data The chest data
     * @return true if the player can access the chest
     */
    public boolean canAccessChest(Player player, ChestData data) {
        return player.getUniqueId().equals(data.getOwnerUUID()) || 
               player.hasPermission("friendlydeathchest.admin");
    }
    
    /**
     * Starts the task that checks for chest expiry and sends warnings
     */
    private void startExpiryWarningTask() {
        // Only start if expiry warnings are enabled
        if (config.getExpiryWarningTime() <= 0) {
            return;
        }

        // Cancel existing task if running
        if (expiryWarningTask != null) {
            expiryWarningTask.cancel();
        }

        // Create new task
        expiryWarningTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long warningTime = config.getExpiryWarningTime() * 60000; // Convert to milliseconds

                for (Map.Entry<Location, ChestData> entry : deathChests.entrySet()) {
                    ChestData chestData = entry.getValue();
                    
                    // Skip if chest has no expiry time
                    if (chestData.getExpiryTime() <= 0) {
                        continue;
                    }
                    
                    // Check if chest is within the warning period
                    long timeUntilExpiry = chestData.getExpiryTime() - currentTime;
                    if (timeUntilExpiry > 0 && timeUntilExpiry <= warningTime && !chestData.isWarningShown()) {
                        // Mark warning as shown
                        chestData.setWarningShown(true);
                        
                        // Get the owner player
                        Player owner = plugin.getServer().getPlayer(chestData.getOwnerUUID());
                        if (owner != null && owner.isOnline()) {
                            // Send warning message
                            String message = config.getExpiryWarningMessage()
                                    .replace("{time}", String.valueOf(timeUntilExpiry / 60000)) // minutes
                                    .replace("{location}", formatLocation(entry.getKey()));
                            owner.sendMessage(message);
                        }
                    }
                }
            }
        };
        
        // Run every minute
        expiryWarningTask.runTaskTimer(plugin, 20 * 60, 20 * 60);
    }

    /**
     * Cancels the expiration task
     */
    public void cancelExpirationTask() {
        if (expiryWarningTask != null) {
            expiryWarningTask.cancel();
            expiryWarningTask = null;
        }
    }

    /**
     * Cancels the expiry warning task
     */
    public void cancelExpiryWarningTask() {
        if (expiryWarningTask != null) {
            expiryWarningTask.cancel();
            expiryWarningTask = null;
        }
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

    /**
     * Checks if a player can build at the given location
     * 
     * @param player The player to check
     * @param location The location to check
     * @return true if the player can build at the location
     */
    public boolean canBuild(Player player, Location location) {
        if (!respectProtectionPlugins) {
            return true;
        }
        
        // Check WorldGuard if available
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                // Use reflection to avoid hard dependency
                Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                Object worldGuardPlugin = worldGuardPluginClass.getMethod("inst").invoke(null);
                boolean canBuild = (boolean) worldGuardPluginClass.getMethod("canBuild", Player.class, Location.class)
                        .invoke(worldGuardPlugin, player, location);
                
                if (!canBuild) {
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check WorldGuard protection: " + e.getMessage());
            }
        }
        
        // Check GriefPrevention if available
        Plugin griefPrevention = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention != null && griefPrevention.isEnabled()) {
            try {
                // Use reflection to avoid hard dependency
                Class<?> dataStoreClass = Class.forName("me.ryanhamshire.GriefPrevention.DataStore");
                Object dataStore = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention")
                        .getMethod("getDataStore").invoke(null);
                
                String reason = (String) dataStoreClass.getMethod("allowBuild", Player.class, Location.class, Material.class)
                        .invoke(dataStore, player, location, Material.CHEST);
                
                if (reason != null) {
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check GriefPrevention protection: " + e.getMessage());
            }
        }
        
        return true;
    }
    
    /**
     * Loads death chests from persistent storage
     */
    private void loadChests() {
        if (!persistentStorage) {
            return;
        }
        
        File storageFile = new File(plugin.getDataFolder(), "chests.yml");
        if (!storageFile.exists()) {
            return;
        }
        
        YamlConfiguration storage = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection chestsSection = storage.getConfigurationSection("chests");
        
        if (chestsSection == null) {
            return;
        }
        
        for (String key : chestsSection.getKeys(false)) {
            try {
                ConfigurationSection chestSection = chestsSection.getConfigurationSection(key);
                if (chestSection == null) {
                    continue;
                }
                
                // Parse location
                String worldName = chestSection.getString("world");
                int x = chestSection.getInt("x");
                int y = chestSection.getInt("y");
                int z = chestSection.getInt("z");
                
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    continue;
                }
                
                Location location = new Location(world, x, y, z);
                
                // Parse owner UUID
                String uuidString = chestSection.getString("owner");
                UUID ownerUUID = UUID.fromString(uuidString);
                
                // Parse other data
                int experience = chestSection.getInt("experience", 0);
                long expiryTime = chestSection.getLong("expiry-time", 0);
                
                // Skip expired chests
                if (expiryTime > 0 && expiryTime < System.currentTimeMillis()) {
                    continue;
                }
                
                // Check if the chest block exists
                Block block = location.getBlock();
                if (block.getType() != Material.CHEST) {
                    continue;
                }
                
                // Register the chest
                ChestData chestData = new ChestData(ownerUUID, experience, expiryTime);
                deathChests.put(location, chestData);
                
                if (config.isDebugEnabled()) {
                    plugin.getLogger().info("Loaded death chest at " + formatLocation(location) + " for " + ownerUUID);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load death chest: " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + deathChests.size() + " death chests from storage.");
    }
    
    /**
     * Saves death chests to persistent storage
     */
    public void saveChests() {
        if (!persistentStorage) {
            return;
        }
        
        File storageFile = new File(plugin.getDataFolder(), "chests.yml");
        YamlConfiguration storage = new YamlConfiguration();
        
        ConfigurationSection chestsSection = storage.createSection("chests");
        int count = 0;
        
        for (Map.Entry<Location, ChestData> entry : deathChests.entrySet()) {
            Location location = entry.getKey();
            ChestData chestData = entry.getValue();
            
            // Skip chests that are expired
            if (chestData.getExpiryTime() > 0 && chestData.getExpiryTime() < System.currentTimeMillis()) {
                continue;
            }
            
            ConfigurationSection chestSection = chestsSection.createSection(String.valueOf(count++));
            
            // Save location
            chestSection.set("world", location.getWorld().getName());
            chestSection.set("x", location.getBlockX());
            chestSection.set("y", location.getBlockY());
            chestSection.set("z", location.getBlockZ());
            
            // Save chest data
            chestSection.set("owner", chestData.getOwnerUUID().toString());
            chestSection.set("experience", chestData.getExperience());
            chestSection.set("expiry-time", chestData.getExpiryTime());
        }
        
        try {
            storage.save(storageFile);
            if (config.isDebugEnabled()) {
                plugin.getLogger().info("Saved " + count + " death chests to storage.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save death chests: " + e.getMessage());
        }
    }

    public void registerChest(Location location, ChestData chestData) {
        deathChests.put(location, chestData);
    }

    /**
     * Connects two chest blocks to form a double chest
     * 
     * @param firstChest The first chest block
     * @param secondChest The second chest block
     */
    private void connectChests(Block firstChest, Block secondChest) {
        org.bukkit.block.data.type.Chest firstChestData = 
            (org.bukkit.block.data.type.Chest) firstChest.getBlockData();
        org.bukkit.block.data.type.Chest secondChestData = 
            (org.bukkit.block.data.type.Chest) secondChest.getBlockData();
        
        // Determine the direction based on relative positions
        BlockFace direction = null;
        if (firstChest.getX() != secondChest.getX()) {
            direction = firstChest.getX() < secondChest.getX() ? BlockFace.EAST : BlockFace.WEST;
        } else if (firstChest.getZ() != secondChest.getZ()) {
            direction = firstChest.getZ() < secondChest.getZ() ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        
        if (direction != null) {
            firstChestData.setFacing(direction);
            secondChestData.setFacing(direction);
            
            // Set chest types based on direction
            if (direction == BlockFace.NORTH || direction == BlockFace.EAST) {
                firstChestData.setType(Type.LEFT);
                secondChestData.setType(Type.RIGHT);
            } else {
                firstChestData.setType(Type.RIGHT);
                secondChestData.setType(Type.LEFT);
            }
            
            firstChest.setBlockData(firstChestData);
            secondChest.setBlockData(secondChestData);
        }
    }
} 