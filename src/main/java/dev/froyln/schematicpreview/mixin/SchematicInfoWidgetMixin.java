package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import litematica.gui.util.AbstractSchematicInfoCache.SchematicInfo;
import litematica.gui.widget.AbstractSchematicInfoWidget;
import malilib.gui.widget.ContainerWidget;
import malilib.gui.widget.IconWidget;
import malilib.gui.widget.LabelWidget;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.gui.PreviewWidget;
import dev.froyln.schematicpreview.render.PreviewCache;

/** Adds the live 3D preview to Litematica's path-based schematic info panel. */
@Mixin(AbstractSchematicInfoWidget.class)
public abstract class SchematicInfoWidgetMixin extends ContainerWidget
{
    @Shadow(remap = false) @Nullable protected SchematicInfo currentInfo;
    @Shadow(remap = false) @Final protected LabelWidget infoTextLabel;
    @Shadow(remap = false) @Final protected LabelWidget descriptionLabel;
    @Shadow(remap = false) @Final protected IconWidget iconWidget;
    @Shadow(remap = false) protected boolean hasDescription;

    @Unique @Nullable private Path schematicpreview$activePath;
    @Unique @Nullable private PreviewWidget schematicpreview$previewWidget;

    private SchematicInfoWidgetMixin(int width, int height)
    {
        super(width, height);
    }

    @Inject(method = "setActiveEntry", at = @At("HEAD"), remap = false)
    private void schematicpreview$rememberPath(Object entry, CallbackInfo ci)
    {
        this.schematicpreview$activePath = entry instanceof Path ? (Path) entry : null;
    }

    @Inject(method = "reAddSubWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addPreview(CallbackInfo ci)
    {
        this.schematicpreview$closePreview();

        if (Configs.Generic.ENABLED.getBooleanValue() == false ||
            this.currentInfo == null || this.schematicpreview$activePath == null)
        {
            return;
        }

        int y = Math.max(this.infoTextLabel.getBottom(), this.hasDescription ? this.descriptionLabel.getBottom() : 0) + 4;
        int width = this.getWidth() - 8;
        int height = this.getBottom() - y - 4;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        // The live preview replaces the vanilla thumbnail in this panel. Leaving both active
        // makes the thumbnail overlap the preview because the parent positions it at the bottom.
        this.removeWidget(this.iconWidget);
        this.schematicpreview$previewWidget = new PreviewWidget(this.getX() + 4, y, width, height,
                                                                this.schematicpreview$activePath);
        this.addWidget(this.schematicpreview$previewWidget);
    }

    @Inject(method = "updateSubWidgetPositions", at = @At("TAIL"), remap = false)
    private void schematicpreview$updatePreviewGeometry(CallbackInfo ci)
    {
        if (this.schematicpreview$previewWidget == null || this.currentInfo == null)
        {
            return;
        }

        int y = Math.max(this.infoTextLabel.getBottom(), this.hasDescription ? this.descriptionLabel.getBottom() : 0) + 4;
        int width = this.getWidth() - 8;
        int height = this.getBottom() - y - 4;

        if (width > 0 && height > 0)
        {
            this.schematicpreview$previewWidget.updatePreviewGeometry(this.getX() + 4, y, width, height);
        }
    }

    @Inject(method = "clearCache", at = @At("TAIL"), remap = false)
    private void schematicpreview$clearCache(CallbackInfo ci)
    {
        this.schematicpreview$closePreview();
        PreviewCache.close();
    }

    @Unique
    private void schematicpreview$closePreview()
    {
        if (this.schematicpreview$previewWidget != null)
        {
            this.schematicpreview$previewWidget.close();
            this.schematicpreview$previewWidget = null;
        }
    }
}
