package dev.froyln.schematicpreview.data;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.item.Item;

import malilib.config.util.ConfigUtils;
import malilib.util.data.json.JsonUtils;

import dev.froyln.schematicpreview.Reference;
import litematica.util.LitematicaDirectories;

/**
 * Per-directory custom icons, persisted to {@code config/schematicpreview_icons.json}. Keys use
 * {@code /} separators and are relative to Litematica's schematics base directory ({@code .} for
 * the base itself) when the directory is inside it, absolute otherwise. Unknown item ids are
 * dropped on load; the file is rewritten after a user change or when old absolute keys were
 * migrated to relative ones (on first use).
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

            if (itemId == null || Item.getByNameOrId(itemId) == null)
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
        if (migrationDone)
        {
            return;
        }

        migrationDone = true;
        Path base = getBaseDirectory();

        if (base == null)
        {
            return;
        }

        boolean migrated = false;

        for (Map.Entry<String, Entry> e : new HashMap<>(ICONS).entrySet())
        {
            String relativeKey = migrateKey(e.getKey(), base);

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

    /** The relative key for an old absolute key inside {@code base}, or null if it needs no migration. */
    @Nullable
    private static String migrateKey(String key, Path base)
    {
        try
        {
            Path path = Paths.get(key);
            return path.isAbsolute() ? relativeKey(path.normalize(), base) : null;
        }
        catch (InvalidPathException e)
        {
            return null;
        }
    }

    @Nullable
    private static String relativeKey(Path directory, Path base)
    {
        if (directory.startsWith(base) == false)
        {
            return null;
        }

        String relative = base.relativize(directory).toString().replace('\\', '/');
        return relative.isEmpty() ? "." : relative;
    }

    @Nullable
    private static Path getBaseDirectory()
    {
        try
        {
            return LitematicaDirectories.getSchematicsBaseDirectory().toAbsolutePath().normalize();
        }
        catch (Throwable t)
        {
            return null;
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
    public static Entry get(Path directory)
    {
        return ICONS.get(keyFor(directory));
    }

    /**
     * @return false if {@code itemId} isn't a registered item - the caller should not treat the
     * icon as saved in that case.
     */
    public static boolean set(Path directory, String itemId, IconPosition position)
    {
        if (Item.getByNameOrId(itemId) == null)
        {
            return false;
        }

        ICONS.put(keyFor(directory), new Entry(itemId, position));
        save();
        return true;
    }

    public static void remove(Path directory)
    {
        if (ICONS.remove(keyFor(directory)) != null)
        {
            save();
        }
    }

    private static String keyFor(Path directory)
    {
        migrateAbsoluteKeys();

        Path absolute = directory.toAbsolutePath().normalize();
        Path base = getBaseDirectory();
        String relative = base != null ? relativeKey(absolute, base) : null;

        return relative != null ? relative : absolute.toString().replace('\\', '/');
    }

    private static Path getFile()
    {
        return ConfigUtils.getConfigDirectory().resolve(FILE_NAME);
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
