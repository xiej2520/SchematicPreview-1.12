# SchematicPreview Ornithe port

SchematicPreview is built for Minecraft 1.12.2 with Fabric Loader, Ornithe Standard Libraries,
MaLiLib, and Litematica. The original LiteLoader/ForgeGradle build and its downloaded `.litemod`
dependency are no longer used.

## Build

`build.gradle` uses Fabric Loom and Ploceus with stable MCP 1.12 mappings. MaLiLib and Litematica
are resolved from the sibling workspaces through the paths in `gradle.properties`; this keeps the
port aligned with the local Ornithe branches instead of silently using incompatible published
artifacts. The `flake.nix` supplies the Gradle/JDK and native libraries needed by the development
client.

```bash
nix develop
./gradlew build
```

The resulting jar is `build/libs/schematicpreview-ornithe-1.12.2-*.jar`.

## Runtime integration

- `SchematicPreview` is the OSL `client-init` entrypoint and registers `InitHandler` with
  MaLiLib's initialization dispatcher.
- Browser UI mixins target the current `BaseSchematicBrowserScreen` and
  `SchematicBrowserScreen` APIs. The path-based info widget stores its selected `Path` separately
  because current Litematica `SchematicInfo` contains metadata, not a file reference.
- Preview loading uses `LoadedSchematic.tryLoadSchematic`, preserving Litematica's active data
  conversion settings, and the cache indexes every region in the current `Schematic` API.
- Rendering adapts Litematica's `BlockContainer`/MaLiLib `BlockState` wrappers to the 1.12.2
  `IBlockAccess` used by `BlockRendererDispatcher`. Block-entity tags are converted back to
  vanilla NBT only for the temporary preview world.
- Material replacement writes through current `BlockContainer` and `SchematicRegion` maps and
  marks affected placements for rebuild.

The renderer remains a self-contained 1.12.2 VBO/FBO renderer. It deliberately does not copy the
newer Minecraft vertex-consumer pipeline, because those APIs do not exist in this target version.
