package dev.froyln.schematicpreview.gui;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.render.PreviewCache;
import dev.froyln.schematicpreview.render.PreviewRenderer;

/** Full-screen 1.15 preview using the same cached renderer as the browser panel. */
public class PreviewFullscreenScreen extends GuiBase
{
    private static final int TOP_MARGIN = 24;
    private static final int TOOLBAR_BUTTON_HEIGHT = 20;
    private static final int TOOLBAR_BUTTON_WIDTH = 18;

    private final File file;
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

    public PreviewFullscreenScreen(File file)
    {
        this.file = file;
        this.title = StringUtils.translate("schematicpreview.title.preview");
        this.useTitleHierarchy = false;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        ButtonGeneric save = new ButtonGeneric(2, 2, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT, "",
                StringUtils.translate("schematicpreview.button.save_screenshot"));
        save.setRenderDefaultBackground(false);
        this.addButton(save, (button, mouseButton) -> this.capture(true));

        ButtonGeneric copy = new ButtonGeneric(22, 2, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT, "",
                StringUtils.translate("schematicpreview.button.copy_screenshot"));
        copy.setRenderDefaultBackground(false);
        this.addButton(copy, (button, mouseButton) -> this.capture(false));

        ButtonGeneric freecam = new ButtonGeneric(42, 2, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT, "",
                StringUtils.translate("schematicpreview.button.freecam"));
        freecam.setRenderDefaultBackground(false);
        this.addButton(freecam, (button, mouseButton) -> this.freecam = ! this.freecam);

        ButtonGeneric close = new ButtonGeneric(Math.max(64, this.width - 20), 2,
                TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT, "",
                StringUtils.translate("malilib.gui.button.close"));
        close.setRenderDefaultBackground(false);
        this.addButton(close, (button, mouseButton) -> this.closeGui(true));
    }

    @Override
    protected void drawTitle(int mouseX, int mouseY, float partialTicks)
    {
        // Leave room for the icon controls. The default hierarchy title is long enough to
        // overlap them on the fullscreen screen.
        this.drawString(this.title, 70, 10, COLOR_WHITE);
    }

    @Override
    protected void drawContents(int mouseX, int mouseY, float partialTicks)
    {
        this.updateCamera(mouseX, mouseY);

        if (this.cameraInitialized)
        {
            PreviewCache.renderPreview(this.file, 4, TOP_MARGIN, this.width - 8, this.height - TOP_MARGIN - 4,
                    this.yRot, this.xRot, this.distance,
                    this.targetX, this.targetY, this.targetZ);
        }
        else
        {
            RenderUtils.drawOutlinedBox(4, TOP_MARGIN, this.width - 8, this.height - TOP_MARGIN - 4,
                    0xA0000000, COLOR_HORIZONTAL_BAR);
            String text = StringUtils.translate("schematicpreview.label.preview.loading");
            this.drawString(text, this.width / 2 - this.getStringWidth(text) / 2,
                    this.height / 2, COLOR_WHITE);
        }

        this.drawToolbarButton(2, PreviewIcons.SAVE, this.isOverToolbarButton(2, mouseX, mouseY));
        this.drawToolbarButton(22, PreviewIcons.COPY, this.isOverToolbarButton(22, mouseX, mouseY));
        this.drawToolbarButton(42, PreviewIcons.FREECAM,
                this.freecam || this.isOverToolbarButton(42, mouseX, mouseY));
        int closeX = Math.max(64, this.width - 20);
        this.drawToolbarButton(closeX, PreviewIcons.CLOSE, this.isOverToolbarButton(closeX, mouseX, mouseY));
    }

    private boolean isOverToolbarButton(int x, int mouseX, int mouseY)
    {
        return mouseX >= x && mouseX < x + TOOLBAR_BUTTON_WIDTH &&
               mouseY >= 2 && mouseY < 2 + TOOLBAR_BUTTON_HEIGHT;
    }

    private void drawToolbarButton(int x, PreviewIcons icon, boolean highlighted)
    {
        RenderUtils.drawOutlinedBox(x, 2, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT,
                highlighted ? 0xD0707070 : 0xB0202020, 0xFF999999);
        icon.renderAt(x + 3, 6, highlighted);
    }

    private void updateCamera(int mouseX, int mouseY)
    {
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
                double yaw = Math.toRadians(this.yRot);
                this.targetX -= dx * Math.cos(yaw) * scale;
                this.targetZ -= dx * Math.sin(yaw) * scale;
                this.targetY += dy * scale;
            }

            this.dragX = mouseX;
            this.dragY = mouseY;
        }

        if (this.cameraInitialized == false)
        {
            CompletableFuture<LitematicaSchematic> future = PreviewCache.getSchematic(this.file);

            if (future.isDone())
            {
                LitematicaSchematic schematic = future.getNow(null);

                if (schematic != null)
                {
                    PreviewRenderer renderer = PreviewCache.getRenderer(this.file, schematic);
                    net.minecraft.util.math.Vec3d center = renderer.getCenter();
                    this.targetX = center.x;
                    this.targetY = center.y;
                    this.targetZ = center.z;
                    this.yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
                    this.xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
                    this.distance = renderer.getDefaultDistance(Configs.Preview.PREVIEW_FOV.getDoubleValue(),
                            (double) Math.max(1, this.width) / Math.max(1, this.height));
                    this.cameraInitialized = true;
                }
            }
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        // GuiBase dispatches ButtonGeneric actions from super.onMouseClicked().  This must
        // happen before the camera drag handler, otherwise clicks on save/copy/close become
        // camera drags and the clipboard button appears not to work.
        if (super.onMouseClicked(mouseX, mouseY, mouseButton))
        {
            return true;
        }

        if (mouseButton == 0 || mouseButton == 1)
        {
            this.dragging = true;
            this.dragButton = mouseButton;
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
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
        if (this.cameraInitialized)
        {
            double factor = mouseWheelDelta < 0 ? 1.1 : 1.0 / 1.1;
            this.distance = clamp(this.distance * factor, 1.5, 16384.0);
            return true;
        }

        return super.onMouseScrolled(mouseX, mouseY, mouseWheelDelta);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private void capture(boolean save)
    {
        if (this.cameraInitialized == false)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "schematicpreview.message.preview_not_ready");
            return;
        }

        int width = Math.max(1, this.width - 8);
        int height = Math.max(1, this.height - TOP_MARGIN - 4);
        java.awt.image.BufferedImage image = PreviewCache.capturePreview(this.file, width, height,
                this.yRot, this.xRot, this.distance, this.targetX, this.targetY, this.targetZ);

        if (image == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "schematicpreview.message.preview_not_ready");
            return;
        }

        if (save)
        {
            File output = ScreenshotUtil.save(image, this.file);

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

    @Override
    public void removed()
    {
        PreviewCache.close();
        super.removed();
    }
}
