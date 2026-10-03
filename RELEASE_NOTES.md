# HQTiers 4.0

- Rebuilt the nametag editor with a live preview using the signed-in player's IGN, individual Left/Right controls, ordering, labels, and pagination for smaller GUI sizes.
- Added icon-spacing and name-divider toggles. New layouts default to Left; existing whole-tag positioning preferences migrate.
- Implemented the official global leaderboard with placement, points, counted modes, pagination, search, profile navigation, and refresh.
- Global nametags show placement in the Tier slot and stay hidden for accounts with no ranked games.
- Replaced the nonfunctional streak column with ranked win rate.
- Fixed dangling separators, duplicate decoration, highest-tier selection, TR values, rating-history labels, duplicate history requests, and stale history/search responses.
- Updated Cloth Config and Mod Menu for each Minecraft version and bounded the 26.2/26.3 compatibility ranges.

## Validation

| Branch | Minecraft used for build and client check | Cloth Config | Mod Menu | Regression tests |
| --- | --- | --- | --- | --- |
| 1.21.11 | 1.21.11 | 21.11.153 | 17.0.1 | 14 passed |
| 26.1 | 26.1.2 | 26.1.154 | 18.0.2 | 14 passed |
| 26.2 | 26.2 | 26.2.155 | 20.0.3 | 14 passed |
| 26.3 | 26.3 | 26.3.159 | 21.0.0 | 14 passed |

Each branch passed a clean Gradle build. Temporary development-client checks opened the config, nametag editor, live global leaderboard, and player overview. Screenshots were inspected; the username preview was checked in the 26.3 client after the final preview change. The test addon is not part of any release JAR. Bytecode targets remain Java 21 for 1.21.11 and Java 25 for 26.x.

Lunar Client and nametags on live multiplayer servers still need manual testing. Development runs used Fabric's offline test account; Realms authentication and the missing system narrator library emitted environment warnings.

Production Java line counts were measured after the layout refactoring: formatter 176, composer 77, name composition 45, layout editor 198 on 1.21.11. The leaderboard and profile screens remain larger existing classes.
