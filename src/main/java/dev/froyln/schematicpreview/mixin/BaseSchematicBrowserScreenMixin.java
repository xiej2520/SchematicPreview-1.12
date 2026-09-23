package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import litematica.gui.BaseSchematicBrowserScreen;
import litematica.gui.util.SchematicBrowserIconProvider;
import litematica.schematic.LoadedSchematic;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.BaseFileBrowserWidget;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import malilib.gui.widget.list.entry.DataListEntryWidgetFactory;
import malilib.gui.widget.list.header.DirectoryNavigationWidget;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.gui.BrowserWidgetAccessors;
import dev.froyln.schematicpreview.gui.PreviewDirectoryEntryWidget;
import dev.froyln.schematicpreview.gui.SchematicPreviewIcons;
import dev.froyln.schematicpreview.gui.TileEntryWidgetFactory;
import dev.froyln.schematicpreview.gui.PreviewTypeButtonAccess;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * Installs preview-aware entry widgets/factory and a preview-type cycle button into every
 * schematic browser screen; all three subclasses funnel through {@code createListWidget()}.
 */
@Mixin(BaseSchematicBrowserScreen.class)
public abstract class BaseSchematicBrowserScreenMixin
{
    @Unique private static final Logger SCHEMATICPREVIEW_LOGGER = LogManager.getLogger("SchematicPreview");
    private static final int BUTTON_SIZE = 14;

    @Shadow(remap = false) protected SchematicBrowserIconProvider cachingIconProvider;
    @Shadow(remap = false) protected Optional<LoadedSchematic> lastSelectedSchematic;
    @Shadow(remap = false) @Nullable protected Path lastSelectedSchematicFile;

    @Inject(method = "getLastSelectedSchematic", at = @At("HEAD"), remap = false, cancellable = true)
    private void schematicpreview$loadLastSelectedSchematicSafely(CallbackInfoReturnable<Optional<LoadedSchematic>> cir)
    {
        if (this.lastSelectedSchematic.isPresent() == false && this.lastSelectedSchematicFile != null)
        {
            try
            {
                this.lastSelectedSchematic = LoadedSchematic.tryLoadSchematic(this.lastSelectedSchematicFile);
            }
            catch (Throwable t)
            {
                // Conversion is a property of the selected file, not a reason to destroy the
                // browser. Leave the selection active with no loaded schematic so the manager
                // can still show the file and the user can select another one.
                SCHEMATICPREVIEW_LOGGER.warn("Could not load selected schematic " + this.lastSelectedSchematicFile + ": " + t);
                this.lastSelectedSchematic = Optional.empty();
            }
        }

        cir.setReturnValue(this.lastSelectedSchematic);
    }

    @Inject(method = "createListWidget", at = @At("RETURN"), remap = false)
    private void schematicpreview$installPreviewEntries(CallbackInfoReturnable<BaseFileBrowserWidget> cir)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        PreviewCache.invalidateDirectories();

        BaseFileBrowserWidget listWidget = cir.getReturnValue();
        BrowserWidgetAccessors.setAreEntriesFixedHeight(listWidget, false);

        this.schematicpreview$applyPreviewType(listWidget);

        DirectoryNavigationWidget nav = BrowserWidgetAccessors.getNavigationWidget(listWidget);
        GenericButton button = GenericButton.create(BUTTON_SIZE, BUTTON_SIZE, SchematicPreviewIcons.PREVIEW_TYPE);
        button.translateAndAddHoverString("schematicpreview.button.preview_type");
        button.setActionListener((mouseButton, widget) -> {
            if (mouseButton != 0 && mouseButton != 1)
            {
                return false;
            }

            Configs.Menu.PREVIEW_TYPE.cycleValue(mouseButton == 1);
            this.schematicpreview$applyPreviewType(listWidget);
            listWidget.refreshFilteredEntries();
            return true;
        });
        ((PreviewTypeButtonAccess) nav).schematicpreview$setPreviewTypeButton(button);
    }

    @Unique
    private void schematicpreview$applyPreviewType(BaseFileBrowserWidget listWidget)
    {
        PreviewType type = Configs.Menu.PREVIEW_TYPE.getValue();
        DataListEntryWidgetFactory<DirectoryEntry> entryFactory = (data, constructData) ->
                new PreviewDirectoryEntryWidget(data, constructData, listWidget, this.cachingIconProvider, type);
        listWidget.setDataListEntryWidgetFactory(entryFactory);

        if (type.isTile())
        {
            int cellWidth = listWidget.getEntryWidgetWidth() / type.getColumns();
            listWidget.setListEntryWidgetFixedHeight(type.getHeight(cellWidth));
            listWidget.setListEntryWidgetFactory(new TileEntryWidgetFactory(listWidget, entryFactory, type));
        }
        else
        {
            // LIST / LIST_PREVIEW heights don't depend on the width.
            listWidget.setListEntryWidgetFixedHeight(type.getHeight(0));
            listWidget.setListEntryWidgetFactory(listWidget);
        }
    }
}
