# BetterAdmin

A GUI-based Paper admin plugin for Minecraft `26.2` servers.

## Features
- Player management GUI
- Kick, ban, temp-ban, mute, unmute
- Inventory viewer and ender chest viewer
- Teleport, heal, feed, freeze
- Player lookup by name or UUID
- Recent admin history display
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
