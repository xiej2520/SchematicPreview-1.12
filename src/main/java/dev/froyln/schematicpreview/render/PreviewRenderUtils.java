package dev.froyln.schematicpreview.render;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;


public final class PreviewRenderUtils
{
    private PreviewRenderUtils()
    {
    }

    public static void blitFramebuffer(Framebuffer framebuffer, int x, int y, int width, int height)
    {
        blitFramebuffer(framebuffer, x, y, width, height, width, height);
    }

    public static void blitFramebuffer(Framebuffer framebuffer, int x, int y, int width, int height,
                                       int usedWidth, int usedHeight)
    {
        RenderSystem.enableTexture();
        // The preview framebuffer already has an opaque background. Blending it with the
        // screen would let the live world show through transparent schematic blocks.
        RenderSystem.disableBlend();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.bindTexture(framebuffer.colorAttachment);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
        float maxU = usedWidth / (float) framebuffer.textureWidth;
        float maxV = usedHeight / (float) framebuffer.textureHeight;
        buffer.vertex(x, y + height, 0).texture(0, 0).next();
        buffer.vertex(x + width, y + height, 0).texture(maxU, 0).next();
        buffer.vertex(x + width, y, 0).texture(maxU, maxV).next();
        buffer.vertex(x, y, 0).texture(0, maxV).next();
        tessellator.draw();
        RenderSystem.disableBlend();
    }

    public static void placeholder(int x, int y, int width, int height, String text)
    {
        com.mojang.blaze3d.systems.RenderSystem.disableTexture();
        com.mojang.blaze3d.systems.RenderSystem.color4f(0.02f, 0.02f, 0.02f, 0.65f);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION);
        buffer.vertex(x, y + height, 0).next();
        buffer.vertex(x + width, y + height, 0).next();
        buffer.vertex(x + width, y, 0).next();
        buffer.vertex(x, y, 0).next();
        tessellator.draw();
        RenderSystem.enableTexture();
        MinecraftClient.getInstance().textRenderer.drawWithShadow(text, x + 4, y + 4, 0xFFFFFFFF);
    }
}
