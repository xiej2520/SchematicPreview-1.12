package dev.froyln.schematicpreview.materials;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import litematica.data.DataManager;
import litematica.materials.MaterialCache;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicRegion;
import litematica.schematic.SchematicMetadata;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.placement.SchematicPlacement;
import malilib.util.data.tag.CompoundData;
import malilib.util.game.wrap.BlockWrap;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;

/** Replaces every state represented by a material-list row in a schematic. */
/** Replaces every state represented by a material-list row in a schematic. */
public final class BlockReplacer
{
    private BlockReplacer() {}

    public static long replace(ItemStack oldStack, Block newBlock, int newMeta,
                               Schematic schematic, Collection<String> regionNames)
    {
        MaterialCache cache = MaterialCache.getInstance();
        Map<BlockState, Boolean> matches = new IdentityHashMap<>();
        IBlockState newBase = newBlock.getStateFromMeta(newMeta);
        long count = 0;

        for (String regionName : regionNames)
        {
            SchematicRegion region = schematic.getRegions().get(regionName);
            if (region == null) continue;

            BlockContainer container = region.getBlockContainer();
            ContainerAccessors.forceRealResizeOnOverflow(container);
            Vec3i size = container.getSize();

            for (int y = 0; y < Math.abs(size.getY()); ++y)
            {
                for (int z = 0; z < Math.abs(size.getZ()); ++z)
                {
                    for (int x = 0; x < Math.abs(size.getX()); ++x)
                    {
                        BlockState state = container.getBlockState(x, y, z);
                        if (matches.computeIfAbsent(state, s -> requiresItem(cache, s, oldStack)))
                        {
                            IBlockState replacement = buildReplacement(state.vanillaState(), newBlock, newBase, newMeta);
                            container.setBlockState(x, y, z, BlockState.of(replacement));
                            ++count;

                            if (newBlock != state.getBlock())
                            {
                                replaceBlockEntityData(region, new BlockPos(x, y, z), newBlock, newMeta);
                            }
                        }
                    }
                }
            }
        }

        if (count > 0)
        {
            SchematicMetadata meta = schematic.getMetadata();
            if (meta.getTotalBlocks() >= 0 && newBlock == Blocks.AIR)
            {
                meta.setTotalBlocks(meta.getTotalBlocks() - count);
            }
            meta.setTimeModifiedToNow();

            for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicPlacements())
            {
                if (placement.getSchematic() == schematic)
                {
                    DataManager.getSchematicPlacementManager().markChunksForRebuild(placement);
                }
            }
        }

        return count;
    }

    private static boolean requiresItem(MaterialCache cache, BlockState state, ItemStack stack)
    {
        if (state.isAirMaterial()) return false;
        for (ItemStack required : cache.getItems(state))
        {
            if (required.getItem() == stack.getItem() && required.getMetadata() == stack.getMetadata()) return true;
        }
        return false;
    }

    private static void replaceBlockEntityData(SchematicRegion region, BlockPos pos, Block newBlock, int newMeta)
    {
        region.getBlockTickMap().remove(pos);
        CompoundData data = createDefaultTileEntityData(newBlock, newMeta, pos);
        if (data != null) region.getBlockEntityMap().put(pos, data);
        else region.getBlockEntityMap().remove(pos);
    }

    @Nullable
    private static CompoundData createDefaultTileEntityData(Block block, int meta, BlockPos pos)
    {
        if ((block instanceof ITileEntityProvider) == false) return null;
        try
        {
            TileEntity te = ((ITileEntityProvider) block).createNewTileEntity(null, meta);
            if (te == null) return null;
            te.setPos(pos.toVanillaPos());
            return BlockWrap.writeBlockEntityToTag(te);
        }
        catch (Throwable t) { return null; }
    }

    private static IBlockState buildReplacement(IBlockState oldState, Block newBlock,
                                                IBlockState newBase, int newMeta)
    {
        IBlockState result = newBase;
        for (IProperty<?> property : oldState.getPropertyKeys())
        {
            if (result.getPropertyKeys().contains(property))
            {
                IBlockState candidate = copyProperty(oldState, result, property);
                if (newBlock.damageDropped(candidate) == newMeta) result = candidate;
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> IBlockState copyProperty(IBlockState from,
                                                                        IBlockState to,
                                                                        IProperty<T> property)
    {
        return to.withProperty(property, from.getValue(property));
    }
}
