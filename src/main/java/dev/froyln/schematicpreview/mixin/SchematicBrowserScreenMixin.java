package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import litematica.gui.SchematicBrowserScreen;
import litematica.schematic.Schematic;

import dev.froyln.schematicpreview.materials.MaterialListAccessors;

/** Keeps the source path for schematic material lists, whose current Litematica API deliberately
 * stores only the parsed {@link Schematic}. The argument hook also covers region-filtered lists,
 * because the same schematic object is passed through the region-selection callback. */
@Mixin(SchematicBrowserScreen.class)
public abstract class SchematicBrowserScreenMixin
{
    @ModifyArg(method = "createMaterialList",
               at = @At(value = "INVOKE",
                        target = "Llitematica/materials/MaterialListUtils;openMaterialListScreenFor(Llitematica/schematic/Schematic;)V"),
               index = 0, remap = false)
    private Schematic schematicpreview$rememberMaterialListFile(Schematic schematic)
    {
        Path file = ((BaseSchematicBrowserScreenAccessor) (Object) this).schematicpreview$getLastSelectedSchematicFile();

        if (file != null)
        {
            MaterialListAccessors.rememberSchematicFile(schematic, file);
        }

        return schematic;
    }
}
