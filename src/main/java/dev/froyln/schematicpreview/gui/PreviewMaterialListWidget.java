package dev.froyln.schematicpreview.gui;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;

/** The 1.15 material-list widget with replacement-aware rows. */
public class PreviewMaterialListWidget extends WidgetListMaterialList
{
    private final GuiMaterialList parent;

    public PreviewMaterialListWidget(int x, int y, int width, int height, GuiMaterialList parent)
    {
        super(x, y, width, height, parent);
        this.parent = parent;
    }

    @Override
    protected WidgetMaterialListEntry createListEntryWidget(int x, int y, int listIndex,
            boolean isOdd, @Nullable MaterialListEntry entry)
    {
        MaterialListBase materialList = this.parent.getMaterialList();
        return new ReplaceMaterialListEntryWidget(x, y, this.browserEntryWidth,
                this.getBrowserEntryHeightFor(entry), isOdd, materialList, entry, listIndex, this);
    }
}
