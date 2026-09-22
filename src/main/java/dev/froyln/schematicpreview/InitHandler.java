package dev.froyln.schematicpreview;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;

import dev.froyln.schematicpreview.config.ConfigScreen;
import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.render.PreviewCache;

public class InitHandler implements IInitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        DirectoryIconStore.load();
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
        InputEventHandler.getKeybindManager().registerKeybindProvider(new KeybindProvider());
        TickHandler.getInstance().registerClientTickHandler(PreviewCache::tickClose);

        Configs.Generic.OPEN_CONFIG_SCREEN.getKeybind().setCallback(new IHotkeyCallback()
        {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key)
            {
                GuiBase.openGui(ConfigScreen.create());
                return true;
            }
        });
    }

    private static final class KeybindProvider implements IKeybindProvider
    {
        @Override
        public void addKeysToMap(IKeybindManager manager)
        {
            for (IHotkey hotkey : Configs.HOTKEYS)
            {
                manager.addKeybindToMap(hotkey.getKeybind());
            }
        }

        @Override
        public void addHotkeys(IKeybindManager manager)
        {
            manager.addHotkeysForCategory(Reference.MOD_ID,
                    "schematicpreview.hotkeys.category.generic", Configs.HOTKEYS);
        }
    }
}
