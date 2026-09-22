package dev.froyln.schematicpreview.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.level.ColorResolver;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.world.ChunkManagerSchematic;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

/**
 * A read-only render view over all regions in one 1.15 Litematica schematic.
 *
 * Block models use the schematic-local view directly. The accompanying schematic world only
 * supplies block state and neighbor lookups to vanilla block-entity renderers.
 */
public class SchematicBlockAccess implements BlockRenderView
{
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private final List<Region> regions = new ArrayList<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final WorldSchematic previewWorld;
    private final BlockPos boxMin;
    private final BlockPos boxMax;
    private boolean blockEntitiesAttached;

    public SchematicBlockAccess(LitematicaSchematic schematic)
    {
        this.previewWorld = SchematicWorldHandler.createSchematicWorld();
        BlockPos min = null;
        BlockPos max = null;

        for (String name : schematic.getAreaPositions().keySet())
        {
            BlockPos position = schematic.getSubRegionPosition(name);
            BlockPos size = schematic.getAreaSize(name);
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(name);

            if (position == null || size == null || container == null)
            {
                continue;
            }

            int minX = position.getX() + (size.getX() < 0 ? size.getX() + 1 : 0);
            int minY = position.getY() + (size.getY() < 0 ? size.getY() + 1 : 0);
            int minZ = position.getZ() + (size.getZ() < 0 ? size.getZ() + 1 : 0);
            Vec3i actualSize = container.getSize();
            BlockPos regionMin = new BlockPos(minX, minY, minZ);
            BlockPos regionMax = regionMin.add(actualSize.getX() - 1, actualSize.getY() - 1, actualSize.getZ() - 1);
            this.regions.add(new Region(regionMin, container));
            this.loadBlockEntities(schematic.getBlockEntityMapForRegion(name), regionMin);

            min = min == null ? regionMin : min(min, regionMin);
            max = max == null ? regionMax : max(max, regionMax);
        }

        this.boxMin = min != null ? min : BlockPos.ORIGIN;
        this.boxMax = max != null ? max : BlockPos.ORIGIN;
        this.loadPreviewChunks();
    }

    public BlockPos getBoxMin()
    {
        return this.boxMin;
    }

    public Vec3i getBoxSize()
    {
        return new Vec3i(this.boxMax.getX() - this.boxMin.getX() + 1,
                          this.boxMax.getY() - this.boxMin.getY() + 1,
                          this.boxMax.getZ() - this.boxMin.getZ() + 1);
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        for (Region region : this.regions)
        {
            BlockState state = region.getBlockState(pos);

            if (state != null)
            {
                return state;
            }
        }

        return AIR;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return this.blockEntities.get(pos);
    }

    public Iterable<BlockEntity> getBlockEntities()
    {
        return this.blockEntities.values();
    }

    public void setPreviewBlockState(BlockPos pos, BlockState state)
    {
        this.previewWorld.setBlockState(pos, state, 0);
    }

    public void attachBlockEntities()
    {
        if (this.blockEntitiesAttached)
        {
            return;
        }

        for (BlockEntity blockEntity : this.blockEntities.values())
        {
            BlockPos pos = blockEntity.getPos();
            blockEntity.setLocation(this.previewWorld, pos);
            this.previewWorld.setBlockEntity(pos, blockEntity);
        }

        this.blockEntitiesAttached = true;
    }

    public void close()
    {
        this.previewWorld.getChunkManager().getLoadedChunks().clear();
        this.blockEntities.clear();
    }

    private void loadBlockEntities(@Nullable Map<BlockPos, CompoundTag> tags, BlockPos regionMin)
    {
        if (tags == null)
        {
            return;
        }

        for (Map.Entry<BlockPos, CompoundTag> entry : tags.entrySet())
        {
            try
            {
                BlockEntity blockEntity = BlockEntity.createFromTag(entry.getValue().copy());

                if (blockEntity != null)
                {
                    blockEntity.setPos(regionMin.add(entry.getKey()));
                    this.blockEntities.put(blockEntity.getPos(), blockEntity);
                }
            }
            catch (Throwable ignored)
            {
                // An unsupported or malformed block entity must not prevent the rest of the
                // schematic from being previewed.
            }
        }
    }

    private void loadPreviewChunks()
    {
        ChunkManagerSchematic chunks = this.previewWorld.getChunkManager();

        for (Region region : this.regions)
        {
            Vec3i size = region.container.getSize();
            int minChunkX = Math.floorDiv(region.min.getX(), 16);
            int minChunkZ = Math.floorDiv(region.min.getZ(), 16);
            int maxChunkX = Math.floorDiv(region.min.getX() + size.getX() - 1, 16);
            int maxChunkZ = Math.floorDiv(region.min.getZ() + size.getZ() - 1, 16);

            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ)
            {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX)
                {
                    chunks.loadChunk(chunkX, chunkZ);
                }
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        return net.minecraft.client.MinecraftClient.getInstance().world != null
                ? net.minecraft.client.MinecraftClient.getInstance().world.getLightingProvider() : null;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver resolver)
    {
        // Preview colors should not depend on the biome under the player's cursor.
        return 0xFFFFFF;
    }

    @Override
    public int getLightLevel(net.minecraft.world.LightType type, BlockPos pos)
    {
        return 15;
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
    {
        return 15;
    }

    @Override
    public boolean isSkyVisible(BlockPos pos)
    {
        return true;
    }

    @Override
    public int getHeight()
    {
        return 256;
    }

    private static BlockPos min(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static final class Region
    {
        private final BlockPos min;
        private final LitematicaBlockStateContainer container;

        private Region(BlockPos min, LitematicaBlockStateContainer container)
        {
            this.min = min;
            this.container = container;
        }

        @Nullable
        private BlockState getBlockState(BlockPos pos)
        {
            Vec3i size = this.container.getSize();
            int x = pos.getX() - this.min.getX();
            int y = pos.getY() - this.min.getY();
            int z = pos.getZ() - this.min.getZ();

            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ())
            {
                return null;
            }

            return this.container.get(x, y, z);
        }
    }
}
