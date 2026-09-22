package dev.froyln.schematicpreview.config;

import java.util.Locale;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum PreviewType implements IConfigOptionListEntry
{
    LIST(1), LIST_PREVIEW(1), TILE_5(5), TILE_4(4), TILE_3(3);

    private final int columns;

    PreviewType(int columns)
    {
        this.columns = columns;
    }

    public int getColumns()
    {
        return this.columns;
    }

    public int getHeight(int width)
    {
        if (this == LIST)
        {
            return Configs.Menu.LIST_ENTRY_HEIGHT.getIntegerValue();
        }

        if (this == LIST_PREVIEW)
        {
            return Configs.Menu.LIST_PREVIEW_ENTRY_HEIGHT.getIntegerValue();
        }

        return (int) (width * Configs.Menu.TILE_HEIGHT_RATIO.getDoubleValue());
    }

    public boolean isTile()
    {
        return this == TILE_5 || this == TILE_4 || this == TILE_3;
    }

    public boolean hasPreview()
    {
        return this != LIST;
    }

    @Override
    public String getStringValue()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getDisplayName()
    {
        return this.name();
    }

    @Override
    public IConfigOptionListEntry fromString(String value)
    {
        for (PreviewType type : values())
        {
            if (type.getStringValue().equals(value))
            {
                return type;
            }
        }

        return LIST;
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward)
    {
        PreviewType[] values = values();
        int index = this.ordinal() + (forward ? 1 : -1);

        if (index < 0)
        {
            index = values.length - 1;
        }
        else if (index >= values.length)
        {
            index = 0;
        }

        return values[index];
    }
}
