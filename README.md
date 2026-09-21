# SchematicPreview for Ornithe 1.12.2

A [Litematica](https://github.com/maruohon/litematica) addon for Minecraft 1.12.2 /
[Ornithe](https://ornithemc.net/) that brings the features of the Fabric mod
[DimasKama/SchematicPreview](https://github.com/DimasKama/SchematicPreview) to 1.12.2,
re-implemented from scratch:

- **Schematic previews** — live 3D preview of the selected schematic in the browser side
  panel (drag to rotate, scroll to zoom, fullscreen and free-camera modes) and small previews
  per entry in list or 3/4/5-column tile layouts.
- **Directory icons** — right-click a folder to give it any item as icon.
- **Replace button** in the material list — swap every block of one type in the schematic.

- **Save / Save as** in the material list — write a schematic edited with Replace back to
  disk (overwrite or new file) without loading or placing it first.
- **Open schematics folder** button in Litematica's main menu — opens the schematics
  directory in your file manager.

## Screenshots

<!-- Drop PNGs into docs/images/ with these names; the table renders once they exist. -->

| Browser side panel | Fullscreen preview |
|---|---|
| ![side panel](docs/images/side-panel.png) | ![fullscreen](docs/images/fullscreen.png) |

| Material list with Replace / Save / Save as |
|---|
| ![replace](docs/images/replace.png) |

<!-- Wanted: docs/images/tiles.png — the browser in a tile layout (preview-type button). -->

## Usage

- **Config screen:** `Right Shift + F8` (rebindable) or MaLiLib's config screen. Tabs Generic /
  Menu / Preview / Hotkeys.
- **Preview:** select a schematic in any Litematica browser. Drag = rotate, scroll = zoom,
  the two buttons in the preview's corner open **fullscreen** and toggle **freecam** (in
  freecam, drag pans instead of orbiting). Fullscreen has **Save PNG** and **Copy** buttons
  (Wayland desktops need `wl-copy` on PATH for Copy).
- **Preview type:** the grid button next to the browser's path bar cycles List / List preview /
  Tile 5 / 4 / 3 columns (right-click cycles backwards). Tile and list previews are skipped for
  schematics above `previewMaxVolume` blocks (default 125 000).
- **Directory icons:** right-click a folder's icon in the browser, type an item id
  (`minecraft:diamond_block`), pick a position. Stored in `config/schematicpreview_icons.json`.
- **Replace:** open a material list (browser → *Material list*, or Loaded Schematics), click
  **Replace** on a row, pick a block. Keeps orientation properties the two blocks share.
- **Save / Save as:** in a schematic-backed material list, next to *Export*. *Save* overwrites
  the file after a confirmation; *Save as* asks for a name and never overwrites.
- **Open schematics folder:** in Litematica's main menu, under *Configuration menu*. Opens
  the folder Litematica loads schematics from (`schematics/` in the game directory).

Design notes: `AGENTS.md`, `docs/port-design.md`.

## Requirements (runtime)

| Mod | Version |
|-----|---------|
| Minecraft | 1.12.2 |
| Fabric Loader | 0.15.3 or newer |
| Ornithe Standard Libraries | 0.16.3 |
| MaLiLib (Ornithe) | 0.60.2-xiej.6 |
| Litematica (Ornithe) | 0.40.1-xiej.1 |

The supported MaLiLib and Litematica jars are built from the sibling Ornithe workspaces. The
Gradle file uses those local artifacts by default.

## Building

This is a Fabric Loom/Ploceus build. Gradle 8.5+ and a modern JDK are used for the build; the
compiled classes target Java 8 for Minecraft 1.12.2. The supplied flake provides Gradle and the
graphics libraries used by the client.

```bash
nix develop
./gradlew build                    # → build/libs/schematicpreview-ornithe-1.12.2-*.jar
./gradlew runClient                # development client, run dir ./run
```

The build expects these local sibling artifacts:

- `../malilib/build/libs/malilib-ornithe-1.12.2-0.60.2-xiej.6.jar`
- `../litematica/build/devlibs/litematica-ornithe-1.12.2-0.40.1-xiej.1-dev.jar`

Adjust `malilib_jar` and `litematica_jar` in `gradle.properties` if your local build names differ.

## License

[LGPL-3.0](LICENSE), same as Litematica and MaLiLib. This is a clean-room reimplementation:
the original Fabric mod is "All rights reserved" and no code or assets from it are used here.
