package dev.froyln.schematicpreview.gui;

import java.nio.file.Path;

import malilib.gui.BaseScreen;
import malilib.gui.util.ScreenContext;
import malilib.input.Keys;
import malilib.render.text.StyledTextLine;
import malilib.render.text.TextRenderer;
import malilib.util.StringUtils;

/**
 * Fullscreen view of a schematic preview. Asks {@code PreviewCache} for the same path's
 * {@code PreviewRenderer} the side panel used, so entering/leaving fullscreen never
 * re-tessellates.
 */
public class PreviewFullscreenScreen extends BaseScreen
{
    private static final int TOP_MARGIN = 16;

    private final Path path;
    private PreviewWidget widget;

    public PreviewFullscreenScreen(Path path)
    {
        this.path = path;

        // Solid backdrop instead of malilib's translucent default.
        this.backgroundColor = 0xFF000000;
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.widget = this.addWidget(new PreviewWidget(this.getX(), this.getY() + TOP_MARGIN,
                                                        this.getScreenWidth(), this.getScreenHeight() - TOP_MARGIN, this.path));
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        if (this.widget != null)
        {
            this.widget.setPositionAndSize(this.getX(), this.getY() + TOP_MARGIN,
                                           this.getScreenWidth(), this.getScreenHeight() - TOP_MARGIN);
        }

    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers)
    {
        if (this.widget != null)
        {
            if (keyCode == Keys.KEY_R)
            {
                this.widget.resetCamera();
                return true;
            }

            if (keyCode == Keys.KEY_SPACE)
            {
                this.widget.toggleAutoRotate();
                return true;
            }

            if (keyCode == Keys.KEY_T)
            {
                this.widget.toggleTileEntities();
                return true;
            }
        }

        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderCustomContents(ScreenContext ctx)
    {
        super.renderCustomContents(ctx);

        // Right-aligned in the top bar; skipped when it would run into the screen title.
        TextRenderer textRenderer = TextRenderer.INSTANCE;
        StyledTextLine hint = StyledTextLine.parseFirstLine(StringUtils.translate("schematicpreview.label.fullscreen.hints"));
        int hintX = this.getX() + this.getScreenWidth() - hint.renderWidth - 8;
        int titleRight = this.getX() + this.titleX + (this.titleText != null ? this.titleText.renderWidth : 0);

        if (hintX > titleRight + 8)
        {
            textRenderer.renderLine(hintX, this.getY() + this.titleY, this.z + 1f, 0xFFB0B0B0, true, hint, ctx);
        }
    }

    @Override
    public void onGuiClosed()
    {
        if (this.widget != null)
        {
            this.widget.close();
        }

        super.onGuiClosed();
    }
}
