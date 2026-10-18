package dev.froyln.schematicpreview.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;

import dev.froyln.schematicpreview.config.Configs;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicRegion;

/**
 * Single owner of the off-thread schematic loads and the tessellated {@link PreviewRenderer}s
 * for whatever schematic path(s) are currently on screen. Freed entirely once no screen is
 * open (see {@link #tickClose()}), so GL resources never outlive the GUI that requested them.
 */
public final class PreviewCache
{
    private static final Logger LOGGER = LogManager.getLogger("SchematicPreview");
    private static final int MAX_CACHED_PATHS = 48;

    // A queued load whose path was not requested for this long is dropped when the worker reaches it.
    private static final long STALE_LOAD_NANOS = TimeUnit.SECONDS.toNanos(1);

    // Newest request first: after fast scrolling the visible rows are the most recently requested.
    private static final BlockingDeque<Runnable> QUEUE = new LinkedBlockingDeque<>();
    private static final Executor LOADER = QUEUE::addFirst;

    static
    {
        Thread thread = new Thread(() -> {
            while (true)
            {
                try
                {
                    QUEUE.takeFirst().run();
                }
                catch (InterruptedException e)
                {
                    return;
                }
                catch (Throwable t)
                {
                    LOGGER.warn("Schematic loader task failed", t);
                }
            }
        }, "SchematicPreview-Loader");
        thread.setDaemon(true);
        thread.start();
    }

    private static final Map<Path, Entry> SCHEMATICS = new HashMap<>();
    // Paths whose load failure was already logged, so rebuilt rows do not repeat the warning.
    private static final Set<Path> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Map<Path, PreviewRenderer> RENDERERS = new HashMap<>();
    // Least recently used paths first. The value is the frame the path was last used in; paths
    // used in the current frame are never evicted (they may be mid-draw).
    private static final Map<Path, Integer> USAGE = new LinkedHashMap<>(64, 0.75f, true);
    private static int frame;

    // Directory -> sorted schematic candidates; malilib rebuilds entry widgets on every scroll.
    // Keeping all candidates lets a broken/unsupported first file fall through to the next one.
    private static final Map<Path, List<Path>> SCHEMATIC_FILES = new HashMap<>();

    // One texture per list/tile preview, rendered once and blitted afterwards.
    private static final Map<Path, Snapshot> SNAPSHOTS = new HashMap<>();
    // Frame in which the side panel / fullscreen preview last used the path's renderer.
    private static final Map<Path, Integer> LARGE_USE = new HashMap<>();

    private PreviewCache()
    {
    }

    private static final class Entry
    {
        private CompletableFuture<Schematic> future;
        private volatile long lastRequested;
        // Dropped from the queue unstarted because the path was no longer being requested.
        private volatile boolean cancelled;
    }

    /** Marks {@code file} as used this frame and evicts the least recently used paths over the limit. */
    private static void touch(Path file)
    {
        USAGE.put(file, frame);

        if (USAGE.size() <= MAX_CACHED_PATHS)
        {
            return;
        }

        Iterator<Map.Entry<Path, Integer>> iterator = USAGE.entrySet().iterator();

        while (USAGE.size() > MAX_CACHED_PATHS && iterator.hasNext())
        {
            Map.Entry<Path, Integer> entry = iterator.next();

            // Ordered by last use, so everything after this is from the current frame as well.
            if (entry.getValue() == frame)
            {
                break;
            }

            iterator.remove();
            release(entry.getKey());
        }
    }

    /** Frees everything cached for {@code file} except its size info and directory candidates. */
    private static void release(Path file)
    {
        SCHEMATICS.remove(file);
        LARGE_USE.remove(file);

        Snapshot snapshot = SNAPSHOTS.remove(file);

        if (snapshot != null)
        {
            snapshot.close();
        }

        PreviewRenderer renderer = RENDERERS.remove(file);

        if (renderer != null)
        {
            renderer.close();
        }
    }

    public static CompletableFuture<Schematic> getSchematic(Path file)
    {
        touch(file);
        Entry entry = SCHEMATICS.get(file);

        if (entry == null || entry.cancelled)
        {
            entry = new Entry();
            entry.lastRequested = System.nanoTime();
            entry.future = load(file, entry);
            SCHEMATICS.put(file, entry);
        }

        entry.lastRequested = System.nanoTime();
        return entry.future;
    }

