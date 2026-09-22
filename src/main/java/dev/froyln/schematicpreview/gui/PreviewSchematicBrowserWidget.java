package dev.froyln.schematicpreview.gui;

import java.io.File;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetDirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntryType;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.render.PreviewCache;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import java.util.concurrent.CompletableFuture;

/** 1.15-compatible WidgetSchematicBrowser with live entry and information-panel previews. */
public class PreviewSchematicBrowserWidget extends WidgetSchematicBrowser
{
    private static final int PREVIEW_BUTTON_SIZE = 16;
    private static final int PREVIEW_BUTTON_GAP = 2;
    private static final int PREVIEW_TYPE_BUTTON_RIGHT_MARGIN = 36;

    private File cameraFile;
    private boolean cameraInitialized;
    private boolean dragging;
    private boolean freecam;
    private int dragButton;
    private int dragX;
    private int dragY;
    private float yRot;
    private float xRot;
    private double distance;
    private double targetX;
    private double targetY;
    private double targetZ;
    private long lastPreviewClickTime;

    public PreviewSchematicBrowserWidget(int x, int y, int width, int height,
            GuiSchematicBrowserBase parent, @Nullable ISelectionListener<DirectoryEntry> listener)
    {
        super(x, y, width, height, parent, listener);
    }

    @Override
    protected int getBrowserEntryHeightFor(@Nullable DirectoryEntry entry)
    {
        PreviewType type = (PreviewType) Configs.Menu.PREVIEW_TYPE.getOptionListValue();
        return type.hasPreview() ? Math.max(type.getHeight(this.browserEntryWidth), 48) : super.getBrowserEntryHeightFor(entry);
    }

