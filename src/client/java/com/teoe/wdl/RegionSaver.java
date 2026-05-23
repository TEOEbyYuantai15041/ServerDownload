package com.teoe.wdl;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.RegistryWrapper;

import java.io.DataOutputStream;
import java.io.File;

public class RegionSaver {

    public static void saveChunkToRegion(File regionDir, ChunkPos pos, NbtCompound chunkNbt) {
        int regionX = pos.x >> 5;
        int regionZ = pos.z >> 5;
        File mcaFile = new File(regionDir, "r." + regionX + "." + regionZ + ".mca");

        try {
            // 使用 Minecraft 原生的 RegionFile 系统自动写入 .mca
            try (net.minecraft.world.storage.RegionFile region = new net.minecraft.world.storage.RegionFile(new net.minecraft.world.storage.StorageKey("wdl", net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of("minecraft", "overworld")), "chunks"), mcaFile.toPath(), regionDir.toPath(), true)) {
                try (DataOutputStream out = region.getChunkOutputStream(pos)) {
                    net.minecraft.nbt.NbtIo.write(chunkNbt, out);
                }
                region.sync(); // Force sync to disk to prevent data loss on crash/disconnect
            }
        } catch (Exception e) {
            System.err.println("Failed to save chunk " + pos);
            e.printStackTrace();
        }
    }

    public static NbtCompound serializeClientChunk(ClientWorld world, WorldChunk chunk, net.minecraft.registry.DynamicRegistryManager registryManager) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", SharedConstants.getGameVersion().dataVersion().id());
        root.putInt("xPos", chunk.getPos().x);
        root.putInt("zPos", chunk.getPos().z);
        root.putInt("yPos", chunk.getBottomY());
        root.putString("Status", "minecraft:full"); // 标记为完整区块

        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, registryManager);
        Registry<Biome> biomeRegistry = registryManager.getOrThrow(RegistryKeys.BIOME);

        NbtList sectionsList = new NbtList();
        ChunkSection[] sections = chunk.getSectionArray();

        // 遍历区块所有的 16x16x16 小节，并使用自带的编解码器转换为标准 NBT
        for (int y = 0; y < sections.length; y++) {
            ChunkSection section = sections[y];
            if (section.isEmpty()) continue;

            NbtCompound sectionNbt = new NbtCompound();
            sectionNbt.putByte("Y", (byte) chunk.sectionIndexToCoord(y));

            // 序列化方块 (BlockStates)
            PalettedContainer<BlockState> blockStates = section.getBlockStateContainer();
            Codec<ReadableContainer<BlockState>> blockCodec = PalettedContainer.createReadableContainerCodec(
                Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState()
            );
            blockCodec.encodeStart(ops, blockStates).result().ifPresent(elem -> sectionNbt.put("block_states", elem));

            // 序列化群系 (Biomes)
            ReadableContainer<RegistryEntry<Biome>> biomes = section.getBiomeContainer();
            Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec = PalettedContainer.createReadableContainerCodec(
                biomeRegistry.getIndexedEntries(), biomeRegistry.getEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.getOrThrow(BiomeKeys.PLAINS)
            );
            biomeCodec.encodeStart(ops, biomes).result().ifPresent(elem -> sectionNbt.put("biomes", elem));

            sectionsList.add(sectionNbt);
        }
        root.put("sections", sectionsList);

        // 保存方块实体 (如箱子内容、告示牌文字)
        NbtList blockEntitiesList = new NbtList();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            net.minecraft.util.math.BlockPos pos = be.getPos();
            if (DownloadManager.savedBlockEntitiesCache.containsKey(pos)) {
                blockEntitiesList.add(DownloadManager.savedBlockEntitiesCache.get(pos));
            } else {
                NbtCompound beNbt = be.createNbtWithIdentifyingData(registryManager);
                blockEntitiesList.add(beNbt);
            }
        }
        root.put("block_entities", blockEntitiesList);

        return root;
    }
}