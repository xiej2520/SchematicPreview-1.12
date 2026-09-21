package dev.froyln.schematicpreview;

import net.ornithemc.osl.entrypoints.api.client.ClientModInitializer;

import malilib.registry.Registry;

public class SchematicPreview implements ClientModInitializer
{
    @Override
    public void initClient()
    {
        Registry.INITIALIZATION_DISPATCHER.registerInitializationHandler(new InitHandler());
    }
}
