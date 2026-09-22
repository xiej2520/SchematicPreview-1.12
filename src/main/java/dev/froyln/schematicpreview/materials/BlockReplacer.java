package dev.froyln.schematicpreview.materials;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/** Replaces a material-list item in every selected schematic region. */
public final class BlockReplacer
{
    private BlockReplacer()
    {
    }

    public static long replace(ItemStack oldStack, ItemStack newStack, LitematicaSchematic schematic,
            Collection<String> regionNames)
    {
        Block newBlock = Block.getBlockFromItem(newStack.getItem());

        if (newBlock == Blocks.AIR)
        {
            return 0;
        }

        MaterialCache cache = MaterialCache.getInstance();
        Map<BlockState, Boolean> matches = new HashMap<>();
        long count = 0;

        for (String regionName : regionNames)
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container == null)
            {
                continue;
            }

            net.minecraft.util.math.Vec3i size = container.getSize();

            for (int y = 0; y < size.getY(); ++y)
            {
                for (int z = 0; z < size.getZ(); ++z)
                {
                    for (int x = 0; x < size.getX(); ++x)
                    {
                        BlockState oldState = container.get(x, y, z);

                        if (matches.computeIfAbsent(oldState, state -> requiresItem(cache, state, oldStack)))
                        {
                            BlockState newState = copySharedProperties(oldState, newBlock.getDefaultState());
                            container.set(x, y, z, newState);
                            ++count;

                            if (oldState.getBlock() != newBlock)
                            {
                                BlockPos pos = new BlockPos(x, y, z);
                                Map<BlockPos, CompoundTag> blockEntities = schematic.getBlockEntityMapForRegion(regionName);

                                if (blockEntities != null)
                                {
                                    blockEntities.remove(pos);
                                }

                                Map<BlockPos, ?> scheduledTicks = schematic.getScheduledBlockTicksForRegion(regionName);

                                if (scheduledTicks != null)
                                {
                                    scheduledTicks.remove(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (count > 0)
        {
            schematic.getMetadata().setTimeModifiedToNow();
            schematic.getMetadata().setModifiedSinceSaved();
            markPlacementsForRebuild(schematic);
        }

        return count;
    }

    private static boolean requiresItem(MaterialCache cache, BlockState state, ItemStack oldStack)
    {
        if (state.isAir())
        {
            return false;
        }

        ImmutableList<ItemStack> items = cache.getItems(state);

        for (ItemStack required : items)
        {
            if (ItemStack.areItemsEqual(oldStack, required))
            {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState copySharedProperties(BlockState oldState, BlockState newState)
    {
        for (Property property : oldState.getProperties())
        {
            if (newState.contains(property))
            {
                try
                {
                    newState = newState.with(property, oldState.get(property));
                }
                catch (IllegalArgumentException ignored)
                {
                    // The property exists by name but has an incompatible value domain.
                }
            }
        }

        return newState;
    }

    private static void markPlacementsForRebuild(LitematicaSchematic schematic)
    {
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllPlacementsOfSchematic(schematic))
        {
            DataManager.getSchematicPlacementManager().markChunksForRebuild(placement);
        }
    }
}