    private static CompletableFuture<Schematic> load(Path file, Entry entry)
    {
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                if (System.nanoTime() - entry.lastRequested > STALE_LOAD_NANOS)
                {
                    entry.cancelled = true;
                    return null;
                }

                LoadedSchematic loaded = LoadedSchematic.tryLoadSchematic(file).orElse(null);
                Schematic schematic = loaded != null ? loaded.schematic : null;

                if (schematic != null)
                {
                    // Warms Litematica's cached per-container block counts off-thread.
                    getBlockCount(schematic);
                }

                return schematic;
            }
            catch (Throwable t)
            {
                if (LOGGED_FAILURES.add(file))
                {
                    LOGGER.warn("Could not load schematic " + file, t);
                }

                return null;
            }
        }, LOADER);
    }

    /** The renderer for the side panel / fullscreen preview; it is kept alive while those use it. */
    public static PreviewRenderer getRenderer(Path file, Schematic schematic)
    {
        LARGE_USE.put(file, frame);
        return getOrCreateRenderer(file, schematic);
    }

    private static PreviewRenderer getOrCreateRenderer(Path file, Schematic schematic)
    {
        touch(file);

        return RENDERERS.computeIfAbsent(file, p -> {
            PreviewRenderer renderer = new PreviewRenderer();
            renderer.setup(schematic);
            return renderer;
        });
    }

    /**
     * All regular files in {@code directory} (sorted by path) accepted by {@code filter};
     * cached until {@link #close()}.
     */
    public static List<Path> getSchematicFilesIn(Path directory, Predicate<Path> filter)
    {
        return SCHEMATIC_FILES.computeIfAbsent(directory, dir -> {
            try (Stream<Path> stream = Files.list(dir))
            {
                return Collections.unmodifiableList(stream.filter(Files::isRegularFile)
                                                       .filter(filter)
                                                       .sorted()
                                                       .collect(Collectors.toList()));
            }
            catch (IOException ignore)
            {
                return Collections.emptyList();
            }
        });
    }

    public enum SmallPreviewResult
    {
        RENDERED,
        PENDING,
        UNAVAILABLE
    }

    /** Non-air block count from the containers; the file's own {@code TotalBlocks} tag is untrusted. */
    public static long getBlockCount(Schematic schematic)
    {
        long count = 0;

        for (SchematicRegion region : schematic.getRegions().values())
        {
            count += region.getBlockContainer().getTotalBlockCount();
        }

        return count;
    }

    /**
     * Renders a small fixed-angle preview of {@code file} into the given rectangle, gated by
     * {@code PREVIEW_MAX_VOLUME}. Returns {@code false} (draws nothing) while loading, on parse
     * failure, or over the cap - callers keep their own fallback icon underneath.
     */
    public static boolean renderSmallPreview(Path file, int x, int y, int width, int height, float z)
    {
        return renderSmallPreviewResult(file, x, y, width, height, z) == SmallPreviewResult.RENDERED;
    }

    /** Reports whether a preview is loading or cannot be rendered, in addition to success. */
    public static SmallPreviewResult renderSmallPreviewResult(Path file, int x, int y, int width, int height, float z)
    {
        if (width <= 0 || height <= 0)
        {
            return SmallPreviewResult.UNAVAILABLE;
        }

        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        int renderWidth = Math.max(1, width * scale);
        int renderHeight = Math.max(1, height * scale);
        float yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
        float xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
        double fov = Configs.Preview.PREVIEW_FOV.getDoubleValue();
        boolean tileEntities = Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue();

        Snapshot snapshot = SNAPSHOTS.get(file);
        SmallPreviewResult result = SmallPreviewResult.RENDERED;

        if (snapshot != null)
        {
            touch(file);
        }

        if (snapshot == null || snapshot.matches(renderWidth, renderHeight, fov, yRot, xRot, tileEntities) == false)
        {
            result = updateSnapshot(file, renderWidth, renderHeight, fov, yRot, xRot, tileEntities);
            snapshot = SNAPSHOTS.get(file);
        }

        // A stale snapshot is better than a blank row while the new one is being built.
        if (snapshot == null)
        {
            return result;
        }

        PreviewRenderUtils.blitFramebuffer(snapshot.fbo, x, y, width, height, z);
        return SmallPreviewResult.RENDERED;
    }

    /** Renders {@code file} once into its own texture; returns RENDERED when the snapshot is up to date. */
    private static SmallPreviewResult updateSnapshot(Path file, int renderWidth, int renderHeight, double fov,
                                                     float yRot, float xRot, boolean tileEntities)
    {
        CompletableFuture<Schematic> future = getSchematic(file);

        if (future.isDone() == false)
        {
            return SmallPreviewResult.PENDING;
        }

        Schematic schematic = future.getNow(null);

        if (schematic == null || schematic.getMetadata().getTotalVolume() > Configs.Preview.PREVIEW_MAX_VOLUME.getIntegerValue())
        {
            return SmallPreviewResult.UNAVAILABLE;
        }

        try
        {
            PreviewRenderer renderer = getOrCreateRenderer(file, schematic);

            renderer.tick();

            if (renderer.isTessellationDone() == false || renderer.hasFailed())
            {
                return renderer.hasFailed() ? SmallPreviewResult.UNAVAILABLE : SmallPreviewResult.PENDING;
            }

            Snapshot snapshot = SNAPSHOTS.get(file);

            if (snapshot == null || snapshot.fbo.framebufferWidth != renderWidth || snapshot.fbo.framebufferHeight != renderHeight)
            {
                if (snapshot != null)
                {
                    snapshot.close();
                }

                Framebuffer fbo = new Framebuffer(renderWidth, renderHeight, true);
                fbo.setFramebufferFilter(GL11.GL_NEAREST);
                snapshot = new Snapshot(fbo);
                SNAPSHOTS.put(file, snapshot);
            }

            snapshot.fbo.bindFramebuffer(true);

            Vec3d center = renderer.getCenter();
            renderer.draw(renderWidth, renderHeight, fov, yRot, xRot, renderer.getDefaultDistance(fov, (double) renderWidth / renderHeight),
                          center.x, center.y, center.z, tileEntities, false);
            snapshot.set(fov, yRot, xRot, tileEntities);

            // Only keep the geometry while a side panel / fullscreen preview of the same path shares it.
            Integer largeFrame = LARGE_USE.get(file);

            if (largeFrame == null || frame - largeFrame > 1)
            {
                RENDERERS.remove(file);
                renderer.close();
            }

            return SmallPreviewResult.RENDERED;
        }
        catch (Throwable t)
        {
            LOGGER.warn("Could not draw schematic list preview for " + file, t);
            return SmallPreviewResult.UNAVAILABLE;
        }
        finally
        {
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
        }
    }

    private static final class Snapshot
    {
        private final Framebuffer fbo;
        private double fov;
        private float yRot;
        private float xRot;
        private boolean tileEntities;
        private boolean valid;

        private Snapshot(Framebuffer fbo)
        {
            this.fbo = fbo;
        }

        private void set(double fov, float yRot, float xRot, boolean tileEntities)
        {
            this.fov = fov;
            this.yRot = yRot;
            this.xRot = xRot;
            this.tileEntities = tileEntities;
            this.valid = true;
        }

        private boolean matches(int width, int height, double fov, float yRot, float xRot, boolean tileEntities)
        {
            return this.valid && this.fbo.framebufferWidth == width && this.fbo.framebufferHeight == height &&
                   this.fov == fov && this.yRot == yRot && this.xRot == xRot && this.tileEntities == tileEntities;
        }

        private void close()
        {
            this.fbo.deleteFramebuffer();
        }
    }

    public static void tickClose()
    {
        frame++;

        boolean hasState = SCHEMATICS.isEmpty() == false || RENDERERS.isEmpty() == false ||
                           SCHEMATIC_FILES.isEmpty() == false || SNAPSHOTS.isEmpty() == false;

        if (Minecraft.getMinecraft().currentScreen == null && hasState)
        {
            close();
        }
    }

    /** Drops the cached schematic and renderer for {@code file}; call after overwriting it on disk. */
    public static void invalidate(Path file)
    {
        USAGE.remove(file);
        release(file);
        invalidateDirectory(file.getParent());
    }

    /** Forgets the directory's schematic candidates, e.g. after a new file was written into it. */
    public static void invalidateDirectory(@Nullable Path directory)
    {
        SCHEMATIC_FILES.remove(directory);
    }

    /** Forgets every directory's candidates; called whenever a browser list is (re)built. */
    public static void invalidateDirectories()
    {
        SCHEMATIC_FILES.clear();
    }

    public static void close()
    {
        for (PreviewRenderer renderer : RENDERERS.values())
        {
            renderer.close();
        }

        RENDERERS.clear();
        USAGE.clear();
        SCHEMATICS.clear();
        LOGGED_FAILURES.clear();
        SCHEMATIC_FILES.clear();

        for (Snapshot snapshot : SNAPSHOTS.values())
        {
            snapshot.close();
        }

        SNAPSHOTS.clear();
        LARGE_USE.clear();
    }
}
