package dev.froyln.schematicpreview.gui;

import javax.annotation.Nullable;

import malilib.gui.widget.button.GenericButton;

public interface PreviewTypeButtonAccess
{
    void schematicpreview$setPreviewTypeButton(@Nullable GenericButton button);

    @Nullable
    GenericButton schematicpreview$getPreviewTypeButton();
}
