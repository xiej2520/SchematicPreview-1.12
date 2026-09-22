package dev.froyln.schematicpreview.data;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

import dev.froyln.schematicpreview.Reference;

/**
 * Per-directory custom icons, keyed by the path relative to Litematica's schematics base
 * directory (absolute for directories outside it) with {@code /} separators, persisted to
 * {@code config/schematicpreview_icons.json}. Unknown item ids are dropped on load; the file
 * is rewritten after a user change or when old absolute keys were migrated to relative ones.
 */
public final class DirectoryIconStore
{
    private static final String FILE_NAME = Reference.MOD_ID + "_icons.json";

    private static final Map<String, Entry> ICONS = new HashMap<>();
    private static boolean migrationDone;

    private DirectoryIconStore()
    {
    }

    public static void load()
    {
        ICONS.clear();
        migrationDone = false;

        JsonElement root = JsonUtils.parseJsonFile(getFile());

        if (root == null || root.isJsonObject() == false)
        {
            return;
        }

        JsonObject icons = JsonUtils.getNestedObject(root.getAsJsonObject(), "icons", false);

        if (icons == null)
        {
            return;
        }

        for (Map.Entry<String, JsonElement> e : icons.entrySet())
        {
            if (e.getValue().isJsonObject() == false)
            {
                continue;
            }

            JsonObject obj = e.getValue().getAsJsonObject();
            String itemId = JsonUtils.getString(obj, "itemId");

            if (itemId == null || getItem(itemId) == null)
            {
                continue;
            }

            IconPosition position = IconPosition.fromJsonName(JsonUtils.getStringOrDefault(obj, "pos", IconPosition.DEFAULT.jsonName));
            ICONS.put(e.getKey(), new Entry(itemId, position));
        }
    }

    /**
     * Rewrites old absolute keys inside the schematics base directory as relative keys. Runs on
     * first use rather than in {@link #load()}: the base directory comes from Litematica's
     * config, which may not be loaded yet during mod init.
     */
    private static void migrateAbsoluteKeys()
    {
        if (migrationDone || getBasePath() == null)
        {
            return;
        }

        migrationDone = true;
        boolean migrated = false;

        for (Map.Entry<String, Entry> e : new HashMap<>(ICONS).entrySet())
        {
            String relativeKey = toRelativeKey(e.getKey());

            if (relativeKey != null)
            {
                ICONS.remove(e.getKey());
                // An already relative entry for the same directory wins over a stale absolute one.
                ICONS.putIfAbsent(relativeKey, e.getValue());
                migrated = true;
            }
        }

        if (migrated)
        {
            save();
        }
    }

    private static void save()
    {
        JsonObject root = new JsonObject();
        JsonObject icons = new JsonObject();

        for (Map.Entry<String, Entry> e : ICONS.entrySet())
        {
            JsonObject obj = new JsonObject();
            obj.addProperty("itemId", e.getValue().itemId);
            obj.addProperty("pos", e.getValue().position.jsonName);
            icons.add(e.getKey(), obj);
        }

        root.add("icons", icons);
        JsonUtils.writeJsonToFile(root, getFile());
    }

    @Nullable
    public static Entry get(File directory)
    {
        Entry entry = ICONS.get(keyFor(directory));

        // Covers old absolute keys that could not be migrated (base directory unknown).
        return entry != null ? entry : ICONS.get(toKey(directory.getAbsoluteFile().toPath().normalize()));
    }

    /**
     * @return false if {@code itemId} isn't a registered item - the caller should not treat the
     * icon as saved in that case.
     */
    public static boolean set(File directory, String itemId, IconPosition position)
    {
        if (getItem(itemId) == null)
        {
            return false;
        }

        ICONS.put(keyFor(directory), new Entry(itemId, position));
        save();
        return true;
    }

    public static void remove(File directory)
    {
        if (ICONS.remove(keyFor(directory)) != null)
        {
            save();
        }
    }

    private static String keyFor(File directory)
    {
        migrateAbsoluteKeys();

        Path path = directory.getAbsoluteFile().toPath().normalize();
        Path base = getBasePath();

        if (base != null && path.startsWith(base))
        {
            return toKey(base.relativize(path));
        }

        return toKey(path);
    }

    /** @return the relative key for an old absolute key inside the base directory, else null. */
    @Nullable
    private static String toRelativeKey(String key)
    {
        Path base = getBasePath();

        if (base == null)
        {
            return null;
        }

        try
        {
            Path path = new File(key).toPath();

            if (path.isAbsolute() && path.normalize().startsWith(base))
            {
                return toKey(base.relativize(path.normalize()));
            }
        }
        catch (RuntimeException ignored)
        {
            // Not a usable path on this platform; keep the key as it is.
        }

        return null;
    }

    private static String toKey(Path path)
    {
        String key = path.toString().replace('\\', '/');
        return key.isEmpty() ? "." : key;
    }

    @Nullable
    private static Path getBasePath()
    {
        try
        {
            File base = DataManager.getSchematicsBaseDirectory();
            return base != null ? base.getAbsoluteFile().toPath().normalize() : null;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static File getFile()
    {
        return new File(FileUtils.getConfigDirectory(), FILE_NAME);
    }

    @Nullable
    private static Item getItem(String id)
    {
        try
        {
            return Registry.ITEM.getOrEmpty(new Identifier(id)).orElse(null);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    public static class Entry
    {
        public final String itemId;
        public final IconPosition position;

        public Entry(String itemId, IconPosition position)
        {
            this.itemId = itemId;
            this.position = position;
        }
    }
}
