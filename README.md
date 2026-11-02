A client-side Fabric mod that brings [PvPHQ](https://pvphq.com/) stats directly into Minecraft, displaying player rankings in nametags, the tab list, chat and leaderboards. Fully customizable through **Mod Menu**.

## Features

### Stats & Nametags
- PvPHQ stats displayed on player nametags and in the tab list
- Optional stats beside chat names, using your configured display mode and layout
- Global, Highest Tier and per-ladder display modes
- Choose which stats appear, their order and whether each element sits left or right of your name
- Customize labels, colors, icon spacing and separators
- Live preview of your nametag (**Mod Menu → HQTiers → Nametag & Tab → Edit Nametag Layout**)

### Leaderboards & Profiles
- Global leaderboard with placements, points, and ranked gamemode counts
- Per-ladder leaderboards with tiers and TR
- Scroll to load more players, switch ladders or refresh the leaderboard
- Search by name and click a player to open their profile
- View ladder stats, wins/losses, win rate, leaderboard positions and rating history
- You can also look up players with **/hqtiers stats <ign>**

## Keybinds

| Key | Action |
|-----|--------|
| `L` | Open the leaderboard |
| `K` | Open your player stats |
| `←` / `→` | Switch between ladders |
| `↑` | Toggle nametag stats |
| `↓` | Toggle tablist stats |
| `U` | Toggle chat stats |
| Unbound | Refresh tiers for nearby players and yourself (10-second cooldown) |

All keybinds can be customized under **Options → Controls → HQTiers**. By default, the leaderboard keybind conflicts with the advancements screen, so rebind either action if you want to use both.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu)

## Links & Credits

**Developed by [micahcode](https://github.com/MicahXX)** · [PvPHQ Website](https://pvphq.com/) · [PvPHQ Discord](https://discord.gg/pvphq)

Originally developed as [FlowTiers](https://modrinth.com/mod/FlowTiers) alongside [fecl](https://github.com/ykk4ga), who started the project.

## Building

Use JDK 25 to run Gradle. This branch builds for Java 25 and Minecraft 26.1.2. From the project directory, run:

```bash
bash gradlew build
```

The build writes the mod JAR to `build/libs/`. Use the JAR without the `-sources` suffix.
