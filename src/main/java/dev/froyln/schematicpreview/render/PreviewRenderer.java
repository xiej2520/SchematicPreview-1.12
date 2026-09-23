package dev.froyln.schematicpreview.render;

import java.util.HashMap;
import java.util.Map;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.BufferUtils;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import dev.froyln.schematicpreview.config.Configs;

/** Client-thread tessellation and drawing for one schematic. */
public class PreviewRenderer
{
    private static final Logger LOGGER = LogManager.getLogger("SchematicPreview");
    private static final int TESSELLATE_BUDGET_PER_TICK = 4096;
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    private final Map<RenderLayer, BufferBuilder> buildingBuffers = new HashMap<>();
    private final Map<RenderLayer, VertexBuffer> vbos = new HashMap<>();
    private SchematicBlockAccess access;
    private WorldRendererSchematic renderer;
    private long cursor;
    private long totalVolume;
    private boolean tessellationDone;
    private boolean failed;

    public void setup(LitematicaSchematic schematic)
    {
        this.access = new SchematicBlockAccess(schematic);
        this.renderer = new WorldRendererSchematic(MinecraftClient.getInstance());
        Vec3i size = this.access.getBoxSize();
        this.totalVolume = (long) size.getX() * size.getY() * size.getZ();
        this.cursor = 0;
        this.tessellationDone = this.totalVolume <= 0;
        this.failed = false;
    }

    public boolean isTessellationDone()
    {
        return this.tessellationDone;
    }

    public boolean hasFailed()
    {
        return this.failed;
    }

    public Vec3d getCenter()
    {
        Vec3i size = this.access.getBoxSize();
        BlockPos min = this.access.getBoxMin();
        return new Vec3d(min.getX() + size.getX() / 2.0, min.getY() + size.getY() / 2.0, min.getZ() + size.getZ() / 2.0);
    }

