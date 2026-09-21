package dev.froyln.schematicpreview.materials;

import java.nio.file.Path;

import litematica.schematic.Schematic;
import litematica.schematic.util.SchematicFileUtils;
import malilib.gui.BaseScreen;
import malilib.gui.ConfirmActionScreen;
import malilib.gui.TextInputScreen;
import malilib.gui.util.GuiUtils;
import malilib.util.FileNameUtils;
import malilib.overlay.message.MessageDispatcher;

import dev.froyln.schematicpreview.gui.PopupScreenCompat;
import dev.froyln.schematicpreview.render.PreviewCache;

/** Save helpers for schematic edits made by the material-list UI. */
public final class SchematicSaver
{
    private SchematicSaver() {}

    public static void save(Schematic schematic, Path file)
    {
        String name = file.getFileName().toString();
        ConfirmActionScreen screen = new ConfirmActionScreen(280,
                "schematicpreview.gui.save_schematic.confirm_title",
                () -> overwrite(schematic, file, name),
                "schematicpreview.gui.save_schematic.confirm_message", name);
        screen.setParent(GuiUtils.getCurrentScreen());
        BaseScreen.openScreen(PopupScreenCompat.keepPopupSize(screen));
    }

    public static void saveAs(Schematic schematic, Path sourceFile)
    {
        Path dir = sourceFile.getParent();
        String defaultName = FileNameUtils.getFileNameWithoutExtension(sourceFile.getFileName().toString()) + "_replaced";
        BaseScreen.openScreenWithParent(PopupScreenCompat.keepPopupSize(
                new TextInputScreen("schematicpreview.gui.save_schematic_as.title", defaultName,
                                    name -> writeAs(schematic, dir, name))));
    }

    private static void overwrite(Schematic schematic, Path file, String name)
    {
        if (SchematicFileUtils.writeToFile(schematic, file, true))
        {
            PreviewCache.invalidate(file);
            MessageDispatcher.success().translate("schematicpreview.message.schematic_saved", name);
        }
    }

    private static boolean writeAs(Schematic schematic, Path dir, String name)
    {
        if (name.trim().isEmpty()) return false;
        Path file = dir.resolve(name);
        boolean success = SchematicFileUtils.writeToFile(schematic, file, false);
        if (success)
        {
            PreviewCache.invalidateDirectory(dir);
            MessageDispatcher.success().translate("schematicpreview.message.schematic_saved", name);
        }
        return success;
    }
}
