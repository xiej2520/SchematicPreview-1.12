package dev.froyln.schematicpreview.mixin;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.Util;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.config.Configs;

/** Adds the same schematics-folder shortcut as the newer Litematica menu. */
@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin extends GuiBase
{
    @Shadow(remap = false)
    protected abstract int getButtonWidth();

    @Unique
    private ButtonGeneric schematicpreview$openFolderButton;

    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void schematicpreview$addOpenFolderButton(CallbackInfo ci)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        int width = this.getButtonWidth();
        int x = 12 + width + 20;

        if (this.schematicpreview$openFolderButton == null)
        {
            this.schematicpreview$openFolderButton = new ButtonGeneric(x, 52, width, 20,
                    StringUtils.translate("schematicpreview.button.open_schematics_folder"));
        }
        else
        {
            this.schematicpreview$openFolderButton.setPosition(x, 52);
            this.schematicpreview$openFolderButton.setWidth(width);
        }

        this.addButton(this.schematicpreview$openFolderButton, (button, mouseButton) ->
        {
            File directory = DataManager.getSchematicsBaseDirectory();
            Util.getOperatingSystem().open(directory);
        });
    }
}
