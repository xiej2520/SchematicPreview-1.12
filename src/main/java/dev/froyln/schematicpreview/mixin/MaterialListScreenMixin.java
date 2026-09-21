package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import litematica.gui.MaterialListScreen;
import litematica.materials.MaterialListBase;
import litematica.materials.MaterialListEntry;
import litematica.materials.MaterialListPlacement;
import litematica.materials.MaterialListSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.placement.SchematicPlacement;
import litematica.data.DataManager;
import malilib.gui.BaseScreen;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.DataListWidget;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.gui.ReplaceMaterialListEntryWidget;
import dev.froyln.schematicpreview.materials.MaterialListAccessors;
import dev.froyln.schematicpreview.materials.SchematicSaver;

/**
 * Adds the Replace button to schematic- or placement-backed material lists (not the area
 * analyzer), and Save/Save as buttons when the schematic has a backing file.
 * {@code extends BaseScreen} is the usual trick to reach the inherited {@code addWidget}.
 */
@Mixin(MaterialListScreen.class)
public abstract class MaterialListScreenMixin extends BaseScreen
{
    @Shadow(remap = false) protected MaterialListBase materialList;
    @Shadow(remap = false) protected GenericButton exportButton;

    @Unique private GenericButton schematicpreview_saveButton;
    @Unique private GenericButton schematicpreview_saveAsButton;

    @Inject(method = "createListWidget", at = @At("RETURN"), remap = false)
    private void schematicpreview$installReplaceButton(CallbackInfoReturnable<DataListWidget<MaterialListEntry>> cir)
    {
        MaterialListBase materialList = this.materialList;

        if (Configs.Generic.ENABLED.getBooleanValue() &&
            (materialList instanceof MaterialListSchematic || materialList instanceof MaterialListPlacement))
        {
            cir.getReturnValue().setDataListEntryWidgetFactory(
                    (data, constructData) -> new ReplaceMaterialListEntryWidget(data, constructData, materialList));
        }
    }

    @Inject(method = "reAddActiveWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addSaveButtons(CallbackInfo ci)
    {
        Schematic schematic = this.schematicpreview$savableSchematic();
        Path file = this.schematicpreview$schematicFile();

        if (schematic == null || file == null)
        {
            return;
        }

        if (this.schematicpreview_saveButton == null)
        {
            this.schematicpreview_saveButton = GenericButton.create(18, "schematicpreview.gui.save_schematic",
                    () -> SchematicSaver.save(this.schematicpreview$savableSchematic(), this.schematicpreview$schematicFile()));
            this.schematicpreview_saveAsButton = GenericButton.create(18, "schematicpreview.gui.save_schematic_as",
                    () -> SchematicSaver.saveAs(this.schematicpreview$savableSchematic(), this.schematicpreview$schematicFile()));
        }

        this.addWidget(this.schematicpreview_saveButton);
        this.addWidget(this.schematicpreview_saveAsButton);
    }

    @Inject(method = "updateWidgetPositions", at = @At("TAIL"), remap = false)
    private void schematicpreview$positionSaveButtons(CallbackInfo ci)
    {
        if (this.schematicpreview_saveButton == null)
        {
            return;
        }

        this.schematicpreview_saveButton.setPosition(this.exportButton.getRight() + 2, this.exportButton.getY());
        this.schematicpreview_saveAsButton.setPosition(this.schematicpreview_saveButton.getRight() + 2, this.exportButton.getY());
    }

    /**
     * The schematic to save, or {@code null} if this list isn't schematic-backed or that
     * schematic has no file to write to (e.g. one created fresh and never saved).
     */
    @Unique
    @Nullable
    private Schematic schematicpreview$savableSchematic()
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false || (this.materialList instanceof MaterialListSchematic) == false)
        {
            return null;
        }

        Schematic schematic = MaterialListAccessors.getSchematic((MaterialListSchematic) this.materialList);

        return schematic;
    }

    @Unique
    @Nullable
    private Path schematicpreview$schematicFile()
    {
        Schematic schematic = this.schematicpreview$savableSchematic();

        if (schematic == null)
        {
            return null;
        }

        Path remembered = MaterialListAccessors.getSchematicFile(schematic);

        if (remembered != null)
        {
            return remembered;
        }

        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicPlacements())
        {
            if (placement.getSchematic() == schematic)
            {
                return placement.getSchematicFile();
            }
        }

        return null;
    }
}
