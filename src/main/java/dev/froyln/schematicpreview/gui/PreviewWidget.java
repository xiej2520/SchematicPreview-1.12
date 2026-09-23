package dev.froyln.schematicpreview.gui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import malilib.gui.BaseScreen;
import malilib.gui.util.ScreenContext;
import malilib.gui.widget.InteractableWidget;
import malilib.overlay.message.MessageDispatcher;
import malilib.render.ShapeRenderUtils;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.render.PreviewCache;
import dev.froyln.schematicpreview.render.PreviewRenderer;
import dev.froyln.schematicpreview.render.PreviewRenderUtils;
import litematica.schematic.Schematic;

/**
 * Live 3D render of a schematic, tessellated and cached by {@link PreviewCache}. Each instance
 * (side panel, fullscreen) owns its own {@link Framebuffer} sized to its own pixel dimensions,
 * and its own camera state; the tessellated geometry itself is shared via the cache.
 */
public class PreviewWidget extends InteractableWidget
{
    private static final int BUTTON_SIZE = 14;
    private static final double MIN_DISTANCE = 1.5;

    private final Path path;
    @Nullable private Framebuffer fbo;
    private int fboScale;

    private boolean cameraInitialized;
    private float yRot;
    private float xRot;
    private double distance;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double maxDistance = 64.0;

    @Nullable private Consumer<BufferedImage> pendingCapture;

    private boolean freecam;
    private boolean dragging;
    private int dragButton;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private float dragStartYRot;
    private float dragStartXRot;
    private double dragStartTargetX;
    private double dragStartTargetY;
    private double dragStartTargetZ;

    public PreviewWidget(int x, int y, int width, int height, Path path)
    {
        super(x, y, width, height);

        // InteractableWidget only dispatches these callbacks for widgets that
        // explicitly opt into the corresponding input categories.
        this.canReceiveMouseClicks = true;
        this.canReceiveMouseMoves = true;
        this.canReceiveMouseScrolls = true;
        this.path = path;
    }

    private void openFullscreen()
    {
        BaseScreen.openScreenWithParent(new PreviewFullscreenScreen(this.path));
    }

