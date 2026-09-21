package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import litematica.materials.MaterialListPlacement;
import litematica.schematic.placement.SchematicPlacement;

/**
 * Exposes the placement a {@code MaterialListPlacement} was built from, so the Replace button
 * can reach the underlying schematic to mutate.
 */
@Mixin(MaterialListPlacement.class)
public interface MaterialListPlacementAccessor
{
    @Accessor(value = "placement", remap = false)
    SchematicPlacement schematicpreview$getPlacement();
}
