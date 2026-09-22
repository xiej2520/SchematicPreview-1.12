package dev.froyln.schematicpreview.gui;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.materials.BlockReplacer;
import dev.froyln.schematicpreview.mixin.MaterialListPlacementAccessor;
import dev.froyln.schematicpreview.mixin.MaterialListSchematicAccessor;

/** Adds a small block-id replacement action to schematic-backed material rows. */
public class ReplaceMaterialListEntryWidget extends WidgetMaterialListEntry
{
    private final MaterialListBase materialList;
    @Nullable private final MaterialListEntry entry;

    public ReplaceMaterialListEntryWidget(int x, int y, int width, int height, boolean isOdd,
            MaterialListBase materialList, @Nullable MaterialListEntry entry, int listIndex,
            WidgetListMaterialList listWidget)
    {
        super(x, y, width, height, isOdd, materialList, entry, listIndex, listWidget);
        this.materialList = materialList;
        this.entry = entry;

        if (entry != null)
        {
            ButtonGeneric button = new ButtonGeneric(x + width - 62, y + 1, -1, true,
                    StringUtils.translate("schematicpreview.gui.replace_block"));
            this.addButton(button, new ReplaceAction());
        }
    }

    private final class ReplaceAction implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            if (entry == null)
            {
                return;
            }

            GuiBase.openGui(new BlockSelectScreen("schematicpreview.gui.replace_block.title",
                    ReplaceMaterialListEntryWidget.this::replace).setParent(ReplaceMaterialListEntryWidget.this.mc.currentScreen));
        }
    }

    private void replace(net.minecraft.item.ItemStack newStack)
    {
        if (this.materialList instanceof MaterialListSchematic)
        {
            MaterialListSchematicAccessor accessor = (MaterialListSchematicAccessor) this.materialList;
            LitematicaSchematic schematic = accessor.schematicpreview$getSchematic();
            BlockReplacer.replace(this.entry.getStack(), newStack, schematic, accessor.schematicpreview$getRegions());
            this.materialList.reCreateMaterialList();
        }
        else if (this.materialList instanceof MaterialListPlacement)
        {
            SchematicPlacement placement = ((MaterialListPlacementAccessor) this.materialList).schematicpreview$getPlacement();
            LitematicaSchematic schematic = placement.getSchematic();
            BlockReplacer.replace(this.entry.getStack(), newStack, schematic, schematic.getAreaPositions().keySet());
            this.materialList.reCreateMaterialList();
        }
    }
}