    public double getDefaultDistance(double fovY, double aspect)
    {
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());
        double halfFovY = Math.toRadians(fovY) / 2.0;
        double halfFovX = Math.atan(Math.tan(halfFovY) * aspect);
        double distanceY = diagonal / 2.0 / Math.sin(halfFovY);
        double distanceX = diagonal / 2.0 / Math.sin(halfFovX);
        return Math.max(3.0, Math.max(distanceX, distanceY) * 1.1);
    }

    public void tick()
    {
        if (this.tessellationDone || this.failed)
        {
            return;
        }

        try
        {
            this.tickInternal();
        }
        catch (Throwable t)
        {
            this.failed = true;
            this.tessellationDone = true;
            this.discardBuildingBuffers();
            LOGGER.warn("Could not tessellate schematic preview", t);
        }
    }

    private void tickInternal()
    {
        if (this.tessellationDone)
        {
            return;
        }

        Vec3i size = this.access.getBoxSize();
        BlockPos min = this.access.getBoxMin();
        long limit = Math.min(this.totalVolume, this.cursor + TESSELLATE_BUDGET_PER_TICK);

        while (this.cursor < limit)
        {
            long index = this.cursor++;
            int x = (int) (index % size.getX());
            index /= size.getX();
            int y = (int) (index % size.getY());
            int z = (int) (index / size.getY());
            BlockPos pos = min.add(x, y, z);
            BlockState state = this.access.getBlockState(pos);
            this.access.setPreviewBlockState(pos, state);

            if (state.isAir() || state.getRenderType() != net.minecraft.block.BlockRenderType.MODEL)
            {
                continue;
            }

            RenderLayer layer = RenderLayers.getBlockLayer(state);
            BufferBuilder buffer = this.buildingBuffers.get(layer);

            if (buffer == null)
            {
                VertexFormat format = layer.getVertexFormat();
                buffer = new BufferBuilder(Math.max(256, layer.getExpectedBufferSize()));
                buffer.begin(GL11.GL_QUADS, format);
                this.buildingBuffers.put(layer, buffer);
            }

            try
            {
                // WorldRendererSchematic expects the matrix to already be translated to the
                // block's position.  The normal chunk renderer supplies this per-block
                // translation before calling renderBlock(); using an identity matrix here
                // collapses every model onto the origin of the preview.
                MatrixStack matrices = new MatrixStack();
                matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                this.renderer.renderBlock(this.access, state, pos, matrices, buffer);
            }
            catch (Throwable ignored)
            {
                // A malformed/custom block model must not make the schematic browser unusable.
            }
        }

        if (this.cursor >= this.totalVolume)
        {
            this.access.attachBlockEntities();
            this.upload();
            this.tessellationDone = true;
        }
    }

    private void discardBuildingBuffers()
    {
        for (BufferBuilder buffer : this.buildingBuffers.values())
        {
            buffer.clear();
        }

        this.buildingBuffers.clear();
    }

    private void upload()
    {
        for (Map.Entry<RenderLayer, BufferBuilder> entry : this.buildingBuffers.entrySet())
        {
            BufferBuilder buffer = entry.getValue();
            buffer.end();
            VertexBuffer vbo = new VertexBuffer(entry.getKey().getVertexFormat());
            vbo.upload(buffer);
            this.vbos.put(entry.getKey(), vbo);
        }

        this.buildingBuffers.clear();
    }

    public void draw(int width, int height, double fov, float yRot, float xRot, double distance,
                     double targetX, double targetY, double targetZ)
    {
        if (width <= 0 || height <= 0)
        {
            return;
        }

        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());
        RenderSystem.viewport(0, 0, width, height);
        RenderSystem.clearColor(0.03f, 0.03f, 0.03f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.defaultAlphaFunc();
        RenderSystem.color4f(1f, 1f, 1f, 1f);

        RenderSystem.matrixMode(GL11.GL_PROJECTION);
        RenderSystem.pushMatrix();
        RenderSystem.loadIdentity();
        double near = 0.05;
        double far = Math.max(16.0, diagonal * 4.0);
        double top = Math.tan(Math.toRadians(fov) * 0.5) * near;
        double right = top * width / (double) height;
        GL11.glFrustum(-right, right, -top, top, near, far);

        RenderSystem.matrixMode(GL11.GL_MODELVIEW);
        RenderSystem.pushMatrix();
        RenderSystem.loadIdentity();

        // VertexBuffer.draw() resets the OpenGL model-view matrix before multiplying the
        // supplied matrix.  Keep the complete camera transform in this MatrixStack; applying
        // distance/rotation through RenderSystem here would otherwise be discarded for every
        // VBO draw, leaving the preview zoomed into a corner and making camera controls appear
        // ineffective.
        MatrixStack matrices = new MatrixStack();
        matrices.translate(0.0, 0.0, -distance);
        matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(xRot));
        matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(yRot));
        matrices.translate(-targetX, -targetY, -targetZ);
        LightmapTextureManager lightmap = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager();
        lightmap.enable();
        MinecraftClient.getInstance().getTextureManager().bindTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX);

        this.drawLayer(RenderLayer.getSolid(), matrices);
        this.drawLayer(RenderLayer.getCutoutMipped(), matrices);
        this.drawLayer(RenderLayer.getCutout(), matrices);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        this.drawLayer(RenderLayer.getTranslucent(), matrices);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        if (Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue())
        {
            this.drawBlockEntities(matrices);
        }

        lightmap.disable();

        RenderSystem.matrixMode(GL11.GL_PROJECTION);
        RenderSystem.popMatrix();
        RenderSystem.matrixMode(GL11.GL_MODELVIEW);
        RenderSystem.popMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableAlphaTest();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    private void drawBlockEntities(MatrixStack matrices)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockEntityRenderDispatcher dispatcher = BlockEntityRenderDispatcher.INSTANCE;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        for (BlockEntity blockEntity : this.access.getBlockEntities())
        {
            matrices.push();

            try
            {
                BlockPos pos = blockEntity.getPos();
                matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                dispatcher.renderEntity(blockEntity, matrices, consumers, FULL_BRIGHT_LIGHT,
                                        net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
            }
            catch (Throwable ignored)
            {
                // A block entity renderer may depend on state unavailable in the preview view.
                // Keep that one entity from aborting the whole screen render.
            }
            finally
            {
                matrices.pop();
            }
        }

        consumers.draw();
    }

    private void drawLayer(RenderLayer layer, MatrixStack matrices)
    {
        VertexBuffer vbo = this.vbos.get(layer);

        if (vbo == null)
        {
            return;
        }

        layer.startDrawing();
        vbo.bind();
        layer.getVertexFormat().startDrawing(0L);
        vbo.draw(matrices.peek().getModel(), layer.getDrawMode());
        VertexBuffer.unbind();
        layer.getVertexFormat().endDrawing();
        layer.endDrawing();
    }

    /** Captures the same camera view used by the fullscreen preview into an ARGB image. */
    public BufferedImage captureImage(int width, int height, double fov, float yRot, float xRot,
                                      double distance, double targetX, double targetY, double targetZ)
    {
        if (width <= 0 || height <= 0 || this.tessellationDone == false)
        {
            return null;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer framebuffer = new Framebuffer(width, height, true, true);

        try
        {
            framebuffer.beginWrite(true);
            this.draw(width, height, fov, yRot, xRot, distance, targetX, targetY, targetZ);

            RenderSystem.bindTexture(framebuffer.colorAttachment);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            IntBuffer pixelBuffer = BufferUtils.createIntBuffer(width * height);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);

            int[] pixels = new int[width * height];
            pixelBuffer.get(pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int row = 0; row < height; ++row)
            {
                image.setRGB(0, row, width, 1, pixels, (height - row - 1) * width, width);
            }

            return image;
        }
        finally
        {
            mc.getFramebuffer().beginWrite(true);
            framebuffer.delete();
        }
    }

    public void close()
    {
        this.access.close();

        for (BufferBuilder buffer : this.buildingBuffers.values())
        {
            buffer.clear();
        }

        this.buildingBuffers.clear();

        for (VertexBuffer vbo : this.vbos.values())
        {
            vbo.close();
        }

        this.vbos.clear();
    }
}
