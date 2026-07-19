# BetterAdmin

A GUI-based Paper admin plugin for Minecraft `26.2` servers.

## Features
- Player management GUI with pagination for large servers
- Kick, ban, temp-ban, mute, unmute — bans are profile/UUID-based, not name-based
- Confirmation step before ban/tempban actions to prevent misclicks
- Inventory viewer and ender chest viewer
- Teleport, heal, feed, freeze/unfreeze toggle
- Staff vanish mode
- Player lookup by name or UUID
- Recent admin history with one-click undo for bans, mutes, and freezes
- History, mutes, and freezes persist across server restarts (`data.yml`)
- `/adminpanel reload` to reload `config.yml` without a restart
- Configurable permission and locale messages via `config.yml`

## Installation
1. Build with Gradle: `./gradlew build`
2. Copy `build/libs/betteradmin.jar` into your server `plugins/` folder.
3. Start the server.

## Usage
- `/adminpanel` to open the admin GUI

## Permissions
Defined in `plugin.yml` under the `permissions` section.

## Publishing
This repository includes a GitHub Actions workflow to publish GitHub Releases and upload plugin builds to Modrinth.

## License
MIT
