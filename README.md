![Full Ghast Ahead](https://raw.githubusercontent.com/ProfetGit/full-ghast-ahead/main/docs/banner.gif)

**Your happy ghast, twice as fast.**

Full Ghast Ahead is a vanilla data pack for **Minecraft Java 26.2 and 26.3**. A happy ghast flies **2× faster** while you steer it: forward, sideways, up and down. Get off, and it floats along at its usual pace again.

## Why this pack?

**Vanilla happy ghasts are slow.** Steered, a happy ghast does about 3.6 blocks per second, slower than you sprint. With the pack it does about 7.2 by default, and up to 14.4 if you want.

**Only while you ride.** The boost switches on when a player steers the ghast and off the moment they get off. Happy ghasts that nobody rides behave exactly as in vanilla.

**Still feels like vanilla.** The pack raises the ghast's own flying speed, the value the game already uses for ridden flight. Accelerating, turning, climbing and slowing down keep their vanilla feel, just faster. Nothing is teleported or pushed around.

**One zip, server-side only.** No mods, no resource pack, no client install and no experimental features. It works in singleplayer and on servers.

**Tested, not hoped.** Every release is checked by 30 automated tests on real 26.2 and 26.3 servers. They cover getting on and off, ghasts without a harness, mobs in the saddle, hostile ghasts, the settings menu, `/reload` and uninstalling. The speed was also measured in the real game: 3.61 blocks per second in vanilla, 7.21 at 2× and 14.43 at 4×.

## How to use

1. Put a harness on a happy ghast.
2. Get on and fly. The player in the front seat steers, and gets the speed.

## Settings

Server operators pick the speed from a clickable menu:

`/function full_ghast_ahead:settings`

| Setting | Steered speed |
|---|---|
| 1× | about 3.6 blocks/s (vanilla) |
| 1.5× | about 5.4 blocks/s |
| **2×** (default) | about 7.2 blocks/s |
| 2.5× | about 9.0 blocks/s |
| 3× | about 10.8 blocks/s |
| 4× | about 14.4 blocks/s |

For comparison, sprinting is about 5.6 blocks/s and the fastest horses reach about 14. A new setting applies straight away, also to a ghast someone is already riding, and it survives `/reload` and restarts.

## Commands

| Command | What it does |
|---|---|
| `/function full_ghast_ahead:settings` | Opens the settings menu |
| `/function full_ghast_ahead:uninstall` | Returns every boosted happy ghast to vanilla speed and removes the pack's data |

## Installation

**Singleplayer**
- New world: under **More → Data Packs**, drag the `.zip` file into the window.
- Existing world: put the `.zip` file in the world's `datapacks/` folder, then run `/reload` or reopen the world.

**Server:** put the `.zip` file in `world/datapacks/`, then run `/reload` or restart the server.

Don't unzip the file.

## Compatibility

- One zip supports Minecraft Java **26.2 and 26.3**.
- Only happy ghasts are affected. Hostile ghasts are untouched.
- Everything lives in the `full_ghast_ahead` namespace. It adds to the vanilla `#minecraft:load` and `#minecraft:tick` function tags and doesn't replace any files.
- Other packs that change a happy ghast's flying speed keep working. This pack multiplies the ghast's base speed on top of them.

## Good to know

- The multiplier is on the whole flight, so climbing, sinking and sideways movement speed up too.
- While a ghast is steered, it carries an attribute modifier named `full_ghast_ahead:boost`. It is removed when the rider gets off. If you remove the pack while a player is logged out mid-ride, that one ghast keeps the boost.

## Uninstall

1. Run `/function full_ghast_ahead:uninstall`.
2. Remove `FullGhastAhead-1.0.0.zip` from the `datapacks` folder.
3. Run `/reload`, or reopen the world or restart the server.

## Support

Full Ghast Ahead is free. If it saves you some time, a coffee helps fund the next update.

[![Support me on Ko-fi](https://raw.githubusercontent.com/ProfetGit/assets/main/kofi-banner.gif)](https://ko-fi.com/profetgit)

## License

© 2026 Profet. All rights reserved.

- **You can** use Full Ghast Ahead on any server, including monetized ones, and include the unmodified zip in any modpack that credits Profet and links here. You can also feature it in videos and modify it for your own world or server.
- **Please don't** re-upload Full Ghast Ahead or a modified version of it elsewhere, sell it, or present it as your own.

The full terms are in the `LICENSE` file inside the zip. For anything else, just ask.