    @Override
    protected WidgetDirectoryEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, DirectoryEntry entry)
    {
        return new PreviewDirectoryEntryWidget(x, y, this.browserEntryWidth,
                this.getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, this, this.iconProvider);
    }

    @Override
    protected void drawAdditionalContents(int mouseX, int mouseY)
    {
        super.drawAdditionalContents(mouseX, mouseY);
        this.drawPreviewTypeButton(mouseX, mouseY);

        DirectoryEntry entry = this.getLastSelectedEntry();

        if (entry == null || entry.getType() != DirectoryEntryType.FILE)
        {
            return;
        }

        int x = this.posX + this.totalWidth - this.infoWidth + 5;
        int y = this.posY + Math.min(118, this.infoHeight / 2);
        int size = Math.min(this.infoWidth - 10, this.infoHeight - (y - this.posY) - 8);

        if (size > 8)
        {
            this.updateCamera(entry.getFullPath(), size, mouseX, mouseY);
            PreviewCache.renderPreview(entry.getFullPath(), x, y, size, size,
                                       this.yRot, this.xRot, this.distance,
                                       this.targetX, this.targetY, this.targetZ);
            this.drawPreviewControls(x, y, size, mouseX, mouseY);
        }
    }

    @Override
    public void drawContents(int mouseX, int mouseY, float partialTicks)
    {
        PreviewType type = (PreviewType) Configs.Menu.PREVIEW_TYPE.getOptionListValue();

        if (type.isTile())
        {
            this.drawTileContents(mouseX, mouseY, partialTicks, type);
        }
        else
        {
            super.drawContents(mouseX, mouseY, partialTicks);
        }
    }

    private void drawTileContents(int mouseX, int mouseY, float partialTicks, PreviewType type)
    {
        RenderUtils.drawOutlinedBox(this.posX, this.posY, this.browserWidth, this.browserHeight,
                0xB0000000, COLOR_HORIZONTAL_BAR);

        if (this.widgetSearchBar != null)
        {
            this.widgetSearchBar.render(mouseX, mouseY, false);
        }

        int columns = type.getColumns();
        int cellWidth = Math.max(1, this.browserEntryWidth / columns);
        int entryHeight = Math.max(24, type.getHeight(cellWidth));
        int usableHeight = this.browserHeight - this.browserPaddingY - this.browserEntriesOffsetY;
        int visibleRows = Math.max(1, usableHeight / entryHeight);
        int rows = (this.listContents.size() + columns - 1) / columns;
        int totalHeight = Math.max(usableHeight, rows * entryHeight);
        int scrollbarHeight = this.browserHeight - this.browserEntriesOffsetY - 8;
        int scrollBarX = this.posX + this.browserWidth - 9;
        int scrollBarY = this.browserEntriesStartY + this.browserEntriesOffsetY;

        this.maxVisibleBrowserEntries = visibleRows * columns;
        this.scrollBar.setMaxValue(Math.max(0, rows - visibleRows));
        this.scrollBar.render(mouseX, mouseY, partialTicks, scrollBarX, scrollBarY,
                8, scrollbarHeight, totalHeight);
        this.listWidgets.clear();

        int firstRow = Math.min(this.scrollBar.getValue(), Math.max(0, rows - visibleRows));
        int startIndex = firstRow * columns;
        int startX = this.posX + 2;
        int startY = this.posY + 4 + this.browserEntriesOffsetY;

        for (int row = 0; row < visibleRows && firstRow + row < rows; ++row)
        {
            for (int column = 0; column < columns; ++column)
            {
                int index = startIndex + row * columns + column;

                if (index >= this.listContents.size())
                {
                    break;
                }

                int x = startX + column * cellWidth;
                int y = startY + row * entryHeight;
                DirectoryEntry entry = this.listContents.get(index);
                WidgetDirectoryEntry widget = new PreviewDirectoryEntryWidget(x, y,
                        cellWidth - 2, entryHeight, (index & 0x1) != 0, entry, index, this, this.iconProvider);
                this.listWidgets.add(widget);
                boolean selected = entry.equals(this.getLastSelectedEntry());
                widget.render(mouseX, mouseY, selected);
            }
        }

        this.drawAdditionalContents(mouseX, mouseY);
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (this.isOverPreviewTypeButton(mouseX, mouseY))
        {
            Configs.Menu.PREVIEW_TYPE.setOptionListValue(
                    Configs.Menu.PREVIEW_TYPE.getOptionListValue().cycle(mouseButton == 0));
            this.refreshEntries();
            return true;
        }

        if (mouseButton == 0 && this.isOverFullscreenButton(mouseX, mouseY) &&
            this.getLastSelectedEntry() != null &&
            this.getLastSelectedEntry().getType() == DirectoryEntryType.FILE)
        {
            GuiBase.openGui(new PreviewFullscreenScreen(this.getLastSelectedEntry().getFullPath())
                    .setParent(this.mc.currentScreen));
            return true;
        }

        if (mouseButton == 0 && this.isOverFreecamButton(mouseX, mouseY))
        {
            this.freecam = ! this.freecam;
            return true;
        }

        if (mouseButton == 0 && this.isOverSaveButton(mouseX, mouseY))
        {
            this.captureSidePreview(true);
            return true;
        }

        if (mouseButton == 0 && this.isOverCopyButton(mouseX, mouseY))
        {
            this.captureSidePreview(false);
            return true;
        }

        if (this.isOverPreview(mouseX, mouseY) && this.getLastSelectedEntry() != null)
        {
            if (mouseButton == 0 || mouseButton == 1)
            {
                long now = System.currentTimeMillis();

                if (mouseButton == 0 && now - this.lastPreviewClickTime < 350L)
                {
                    DirectoryEntry selected = this.getLastSelectedEntry();

                    if (selected.getType() == DirectoryEntryType.FILE)
                    {
                        fi.dy.masa.malilib.gui.GuiBase.openGui(
                                new PreviewFullscreenScreen(selected.getFullPath()).setParent(this.mc.currentScreen));
                        this.lastPreviewClickTime = 0L;
                        return true;
                    }
                }

                this.lastPreviewClickTime = now;
                this.dragging = true;
                this.dragButton = mouseButton;
                this.dragX = mouseX;
                this.dragY = mouseY;
                return true;
            }
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        this.dragging = false;
        return super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double mouseWheelDelta)
    {
        if (this.isOverPreview(mouseX, mouseY) && this.cameraInitialized)
        {
            double factor = mouseWheelDelta < 0 ? 1.1 : 1.0 / 1.1;
            this.distance = clamp(this.distance * factor, 1.5, 4096.0);
            return true;
        }

        return super.onMouseScrolled(mouseX, mouseY, mouseWheelDelta);
    }

    private boolean isOverPreview(int mouseX, int mouseY)
    {
        int x = this.posX + this.totalWidth - this.infoWidth + 5;
        int y = this.posY + Math.min(118, this.infoHeight / 2);
        int size = Math.min(this.infoWidth - 10, this.infoHeight - (y - this.posY) - 8);
        return size > 8 && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    private boolean isOverFullscreenButton(int mouseX, int mouseY)
    {
        int x = this.getPreviewX();
        int y = this.getPreviewY();
        int size = this.getPreviewSize();
        int buttonX = this.getPreviewButtonX(2);
        return size > 18 && mouseX >= buttonX && mouseX < buttonX + PREVIEW_BUTTON_SIZE &&
               mouseY >= y + 2 && mouseY < y + 18;
    }

    private boolean isOverFreecamButton(int mouseX, int mouseY)
    {
        int y = this.getPreviewY();
        int size = this.getPreviewSize();
        int buttonX = this.getPreviewButtonX(3);
        return size > 18 && mouseX >= buttonX && mouseX < buttonX + PREVIEW_BUTTON_SIZE &&
               mouseY >= y + 2 && mouseY < y + 18;
    }

    private boolean isOverCopyButton(int mouseX, int mouseY)
    {
        int x = this.getPreviewX();
        int y = this.getPreviewY();
        int size = this.getPreviewSize();
        int buttonX = this.getPreviewButtonX(1);
        return size > 36 && mouseX >= buttonX && mouseX < buttonX + PREVIEW_BUTTON_SIZE &&
               mouseY >= y + 2 && mouseY < y + 18;
    }

    private boolean isOverSaveButton(int mouseX, int mouseY)
    {
        int x = this.getPreviewX();
        int y = this.getPreviewY();
        int size = this.getPreviewSize();
        int buttonX = this.getPreviewButtonX(0);
        return size > 54 && mouseX >= buttonX && mouseX < buttonX + PREVIEW_BUTTON_SIZE &&
               mouseY >= y + 2 && mouseY < y + 18;
    }

    private void drawPreviewControls(int x, int y, int size, int mouseX, int mouseY)
    {
        if (size <= 18)
        {
            return;
        }

        this.drawPreviewButton(this.getPreviewButtonX(0), y + 2, PreviewIcons.SAVE,
                this.isOverSaveButton(mouseX, mouseY));
        this.drawPreviewButton(this.getPreviewButtonX(1), y + 2, PreviewIcons.COPY,
                this.isOverCopyButton(mouseX, mouseY));
        this.drawPreviewButton(this.getPreviewButtonX(2), y + 2, PreviewIcons.FULLSCREEN,
                this.isOverFullscreenButton(mouseX, mouseY));
        this.drawPreviewButton(this.getPreviewButtonX(3), y + 2, PreviewIcons.FREECAM,
                this.freecam || this.isOverFreecamButton(mouseX, mouseY));
    }

    private void drawPreviewButton(int x, int y, PreviewIcons icon, boolean hovered)
    {
        RenderUtils.drawOutlinedBox(x, y, PREVIEW_BUTTON_SIZE, PREVIEW_BUTTON_SIZE,
                hovered ? 0xD0707070 : 0xB0202020, 0xFF999999);
        icon.renderAt(x + 2, y + 2, hovered);
    }

    private int getPreviewButtonX(int index)
    {
        return this.getPreviewX() + this.getPreviewSize() -
                (4 - index) * (PREVIEW_BUTTON_SIZE + PREVIEW_BUTTON_GAP);
    }

    private int getPreviewX()
    {
        return this.posX + this.totalWidth - this.infoWidth + 5;
    }

    private int getPreviewY()
    {
        return this.posY + Math.min(118, this.infoHeight / 2);
    }

    private int getPreviewSize()
    {
        int y = this.getPreviewY();
        return Math.min(this.infoWidth - 10, this.infoHeight - (y - this.posY) - 8);
    }

    private boolean isOverPreviewTypeButton(int mouseX, int mouseY)
    {
        int x = this.getPreviewTypeButtonX();
        return mouseX >= x && mouseX < x + this.getPreviewTypeButtonWidth() &&
               mouseY >= this.posY + 3 && mouseY < this.posY + 19;
    }

    private int getPreviewTypeButtonX()
    {
        // Keep the directory-navigation icons and the search toggle unobstructed. The
        // navigation bar reserves the rightmost icon-sized area for the search toggle.
        return Math.max(this.posX + 60,
                this.posX + this.browserWidth - this.getPreviewTypeButtonWidth() -
                        PREVIEW_TYPE_BUTTON_RIGHT_MARGIN);
    }

    private int getPreviewTypeButtonWidth()
    {
        String text = this.getPreviewTypeButtonText();
        return Math.max(112, this.getStringWidth(text) + 8);
    }

    private String getPreviewTypeButtonText()
    {
        return StringUtils.translate("schematicpreview.button.preview_type_short") + ": " +
                ((PreviewType) Configs.Menu.PREVIEW_TYPE.getOptionListValue()).getDisplayName();
    }

    private void drawPreviewTypeButton(int mouseX, int mouseY)
    {
        int x = this.getPreviewTypeButtonX();
        int y = this.posY + 3;
        boolean hovered = this.isOverPreviewTypeButton(mouseX, mouseY);
        int color = hovered ? 0xB0707070 : 0xA0202020;
        RenderUtils.drawOutlinedBox(x, y, this.getPreviewTypeButtonWidth(), 16, color, 0xFF999999);
        String text = this.getPreviewTypeButtonText();
        this.drawString(text, x + 3, y + 4, 0xFFFFFFFF);
    }

    private void captureSidePreview(boolean save)
    {
        DirectoryEntry entry = this.getLastSelectedEntry();

        if (entry == null || entry.getType() != DirectoryEntryType.FILE || this.cameraInitialized == false)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "schematicpreview.message.preview_not_ready");
            return;
        }

        int size = Math.max(1, this.getPreviewSize());
        java.awt.image.BufferedImage image = PreviewCache.capturePreview(entry.getFullPath(), size, size,
                this.yRot, this.xRot, this.distance, this.targetX, this.targetY, this.targetZ);

        if (image == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "schematicpreview.message.preview_not_ready");
            return;
        }

        if (save)
        {
            File output = ScreenshotUtil.save(image, entry.getFullPath());

            if (output != null)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS,
                        "schematicpreview.message.screenshot_saved", output.getName());
            }
            else
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "schematicpreview.message.screenshot_failed");
            }
        }
        else if (ScreenshotUtil.copyToClipboard(image))
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "schematicpreview.message.image_copied");
        }
        else
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "schematicpreview.message.image_copy_failed");
        }
    }

    private void updateCamera(File file, int size, int mouseX, int mouseY)
    {
        if (this.cameraFile == null || this.cameraFile.equals(file) == false)
        {
            this.cameraFile = file;
            this.cameraInitialized = false;
        }

        if (this.dragging)
        {
            int dx = mouseX - this.dragX;
            int dy = mouseY - this.dragY;
            boolean rotating = (this.dragButton == 0) != this.freecam;

            if (rotating)
            {
                this.yRot += dx * 0.5f;
                this.xRot = (float) clamp(this.xRot - dy * 0.5f, -90.0, 90.0);
            }
            else
            {
                double scale = this.distance / 300.0;
                this.targetX -= dx * Math.cos(Math.toRadians(this.yRot)) * scale;
                this.targetZ -= dx * Math.sin(Math.toRadians(this.yRot)) * scale;
                this.targetY += dy * scale;
            }

            this.dragX = mouseX;
            this.dragY = mouseY;
        }

        if (this.cameraInitialized == false)
        {
            CompletableFuture<LitematicaSchematic> future = PreviewCache.getSchematic(file);

            if (future.isDone())
            {
                LitematicaSchematic schematic = future.getNow(null);

                if (schematic != null)
                {
                    dev.froyln.schematicpreview.render.PreviewRenderer renderer = PreviewCache.getRenderer(file, schematic);
                    net.minecraft.util.math.Vec3d center = renderer.getCenter();
                    this.targetX = center.x;
                    this.targetY = center.y;
                    this.targetZ = center.z;
                    this.yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
                    this.xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
                    this.distance = renderer.getDefaultDistance(Configs.Preview.PREVIEW_FOV.getDoubleValue(), 1.0);
                    this.cameraInitialized = true;
                }
            }
        }
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
