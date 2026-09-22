package dev.froyln.schematicpreview.config;

import java.io.File;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

import dev.froyln.schematicpreview.Reference;

public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    public static class Generic
    {
        public static final ConfigBoolean ENABLED = new ConfigBoolean("enabled", true, "Toggle mod enabled");
        public static final ConfigHotkey OPEN_CONFIG_SCREEN = new ConfigHotkey("openConfigScreen", "RIGHT_SHIFT,F8", "Open config screen");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(ENABLED, OPEN_CONFIG_SCREEN);
    }

    public static class Menu
    {
        public static final ConfigOptionList PREVIEW_TYPE = new ConfigOptionList("previewType", PreviewType.LIST, "Selected browser entry type");
        public static final ConfigInteger ENTRY_GAP_X = new ConfigInteger("entryGapX", 2, 0, 10, "Browser entry X axis gap");
        public static final ConfigInteger ENTRY_GAP_Y = new ConfigInteger("entryGapY", 2, 0, 10, "Browser entry Y axis gap");
        public static final ConfigInteger LIST_ENTRY_HEIGHT = new ConfigInteger("listEntryHeight", 15, 10, 80, "List entry height");
        public static final ConfigInteger LIST_PREVIEW_ENTRY_HEIGHT = new ConfigInteger("listPreviewEntryHeight", 35, 15, 80, "List preview entry height");
        public static final ConfigDouble TILE_HEIGHT_RATIO = new ConfigDouble("tileHeightRatio", 1.0, 0.3, 5.0, "Tile height ratio");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PREVIEW_TYPE, ENTRY_GAP_X, ENTRY_GAP_Y, LIST_ENTRY_HEIGHT,
                LIST_PREVIEW_ENTRY_HEIGHT, TILE_HEIGHT_RATIO);
    }

    public static class Preview
    {
        public static final ConfigInteger PREVIEW_MAX_VOLUME = new ConfigInteger("previewMaxVolume", 125000, 0, Integer.MAX_VALUE, "Max list/tile preview volume");
        public static final ConfigInteger PREVIEW_MAX_BLOCKS = new ConfigInteger("previewMaxBlocks", 1000000, 1000, Integer.MAX_VALUE, "Max blocks tessellated in a full preview");
        public static final ConfigBoolean RENDER_TILE_ENTITIES = new ConfigBoolean("renderTileEntities", true, "Render block entities");
        public static final ConfigDouble PREVIEW_FOV = new ConfigDouble("previewFov", 50.0, 30.0, 100.0, "Preview FOV");
        public static final ConfigDouble PREVIEW_ROTATION_Y = new ConfigDouble("previewRotationY", 45.0, -180.0, 180.0, "Preview Y rotation");
        public static final ConfigDouble PREVIEW_ROTATION_X = new ConfigDouble("previewRotationX", 30.0, -90.0, 90.0, "Preview X rotation");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PREVIEW_MAX_VOLUME, PREVIEW_MAX_BLOCKS, RENDER_TILE_ENTITIES,
                PREVIEW_FOV, PREVIEW_ROTATION_Y, PREVIEW_ROTATION_X);
    }

    public static final ImmutableList<ConfigHotkey> HOTKEYS = ImmutableList.of(Generic.OPEN_CONFIG_SCREEN);

    public static File getConfigFile()
    {
        return new File(FileUtils.getConfigDirectory(), CONFIG_FILE_NAME);
    }

    @Override
    public void load()
    {
        File file = getConfigFile();

        if (file.isFile() == false)
        {
            return;
        }

        JsonElement element = JsonUtils.parseJsonFile(file);

        if (element != null && element.isJsonObject())
        {
            JsonObject root = element.getAsJsonObject();
            ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.readConfigBase(root, "Menu", Menu.OPTIONS);
            ConfigUtils.readConfigBase(root, "Preview", Preview.OPTIONS);
            ConfigUtils.readHotkeys(root, "Hotkeys", HOTKEYS);
        }
    }

    @Override
    public void save()
    {
        File dir = FileUtils.getConfigDirectory();

        if ((dir.isDirectory() || dir.mkdirs()) == false)
        {
            return;
        }

        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Menu", Menu.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Preview", Preview.OPTIONS);
        ConfigUtils.writeHotkeys(root, "Hotkeys", HOTKEYS);
        JsonUtils.writeJsonToFile(root, getConfigFile());
    }
}
