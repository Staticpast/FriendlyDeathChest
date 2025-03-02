package io.mckenz.friendlydeathchest.utils;

import io.mckenz.friendlydeathchest.FriendlyDeathChest;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

/**
 * Utility class for checking for plugin updates
 */
public class UpdateChecker implements Listener {
    private final FriendlyDeathChest plugin;
    private final int resourceId;
    private final boolean notifyAdmins;
    private String latestVersion;
    private boolean updateAvailable = false;

    /**
     * Creates a new update checker
     * 
     * @param plugin The plugin instance
     * @param resourceId The SpigotMC resource ID
     * @param notifyAdmins Whether to notify admins when they join
     */
    public UpdateChecker(FriendlyDeathChest plugin, int resourceId, boolean notifyAdmins) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.notifyAdmins = notifyAdmins;
        
        if (notifyAdmins) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Checks for updates
     */
    public void checkForUpdates() {
        if (resourceId == 0) {
            plugin.getLogger().warning("Resource ID is not set. Update checking is disabled.");
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                latestVersion = fetchLatestVersion();
                
                if (latestVersion == null) {
                    plugin.getLogger().warning("Failed to check for updates.");
                    return;
                }
                
                // Normalize versions for comparison
                String normalizedCurrent = normalizeVersion(currentVersion);
                String normalizedLatest = normalizeVersion(latestVersion);
                
                if (!normalizedCurrent.equalsIgnoreCase(normalizedLatest)) {
                    updateAvailable = true;
                    plugin.getLogger().info("A new update is available: v" + latestVersion);
                    plugin.getLogger().info("You are currently running: v" + currentVersion);
                    plugin.getLogger().info("Download the latest version from: https://www.spigotmc.org/resources/" + resourceId);
                } else {
                    plugin.getLogger().info("You are running the latest version: v" + currentVersion);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    /**
     * Gets the latest version from SpigotMC
     * 
     * @return The latest version, or null if an error occurred
     * @throws IOException If an I/O error occurs
     */
    private String fetchLatestVersion() throws IOException {
        URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
        
        try (InputStream inputStream = url.openStream();
             Scanner scanner = new Scanner(inputStream)) {
            if (scanner.hasNext()) {
                return scanner.next();
            }
        }
        
        return null;
    }
    
    /**
     * Normalize a version string for comparison
     * @param version The version string to normalize
     * @return The normalized version string
     */
    private String normalizeVersion(String version) {
        // Remove 'v' prefix if present
        if (version.startsWith("v")) {
            version = version.substring(1);
        }
        
        // Remove any suffixes like -RELEASE, -SNAPSHOT, etc.
        int dashIndex = version.indexOf('-');
        if (dashIndex > 0) {
            version = version.substring(0, dashIndex);
        }
        
        return version.trim();
    }

    /**
     * Checks if an update is available
     * 
     * @return True if an update is available, false otherwise
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Gets the latest version
     * 
     * @return The latest version
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Notifies admins when they join if an update is available
     * 
     * @param event The player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateAvailable && notifyAdmins && event.getPlayer().hasPermission("friendlydeathchest.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                event.getPlayer().sendMessage("§8[§cFriendlyDeathChest§8] §7A new update is available: §cv" + latestVersion);
                event.getPlayer().sendMessage("§8[§cFriendlyDeathChest§8] §7You are currently running: §cv" + plugin.getDescription().getVersion());
                event.getPlayer().sendMessage("§8[§cFriendlyDeathChest§8] §7Download the latest version from: §chttps://www.spigotmc.org/resources/" + resourceId);
            }, 40L); // 2 seconds delay
        }
    }
} 