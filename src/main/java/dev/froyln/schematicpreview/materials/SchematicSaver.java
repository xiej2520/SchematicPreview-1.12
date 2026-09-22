package dev.froyln.schematicpreview.materials;

import java.io.File;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.GuiUtils;

import dev.froyln.schematicpreview.render.PreviewCache;

/** Save and Save As for schematic-backed material lists. */
public final class SchematicSaver
{
    private SchematicSaver()
    {
    }

    public static void save(LitematicaSchematic schematic)
    {
        File file = schematic.getFile();

        if (file == null)
        {
            return;
        }

        GuiConfirmAction confirm = new GuiConfirmAction(280,
                "schematicpreview.gui.save_schematic.confirm_title", new IConfirmationListener()
        {
            @Override
            public boolean onActionConfirmed()
            {
                if (schematic.writeToFile(file.getParentFile(), file.getName(), true))
                {
                    schematic.getMetadata().clearModifiedSinceSaved();
                    PreviewCache.invalidate(file);
                    InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "schematicpreview.message.schematic_saved", file.getName());
                }

                return true;
            }

            @Override
            public boolean onActionCancelled()
            {
                return true;
            }
        }, GuiUtils.getCurrentScreen(), "schematicpreview.gui.save_schematic.confirm_message", file.getName());
        GuiBase.openGui(confirm);
    }

    public static void saveAs(LitematicaSchematic schematic)
    {
        File source = schematic.getFile();

        if (source == null)
        {
            return;
        }

        String name = source.getName();

        if (name.endsWith(LitematicaSchematic.FILE_EXTENSION))
        {
            name = name.substring(0, name.length() - LitematicaSchematic.FILE_EXTENSION.length());
        }

        GuiTextInput input = new GuiTextInput(128, "schematicpreview.gui.save_schematic_as.title",
                name + "_replaced", GuiUtils.getCurrentScreen(), value -> {
                    String trimmed = value.trim();

                    if (trimmed.isEmpty() == false && schematic.writeToFile(source.getParentFile(), trimmed, false))
                    {
                        PreviewCache.invalidateDirectory(source.getParentFile());
                        InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "schematicpreview.message.schematic_saved", trimmed);
                    }
                });
        GuiBase.openGui(input);
    }
}
