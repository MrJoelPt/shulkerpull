# Shulker Pull - BuildPrint Addon

A NeoForge 26.3 add-on for [BuildPrint](https://www.curseforge.com/minecraft/mc-mods/buildprint). When BuildPrint's Easy Place or printer still needs a block near you and that block is inside a shulker box (or a bundle inside one) in your inventory, Shulker Pull moves it into your inventory for you.

- Takes at most one stack of each material, and never more than the build still needs.
- Refills the stack as you use it. The rest stays in the box.
- Items are only moved, never created: the server checks the totals after every transfer and undoes it if anything does not add up.
- Off by default every time you start the game. Toggle with the key under Options > Controls > Shulker Pull (default: backslash).
- A small badge next to the hotbar shows when it is on.

## Requirements

- Minecraft 26.3 with NeoForge
- BuildPrint 0.1.17 or newer

Install on both the client and the server. Without it on the server it does nothing.

It only uses BuildPrint's public API (`com.buildprint.api`). It is unofficial and not affiliated with BuildPrint or its author.

The ready-to-use jar is in `releases/`. See `BUILD_NOTES.txt` for rebuild steps.

## License

MIT, see `LICENSE`.
