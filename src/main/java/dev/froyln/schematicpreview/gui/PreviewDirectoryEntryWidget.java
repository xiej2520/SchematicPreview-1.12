package dev.froyln.schematicpreview.gui;

import java.io.File;
import java.util.Collections;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.interfaces.IFileBrowserIconProvider;
import fi.dy.masa.malilib.gui.widgets.WidgetDirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntryType;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.data.IconPosition;
import dev.froyln.schematicpreview.render.PreviewCache;

/** A 1.15 browser entry with live previews and editable directory icons. */
public class PreviewDirectoryEntryWidget extends WidgetDirectoryEntry
{
    private static final int TEXT_STRIP_HEIGHT = 12;
    private static final int PADDING = 2;

    private final PreviewType previewType;
    @Nullable private final DirectoryIconStore.Entry iconEntry;
    @Nullable private final File firstSchematic;
    private final boolean showBigVisual;
    @Nullable private final String clampedTileName;

    public PreviewDirectoryEntryWidget(int x, int y, int width, int height, boolean isOdd,
            DirectoryEntry entry, int listIndex, WidgetSchematicBrowser browser,
            IFileBrowserIconProvider iconProvider)
    {
        super(x, y, width, height, isOdd, entry, listIndex, browser, iconProvider);

        this.previewType = (PreviewType) Configs.Menu.PREVIEW_TYPE.getOptionListValue();
        this.iconEntry = entry.getType() == DirectoryEntryType.DIRECTORY
                ? DirectoryIconStore.get(entry.getFullPath()) : null;
        boolean wantsSchematicVisual = this.previewType.hasPreview() &&
                (this.iconEntry == null || this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC);
        this.firstSchematic = entry.getType() == DirectoryEntryType.DIRECTORY && wantsSchematicVisual
                ? PreviewCache.getFirstSchematicIn(entry.getFullPath(), PreviewDirectoryEntryWidget::isSchematic) : null;

        boolean directoryBigIcon = this.iconEntry != null && this.iconEntry.position == IconPosition.CENTER;
        boolean directorySchematicVisual = entry.getType() == DirectoryEntryType.DIRECTORY &&
                wantsSchematicVisual && this.firstSchematic != null;
        this.showBigVisual = entry.getType() == DirectoryEntryType.FILE && this.previewType.hasPreview() ||
                directoryBigIcon || directorySchematicVisual;
        this.clampedTileName = this.showBigVisual && this.previewType.isTile()
                ? StringUtils.getClampedDisplayStringRenderlen(Collections.singletonList(this.getDisplayName()),
                        this.width - PADDING * 2, "", "") : null;
    }

    private static boolean isSchematic(File file)
    {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schem") ||
               name.endsWith(".schematic") || name.endsWith(".nbt");
    }

    @Override
    protected boolean onMouseClickedImpl(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 1 && this.entry.getType() == DirectoryEntryType.DIRECTORY &&
            this.isIconArea(mouseX, mouseY))
        {
            GuiBase.openGui(new DirectoryIconEditScreen(this.entry.getFullPath()).setParent(this.mc.currentScreen));
            return true;
        }

        return super.onMouseClickedImpl(mouseX, mouseY, mouseButton);
    }

    private boolean isIconArea(int mouseX, int mouseY)
    {
        if (this.previewType.isTile())
        {
            return mouseY - this.y < this.height - TEXT_STRIP_HEIGHT;
        }

        return mouseX - this.x < (this.previewType.hasPreview() ? this.height : 20);
    }

    @Override
    public void render(int mouseX, int mouseY, boolean selected)
    {
        super.render(mouseX, mouseY, selected);

        if (this.entry.getType() == DirectoryEntryType.DIRECTORY)
        {
            this.renderDirectoryVisual();
        }
        else if (this.showBigVisual)
        {
            int[] box = this.previewBox();
            PreviewCache.renderSmallPreview(new File(this.entry.getDirectory(), this.entry.getName()),
                    box[0], box[1], box[2], box[3]);
        }

        if (this.clampedTileName != null)
        {
            int textY = this.y + this.height - TEXT_STRIP_HEIGHT + 2;
            int textX = this.x + Math.max(0, (this.width - StringUtils.getStringWidth(this.clampedTileName)) / 2);
            this.drawString(textX, textY, -1, this.clampedTileName);
        }
    }

    private int[] previewBox()
    {
        if (this.previewType.isTile())
        {
            return new int[] { this.x, this.y, this.width, Math.max(1, this.height - TEXT_STRIP_HEIGHT) };
        }

        int size = Math.max(1, this.height - 2 * PADDING);
        return new int[] { this.x + PADDING, this.y + PADDING, size, size };
    }

    private void renderDirectoryVisual()
    {
        if (this.iconEntry == null)
        {
            if (this.firstSchematic != null && this.previewType.hasPreview())
            {
                int[] box = this.previewBox();
                PreviewCache.renderSmallPreview(this.firstSchematic, box[0], box[1], box[2], box[3]);
            }

            return;
        }

        Item item;

        try
        {
            item = Registry.ITEM.getOrEmpty(new Identifier(this.iconEntry.itemId)).orElse(null);
        }
        catch (IllegalArgumentException e)
        {
            return;
        }

        if (item == null)
        {
            return;
        }

        if (this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC && this.firstSchematic != null &&
            this.previewType.hasPreview())
        {
            int[] box = this.previewBox();
            PreviewCache.renderSmallPreview(this.firstSchematic, box[0], box[1], box[2], box[3]);
        }

        ItemStack stack = new ItemStack(item);

        if (this.iconEntry.position == IconPosition.CENTER)
        {
            int[] box = this.previewBox();
            float scale = Math.max(1, Math.min(box[2], box[3]) / 20f);
            float drawX = box[0] + (box[2] - 16 * scale) / 2f;
            float drawY = box[1] + (box[3] - 16 * scale) / 2f;
            InventoryOverlay.renderStackAt(stack, drawX, drawY, scale, this.mc);
        }
        else
        {
            InventoryOverlay.renderStackAt(stack, this.x + 2,
                    this.y + (this.height - 16) / 2f, 1f, this.mc);
        }
    }
}
