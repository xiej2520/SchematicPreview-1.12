package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import malilib.gui.widget.InfoIconWidget;
import malilib.gui.widget.list.header.DirectoryNavigationWidget;

@Mixin(DirectoryNavigationWidget.class)
public interface DirectoryNavigationWidgetAccessor
{
    @Accessor(value = "infoWidget", remap = false)
    InfoIconWidget schematicpreview$getInfoWidget();
}
