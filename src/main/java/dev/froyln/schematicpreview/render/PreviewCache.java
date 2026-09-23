package dev.froyln.schematicpreview.render;

import java.io.File;
import java.io.FileFilter;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import dev.froyln.schematicpreview.config.Configs;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/** Shared schematic loads, renderers, and the small preview framebuffer. */
public final class PreviewCache
{
    private static final Logger LOGGER = LogManager.getLogger("SchematicPreview");

    private static final Executor LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SchematicPreview-Loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<File, CompletableFuture<LitematicaSchematic>> SCHEMATICS = new HashMap<>();
    private static final Map<File, PreviewRenderer> RENDERERS = new HashMap<>();
    @Nullable private static Framebuffer smallFramebuffer;

    private PreviewCache()
    {
    }

    public static CompletableFuture<LitematicaSchematic> getSchematic(File file)
    {
        File key = file.getAbsoluteFile();
        return SCHEMATICS.computeIfAbsent(key, PreviewCache::load);
    }

    private static CompletableFuture<LitematicaSchematic> load(File file)
    {
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                return LitematicaSchematic.createFromFile(file.getParentFile(), file.getName());
            }
            catch (Throwable ignored)
            {
                return null;
            }
        }, LOADER);
    }

    public static PreviewRenderer getRenderer(File file, LitematicaSchematic schematic)
    {
        File key = file.getAbsoluteFile();
        return RENDERERS.computeIfAbsent(key, ignored -> {
            PreviewRenderer renderer = new PreviewRenderer();
            renderer.setup(schematic);
            return renderer;
        });
    }

    /** Returns the first litematic file in a directory, for directory-entry previews. */
    @Nullable
    public static File getFirstSchematicIn(File directory, @Nullable FileFilter filter)
    {
        File[] files = directory.listFiles(file -> file.isFile() &&
                (filter == null || filter.accept(file)));

        if (files == null || files.length == 0)
        {
            return null;
        }

        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }

    /**
     * Returns the number of blocks represented by the schematic.  Normal litematic files carry
     * this value in Metadata; the container walk is only a fallback for files with an absent or
     * stale metadata value.
     */
    public static long getBlockCount(LitematicaSchematic schematic)
    {
        int metadataCount = schematic.getMetadata().getTotalBlocks();

        if (metadataCount > 0)
        {
            return metadataCount;
        }

        long count = 0;

        for (String name : schematic.getAreaPositions().keySet())
        {
            fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer container =
                    schematic.getSubRegionContainer(name);

            if (container == null)
            {
                continue;
            }

            Vec3i size = container.getSize();

            for (int y = 0; y < size.getY(); ++y)
            {
                for (int z = 0; z < size.getZ(); ++z)
                {
                    for (int x = 0; x < size.getX(); ++x)
                    {
                        if (container.get(x, y, z).isAir() == false)
                        {
                            ++count;
                        }
                    }
                }
            }
        }

        return count;
    }

    public static boolean renderSmallPreview(File file, int x, int y, int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return false;
        }

        CompletableFuture<LitematicaSchematic> future = getSchematic(file);

        if (future.isDone() == false)
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Loading...");
            return true;
        }

        LitematicaSchematic schematic = future.getNow(null);

        if (schematic == null)
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Invalid schematic");
            return true;
        }

        if (schematic.getMetadata().getTotalVolume() > Configs.Preview.PREVIEW_MAX_VOLUME.getIntegerValue())
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Schematic too large");
            return true;
        }

        PreviewRenderer renderer = getRenderer(file, schematic);
        renderer.tick();

        if (renderer.hasFailed())
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Invalid preview");
            return true;
        }

        double distance = renderer.getDefaultDistance(Configs.Preview.PREVIEW_FOV.getDoubleValue(), (double) width / height);
        Vec3d center = renderer.getCenter();
        renderPreview(file, x, y, width, height,
                      (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue(),
                      (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue(), distance,
                      center.x, center.y, center.z);
        return true;
    }

    public static boolean renderPreview(File file, int x, int y, int width, int height,
                                        float yRot, float xRot, double distance,
                                        double targetX, double targetY, double targetZ)
    {
        CompletableFuture<LitematicaSchematic> future = getSchematic(file);

        if (future.isDone() == false)
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Loading...");
            return true;
        }

        LitematicaSchematic schematic = future.getNow(null);

        if (schematic == null)
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Invalid schematic");
            return true;
        }

        if (getBlockCount(schematic) > Configs.Preview.PREVIEW_MAX_BLOCKS.getIntegerValue())
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Too many blocks");
            return true;
        }

        PreviewRenderer renderer = getRenderer(file, schematic);
        renderer.tick();

        if (renderer.hasFailed())
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Invalid preview");
            return true;
        }

        if (renderer.isTessellationDone() == false)
        {
            PreviewRenderUtils.placeholder(x, y, width, height, "Loading preview...");
            return true;
        }

        int scale = (int) Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor());
        int framebufferWidth = Math.max(1, width * scale);
        int framebufferHeight = Math.max(1, height * scale);
        ensureFramebuffer(framebufferWidth, framebufferHeight);
        try
        {
            smallFramebuffer.beginWrite(true);
            renderer.draw(framebufferWidth, framebufferHeight, Configs.Preview.PREVIEW_FOV.getDoubleValue(), yRot, xRot,
                          distance, targetX, targetY, targetZ);
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            PreviewRenderUtils.blitFramebuffer(smallFramebuffer, x, y, width, height,
                                               framebufferWidth, framebufferHeight);
        }
        catch (Throwable t)
        {
            LOGGER.warn("Could not draw schematic preview for " + file, t);
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            PreviewRenderUtils.placeholder(x, y, width, height, "Invalid preview");
        }
        finally
        {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        }
        return true;
    }

    @Nullable
    public static BufferedImage capturePreview(File file, int width, int height,
                                               float yRot, float xRot, double distance,
                                               double targetX, double targetY, double targetZ)
    {
        CompletableFuture<LitematicaSchematic> future = getSchematic(file);

        if (future.isDone() == false)
        {
            return null;
        }

        LitematicaSchematic schematic = future.getNow(null);

        if (schematic == null || getBlockCount(schematic) > Configs.Preview.PREVIEW_MAX_BLOCKS.getIntegerValue())
        {
            return null;
        }

        PreviewRenderer renderer = getRenderer(file, schematic);
        renderer.tick();

        int scale = (int) Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor());
        int framebufferWidth = Math.max(1, width * scale);
        int framebufferHeight = Math.max(1, height * scale);

        return renderer.captureImage(framebufferWidth, framebufferHeight, Configs.Preview.PREVIEW_FOV.getDoubleValue(),
                                     yRot, xRot, distance, targetX, targetY, targetZ);
    }

    private static void ensureFramebuffer(int width, int height)
    {
        if (smallFramebuffer == null)
        {
            smallFramebuffer = new Framebuffer(width, height, true, true);
        }
        else if (smallFramebuffer.textureWidth < width || smallFramebuffer.textureHeight < height)
        {
            smallFramebuffer.resize(Math.max(width, smallFramebuffer.textureWidth),
                                    Math.max(height, smallFramebuffer.textureHeight), true);
        }

        smallFramebuffer.setTexFilter(9728);
    }

    public static void invalidate(File file)
    {
        File key = file.getAbsoluteFile();
        SCHEMATICS.remove(key);
        PreviewRenderer renderer = RENDERERS.remove(key);

        if (renderer != null)
        {
            renderer.close();
        }
    }

    public static void invalidateDirectory(File directory)
    {
        String prefix = directory.getAbsoluteFile().toPath().normalize().toString();

        SCHEMATICS.keySet().removeIf(file -> file.getParentFile() != null &&
                file.getParentFile().getAbsolutePath().startsWith(prefix));
        RENDERERS.entrySet().removeIf(entry -> {
            File file = entry.getKey();
            boolean matches = file.getParentFile() != null &&
                    file.getParentFile().getAbsolutePath().startsWith(prefix);

            if (matches)
            {
                entry.getValue().close();
            }

            return matches;
        });
    }

    public static void close()
    {
        for (PreviewRenderer renderer : RENDERERS.values())
        {
            renderer.close();
        }

        RENDERERS.clear();
        SCHEMATICS.clear();

        if (smallFramebuffer != null)
        {
            smallFramebuffer.delete();
            smallFramebuffer = null;
        }
    }

    public static void tickClose(MinecraftClient mc)
    {
        if (mc.currentScreen == null && (SCHEMATICS.isEmpty() == false || RENDERERS.isEmpty() == false || smallFramebuffer != null))
        {
            close();
        }
    }
}
