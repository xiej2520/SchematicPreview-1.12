package dev.froyln.schematicpreview;

import java.util.List;
import com.google.common.collect.ImmutableList;
import malilib.config.JsonModConfig;
import malilib.config.category.BaseConfigOptionCategory;
import malilib.config.category.ConfigOptionCategory;
import malilib.event.InitializationHandler;
import malilib.gui.BaseScreen;
import malilib.input.ActionResult;
import malilib.input.Hotkey;
import malilib.input.HotkeyCategory;
import malilib.input.HotkeyProvider;
import malilib.registry.Registry;
import dev.froyln.schematicpreview.config.ConfigScreen;
import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.render.PreviewCache;

public class InitHandler implements InitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        DirectoryIconStore.load();

        List<ConfigOptionCategory> categories = ImmutableList.of(
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Generic", Configs.Generic.OPTIONS),
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Menu", Configs.Menu.OPTIONS),
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Preview", Configs.Preview.OPTIONS)
        );
        Registry.CONFIG_MANAGER.registerConfigHandler(
                JsonModConfig.createJsonModConfig(Reference.MOD_INFO, Configs.CURRENT_VERSION, categories, null));

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(Reference.MOD_INFO, ConfigScreen::create);
        Registry.CONFIG_TAB.registerConfigTabSupplier(Reference.MOD_INFO, () -> ConfigScreen.CONFIG_TABS);

        Registry.HOTKEY_MANAGER.registerHotkeyProvider(new HotkeyProvider()
        {
            @Override
            public List<? extends Hotkey> getAllHotkeys()
            {
                return Configs.HOTKEYS;
            }

            @Override
            public List<HotkeyCategory> getHotkeysByCategories()
            {
                return ImmutableList.of(new HotkeyCategory(Reference.MOD_INFO, "schematicpreview.hotkeys.category.generic", Configs.HOTKEYS));
            }
        });

        Registry.TICK_EVENT_DISPATCHER.registerClientTickHandler(PreviewCache::tickClose);

        Configs.Generic.OPEN_CONFIG_SCREEN.setHotkeyCallback((action, key) -> {
            BaseScreen.openScreen(ConfigScreen.create());
            return ActionResult.SUCCESS;
        });
    }
}
