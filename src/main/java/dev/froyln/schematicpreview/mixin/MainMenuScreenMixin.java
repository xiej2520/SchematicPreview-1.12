package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.OpenGlHelper;

import litematica.data.DataManager;
import litematica.gui.MainMenuScreen;
import litematica.util.LitematicaDirectories;
import malilib.gui.BaseScreen;
import malilib.gui.icon.DefaultIcons;
import malilib.gui.widget.button.GenericButton;

import dev.froyln.schematicpreview.config.Configs;

/**
 * Adds an "Open schematics folder" button under Litematica's "Configuration menu" button, via
 * vanilla {@code OpenGlHelper.openFile}. {@code extends BaseScreen} is the usual trick to
 * reach the inherited {@code addWidget}.
 */
@Mixin(MainMenuScreen.class)
public abstract class MainMenuScreenMixin extends BaseScreen
{
    @Shadow(remap = false) @Final protected GenericButton configScreenButton;

    @Unique private GenericButton schematicpreview_openFolderButton;

    @Inject(method = "reAddActiveWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addOpenFolderButton(CallbackInfo ci)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        if (this.schematicpreview_openFolderButton == null)
        {
            this.schematicpreview_openFolderButton = GenericButton.create("schematicpreview.button.open_schematics_folder",
                    DefaultIcons.FILE_BROWSER_DIR);
            this.schematicpreview_openFolderButton.setActionListener(
                    () -> OpenGlHelper.openFile(LitematicaDirectories.getSchematicsBaseDirectory().toFile()));
            this.schematicpreview_openFolderButton.setAutomaticWidth(false);
        }

        // The equal-width pass over Litematica's own buttons has already run at TAIL
        this.schematicpreview_openFolderButton.setWidth(this.configScreenButton.getWidth());
        this.addWidget(this.schematicpreview_openFolderButton);
    }

    @Inject(method = "updateWidgetPositions", at = @At("TAIL"), remap = false)
    private void schematicpreview$positionOpenFolderButton(CallbackInfo ci)
    {
        if (this.schematicpreview_openFolderButton != null)
        {
            this.schematicpreview_openFolderButton.setPosition(this.configScreenButton.getX(),
                    this.configScreenButton.getY() + 22);
        }
    }
}
