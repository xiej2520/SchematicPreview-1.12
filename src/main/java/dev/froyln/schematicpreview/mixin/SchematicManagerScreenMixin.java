package dev.froyln.schematicpreview.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import litematica.gui.SchematicManagerScreen;

/** Keeps a failed selected-schematic load from closing the manager screen. */
@Mixin(SchematicManagerScreen.class)
public abstract class SchematicManagerScreenMixin
{
    @Unique private static final Logger SCHEMATICPREVIEW_LOGGER = LogManager.getLogger("SchematicPreview");

    @WrapOperation(
            method = "onSelectionChange",
            at = @At(
                    value = "INVOKE",
                    target = "Llitematica/gui/SchematicManagerScreen;reAddActiveWidgets()V"),
            remap = false)
    private void schematicpreview$reAddActiveWidgetsSafely(
            SchematicManagerScreen screen, Operation<Void> original)
    {
        try
        {
            original.call(screen);
        }
        catch (Throwable t)
        {
            SCHEMATICPREVIEW_LOGGER.warn("Could not refresh the selected schematic; leaving it unloaded: " + t);
        }
    }
}
