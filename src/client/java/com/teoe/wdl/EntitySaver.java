package com.teoe.wdl;

import net.minecraft.SharedConstants;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;

public class EntitySaver {
    public static void saveEntitiesToRegion(File regionDir, ChunkPos pos, NbtCompound entitiesNbt) {
        int regionX = pos.x >> 5;
        int regionZ = pos.z >> 5;
        File mcaFile = new File(regionDir, "r." + regionX + "." + regionZ + ".mca");

        try {
            try (net.minecraft.world.storage.RegionFile region = new net.minecraft.world.storage.RegionFile(new net.minecraft.world.storage.StorageKey("wdl", net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of("minecraft", "overworld")), "entities"), mcaFile.toPath(), regionDir.toPath(), true)) {
                try (DataOutputStream out = region.getChunkOutputStream(pos)) {
                    net.minecraft.nbt.NbtIo.write(entitiesNbt, out);
                }
                region.sync();
            }
        } catch (Exception e) {
            System.err.println("Failed to save entities " + pos);
            e.printStackTrace();
        }
    }

    public static NbtCompound serializeEntities(ClientWorld world, ChunkPos pos, File baseDir, net.minecraft.registry.DynamicRegistryManager registryManager) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", SharedConstants.getGameVersion().dataVersion().id());
        root.putIntArray("Position", new int[]{pos.x, pos.z});
        
        NbtList entitiesList = new NbtList();
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
            pos.getStartX(), -64, pos.getStartZ(),
            pos.getEndX(), 320, pos.getEndZ()
        );
        
        for (Entity entity : world.getOtherEntities(null, box)) {
            if (entity instanceof net.minecraft.client.network.ClientPlayerEntity) continue;
            
            net.minecraft.storage.NbtWriteView writeView = net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY, registryManager);
            try {
                if (!entity.saveSelfData(writeView)) {
                    if (entity instanceof net.minecraft.client.network.OtherClientPlayerEntity) {
                        entity.writeData(writeView); // Fake players won't save via saveSelfData, so we force writeData
                    } else {
                        continue;
                    }
                }
            } catch (Exception ignored) { continue; }
            NbtCompound entityNbt = writeView.getNbt();
            
            if (entity instanceof net.minecraft.client.network.OtherClientPlayerEntity fakePlayer) {
                String prefix = ConfigManager.fakePlayerPrefix;
                if (prefix != null && !prefix.isEmpty() && fakePlayer.getName().getString().startsWith(prefix)) {
                    try {
                        File playerDataDir = new File(baseDir, "playerdata");
                        playerDataDir.mkdirs();
                        File playerFile = new File(playerDataDir, fakePlayer.getUuidAsString() + ".dat");
                        net.minecraft.nbt.NbtIo.writeCompressed(entityNbt, playerFile.toPath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (entityNbt != null && !entityNbt.isEmpty()) {
                    entitiesList.add(entityNbt);
                }
            }
        }
        
        root.put("Entities", entitiesList);
        return root;
    }
}
