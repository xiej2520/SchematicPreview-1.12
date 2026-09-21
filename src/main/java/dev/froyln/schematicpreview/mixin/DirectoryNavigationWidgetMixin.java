package dev.froyln.schematicpreview.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import malilib.gui.widget.InfoIconWidget;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.header.DirectoryNavigationWidget;
import malilib.gui.widget.list.search.SearchBarWidget;

import dev.froyln.schematicpreview.gui.PreviewTypeButtonAccess;

@Mixin(DirectoryNavigationWidget.class)
public abstract class DirectoryNavigationWidgetMixin implements PreviewTypeButtonAccess
{
    @Unique
    @Nullable
    private GenericButton schematicpreview$previewTypeButton;

    @Override
    public void schematicpreview$setPreviewTypeButton(@Nullable GenericButton button)
    {
        this.schematicpreview$previewTypeButton = button;
    }

    @Override
    @Nullable
    public GenericButton schematicpreview$getPreviewTypeButton()
    {
        return this.schematicpreview$previewTypeButton;
    }

    @Inject(method = "reAddSubWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addPreviewTypeButton(CallbackInfo ci)
    {
        if (this.schematicpreview$previewTypeButton != null &&
            ((SearchBarWidget) (Object) this).isSearchOpen() == false)
        {
            ((DirectoryNavigationWidget) (Object) this).addWidget(this.schematicpreview$previewTypeButton);
        }
    }

    @Inject(method = "updateSubWidgetPositions", at = @At("TAIL"), remap = false)
    private void schematicpreview$positionPreviewTypeButton(CallbackInfo ci)
    {
        if (this.schematicpreview$previewTypeButton == null ||
            ((SearchBarWidget) (Object) this).isSearchOpen())
        {
            return;
        }

        DirectoryNavigationWidget nav = (DirectoryNavigationWidget) (Object) this;
        int buttonY = nav.getY() + (nav.getHeight() - this.schematicpreview$previewTypeButton.getHeight()) / 2;
        GenericButton searchToggleButton = ((SearchBarWidgetAccessor) (Object) nav)
                .schematicpreview$getSearchToggleButton();
        int searchWidth = searchToggleButton != null ? searchToggleButton.getWidth() : 0;
        InfoIconWidget infoWidget = ((DirectoryNavigationWidgetAccessor) (Object) nav)
                .schematicpreview$getInfoWidget();
        int infoWidth = infoWidget != null ? infoWidget.getWidth() : 0;
        int buttonX = nav.getRight() - searchWidth - infoWidth - 4
                - this.schematicpreview$previewTypeButton.getWidth() - 2;

        this.schematicpreview$previewTypeButton.setPosition(buttonX, buttonY);
    }

    @Inject(method = "getMaxPathBarWidth", at = @At("RETURN"), cancellable = true, remap = false)
    private void schematicpreview$reservePreviewTypeButtonSpace(CallbackInfoReturnable<Integer> cir)
    {
        if (this.schematicpreview$previewTypeButton != null)
        {
            cir.setReturnValue(cir.getReturnValue()
                    - this.schematicpreview$previewTypeButton.getWidth() - 2);
        }
    }
}
