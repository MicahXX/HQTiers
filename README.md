A client-side Fabric mod that brings your [PvPHQ](https://pvphq.com/) stats in-game and displays them in the nametags, tab list and leaderboards. Fully customizable via **Mod Menu**.

## Features

### Stats
- PvPHQ stats shown on player nametags and in the tab list
- Global, Highest Tier, and per-ladder (all gamemodes) display modes
- Arrange each nametag element independently to the left or right of the player name
- Edit visibility, order, labels, icon spacing, and the name divider with a live preview using your Minecraft username (**Mod Menu → HQTiers → Nametag & Tab → Edit Nametag Layout**)
- Global mode shows your overall `#placement`; players with no ranked games have no global tag

### Leaderboards & Profiles
- Global leaderboard with overall placement, points, and number of ranked modes
- Per-ladder leaderboards with tier and TR; scroll to load more and use Refresh to reload
- Click a player to open their profile (Global opens the overview)
- Search for players by name
- View ladder stats, win rate, leaderboard positions and rating history
- You can also look up stats with /hqtiers stats <ign> and more with commands

## Keybinds

| Key | Action |
|-----|--------|
| `L` | Open the leaderboard |
| `K` | Open your player stats |
| `←` / `→` | Switch between ladders (gamemodes displayed in the nametag and tab list) |
| *Unbound* | Toggle nametag |
| *Unbound* | Toggle tablist |

All keybinds are rebindable in the settings under **Controls** and the leaderboard bind disables the advancements screen so if you want that rebind it.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu)

## Links & Credits

**Made by** [micahcode](https://github.com/MicahXX) · [PvPHQ Website](https://pvphq.com/) · [PvPHQ Discord](https://discord.gg/pvphq)

This is a fork of [FlowTiers](https://modrinth.com/mod/FlowTiers). Credit to [fecl](https://github.com/ykk4ga), the original creator.

## Building and checking this branch

Use Java 25 to run Gradle. The 1.21.11 branch emits Java 21 bytecode; 26.x branches emit Java 25 bytecode.

```bash
./gradlew build
```

The build runs regression tests for nametag layout, settings migration, unplayed profiles, global placement, and the official leaderboard response format. Install the regular JAR from `build/libs` with Fabric API, Cloth Config, and Mod Menu for the **same Minecraft version**. The `26.1` branch targets Minecraft **26.1.2**.
