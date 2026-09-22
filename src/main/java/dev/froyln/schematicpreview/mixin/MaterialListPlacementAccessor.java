package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

@Mixin(MaterialListPlacement.class)
public interface MaterialListPlacementAccessor
{
    @Accessor(value = "placement", remap = false)
    SchematicPlacement schematicpreview$getPlacement();
}
