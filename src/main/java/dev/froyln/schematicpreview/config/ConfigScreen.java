package dev.froyln.schematicpreview.config;

import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

public class ConfigScreen extends GuiConfigsBase
{
    private ConfigScreen()
    {
        super(10, 50, "schematicpreview", null, "schematicpreview.config");
    }

    public static ConfigScreen create()
    {
        return new ConfigScreen();
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.clearOptions();
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        List<IConfigBase> options = new java.util.ArrayList<>();
        options.addAll(Configs.Generic.OPTIONS);
        options.addAll(Configs.Menu.OPTIONS);
        options.addAll(Configs.Preview.OPTIONS);
        options.addAll(Configs.HOTKEYS);

        return ConfigOptionWrapper.createFor(options);
    }

}
