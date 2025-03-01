package io.mckenz.friendlydeathchest.commands;

import io.mckenz.friendlydeathchest.FriendlyDeathChest;
import io.mckenz.friendlydeathchest.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for the FriendlyDeathChest plugin
 */
public class FDCCommand implements CommandExecutor, TabCompleter {

    private final FriendlyDeathChest plugin;
    private final ConfigManager configManager;
    private final UpdateCommand updateCommand;

    /**
     * Create a new FDC command
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     */
    public FDCCommand(FriendlyDeathChest plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.updateCommand = new UpdateCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Show plugin info
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &fVersion: &e" + plugin.getDescription().getVersion()));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &fAuthor: &eMcKenzieJDan"));
            
            if (sender.hasPermission("friendlydeathchest.admin")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&7[&cFriendlyDeathChest&7] &fCommands: &e/fdc help"));
            }
            
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "help":
                showHelp(sender);
                break;
            case "reload":
                if (!sender.hasPermission("friendlydeathchest.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&7[&cFriendlyDeathChest&7] &cYou don't have permission to use this command."));
                    return true;
                }
                
                configManager.reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&7[&cFriendlyDeathChest&7] &aConfiguration reloaded."));
                break;
            case "update":
                return updateCommand.onCommand(sender, command, label, subArgs);
            default:
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&7[&cFriendlyDeathChest&7] &cUnknown command. Use &e/fdc help &cfor a list of commands."));
                break;
        }

        return true;
    }

    /**
     * Show help message
     * 
     * @param sender The command sender
     */
    private void showHelp(CommandSender sender) {
        if (!sender.hasPermission("friendlydeathchest.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&7[&cFriendlyDeathChest&7] &cYou don't have permission to use this command."));
            return;
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&7[&cFriendlyDeathChest&7] &eCommands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e/fdc &7- &fShow plugin information"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e/fdc help &7- &fShow this help message"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e/fdc reload &7- &fReload the configuration"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "&e/fdc update check &7- &fCheck for updates"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("friendlydeathchest.admin")) {
            return completions;
        }
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "reload", "update");
            String arg = args[0].toLowerCase();
            
            completions = subCommands.stream()
                .filter(s -> s.startsWith(arg))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
            return updateCommand.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }
        
        return completions;
    }
} 