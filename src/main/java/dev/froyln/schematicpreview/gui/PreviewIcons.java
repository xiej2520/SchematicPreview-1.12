package dev.froyln.schematicpreview.gui;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import fi.dy.masa.malilib.render.RenderUtils;

import dev.froyln.schematicpreview.Reference;

/** Pixel-art controls used by the preview browser and fullscreen screen. */
public enum PreviewIcons
{
    FULLSCREEN(0),
    FREECAM(1),
    SAVE(2),
    COPY(3),
    CLOSE(-1);

    private static final Identifier TEXTURE = new Identifier(Reference.MOD_ID, "textures/gui/icons.png");
    private final int row;

    PreviewIcons(int row)
    {
        this.row = row;
    }

    public void renderAt(int x, int y, boolean hovered)
    {
        if (this == CLOSE)
        {
            int color = hovered ? 0xFFFFFFA0 : 0xFFE0E0E0;

            for (int i = 0; i < 5; ++i)
            {
                RenderUtils.drawRect(x + 1 + i * 2, y + 1 + i * 2, 2, 2, color);
                RenderUtils.drawRect(x + 9 - i * 2, y + 1 + i * 2, 2, 2, color);
            }

            return;
        }

        int u = hovered ? 24 : 12;
        int v = this.row * 12;
        float pixelX = 1.0f / 64.0f;
        float pixelY = 1.0f / 64.0f;

        RenderUtils.bindTexture(TEXTURE);
        RenderSystem.enableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1f, 1f, 1f, 1f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(x, y + 12, 0).texture(u * pixelX, (v + 12) * pixelY).next();
        buffer.vertex(x + 12, y + 12, 0).texture((u + 12) * pixelX, (v + 12) * pixelY).next();
        buffer.vertex(x + 12, y, 0).texture((u + 12) * pixelX, v * pixelY).next();
        buffer.vertex(x, y, 0).texture(u * pixelX, v * pixelY).next();
        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }
}
