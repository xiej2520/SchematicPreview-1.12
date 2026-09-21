package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import litematica.schematic.container.ArrayBlockContainer;

/** Exposes Ornithe Litematica's palette-resize guard to the material replacer. */
@Mixin(ArrayBlockContainer.class)
public interface ArrayBlockContainerAccessor
{
    @Accessor(value = "checkForFreedIds", remap = false)
    void schematicpreview$setCheckForFreedIds(boolean value);
}