    private boolean isOverFullscreenButton(int mouseX, int mouseY)
    {
        int x = this.getFullscreenButtonX();
        int y = this.getY() + 2;
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private boolean isOverCopyButton(int mouseX, int mouseY)
    {
        int x = this.getCopyButtonX();
        int y = this.getY() + 2;
        return this.getWidth() > 36 && mouseX >= x && mouseX < x + BUTTON_SIZE &&
               mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private boolean isOverSaveButton(int mouseX, int mouseY)
    {
        int x = this.getSaveButtonX();
        int y = this.getY() + 2;
        return this.getWidth() > 54 && mouseX >= x && mouseX < x + BUTTON_SIZE &&
               mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private boolean isOverFreecamButton(int mouseX, int mouseY)
    {
        int x = this.getFreecamButtonX();
        int y = this.getY() + 2;
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private int getFullscreenButtonX()
    {
        return this.getRight() - 2 - BUTTON_SIZE;
    }

    private int getFreecamButtonX()
    {
        return this.getButtonX(1);
    }

    private int getCopyButtonX()
    {
        return this.getButtonX(2);
    }

    private int getSaveButtonX()
    {
        return this.getButtonX(3);
    }

    private int getButtonX(int indexFromRight)
    {
        return this.getRight() - 2 - BUTTON_SIZE - indexFromRight * (BUTTON_SIZE + 2);
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 0 && this.isOverSaveButton(mouseX, mouseY))
        {
            this.onSave();
            return true;
        }

        if (mouseButton == 0 && this.isOverCopyButton(mouseX, mouseY))
        {
            this.onCopy();
            return true;
        }

        if (mouseButton == 0 && this.isOverFullscreenButton(mouseX, mouseY))
        {
            this.openFullscreen();
            return true;
        }

        if (mouseButton == 0 && this.isOverFreecamButton(mouseX, mouseY))
        {
            this.freecam = !this.freecam;
            return true;
        }

        if (mouseButton == 0 || mouseButton == 1)
        {
            this.dragging = true;
            this.dragButton = mouseButton;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartYRot = this.yRot;
            this.dragStartXRot = this.xRot;
            this.dragStartTargetX = this.targetX;
            this.dragStartTargetY = this.targetY;
            this.dragStartTargetZ = this.targetZ;
            return true;
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onMouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        this.dragging = false;
        super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onMouseMoved(int mouseX, int mouseY)
    {
        if (this.dragging)
        {
            int dx = mouseX - this.dragStartMouseX;
            int dy = mouseY - this.dragStartMouseY;
            boolean rotating = (this.dragButton == 0) != this.freecam;

            if (rotating)
            {
                this.yRot = this.dragStartYRot + dx * 0.5f;
                this.xRot = MathHelper.clamp(this.dragStartXRot - dy * 0.5f, -90f, 90f);
            }
            else
            {
                // ponytail: pan uses a yaw-only right vector and world-up, ignoring pitch -
                // good enough for a preview pan, a full trackball basis isn't worth it here.
                double yawRad = Math.toRadians(this.dragStartYRot);
                double rightX = Math.cos(yawRad);
                double rightZ = Math.sin(yawRad);
                double panScale = this.distance / 300.0;

                this.targetX = this.dragStartTargetX - dx * rightX * panScale;
                this.targetZ = this.dragStartTargetZ - dx * rightZ * panScale;
                this.targetY = this.dragStartTargetY + dy * panScale;
            }

            return true;
        }

        return super.onMouseMoved(mouseX, mouseY);
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, double mouseWheelDelta, double mouseWheelDeltaVertical)
    {
        double factor = mouseWheelDelta < 0 ? 1.1 : (1.0 / 1.1);
        this.distance = MathHelper.clamp(this.distance * factor, MIN_DISTANCE, this.maxDistance);
        return true;
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        int width = this.getWidth();
        int height = this.getHeight();

        if (width <= 0 || height <= 0)
        {
            return;
        }

        CompletableFuture<Schematic> future = PreviewCache.getSchematic(this.path);

        if (future.isDone() == false)
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.loading", ctx);
            return;
        }

        Schematic schematic = future.getNow(null);

        if (schematic == null)
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.invalid", ctx);
            return;
        }

        // Ignores previewMaxVolume, but geometry sits in direct memory until upload.
        if (PreviewCache.getBlockCount(schematic) > Configs.Preview.PREVIEW_MAX_BLOCKS.getIntegerValue())
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.too_large", ctx);
            return;
        }

        PreviewRenderer renderer = PreviewCache.getRenderer(this.path, schematic);

        if (this.cameraInitialized == false)
        {
            Vec3d center = renderer.getCenter();
            this.targetX = center.x;
            this.targetY = center.y;
            this.targetZ = center.z;
            this.distance = renderer.getDefaultDistance(Configs.Preview.PREVIEW_FOV.getDoubleValue(), (double) width / height);
            this.maxDistance = this.distance * 8.0;
            this.yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
            this.xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
            this.cameraInitialized = true;
        }

        renderer.tick();

        if (renderer.hasFailed())
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.invalid", ctx);
            this.renderOverlayButtons(x, y, ctx);
            this.serviceCaptureRequest(renderer);
            return;
        }

        // The renderer builds the VBOs incrementally.  Until the upload is complete the FBO
        // contains only its clear color; showing that as a finished preview looks like a
        // window-size/render failure, especially for larger schematics.
        if (renderer.isTessellationDone() == false)
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.loading", ctx);
            this.renderOverlayButtons(x, y, ctx);
            this.serviceCaptureRequest(renderer);
            return;
        }

        this.drawSceneToFbo(width, height, renderer);
        PreviewRenderUtils.blitFramebuffer(this.fbo, x, y, width, height, z);
        this.renderOverlayButtons(x, y, ctx);
        this.serviceCaptureRequest(renderer);
    }

    /**
     * Resolves a pending {@link #requestCapture(Consumer)} right after this frame's
     * {@link #drawSceneToFbo}, so the capture sees the same GL state and tessellation progress
     * as the on-screen draw - a click handler runs during input processing, before either.
     */
    private void serviceCaptureRequest(PreviewRenderer renderer)
    {
        if (this.pendingCapture == null)
        {
            return;
        }

        Consumer<BufferedImage> callback = this.pendingCapture;
        this.pendingCapture = null;

        if (renderer.isTessellationDone() == false || this.fbo == null)
        {
            callback.accept(null);
            return;
        }

        BufferedImage image = renderer.captureImage(this.fbo.framebufferWidth, this.fbo.framebufferHeight,
                                                     Configs.Preview.PREVIEW_FOV.getDoubleValue(), this.yRot, this.xRot, this.distance,
                                                     this.targetX, this.targetY, this.targetZ, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue());
        callback.accept(image);
    }

