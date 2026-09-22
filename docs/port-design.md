# Fabric 1.15.2 port notes

This branch is a Fabric 1.15.2 port of SchematicPreview. It is built against the local
`pre-rewrite/fabric/1.15.2-xiej` MaLiLib and Litematica branches.

## API translation

The port keeps the feature boundaries of the original implementation while using the APIs that
actually exist in this version:

| Feature | 1.15.2 implementation |
|---|---|
| Mod entrypoint | Fabric `ModInitializer` plus MaLiLib `InitializationHandler` |
| Config and hotkey | MaLiLib `ConfigManager`, `IConfigHandler`, `IKeybindProvider` |
| Browser hook | Mixin on `GuiSchematicBrowserBase.createListWidget()` |
| Browser entries | `WidgetDirectoryEntry` subclass, with a custom tile drawing path |
| Schematic data | `LitematicaSchematic` and `LitematicaBlockStateContainer` |
| Block rendering | `WorldRendererSchematic.renderBlock()` into per-layer `BufferBuilder`s |
| GPU upload | 1.15.2 `VertexBuffer` objects, one per `RenderLayer` |
| Preview target | Shared `Framebuffer`, resized to the largest active preview |
| Material list | Mixin on `GuiMaterialList.createListWidget()` |
| Replacement | `MaterialCache.getItems()` matching, shared-property copy, placement rebuild |

## Rendering flow

Schematic loading runs on a daemon executor because Litematica's file parsing is data-only. The
result is handed back to the client thread through a `CompletableFuture`. The client thread then:

1. creates a read-only `BlockRenderView` spanning every schematic region;
2. visits at most 4096 block positions per client render call;
3. sends model blocks to the appropriate `RenderLayer` buffer;
4. uploads finished buffers to VBOs;
5. renders the VBOs into the preview framebuffer with an orbit camera; and
6. renders block entities through a temporary Litematica schematic world; and
7. restores the Minecraft framebuffer before drawing the GUI texture.

The side panel and full-screen viewer share the parsed schematic and VBOs. Small list/tile
previews share one framebuffer and render sequentially. Entry previews are limited by
`previewMaxVolume`; the side panel/full-screen path is limited by `previewMaxBlocks` and remains
incremental while it tessellates.

The fake view supplies full block light and a fixed white biome color. Block entities use a
temporary Litematica schematic world populated with the schematic's block states and saved NBT,
so stateful renderers can resolve their normal neighbor context without using the live world.

## Browser behavior

The browser mixin replaces the normal `WidgetSchematicBrowser` with
`PreviewSchematicBrowserWidget`, preserving the original list dimensions and selection listener.
The subclass adds preview-aware row heights, directory/file visuals, the preview-type control,
camera input, and the full-screen double-click action. Tile layouts are drawn as rows of cells;
the normal list path remains entirely delegated to MaLiLib.

Directory icon choices are stored by normalized absolute path. Unknown item ids are ignored while
loading and rejected by the editor, so malformed user config cannot break browser construction.

## Material replacement

The replacement action is installed by replacing Litematica's material-list widget with a small
subclass whose rows add a Replace button. The selected row's item is matched against
`MaterialCache.getItems(state)`, which handles multi-item states such as flower pots better than
comparing only the block registry entry. Shared properties are copied only where the replacement
block accepts the same property/value. Changed positions have stale block-entity NBT removed;
all placements referencing the schematic are marked for rebuild.

Save and Save As call Litematica's own `writeToFile` path and invalidate the preview cache after a
successful write.

## Build/runtime notes

The project uses Fabric Loom 1.10 and Java 8 source compatibility. MaLiLib 0.18.4 on this old
Fabric API line declares `fabric-resource-loader-v0`; the build includes the required Fabric API
modules and forces Loader 0.16.14 to prevent the API POM's historical Loader 0.10.5 dependency
from appearing on the development classpath twice.

`gradle build` is the automated check. `gradle runClient` verifies mod resolution and mixin
initialization, but still requires a working OpenGL/EGL display in the host environment.
