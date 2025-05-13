# FriendlyDeathChest

A Minecraft Spigot plugin that creates a chest containing your items when you die, instead of scattering them on the ground.

[![SpigotMC](https://img.shields.io/badge/SpigotMC-FriendlyDeathChest-orange)](https://www.spigotmc.org/resources/friendlydeathchest.122819/)
[![Donate](https://img.shields.io/badge/Donate-PayPal-blue.svg)](https://www.paypal.com/paypalme/mckenzio)

## Features

* 📦 Automatically creates a chest at death location containing all your items
* 📍 Sends coordinates of the chest to the player in chat
* 🔒 Protection system to prevent other players from accessing your death chest
* 🪧 Customizable signs and holograms with time remaining display
* ⚙️ Highly configurable with experience storage, automatic cleanup, and more

## Installation

1. Download the latest release from [Spigot](https://www.spigotmc.org/resources/friendlydeathchest.122819/) or [GitHub Releases](https://github.com/McKenzieJDan/FriendlyDeathChest/releases)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in the `config.yml` file

## Usage

When a player dies, a chest will automatically be created at their death location containing all their items. The player will receive a message with the coordinates of the chest. The chest will be protected so only the owner can access it.

### Commands

* `/fdc reload` - Reload the configuration
* `/fdc version` - Display the plugin version

### Permissions

* `friendlydeathchest.chest` - Allows players to have a death chest created when they die (default: true)
* `friendlydeathchest.admin` - Access to admin commands and bypass chest protection (default: op)

## Configuration

The plugin's configuration file (`config.yml`) is organized into logical sections:

```yaml
# General Settings
enabled: true
create-chest: true
chest-lifetime: 15  # Minutes, -1 for no limit

# Death Chest Settings
protect-chest: true
named-chest: true
store-experience: true

# Hologram Settings
enable-hologram: true
hologram-text: "&4{player}'s Death Chest &7({time})"
show-time-remaining: true

# Sign Settings
enable-sign: true
sign-material: OAK
```

For detailed configuration options, see the comments in the generated config.yml file.

## Requirements

- Spigot/Paper 1.21.4
- Java 21+

## Used By

[SuegoFaults](https://suegofaults.com) - A long-running adult Minecraft server where FriendlyDeathChest protects player loot and keeps deaths drama-free.

## Support
If you find this plugin helpful, consider [buying me a coffee](https://www.paypal.com/paypalme/mckenzio) ☕

## License

[MIT License](LICENSE)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)