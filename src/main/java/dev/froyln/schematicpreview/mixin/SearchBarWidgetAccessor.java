package dev.froyln.schematicpreview.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.search.SearchBarWidget;

@Mixin(SearchBarWidget.class)
public interface SearchBarWidgetAccessor
{
    @Accessor(value = "searchToggleButton", remap = false)
    @Nullable
    GenericButton schematicpreview$getSearchToggleButton();
}
