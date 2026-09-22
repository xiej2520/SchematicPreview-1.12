package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.GuiBase;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.gui.PreviewMaterialListWidget;
import dev.froyln.schematicpreview.mixin.MaterialListSchematicAccessor;
import dev.froyln.schematicpreview.materials.SchematicSaver;

@Mixin(GuiMaterialList.class)
public abstract class GuiMaterialListMixin extends GuiBase
{
    @Shadow(remap = false) public abstract MaterialListBase getMaterialList();

    @Unique private ButtonGeneric schematicpreview_saveButton;
    @Unique private ButtonGeneric schematicpreview_saveAsButton;

    @Inject(method = "createListWidget", at = @At("RETURN"), cancellable = true, remap = false)
    private void schematicpreview$replaceMaterialListWidget(int x, int y,
            CallbackInfoReturnable<WidgetListMaterialList> cir)
    {
        if (Configs.Generic.ENABLED.getBooleanValue())
        {
            GuiMaterialList parent = (GuiMaterialList) (Object) this;
            cir.setReturnValue(new PreviewMaterialListWidget(x, y, 100, 100, parent));
        }
    }

    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void schematicpreview$addSaveButtons(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false ||
            (this.getMaterialList() instanceof MaterialListSchematic) == false)
        {
            return;
        }

        LitematicaSchematic schematic = ((MaterialListSchematicAccessor) this.getMaterialList())
                .schematicpreview$getSchematic();

        if (schematic == null || schematic.getFile() == null)
        {
            return;
        }

        this.schematicpreview_saveButton = new ButtonGeneric(12, this.height - 58, -1, false,
                "schematicpreview.gui.save_schematic");
        this.schematicpreview_saveAsButton = new ButtonGeneric(18, this.height - 58, -1, false,
                "schematicpreview.gui.save_schematic_as");
        this.addButton(this.schematicpreview_saveButton, (button, mouseButton) ->
                SchematicSaver.save(schematic));
        this.schematicpreview_saveAsButton.setPosition(this.schematicpreview_saveButton.getX() +
                this.schematicpreview_saveButton.getWidth() + 2, this.height - 58);
        this.addButton(this.schematicpreview_saveAsButton, (button, mouseButton) ->
                SchematicSaver.saveAs(schematic));
    }
}
