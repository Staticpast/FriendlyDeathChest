# FriendlyDeathChest

A Minecraft Spigot plugin that creates a chest containing your items when you die, instead of scattering them on the ground.

## Features

- 📦 Automatically creates a chest at death location containing all your items
- 🔍 Searches nearby for valid chest placement if death location is blocked
- 📍 Sends coordinates of the chest to the player
- ✨ Visual and sound effects for chest creation and removal
- 🗑️ Chest automatically removes itself when empty
- ⚙️ Configurable search radius for chest placement

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/McKenzieJDan/FriendlyDeathChest/releases)
2. Place the .jar in your server's `plugins` folder
3. Restart your server

## Requirements

- Spigot/Paper 1.21.4
- Java 17+

## Development
To build the plugin yourself:

1. Clone the repository
2. Run `mvn clean package`
3. Find the built jar in the `target` folder

## License

[MIT License](LICENSE)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)