package dev.froyln.schematicpreview.gui;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import malilib.gui.BaseScreen;
import malilib.gui.icon.FileBrowserIconProvider;
import malilib.gui.util.ScreenContext;
import malilib.gui.widget.list.BaseFileBrowserWidget;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import malilib.gui.widget.list.entry.DataListEntryWidgetData;
import malilib.gui.widget.list.entry.DirectoryEntryWidget;
import malilib.render.ItemRenderUtils;
import malilib.render.text.StyledTextLine;
import malilib.render.text.StyledTextUtils;
import malilib.util.data.LeftRight;

import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.data.IconPosition;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * A {@link DirectoryEntryWidget} that draws a live 3D preview over the file-type icon when the
 * active {@link PreviewType} calls for one; directories show their custom icon
 * ({@link DirectoryIconStore}) or a preview of their first renderable schematic file.
 */
public class PreviewDirectoryEntryWidget extends DirectoryEntryWidget
{
    private static final int TILE_TEXT_STRIP_HEIGHT = 12;
    private static final int PREVIEW_PADDING = 2;

    private final PreviewType previewType;
    private final DirectoryEntryType entryType;
    @Nullable private final DirectoryIconStore.Entry iconEntry;
    private final List<Path> schematicFilesInDir;
    private final boolean showBigVisual;
    @Nullable private StyledTextLine clampedTileName;

