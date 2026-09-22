package dev.froyln.schematicpreview.mixin;

import com.google.common.collect.ImmutableList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

@Mixin(MaterialListSchematic.class)
public interface MaterialListSchematicAccessor
{
    @Accessor(value = "schematic", remap = false)
    LitematicaSchematic schematicpreview$getSchematic();

    @Accessor(value = "regions", remap = false)
    ImmutableList<String> schematicpreview$getRegions();
}
