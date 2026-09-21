package dev.froyln.schematicpreview.materials;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import com.google.common.collect.ImmutableList;

import litematica.materials.MaterialListPlacement;
import litematica.materials.MaterialListSchematic;
import litematica.schematic.Schematic;

import dev.froyln.schematicpreview.mixin.MaterialListPlacementAccessor;
import dev.froyln.schematicpreview.mixin.MaterialListSchematicAccessor;

/**
 * Plain call sites for {@code MaterialListSchematicAccessor}/{@code MaterialListPlacementAccessor}.
 * See {@code gui.BrowserWidgetAccessors} for why this can't live in the mixin package or inside
 * a mixin's own injected method.
 */
public final class MaterialListAccessors
{
    private static final Map<Schematic, Path> SCHEMATIC_FILES = new IdentityHashMap<>();

    private MaterialListAccessors()
    {
    }

    public static Schematic getSchematic(MaterialListSchematic materialList)
    {
        return ((MaterialListSchematicAccessor) materialList).schematicpreview$getSchematic();
    }

    public static ImmutableList<String> getRegions(MaterialListSchematic materialList)
    {
        return ((MaterialListSchematicAccessor) materialList).schematicpreview$getRegions();
    }

    public static Schematic getSchematic(MaterialListPlacement materialList)
    {
        return ((MaterialListPlacementAccessor) materialList).schematicpreview$getPlacement().getSchematic();
    }

    public static void rememberSchematicFile(Schematic schematic, Path file)
    {
        if (schematic != null && file != null)
        {
            SCHEMATIC_FILES.put(schematic, file);
        }
    }

    public static Path getSchematicFile(Schematic schematic)
    {
        return SCHEMATIC_FILES.get(schematic);
    }
}
