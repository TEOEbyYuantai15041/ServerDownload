package com.teoe.wdl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import com.teoe.wdl.tracker.ChestTracker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Set;

public class DownloadManager {
    private static boolean isRecording = false;
    public static long currentSeed = 0;
    
    public static final Map<BlockPos, NbtCompound> savedBlockEntitiesCache = new ConcurrentHashMap<>();
    public static final Set<ChunkPos> historicallySavedChunks = ConcurrentHashMap.newKeySet();
    public static final Set<BlockPos> discoveredChests = ConcurrentHashMap.newKeySet();
    private static final BlockingQueue<Runnable> saveQueue = new LinkedBlockingQueue<>();
    private static Thread saveThread;

    private static net.minecraft.registry.DynamicRegistryManager cachedRegistryManager;

    public static void startRecording() {
        isRecording = true;
        com.teoe.wdl.server.MapGenerator.clear();

        historicallySavedChunks.clear();
        discoveredChests.clear();
        saveQueue.clear();
        savedBlockEntitiesCache.clear();
        ChestTracker.ignoredChests.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            cachedRegistryManager = client.world.getRegistryManager();
            
            // Save currently loaded chunks immediately
            int viewDistance = client.options.getClampedViewDistance();
            ChunkPos centerPos = client.player.getChunkPos();
            String dimension = client.world.getRegistryKey().getValue().getPath();
            
            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    net.minecraft.world.chunk.WorldChunk chunk = client.world.getChunkManager().getWorldChunk(centerPos.x + x, centerPos.z + z, false);
                    if (chunk != null) {
                        queueChunkSave(dimension, chunk);
                    }
                }
            }
        }
        startSaveThread();
    }
    
    public static void startRecording(long seed) {
        currentSeed = seed;
        startRecording();
    }

    private static void startSaveThread() {
        if (saveThread == null || !saveThread.isAlive()) {
            saveThread = new Thread(() -> {
                while (isRecording || !saveQueue.isEmpty()) {
                    try {
                        Runnable task = saveQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                        if (task != null) {
                            task.run();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, "TeoeWDL-Save-Thread");
            saveThread.start();
        }
    }

    public static void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        
        MinecraftClient client = MinecraftClient.getInstance();
        NbtCompound playerNbt = null;
        int spawnX = 0, spawnY = 0, spawnZ = 0;
        String playerUuid = null;
        
        if (client.player != null && cachedRegistryManager != null) {
            spawnX = (int) client.player.getX();
            spawnY = (int) client.player.getY();
            spawnZ = (int) client.player.getZ();
            playerUuid = client.player.getUuidAsString();
            
            net.minecraft.storage.NbtWriteView writeView = net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY, cachedRegistryManager);
            if (!client.player.saveSelfData(writeView)) {
                client.player.writeData(writeView);
            }
            playerNbt = writeView.getNbt();
            playerNbt.putString("id", "minecraft:player");
        }
        
        final NbtCompound finalPlayerNbt = playerNbt;
        final int finalSpawnX = spawnX;
        final int finalSpawnY = spawnY;
        final int finalSpawnZ = spawnZ;
        final String finalPlayerUuid = playerUuid;
        
        ModLogger.log("Pushing all loaded chunks into the save queue..."); 
        
        // Serialize all currently loaded chunks within view distance
        int viewDistance = client.options.getClampedViewDistance();
        ChunkPos centerPos = client.player.getChunkPos();
        String dimension = client.world.getRegistryKey().getValue().getPath();
        
        for (int x = -viewDistance; x <= viewDistance; x++) {
            for (int z = -viewDistance; z <= viewDistance; z++) {
                net.minecraft.world.chunk.WorldChunk chunk = client.world.getChunkManager().getWorldChunk(centerPos.x + x, centerPos.z + z, false);
                if (chunk != null) {
                    queueChunkSave(dimension, chunk);
                }
            }
        }
        
        // Push level.dat save task
        saveQueue.add(() -> {
            saveLevelDat(finalPlayerNbt, finalSpawnX, finalSpawnY, finalSpawnZ, finalPlayerUuid);
            ModLogger.log("Map saving completed!");
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.command.save_success"), false);
                }
            });
        });
        
        // Force the save thread to run one more time if it's sleeping,
        // or recreate it if it died, to ensure the stop queue is flushed.
        startSaveThread();
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static boolean isSaving() {
        return !saveQueue.isEmpty();
    }

    public static void onChunkReceived(WorldChunk chunk) {
        if (!isRecording) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        String dimension = client.world.getRegistryKey().getValue().getPath();
        
        // Filter out chunks outside the AutoScanner radius if it's active
        if (com.teoe.wdl.tracker.AutoScanner.isActive() && com.teoe.wdl.tracker.AutoScanner.startPos != null) {
            double distSq = (chunk.getPos().getStartX() + 8 - com.teoe.wdl.tracker.AutoScanner.startPos.x) * (chunk.getPos().getStartX() + 8 - com.teoe.wdl.tracker.AutoScanner.startPos.x) + 
                            (chunk.getPos().getStartZ() + 8 - com.teoe.wdl.tracker.AutoScanner.startPos.z) * (chunk.getPos().getStartZ() + 8 - com.teoe.wdl.tracker.AutoScanner.startPos.z);
            if (distSq > com.teoe.wdl.tracker.AutoScanner.scanRadius * com.teoe.wdl.tracker.AutoScanner.scanRadius) {
                return; // Discard chunks completely outside the radius
            }
        }


        
        // Record for AutoScanner
        for (net.minecraft.block.entity.BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof net.minecraft.inventory.Inventory || be instanceof net.minecraft.block.entity.LootableContainerBlockEntity) {
                net.minecraft.block.Block block = be.getCachedState().getBlock();
                if (block == net.minecraft.block.Blocks.CHISELED_BOOKSHELF ||
                    block == net.minecraft.block.Blocks.JUKEBOX ||
                    block == net.minecraft.block.Blocks.CAMPFIRE ||
                    block == net.minecraft.block.Blocks.SOUL_CAMPFIRE ||
                    block == net.minecraft.block.Blocks.LECTERN ||
                    block == net.minecraft.block.Blocks.DECORATED_POT) {
                    continue; // Ignore blocks that don't have a normal chest GUI
                }
                discoveredChests.add(be.getPos());
            }
        }

        // Queue an initial save just in case
        queueChunkSave(dimension, chunk);
    }

    public static void saveChunk(ChunkPos pos) {
        if (!isRecording) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            net.minecraft.world.chunk.WorldChunk chunk = client.world.getChunkManager().getWorldChunk(pos.x, pos.z, false);
            if (chunk != null) {
                com.teoe.wdl.server.MapGenerator.updateChunk(chunk);
                
                String dimension = client.world.getRegistryKey().getValue().getPath();
                queueChunkSave(dimension, chunk);
            }
        }
    }

    private static File getSaveDir() {
        MinecraftClient client = MinecraftClient.getInstance();
        String serverName = "DownloadedServer";
        if (client.getCurrentServerEntry() != null) {
            serverName = client.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9.-]", "_");
        }
        return new File(client.runDirectory, "wdl_saves/" + serverName + "_WDL");
    }

    private static void queueChunkSave(String dimension, WorldChunk chunk) {
        if (cachedRegistryManager == null) return;
        com.teoe.wdl.server.MapGenerator.updateChunk(chunk);
        try {
            // Serialize on main thread to avoid ConcurrentModificationException
            MinecraftClient client = MinecraftClient.getInstance();
            
            if (com.teoe.wdl.tracker.AutoScanner.scanNether && (dimension.equals("overworld") || dimension.equals("the_nether"))) {
                net.minecraft.world.chunk.ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    net.minecraft.world.chunk.ChunkSection section = sections[i];
                    if (!section.isEmpty() && section.hasAny(state -> state.isOf(net.minecraft.block.Blocks.NETHER_PORTAL))) {
                        int yOffset = chunk.getBottomY() + (i * 16);
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    if (section.getBlockState(x, y, z).isOf(net.minecraft.block.Blocks.NETHER_PORTAL)) {
                                        com.teoe.wdl.tracker.AutoScanner.netherPortals.add(chunk.getPos().getStartPos().add(x, yOffset + y, z));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            NbtCompound nbt = RegionSaver.serializeClientChunk(client.world, chunk, cachedRegistryManager);
            ChunkPos pos = chunk.getPos();
            File baseDirForEntities = getSaveDir();
            NbtCompound entitiesNbt = EntitySaver.serializeEntities(client.world, pos, baseDirForEntities, cachedRegistryManager);
            
            saveQueue.add(() -> {
                try {
                    File baseDir = getSaveDir();
                    File regionDir;
                    File entitiesDir;
                    if (dimension.equals("the_nether")) {
                        regionDir = new File(baseDir, "DIM-1/region");
                        entitiesDir = new File(baseDir, "DIM-1/entities");
                    } else if (dimension.equals("the_end")) {
                        regionDir = new File(baseDir, "DIM1/region");
                        entitiesDir = new File(baseDir, "DIM1/entities");
                    } else {
                        regionDir = new File(baseDir, "region");
                        entitiesDir = new File(baseDir, "entities");
                    }
                    regionDir.mkdirs();
                    entitiesDir.mkdirs();
                    RegionSaver.saveChunkToRegion(regionDir, pos, nbt);
                    if (entitiesNbt != null && entitiesNbt.contains("Entities") && !((net.minecraft.nbt.NbtList)entitiesNbt.get("Entities")).isEmpty()) {
                        EntitySaver.saveEntitiesToRegion(entitiesDir, pos, entitiesNbt);
                    }
                    historicallySavedChunks.add(pos);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveLevelDat(NbtCompound playerNbt, int spawnX, int spawnY, int spawnZ, String playerUuid) {
        File baseDir = getSaveDir();
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            ModLogger.log("Failed to create save directory: " + baseDir.getAbsolutePath());
        }

        try {
            NbtCompound root = new NbtCompound();
            NbtCompound data = new NbtCompound();
            data.putString("LevelName", "Downloaded Server");
            data.putBoolean("allowCommands", true);
            data.putInt("version", 19133);
            
            NbtCompound worldGenSettings = new NbtCompound();
            worldGenSettings.putLong("seed", currentSeed);
            worldGenSettings.putBoolean("generate_features", true);
            worldGenSettings.putBoolean("bonus_chest", false);
            
            NbtCompound dimensions = new NbtCompound();
            NbtCompound overworld = new NbtCompound();
            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:noise");
            generator.putString("settings", "minecraft:overworld");
            
            NbtCompound biomeSource = new NbtCompound();
            biomeSource.putString("type", "minecraft:multi_noise");
            biomeSource.putString("preset", "minecraft:overworld");
            generator.put("biome_source", biomeSource);
            
            generator.putLong("seed", currentSeed);
            overworld.put("generator", generator);
            overworld.putString("type", "minecraft:overworld");
            
            dimensions.put("minecraft:overworld", overworld);
            worldGenSettings.put("dimensions", dimensions);
            
            data.put("WorldGenSettings", worldGenSettings);
            
            data.putLong("RandomSeed", currentSeed);
            if (playerNbt != null) {
                data.putInt("SpawnX", spawnX);
                data.putInt("SpawnY", spawnY);
                data.putInt("SpawnZ", spawnZ);
                
                data.put("Player", playerNbt);
                
                try {
                    File playerDataDir = new File(baseDir, "playerdata");
                    playerDataDir.mkdirs();
                    File playerFile = new File(playerDataDir, playerUuid + ".dat");
                    net.minecraft.nbt.NbtIo.writeCompressed(playerNbt, playerFile.toPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            root.put("Data", data);
            NbtIo.writeCompressed(root, new File(baseDir, "level.dat").toPath());
        } catch (Exception e) {
            ModLogger.log("Failed to save level.dat: " + e.getMessage());
            e.printStackTrace();
        }
    }
}