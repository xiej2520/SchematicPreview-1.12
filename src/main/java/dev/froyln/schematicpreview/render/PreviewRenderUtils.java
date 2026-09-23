package dev.froyln.schematicpreview.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;

import malilib.gui.util.ScreenContext;
import malilib.render.ShapeRenderUtils;
import malilib.render.text.StyledTextLine;
import malilib.render.text.TextRenderer;
import malilib.util.StringUtils;

/** FBO-blit and placeholder helpers shared by {@code PreviewWidget} and {@code PreviewCache}. */
public final class PreviewRenderUtils
{
    private PreviewRenderUtils()
    {
    }

    /** Blits the full extent of {@code fbo}'s color texture at {@code (x, y, width, height)}. */
    public static void blitFramebuffer(Framebuffer fbo, int x, int y, int width, int height, float z)
    {
        blitFramebuffer(fbo, x, y, width, height, fbo.framebufferWidth, fbo.framebufferHeight, z);
    }

    /**
     * Blits only the {@code (usedWidth, usedHeight)} sub-rectangle of {@code fbo} that was
     * rendered into - for the shared grow-only FBO, which can be larger than the viewport.
     */
    public static void blitFramebuffer(Framebuffer fbo, int x, int y, int width, int height, int usedWidth, int usedHeight, float z)
    {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(fbo.framebufferTexture);

        double maxU = usedWidth / (double) fbo.framebufferWidth;
        double maxV = usedHeight / (double) fbo.framebufferHeight;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // A failed preview draw can interrupt the previous blit after begin() but before
        // Tessellator.draw() finishes. Discard that partial batch before starting the next one;
        // otherwise malilib's next GUI batch fails with "Already building" as well.
        try
        {
            buffer.finishDrawing();
        }
        catch (IllegalStateException ignored)
        {
            // The normal case: no batch was left open.
        }

        try
        {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(x, y + height, z).tex(0.0, 0.0).endVertex();
            buffer.pos(x + width, y + height, z).tex(maxU, 0.0).endVertex();
            buffer.pos(x + width, y, z).tex(maxU, maxV).endVertex();
            buffer.pos(x, y, z).tex(0.0, maxV).endVertex();
            tessellator.draw();
        }
        finally
        {
            try
            {
                buffer.finishDrawing();
            }
            catch (IllegalStateException ignored)
            {
                // Tessellator.draw() normally closes the batch; this is only cleanup after a
                // failure or a renderer that already finished it.
            }
        }

        GlStateManager.disableBlend();
    }

    public static void renderPlaceholder(int x, int y, int width, int height, float z, String translationKey, ScreenContext ctx)
    {
        ShapeRenderUtils.renderRectangle(x, y, z, width, height, 0x80000000, ctx);
        String text = StringUtils.translate(translationKey);
        TextRenderer textRenderer = TextRenderer.INSTANCE;
        int textX = x + Math.max(0, (width - textRenderer.getRenderWidth(text)) / 2);
        int textY = y + Math.max(0, (height - textRenderer.getFontHeight()) / 2);
        textRenderer.renderLine(textX, textY, z + 1f, 0xFFFFFFFF, true, StyledTextLine.parseFirstLine(text), ctx);
    }
}
