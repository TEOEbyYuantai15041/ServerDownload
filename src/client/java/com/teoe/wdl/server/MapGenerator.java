package com.teoe.wdl.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapGenerator {
    // Stores the 16x16 RGB map colors for each downloaded chunk
    public static final Map<ChunkPos, int[]> chunkColors = new ConcurrentHashMap<>();
    
    private static int minChunkX = Integer.MAX_VALUE;
    private static int minChunkZ = Integer.MAX_VALUE;
    private static int maxChunkX = Integer.MIN_VALUE;
    private static int maxChunkZ = Integer.MIN_VALUE;

    public static void clear() {
        chunkColors.clear();
        minChunkX = Integer.MAX_VALUE;
        minChunkZ = Integer.MAX_VALUE;
        maxChunkX = Integer.MIN_VALUE;
        maxChunkZ = Integer.MIN_VALUE;
    }

    public static int getMinChunkX() { return minChunkX; }
    public static int getMinChunkZ() { return minChunkZ; }
    public static int getMaxChunkX() { return maxChunkX; }
    public static int getMaxChunkZ() { return maxChunkZ; }

    public static void updateChunk(WorldChunk chunk) {
        if (chunk == null) return;
        ChunkPos pos = chunk.getPos();
        int[] colors = new int[256];
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos topPos = new BlockPos(pos.getStartX() + x, height - 1, pos.getStartZ() + z);
                net.minecraft.block.BlockState state = chunk.getBlockState(topPos);
                
                // Get the MapColor of the block
                int baseColor = state.getMapColor(MinecraftClient.getInstance().world, topPos).color;
                
                // Calculate basic shading based on height variation (similar to vanilla maps)
                // We'll compare with the height of the block to the north (z - 1)
                int northHeight = height;
                if (z > 0) {
                    northHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z - 1);
                } else {
                    // For cross-chunk boundaries, just use current height to avoid complex lookups
                    northHeight = height;
                }
                
                double shadeModifier = 1.0;
                if (height > northHeight) {
                    shadeModifier = 1.2; // Brighter
                } else if (height < northHeight) {
                    shadeModifier = 0.8; // Darker
                }
                
                // Special handling for water depth (approximate)
                if (state.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER) || state.getFluidState().isOf(net.minecraft.fluid.Fluids.FLOWING_WATER)) {
                    int oceanFloor = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR, x, z);
                    int depth = height - oceanFloor;
                    if (depth > 10) shadeModifier *= 0.7;
                    else if (depth > 5) shadeModifier *= 0.85;
                    else shadeModifier *= 1.1; // Shallow water is brighter
                }

                int r = (int) (((baseColor >> 16) & 0xFF) * shadeModifier);
                int g = (int) (((baseColor >> 8) & 0xFF) * shadeModifier);
                int b = (int) ((baseColor & 0xFF) * shadeModifier);
                
                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));
                
                colors[z * 16 + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        
        chunkColors.put(pos, colors);
        
        synchronized (MapGenerator.class) {
            if (pos.x < minChunkX) minChunkX = pos.x;
            if (pos.x > maxChunkX) maxChunkX = pos.x;
            if (pos.z < minChunkZ) minChunkZ = pos.z;
            if (pos.z > maxChunkZ) maxChunkZ = pos.z;
        }
    }

    public static byte[] generateMapJpg() {
        if (chunkColors.isEmpty()) {
            return new byte[0];
        }

        int widthChunks, heightChunks;
        int localMinX, localMinZ;
        
        synchronized (MapGenerator.class) {
            localMinX = minChunkX;
            localMinZ = minChunkZ;
            widthChunks = maxChunkX - minChunkX + 1;
            heightChunks = maxChunkZ - minChunkZ + 1;
        }

        // Prevent OOM for insanely large maps. Limit to 8192x8192 pixels (512x512 chunks)
        if (widthChunks > 512) widthChunks = 512;
        if (heightChunks > 512) heightChunks = 512;

        int pixelWidth = widthChunks * 16;
        int pixelHeight = heightChunks * 16;

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);

        for (Map.Entry<ChunkPos, int[]> entry : chunkColors.entrySet()) {
            ChunkPos pos = entry.getKey();
            int[] colors = entry.getValue();

            int chunkOffsetX = pos.x - localMinX;
            int chunkOffsetZ = pos.z - localMinZ;

            if (chunkOffsetX >= 0 && chunkOffsetX < widthChunks && chunkOffsetZ >= 0 && chunkOffsetZ < heightChunks) {
                int startPixelX = chunkOffsetX * 16;
                int startPixelZ = chunkOffsetZ * 16;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        image.setRGB(startPixelX + x, startPixelZ + z, colors[z * 16 + x]);
                    }
                }
            }
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
