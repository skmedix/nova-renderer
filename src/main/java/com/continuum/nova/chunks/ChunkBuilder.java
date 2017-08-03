package com.continuum.nova.chunks;

import com.continuum.nova.NovaNative;
import com.continuum.nova.utils.DefaultHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Splits chunks up into meshes with one mesh for each shader
 *
 * @author ddubois
 * @since 27-Jul-17
 */
public class ChunkBuilder {
    private static final Logger LOG = LogManager.getLogger(ChunkBuilder.class);
    private final BlockModelShapes modelManager;
    private World world;

    private final Map<String, IGeometryFilter> filters;

    private final AtomicLong timeSpentInBlockRenderUpdate = new AtomicLong(0);
    private final AtomicInteger numChunksUpdated = new AtomicInteger(0);

    public ChunkBuilder(Map<String, IGeometryFilter> filters, World world, BlockModelShapes modelManager) {
        this.filters = filters;
        this.world = world;
        this.modelManager = modelManager;
    }

    public void createMeshesForChunk(ChunkUpdateListener.BlockUpdateRange range) {
        LOG.debug("Updating chunk {}", range);
        Map<String, List<BlockPos>> blocksForFilter = new DefaultHashMap<>(ArrayList::new);
        long startTime = System.currentTimeMillis();

        for(int x = range.min.x; x <= range.max.x; x++) {
            for(int y = range.min.y; y < range.max.y; y++) {
                for(int z = range.min.z; z <= range.max.z; z++) {
                    IBlockState blockState = world.getBlockState(new BlockPos(x, y, z));
                    for(Map.Entry<String, IGeometryFilter> entry : filters.entrySet()) {
                        if(entry.getValue().matches(blockState)) {
                            blocksForFilter.get(entry.getKey()).add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }

        int chunkHashCode = range.min.x;
        chunkHashCode = 31 * chunkHashCode + range.min.z;

        Map<String, List<NovaNative.mc_basic_render_object>> geometriesForFilter = new DefaultHashMap<>(ArrayList::new);
        for(String filterName : blocksForFilter.keySet()) {
            NovaNative.mc_basic_render_object renderObj = makeMeshForBlocks(blocksForFilter.get(filterName), world);
            renderObj.id = chunkHashCode;
            renderObj.x = range.min.x;
            renderObj.y = range.min.y;
            renderObj.z = range.min.z;

            geometriesForFilter.get(filterName).add(renderObj);
        }

        long timeAfterBuildingStruct = System.currentTimeMillis();
        // Using JNA in the old way: 550 ms / chunk

        for(String filterName : geometriesForFilter.keySet()) {
            if(!geometriesForFilter.get(filterName).isEmpty()) {
                for(NovaNative.mc_basic_render_object obj : geometriesForFilter.get(filterName)) {
                    NovaNative.INSTANCE.add_chunk_geometry_for_filter(filterName, obj);
                }
            }
        }
        long timeAfterSendingToNative = System.currentTimeMillis();

        long deltaTime = System.currentTimeMillis() - startTime;
        timeSpentInBlockRenderUpdate.addAndGet(deltaTime);
        numChunksUpdated.incrementAndGet();

        if(numChunksUpdated.get() % 10 == 0) {
            LOG.debug("It's taken an average of {}ms to update {} chunks",
                    (float) timeSpentInBlockRenderUpdate.get() / numChunksUpdated.get(), numChunksUpdated);
            LOG.debug("Detailed stats:\nTime to build chunk: {}ms\nTime to process chunk in native code: {}ms",
                    timeAfterBuildingStruct - startTime, timeAfterSendingToNative - timeAfterBuildingStruct);
        }
    }

    private NovaNative.mc_basic_render_object makeMeshForBlocks(List<BlockPos> blockStates, World world) {
        List<Integer> vertexData = new ArrayList<>();
        IndexList indices = new IndexList();
        NovaNative.mc_basic_render_object chunk_render_object = new NovaNative.mc_basic_render_object();

        int blockIndexCounter = 0;
        for(BlockPos blockPos : blockStates) {
            IBlockState blockState = world.getBlockState(blockPos);
            IBakedModel blockModel = modelManager.getModelForState(blockState);

            List<BakedQuad> quads = new ArrayList<>();
            for(EnumFacing facing : EnumFacing.values()) {
                IBlockState neighbor = blockNextTo(blockPos, world, facing);

                // Only add faces if the block next to us is transparent and not the same as us
                if(neighbor.isTranslucent() && !neighbor.getBlock().equals(blockState.getBlock())) {
                    quads.addAll(blockModel.getQuads(blockState, facing, 0));
                }
            }

            LOG.trace("Retrieved {} faces for BlocKState {}", quads.size(), blockState);

            int faceIndexCounter = 0;
            for(BakedQuad quad : quads) {
                int[] quadVertexData = addPosition(quad, blockPos);

                for(int data : quadVertexData) {
                    vertexData.add(data);
                }

                indices.addIndicesForFace(faceIndexCounter, blockIndexCounter);
                faceIndexCounter += 4;
            }

            blockIndexCounter += faceIndexCounter;
        }

        chunk_render_object.setVertex_data(vertexData);
        chunk_render_object.setIndices(indices);
        chunk_render_object.format = NovaNative.NovaVertexFormat.POS_UV_LIGHTMAPUV_NORMAL_TANGENT.ordinal();

        return chunk_render_object;
    }

    private int[] addPosition(BakedQuad quad, BlockPos blockPos) {
        int[] data = Arrays.copyOf(quad.getVertexData(), quad.getVertexData().length);
        float x = Float.intBitsToFloat(data[0]);
        float y = Float.intBitsToFloat(data[1]);
        float z = Float.intBitsToFloat(data[2]);

        x += blockPos.getX();
        y += blockPos.getY();
        z += blockPos.getZ();

        data[0] = Float.floatToIntBits(x);
        data[1] = Float.floatToIntBits(y);
        data[2] = Float.floatToIntBits(z);

        return data;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Returns the IBlockState of the block next to the block at the provided position in the given direction
     *
     * @param pos The position to get the block next to
     * @param world The world that all these blocks are in
     * @param direction The direction to look in
     * @return The IBlockState of the block in the provided direction
     */
    private static IBlockState blockNextTo(BlockPos pos, World world, EnumFacing direction) {
        BlockPos lookupPos = pos.add(direction.getDirectionVec());
        return world.getBlockState(lookupPos);
    }

    /**
     * This class only exists to make the code on line 111 work
     */
    private static class IndexCounter {
        private int index = 0;

        public void nextFace() {
            index += 4;
        }

        public int getIndex() {
            return index;
        }
    }
}