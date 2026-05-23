package com.teoe.wdl.tracker;

import com.teoe.wdl.DownloadManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChestAura {
    private static int tickDelay = 0;
    public static BlockPos currentTarget = null;
    public static boolean waitingForSync = false;
    public static boolean waitingForInventory = false;
    public static int currentSyncId = -1;
    private static int timeout = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!DownloadManager.isRecording() || client.player == null || client.world == null || !com.teoe.wdl.ConfigManager.saveChests || !AutoScanner.isActive()) {
                currentTarget = null;
                return;
            }

            if (tickDelay > 0) {
                tickDelay--;
                return;
            }

            if (currentTarget != null) {
                // Still waiting... timeout?
                timeout--;
                if (timeout <= 0) {
                    if (client.currentScreen != null) {
                        client.setScreen(null);
                    }
                    if (client.player.currentScreenHandler != null && client.player.currentScreenHandler != client.player.playerScreenHandler) {
                        client.player.closeHandledScreen();
                    }
                    if (currentTarget != null) {
                        ChestTracker.ignoredChests.add(currentTarget);
                        client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chestaura.fail", currentTarget.toShortString()), false);
                    }
                    currentTarget = null;
                    waitingForSync = false;
                    waitingForInventory = false;
                    tickDelay = 1; // 1 tick delay on fail as well
                }
                return;
            }

            // Don't auto open if player is already looking at an inventory manually
            if (client.currentScreen != null && currentTarget == null) {
                return;
            }

            // Check distance first, don't even process if no chests are nearby.
            boolean foundAny = false;
            int chunkX = client.player.getChunkPos().x;
            int chunkZ = client.player.getChunkPos().z;
            int renderDistance = client.options.getClampedViewDistance();
            
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                    net.minecraft.world.chunk.WorldChunk chunk = client.world.getChunkManager().getChunk(chunkX + dx, chunkZ + dz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                    if (chunk != null) {
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            BlockPos pos = be.getPos();
                            if (!ChestTracker.savedChests.contains(pos) && !ChestTracker.ignoredChests.contains(pos)) {
                                if (be instanceof net.minecraft.inventory.Inventory || be instanceof LootableContainerBlockEntity) {
                                    // Check if chest is reachable (5 blocks distance, squared = 25.0)
                                    if (client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) < 25.0) {
                                        foundAny = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (foundAny) break;
                }
                if (foundAny) break;
            }
            if (!foundAny) return;

            // Find nearest target
            BlockPos playerPos = client.player.getBlockPos();
            BlockPos bestTarget = null;
            double bestDist = 25.0; // 5 blocks max check distance (squared = 25.0)
            Vec3d playerEyePos = client.player.getEyePos();

            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                    net.minecraft.world.chunk.WorldChunk chunk = client.world.getChunkManager().getChunk(chunkX + dx, chunkZ + dz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                    if (chunk != null) {
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            BlockPos pos = be.getPos();
                            if (!ChestTracker.savedChests.contains(pos) && !ChestTracker.ignoredChests.contains(pos)) {
                                // Check if it's a lootable container or chest
                                if (be instanceof net.minecraft.inventory.Inventory || be instanceof LootableContainerBlockEntity) {
                                    double dist = client.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                                    if (dist < bestDist) { // Must be within 5 blocks
                                        // Ignore line of sight entirely! Just take the closest chest we can reach.
                                        bestDist = dist;
                                        bestTarget = pos;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bestTarget != null) {
                currentTarget = bestTarget;
                ChestTracker.lastClickedBlock = bestTarget;
                timeout = 40; // 2 seconds timeout
                waitingForSync = true;
                waitingForInventory = false;
                
                BlockHitResult hitResult = new BlockHitResult(
                        new Vec3d(bestTarget.getX() + 0.5, bestTarget.getY() + 0.5, bestTarget.getZ() + 0.5),
                        Direction.UP, bestTarget, false);
                        
                // Get accurate hit result from raycast if possible
                BlockHitResult raycastResult = client.world.raycast(new net.minecraft.world.RaycastContext(
                        playerEyePos, new Vec3d(bestTarget.getX() + 0.5, bestTarget.getY() + 0.5, bestTarget.getZ() + 0.5), 
                        net.minecraft.world.RaycastContext.ShapeType.OUTLINE, 
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, 
                        client.player));
                
                if (raycastResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && raycastResult.getBlockPos().equals(bestTarget)) {
                    hitResult = raycastResult;
                } else {
                    // Force the block pos to be exactly the target, to make sure interactBlock is pointing to the block!
                    hitResult = new BlockHitResult(
                        new Vec3d(bestTarget.getX() + 0.5, bestTarget.getY() + 0.5, bestTarget.getZ() + 0.5),
                        Direction.UP, bestTarget, true); // inside block
                }

                // If raycast failed but we are close, use a default visible direction
                if (hitResult.getSide() == null) {
                    hitResult = hitResult.withSide(Direction.UP);
                }

                // Silent fast packet sending
                int sequence = 0;
                client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence));
                
                // client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chestaura.trying", bestTarget.toShortString(), "SILENT"), false);
                // No swinging hand or chat messages to make it completely invisible and fast!
            }
        });
    }
}