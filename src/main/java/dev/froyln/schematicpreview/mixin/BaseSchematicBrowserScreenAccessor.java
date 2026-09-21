package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import litematica.gui.BaseSchematicBrowserScreen;

@Mixin(BaseSchematicBrowserScreen.class)
public interface BaseSchematicBrowserScreenAccessor
{
    @Accessor(value = "lastSelectedSchematicFile", remap = false)
    @Nullable Path schematicpreview$getLastSelectedSchematicFile();
}
