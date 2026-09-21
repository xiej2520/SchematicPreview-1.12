package dev.froyln.schematicpreview.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import litematica.schematic.Schematic;
import litematica.schematic.SchematicRegion;
import litematica.schematic.container.BlockContainer;
import malilib.util.data.tag.BaseData;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.ListData;

/** A light-weight {@link IBlockAccess} over a schematic's regions. */
public class SchematicBlockAccess implements IBlockAccess
{
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private final List<RegionEntry> regions = new ArrayList<>();
    private final BlockPos boxMin;
    private final BlockPos boxMax;

    public SchematicBlockAccess(Schematic schematic)
    {
        BlockPos min = null;
        BlockPos max = null;

        for (SchematicRegion region : schematic.getRegions().values())
        {
            malilib.util.position.BlockPos pos = region.getRelativePosition();
            Vec3i size = region.getSize();
            int width = Math.abs(size.getX());
            int height = Math.abs(size.getY());
            int depth = Math.abs(size.getZ());
            int minX = pos.getX() + (size.getX() < 0 ? size.getX() + 1 : 0);
            int minY = pos.getY() + (size.getY() < 0 ? size.getY() + 1 : 0);
            int minZ = pos.getZ() + (size.getZ() < 0 ? size.getZ() + 1 : 0);
            BlockPos regionMin = new BlockPos(minX, minY, minZ);
            BlockPos regionMax = regionMin.add(width - 1, height - 1, depth - 1);

            this.regions.add(new RegionEntry(regionMin, width, height, depth,
                                             region.getBlockContainer(), region.getBlockEntityMap()));

            min = min == null ? regionMin : new BlockPos(Math.min(min.getX(), regionMin.getX()),
                                                         Math.min(min.getY(), regionMin.getY()),
                                                         Math.min(min.getZ(), regionMin.getZ()));
            max = max == null ? regionMax : new BlockPos(Math.max(max.getX(), regionMax.getX()),
                                                         Math.max(max.getY(), regionMax.getY()),
                                                         Math.max(max.getZ(), regionMax.getZ()));
        }

        this.boxMin = min != null ? min : BlockPos.ORIGIN;
        this.boxMax = max != null ? max : BlockPos.ORIGIN;
    }

    public BlockPos getBoxMin() { return this.boxMin; }

    public Vec3i getBoxSize()
    {
        return new Vec3i(this.boxMax.getX() - this.boxMin.getX() + 1,
                          this.boxMax.getY() - this.boxMin.getY() + 1,
                          this.boxMax.getZ() - this.boxMin.getZ() + 1);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos)
    {
        for (RegionEntry region : this.regions)
        {
            IBlockState state = region.getBlockState(pos);
            if (state != null) return state;
        }
        return AIR;
    }

    @Override public boolean isAirBlock(BlockPos pos) { return this.getBlockState(pos).getBlock() == Blocks.AIR; }
    @Override public int getCombinedLight(BlockPos pos, int lightValue) { return FULL_BRIGHT_LIGHT; }
    @Override public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
    @Override public int getStrongPower(BlockPos pos, EnumFacing direction) { return 0; }
    @Override public WorldType getWorldType() { return WorldType.DEFAULT; }

    public List<BlockPos> getTileEntityPositions()
    {
        List<BlockPos> positions = new ArrayList<>();
        for (RegionEntry region : this.regions)
        {
            for (malilib.util.position.BlockPos local : region.blockEntities.keySet())
            {
                positions.add(region.min.add(local.getX(), local.getY(), local.getZ()));
            }
        }
        return positions;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos)
    {
        for (RegionEntry region : this.regions)
        {
            TileEntity te = region.getTileEntity(pos);
            if (te != null) return te;
        }
        return null;
    }

    private static final class RegionEntry
    {
        private final BlockPos min;
        private final int width;
        private final int height;
        private final int depth;
        private final BlockContainer container;
        private final Map<malilib.util.position.BlockPos, CompoundData> blockEntities;
        private final Map<BlockPos, TileEntity> createdTileEntities = new HashMap<>();

        RegionEntry(BlockPos min, int width, int height, int depth, BlockContainer container,
                    Map<malilib.util.position.BlockPos, CompoundData> blockEntities)
        {
            this.min = min;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.container = container;
            this.blockEntities = blockEntities;
        }

        @Nullable
        IBlockState getBlockState(BlockPos pos)
        {
            int x = pos.getX() - this.min.getX();
            int y = pos.getY() - this.min.getY();
            int z = pos.getZ() - this.min.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.height || z >= this.depth) return null;
            return this.container.getBlockState(x, y, z).vanillaState();
        }

        @Nullable
        TileEntity getTileEntity(BlockPos pos)
        {
            if (this.createdTileEntities.containsKey(pos)) return this.createdTileEntities.get(pos);

            TileEntity te = null;
            malilib.util.position.BlockPos local = new malilib.util.position.BlockPos(pos.getX() - this.min.getX(),
                                                                                       pos.getY() - this.min.getY(),
                                                                                       pos.getZ() - this.min.getZ());
            CompoundData data = this.blockEntities.get(local);
            if (data != null)
            {
                try { te = TileEntity.create(null, toVanillaCompound(data)); }
                catch (Throwable ignored) { te = null; }
            }
            this.createdTileEntities.put(pos, te);
            return te;
        }
    }

    private static NBTTagCompound toVanillaCompound(CompoundData data)
    {
        NBTTagCompound tag = new NBTTagCompound();
        for (String key : data.getKeys())
        {
            BaseData value = data.getData(key).orElse(null);
            if (value != null) tag.setTag(key, toVanillaTag(value));
        }
        return tag;
    }

    private static NBTBase toVanillaTag(BaseData data)
    {
        switch (data.getType())
        {
            case 1: return new NBTTagByte(((malilib.util.data.tag.ByteData) data).getByte());
            case 2: return new NBTTagShort(((malilib.util.data.tag.ShortData) data).value);
            case 3: return new NBTTagInt(((malilib.util.data.tag.IntData) data).getInt());
            case 4: return new NBTTagLong(((malilib.util.data.tag.LongData) data).getLong());
            case 5: return new NBTTagFloat(((malilib.util.data.tag.FloatData) data).value);
            case 6: return new NBTTagDouble(((malilib.util.data.tag.DoubleData) data).value);
            case 7: return new NBTTagByteArray(((malilib.util.data.tag.ByteArrayData) data).getByteArray());
            case 8: return new NBTTagString(((malilib.util.data.tag.StringData) data).getString());
            case 9:
            {
                ListData list = (ListData) data;
                NBTTagList tag = new NBTTagList();
                for (int i = 0; i < list.size(); ++i) tag.appendTag(toVanillaTag(list.get(i)));
                return tag;
            }
            case 10: return toVanillaCompound((CompoundData) data);
            case 11: return new NBTTagIntArray(((malilib.util.data.tag.IntArrayData) data).getIntArray());
            case 12: return new NBTTagLongArray(((malilib.util.data.tag.LongArrayData) data).getLongArray());
            default: throw new IllegalArgumentException("Unknown tag type: " + data.getType());
        }
    }
}