    private void drawSceneToFbo(int width, int height, PreviewRenderer renderer)
    {
        int scale = new ScaledResolution(this.mc).getScaleFactor();
        int texWidth = Math.max(1, width * scale);
        int texHeight = Math.max(1, height * scale);

        if (this.fbo == null || this.fboScale != scale ||
            this.fbo.framebufferWidth != texWidth || this.fbo.framebufferHeight != texHeight)
        {
            if (this.fbo != null)
            {
                this.fbo.deleteFramebuffer();
            }

            this.fbo = new Framebuffer(texWidth, texHeight, true);
            this.fbo.setFramebufferFilter(GL11.GL_NEAREST);
            this.fboScale = scale;
        }

        this.fbo.bindFramebuffer(true);

        try
        {
            renderer.draw(texWidth, texHeight, Configs.Preview.PREVIEW_FOV.getDoubleValue(), this.yRot, this.xRot, this.distance,
                          this.targetX, this.targetY, this.targetZ, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue(), false);
        }
        finally
        {
            this.mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    private void renderOverlayButtons(int x, int y, ScreenContext ctx)
    {
        int barY = this.getY() + 2;
        this.renderOverlayButton(this.getFullscreenButtonX(), barY, SchematicPreviewIcons.FULLSCREEN,
                this.isOverFullscreenButton(ctx.mouseX, ctx.mouseY), ctx);
        this.renderOverlayButton(this.getFreecamButtonX(), barY, SchematicPreviewIcons.FREECAM,
                this.freecam || this.isOverFreecamButton(ctx.mouseX, ctx.mouseY), ctx);

        if (this.getWidth() > 3 * BUTTON_SIZE)
        {
            this.renderOverlayButton(this.getCopyButtonX(), barY, SchematicPreviewIcons.COPY,
                    this.isOverCopyButton(ctx.mouseX, ctx.mouseY), ctx);
        }

        if (this.getWidth() > 4 * BUTTON_SIZE + 7)
        {
            this.renderOverlayButton(this.getSaveButtonX(), barY, SchematicPreviewIcons.SAVE,
                    this.isOverSaveButton(ctx.mouseX, ctx.mouseY), ctx);
        }
    }

    private void renderOverlayButton(int x, int y, malilib.gui.icon.BaseIcon icon,
                                     boolean highlighted, ScreenContext ctx)
    {
        int color = highlighted ? 0xD0707070 : 0xB0202020;
        int variant = highlighted ? 2 : 1;
        int border = 0xFF999999;
        ShapeRenderUtils.renderRectangle(x, y, this.getZ() + 1f, BUTTON_SIZE, 1, border, ctx);
        ShapeRenderUtils.renderRectangle(x, y + BUTTON_SIZE - 1, this.getZ() + 1f, BUTTON_SIZE, 1, border, ctx);
        ShapeRenderUtils.renderRectangle(x, y + 1, this.getZ() + 1f, 1, BUTTON_SIZE - 2, border, ctx);
        ShapeRenderUtils.renderRectangle(x + BUTTON_SIZE - 1, y + 1, this.getZ() + 1f, 1, BUTTON_SIZE - 2, border, ctx);
        ShapeRenderUtils.renderRectangle(x + 1, y + 1, this.getZ() + 1f,
                BUTTON_SIZE - 2, BUTTON_SIZE - 2, color, ctx);
        icon.renderAt(x + 1, y + 1, this.getZ() + 2f, variant, ctx);
    }

    private void onSave()
    {
        this.requestCapture(image -> {
            if (image == null)
            {
                MessageDispatcher.warning().translate("schematicpreview.message.preview_not_ready");
                return;
            }

            java.io.File file = ScreenshotUtil.save(image, this.path);

            if (file != null)
            {
                MessageDispatcher.success().translate("schematicpreview.message.screenshot_saved", file.getName());
            }
            else
            {
                MessageDispatcher.error().translate("schematicpreview.message.screenshot_failed");
            }
        });
    }

    private void onCopy()
    {
        this.requestCapture(image -> {
            if (image == null)
            {
                MessageDispatcher.warning().translate("schematicpreview.message.preview_not_ready");
            }
            else if (ScreenshotUtil.copyToClipboard(image))
            {
                MessageDispatcher.success().translate("schematicpreview.message.image_copied");
            }
            else
            {
                MessageDispatcher.error().translate("schematicpreview.message.screenshot_failed");
            }
        });
    }

    /**
     * Asks for the current view as a transparent-background image. {@code callback} runs on the
     * next render frame, with {@code null} if nothing has rendered yet or tessellation is
     * still in progress.
     */
    public void requestCapture(Consumer<BufferedImage> callback)
    {
        if (this.cameraInitialized == false || this.fbo == null)
        {
            callback.accept(null);
            return;
        }

        this.pendingCapture = callback;
    }

    public void close()
    {
        if (this.fbo != null)
        {
            this.fbo.deleteFramebuffer();
            this.fbo = null;
        }

        this.fboScale = 0;
    }

    /**
     * Keeps the preview's camera and framebuffer in sync when its parent panel is resized.
     * The default distance depends on the widget aspect ratio, so retaining the old camera
     * after a large-window resize can leave the model outside the narrow side-panel frustum.
     */
    public void updatePreviewGeometry(int x, int y, int width, int height)
    {
        boolean sizeChanged = this.getWidth() != width || this.getHeight() != height;
        this.setPositionAndSize(x, y, width, height);

        if (sizeChanged)
        {
            this.cameraInitialized = false;

            if (this.fbo != null)
            {
                this.fbo.deleteFramebuffer();
                this.fbo = null;
            }

            this.fboScale = 0;
        }
    }
}
