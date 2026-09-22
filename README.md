# SchematicPreview — Fabric 1.15.2

SchematicPreview adds live 3D previews to Litematica's 1.15.2 schematic browser. This branch
targets the Fabric 1.15.2 ports of MaLiLib and Litematica in the sibling repositories.

Implemented features:

- cached, incremental 3D previews in the browser side panel and list/tile entries;
- orbit, pan, zoom, double-click full-screen viewing, and a preview-type control;
- per-directory item icons, persisted in `config/schematicpreview_icons.json`;
- schematic-backed material-list replacement with shared block-state properties preserved;
- Save and Save As for edited schematic-backed material lists;
- a client-thread renderer using 1.15.2 `BufferBuilder`, `VertexBuffer`, `RenderLayer`, and
  `Framebuffer` APIs.

Block entities are rendered through a temporary Litematica schematic world, so stateful renderers
such as chests and shulker boxes can resolve neighboring block states and saved NBT. Their NBT is
removed only when a replacement changes the block at that position.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.15.2 |
| Fabric Loader | 0.16.14 |
| MaLiLib | 0.18.4+xiej.0 |
| Litematica | 0.9.1-1.15.2-dev+xiej.8 |
| Java | 8 for the target game/toolchain |

The default build resolves local development jars when these files exist:

```text
../malilib/build/devlibs/malilib-fabric-1.15.2-0.18.4+xiej.0-dev.jar
../litematica/build/devlibs/litematica-fabric-1.15.2-0.9.1-1.15.2-dev+xiej.8-dev.jar
```

## Build

With Nix:

```bash
nix develop
gradle build
gradle runClient
```

Without Nix, use a Java 8 toolchain and the pinned Gradle executable available in the project
environment:

```bash
GRADLE_USER_HOME=/tmp/schematicpreview-gradle gradle build
```

`gradle build` is the required non-GUI verification. `runClient` also needs a working OpenGL
display. The build includes the three Fabric API modules required by the 1.15 MaLiLib runtime and
forces the configured Fabric Loader version so the old 1.15 Fabric API POM cannot introduce a
second loader jar.

## Controls

- Select a schematic to load its side-panel preview.
- Drag with the left mouse button to orbit; drag with the right button to pan; scroll to zoom.
- Double-click the side-panel preview for the full-screen viewer.
- Click the preview-type control in the browser navigation area to cycle list, list-preview, and
  tile layouts; right-click cycles backward through the option in the config UI.
- Right-click a directory icon to edit its item id and icon position.
- Open a schematic-backed material list to use Replace, Save, and Save As.

## Repository layout

- `src/main/java/dev/froyln/schematicpreview/render/` — schematic view, incremental tessellation,
  shared renderer and framebuffer cache.
- `src/main/java/dev/froyln/schematicpreview/gui/` — browser, directory-icon, full-screen, and
  material-list UI.
- `src/main/java/dev/froyln/schematicpreview/mixin/` — Litematica browser/material-list hooks.
- `flake.nix` — reproducible Java/Gradle/native-library development shell.

This is a clean-room implementation. It uses the 1.15.2 MaLiLib/Litematica APIs and does not copy
code from the original Fabric mod.
