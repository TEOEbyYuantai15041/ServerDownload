# Teoe World Downloader (WDL)

A highly advanced, fully automated, and highly optimized Minecraft Fabric client mod designed for autonomously scanning, downloading, and backing up multiplayer worlds and chest contents.

> **Note:** This mod is built for Fabric 1.21.x and is completely client-side.

## Features

- **Automated Map Scanning (AutoScanner):** Uses a spiral algorithm to fly around and seamlessly download chunks within a specified radius without memory leaks or stuttering.
- **Chest & Container Extraction:** Automatically locates and opens chests to save their NBT contents (bypassing fake containers like bookshelves).
- **Intelligent 3D A* Pathfinding:** Built-in bot navigation that can:
  - Smartly route around walls, water, lava, and lanterns.
  - Automatically sneak under low ceilings and through fences/slabs.
  - Dynamically open doors, fence gates, and interact with levers/buttons to open iron doors.
  - Safely jump over obstacles and stairs.
- **Quantum NoFall Protection:** Deep-engine bypass that manipulates velocity and collision physics to prevent any fall damage while flying or dropping from extreme heights, bypassing server-side checks.
- **Embedded Web Dashboard:** Start, monitor, and configure the download process via a beautiful embedded localhost Web UI. Includes a real-time radar map of the scanned area!
- **Cross-Dimension Scanning:** Automatically locates portals and continues scanning the Nether once the Overworld is complete.
- **One-Click Export:** Download the entire saved world (including `level.dat` and `region` files) directly as a ready-to-play `.zip` from the Web UI.

## Usage

1. Drop the `.jar` into your `.minecraft/mods` folder.
2. Join the server you want to download.
3. Run `/downloadserver localhost <port>` in the game chat (e.g. `/downloadserver localhost 8080`).
4. Open your web browser and go to `http://localhost:8080`.
5. Enter your desired scan radius, configure dimension settings, and click **START SCAN**.
6. Sit back and watch the bot do its magic!
7. When finished, click **DOWNLOAD SAVED WORLD (ZIP)** on the web interface.

## Anti-Cheat Compatibility

This mod simulates genuine player movements (walking speed, realistic jumping, actual interaction rays) instead of teleporting, making it highly resilient against standard Vanilla and basic anti-cheat (NCP) movement checks.

## Special Thanks

Thanks to mircokroon build the original version of world downloader i used some code of his project.
the original link: https://github.com/mircokroon/minecraft-world-downloader
Thanks to Google for gemini (i used some ai in this project)

## License

This project is licensed under the **GPL-3.0 License**. See the `LICENSE` file for more details.
Feel free to use, modify, and distribute it, but please maintain the original copyright attribution.

