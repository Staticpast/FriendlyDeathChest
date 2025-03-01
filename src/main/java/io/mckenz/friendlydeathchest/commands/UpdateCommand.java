package io.mckenz.friendlydeathchest.commands;

import io.mckenz.friendlydeathchest.FriendlyDeathChest;
import io.mckenz.friendlydeathchest.utils.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to check for updates
 */
public class UpdateCommand implements CommandExecutor, TabCompleter {

    private final FriendlyDeathChest plugin;

    /**
     * Create a new update command
     * 
     * @param plugin The plugin instance
     */
    public UpdateCommand(FriendlyDeathChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("friendlydeathchest.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &cYou don't have permission to use this command."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &eChecking for updates..."));
            
            // Check if update checker is enabled
            if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&7[&cFriendlyDeathChest&7] &cUpdate checker is disabled in the config."));
                return true;
            }
            
            // Check if resource ID is set
            int resourceId = plugin.getConfig().getInt("update-checker.resource-id", 0);
            if (resourceId <= 0) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&7[&cFriendlyDeathChest&7] &cResource ID is not set in the config."));
                return true;
            }
            
            // Create a new update checker if needed
            if (plugin.getUpdateChecker() == null) {
                UpdateChecker updateChecker = new UpdateChecker(plugin, resourceId, 
                    plugin.getConfig().getBoolean("update-checker.notify-admins", true));
                updateChecker.checkForUpdates();
                
                // Wait a bit for the update check to complete
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    sendUpdateStatus(sender, updateChecker, resourceId);
                }, 60L); // Wait 3 seconds
            } else {
                // Force a new check with the existing update checker
                plugin.getUpdateChecker().checkForUpdates();
                
                // Wait a bit for the update check to complete
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    sendUpdateStatus(sender, plugin.getUpdateChecker(), resourceId);
                }, 60L); // Wait 3 seconds
            }
            
            return true;
        }
        
        // Show help message
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&7[&cFriendlyDeathChest&7] &eUpdate Commands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e/fdc update check &7- &fCheck for updates"));
        
        return true;
    }
    
    /**
     * Send the update status to the command sender
     * 
     * @param sender The command sender
     * @param updateChecker The update checker
     * @param resourceId The resource ID
     */
    private void sendUpdateStatus(CommandSender sender, UpdateChecker updateChecker, int resourceId) {
        if (updateChecker.isUpdateAvailable()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &eA new update is available: &f" + updateChecker.getLatestVersion() + 
                " &e(Current: &f" + plugin.getDescription().getVersion() + "&e)"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &eDownload it at: &fhttps://www.spigotmc.org/resources/" + resourceId));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &aYou are running the latest version: &f" + 
                plugin.getDescription().getVersion()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("check");
        }
        
        return completions;
    }
} 