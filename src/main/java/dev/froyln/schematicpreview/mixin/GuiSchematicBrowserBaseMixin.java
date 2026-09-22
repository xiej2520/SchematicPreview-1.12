package dev.froyln.schematicpreview.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.gui.PreviewSchematicBrowserWidget;

@Mixin(GuiSchematicBrowserBase.class)
public abstract class GuiSchematicBrowserBaseMixin
{
    @Shadow(remap = false)
    @Nullable
    protected abstract ISelectionListener<DirectoryEntry> getSelectionListener();

    @Inject(method = "createListWidget", at = @At("RETURN"), cancellable = true, remap = false)
    private void schematicpreview$replaceBrowser(int x, int y, CallbackInfoReturnable<WidgetSchematicBrowser> cir)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        GuiSchematicBrowserBase parent = (GuiSchematicBrowserBase) (Object) this;
        cir.setReturnValue(new PreviewSchematicBrowserWidget(x, y, 100, 100, parent, this.getSelectionListener()));
    }
}