    public PreviewDirectoryEntryWidget(DirectoryEntry entry, DataListEntryWidgetData constructData,
                                       BaseFileBrowserWidget fileBrowserWidget,
                                       @Nullable FileBrowserIconProvider iconProvider, PreviewType previewType)
    {
        super(entry, constructData, fileBrowserWidget, iconProvider);

        this.previewType = previewType;
        this.entryType = entry.getType();
        this.iconEntry = this.entryType == DirectoryEntryType.DIRECTORY ? DirectoryIconStore.get(entry.getFullPath()) : null;

        boolean wantsSchematicVisual = previewType.hasPreview() &&
                (this.iconEntry == null || this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC);
        this.schematicFilesInDir = (this.entryType == DirectoryEntryType.DIRECTORY && wantsSchematicVisual)
                ? PreviewCache.getSchematicFilesIn(entry.getFullPath(), BrowserWidgetAccessors.getFileFilter(fileBrowserWidget))
                : Collections.emptyList();

        boolean directoryBigIcon = this.iconEntry != null && this.iconEntry.position == IconPosition.CENTER;
        boolean directorySchematicVisual = this.entryType == DirectoryEntryType.DIRECTORY &&
                wantsSchematicVisual && this.schematicFilesInDir.isEmpty() == false;

        this.showBigVisual = (this.entryType == DirectoryEntryType.FILE && previewType.hasPreview()) ||
                              directoryBigIcon || directorySchematicVisual;

        if (this.entryType == DirectoryEntryType.DIRECTORY)
        {
            this.translateAndAddHoverString("schematicpreview.button.change_directory_icon");
        }

        // The vanilla type icon is kept as the fallback for whatever nothing draws over.
        if (this.showBigVisual)
        {
            if (previewType.isTile())
            {
                int maxWidth = this.getWidth() - PREVIEW_PADDING * 2;
                this.clampedTileName = StyledTextUtils.clampStyledTextToMaxWidth(this.fullNameText, maxWidth, LeftRight.RIGHT, " ...");
            }
            else
            {
                int previewSize = this.getHeight() - PREVIEW_PADDING * 2;
                this.textOffset.setXOffset(previewSize + PREVIEW_PADDING + 3);
            }
        }
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 1 && this.entryType == DirectoryEntryType.DIRECTORY && this.isIconAreaClick(mouseX, mouseY))
        {
            BaseScreen.openScreenWithParent(new DirectoryIconEditScreen(this.data.getFullPath()));
            return true;
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isIconAreaClick(int mouseX, int mouseY)
    {
        if (this.previewType.isTile())
        {
            int previewHeight = this.getHeight() - TILE_TEXT_STRIP_HEIGHT;
            return mouseY - this.getY() < previewHeight;
        }

        return mouseX - this.getX() < this.textOffset.getXOffset();
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        if (this.entryType == DirectoryEntryType.DIRECTORY)
        {
            this.renderDirectoryVisual(x, y, z, ctx);
        }
        else if (this.showBigVisual)
        {
            this.renderPreviewBox(this.data.getFullPath(), x, y, z);
        }

        // Draw the tile name after the preview quad so the preview cannot cover it.
        if (this.clampedTileName != null)
        {
            int textY = y + this.getHeight() - TILE_TEXT_STRIP_HEIGHT + 2;
            int textX = x + Math.max(0, (this.getWidth() - this.clampedTileName.renderWidth) / 2);
            this.renderTextLine(textX, textY, z + 0.5f, this.getTextSettings().getTextColor(),
                               this.getTextSettings().getTextShadowEnabled(), this.clampedTileName, ctx);
        }
    }

    /**
     * The rectangle {@code {x, y, width, height}} a schematic preview or a centered directory
     * icon is drawn into: the whole cell above the name strip for tiles, a padded square at
     * the left of the row for lists.
     */
    private int[] previewBox(int x, int y)
    {
        if (this.previewType.isTile())
        {
            return new int[] { x, y, this.getWidth(), this.getHeight() - TILE_TEXT_STRIP_HEIGHT };
        }

        int size = this.getHeight() - PREVIEW_PADDING * 2;
        return new int[] { x + PREVIEW_PADDING, y + PREVIEW_PADDING, size, size };
    }

    private void renderPreviewBox(Path schematicPath, int x, int y, float z)
    {
        int[] box = this.previewBox(x, y);
        PreviewCache.renderSmallPreview(schematicPath, box[0], box[1], box[2], box[3], z + 0.5f);
    }

    private void renderDirectoryVisual(int x, int y, float z, ScreenContext ctx)
    {
        if (this.iconEntry == null)
        {
            this.renderDirectoryPreview(x, y, z);

            return;
        }

        Item item = Item.getByNameOrId(this.iconEntry.itemId);

        if (item == null)
        {
            return;
        }

        if (this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC)
        {
            this.renderDirectoryPreview(x, y, z);
        }

        ItemStack stack = new ItemStack(item);

        if (this.iconEntry.position == IconPosition.CENTER)
        {
            int[] box = this.previewBox(x, y);
            int scale = Math.max(1, Math.min(box[2], box[3]) / 20);
            int drawX = box[0] + (box[2] - 16 * scale) / 2;
            int drawY = box[1] + (box[3] - 16 * scale) / 2;
            ItemRenderUtils.renderStackAt(stack, drawX, drawY, z + 0.6f, scale, ctx);
        }
        else
        {
            ItemRenderUtils.renderStackAt(stack, x + 2, y + (this.getHeight() - 16) / 2, z + 0.6f, 1f, ctx);
        }
    }

    private boolean renderDirectoryPreview(int x, int y, float z)
    {
        int[] box = this.previewBox(x, y);

        for (Path schematicPath : this.schematicFilesInDir)
        {
            PreviewCache.SmallPreviewResult result = PreviewCache.renderSmallPreviewResult(
                    schematicPath, box[0], box[1], box[2], box[3], z + 0.5f);

            if (result == PreviewCache.SmallPreviewResult.RENDERED)
            {
                return true;
            }

            // Do not queue every file in a large directory while the first candidate is loading.
            // Once it is known to be unusable, continue to the next candidate.
            if (result == PreviewCache.SmallPreviewResult.PENDING)
            {
                return false;
            }
        }

        return false;
    }

    @Override
    protected void renderInfoColumns(int x, int y, float z, ScreenContext ctx)
    {
        if (this.showBigVisual && this.previewType.isTile())
        {
            // The tile name is rendered after the preview in renderAt().
            return;
        }

        super.renderInfoColumns(x, y, z, ctx);
    }
}
