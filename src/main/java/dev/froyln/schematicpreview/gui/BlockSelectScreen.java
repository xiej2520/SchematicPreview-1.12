package dev.froyln.schematicpreview.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.registry.Registry;

import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

/** Searchable picker for exact placeable block item variants in the material list. */
public class BlockSelectScreen extends GuiListBase<ItemStack, BlockSelectScreen.EntryWidget, BlockSelectScreen.ListWidget>
{
    private final List<ItemStack> entries;
    private final Consumer<ItemStack> callback;

    public BlockSelectScreen(String titleKey, Consumer<ItemStack> callback)
    {
        super(10, 30);

        this.setTitle(StringUtils.translate(titleKey));
        this.entries = collectEntries();
        this.callback = callback;
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.height - 60;
    }

    @Override
    protected ListWidget createListWidget(int listX, int listY)
    {
        return new ListWidget(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.entries, this::pick);
    }

    private void pick(ItemStack stack)
    {
        this.callback.accept(stack);
        this.mc.openScreen(this.getParent());
    }

    private static List<ItemStack> collectEntries()
    {
        List<ItemStack> result = new ArrayList<>();
        DefaultedList<ItemStack> variants = DefaultedList.of();

        for (Block block : Registry.BLOCK)
        {
            Item item = Registry.ITEM.get(Registry.BLOCK.getId(block));

            if (item == null || item == Items.AIR)
            {
                continue;
            }

            variants.clear();
            item.appendStacks(ItemGroup.SEARCH, variants);

            for (ItemStack stack : variants)
            {
                if (stack.isEmpty() == false)
                {
                    result.add(stack.copy());
                }
            }
        }

        Collections.sort(result, Comparator.comparing(BlockSelectScreen::getDisplayName,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static String getDisplayName(ItemStack stack)
    {
        return StringUtils.translate(stack.getTranslationKey());
    }

    public static final class ListWidget extends WidgetListBase<ItemStack, EntryWidget>
    {
        private final List<ItemStack> entries;
        private final Consumer<ItemStack> callback;

        private ListWidget(int x, int y, int width, int height, List<ItemStack> entries,
                Consumer<ItemStack> callback)
        {
            super(x, y, width, height, null);
            this.entries = entries;
            this.callback = callback;
            this.allowKeyboardNavigation = true;
            this.browserEntryHeight = 22;
            this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0,
                    MaLiLibIcons.SEARCH, LeftRight.LEFT);
            this.browserEntriesOffsetY = 17;
        }

        @Override
        protected Collection<ItemStack> getAllEntries()
        {
            return this.entries;
        }

        @Override
        protected List<String> getEntryStringsForFilter(ItemStack stack)
        {
            return Collections.singletonList(BlockSelectScreen.getDisplayName(stack).toLowerCase());
        }

        @Override
        protected EntryWidget createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ItemStack entry)
        {
            return new EntryWidget(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry),
                    isOdd, entry, listIndex, this.callback);
        }
    }

    public static final class EntryWidget extends WidgetListEntryBase<ItemStack>
    {
        private final boolean isOdd;
        private final Consumer<ItemStack> callback;

        private EntryWidget(int x, int y, int width, int height, boolean isOdd, ItemStack entry,
                int listIndex, Consumer<ItemStack> callback)
        {
            super(x, y, width, height, entry, listIndex);
            this.isOdd = isOdd;
            this.callback = callback;
        }

        @Override
        public void render(int mouseX, int mouseY, boolean selected)
        {
            int color = selected || this.isMouseOver(mouseX, mouseY) ? 0xA0707070 :
                    (this.isOdd ? 0xA0101010 : 0xA0303030);
            RenderUtils.drawRect(this.x, this.y, this.width, this.height, color);

            if (selected)
            {
                RenderUtils.drawOutline(this.x, this.y, this.width, this.height, 0xFF90D0F0);
            }

            InventoryOverlay.renderStackAt(this.entry, this.x + 2, this.y + 3, 1f, this.mc);
            this.drawStringWithShadow(this.x + 22, this.y + (this.height - this.fontHeight) / 2 + 1,
                    0xFFFFFFFF, BlockSelectScreen.getDisplayName(this.entry));
        }

        @Override
        protected boolean onMouseClickedImpl(int mouseX, int mouseY, int mouseButton)
        {
            if (mouseButton == 0)
            {
                this.callback.accept(this.entry.copy());
                return true;
            }

            return super.onMouseClickedImpl(mouseX, mouseY, mouseButton);
        }
    }
}
