package com.teoe.wdl.tracker;

import com.teoe.wdl.DownloadManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;

public class AutoScanner {
    public enum Phase {
        IDLE, CHUNKS, DROP, CHESTS, MANUAL_PROMPT, MANUAL_SCAN, RETURN, GOTO_PORTAL, WAIT_PORTAL
    }
    
    private static Phase currentPhase = Phase.IDLE;
    
    public static Phase getCurrentPhase() {
        return currentPhase;
    }
    
    private static final Queue<ChunkPos> chunkQueue = new LinkedList<>();
    private static ChunkPos currentChunkTarget = null;
    
    public static boolean scanNether = false;
    public static String startingDimension = "";
    public static boolean saveChests = true;
    public static final List<BlockPos> netherPortals = new ArrayList<>();
    public static BlockPos currentPortalTarget = null;
    
    private static final List<BlockPos> chestList = new ArrayList<>();
    public static BlockPos currentChestTarget = null;
    private static List<BlockPos> currentChestPath = null;
    private static int chestWaitTicks = 0;
    private static Vec3d lastXzPos = null;
    private static int ticksStuckXz = 0;
    private static int recalcAttempts = 0;
    
    public static String ignoredPlayerPrefix = "";

    private static int ticksStuck = 0;
    private static Vec3d lastPos = null;

    public static Vec3d startPos = null;
    public static int scanRadius = 0;
    private static Vec3d homeTarget = null;

    public static void init() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (currentPhase == Phase.MANUAL_PROMPT) {
                if (message.equalsIgnoreCase("y")) {
                    currentPhase = Phase.MANUAL_SCAN;
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.manual_start"), false);
                    return false;
                } else if (message.equalsIgnoreCase("n") || message.equalsIgnoreCase("stop")) {
                    currentPhase = Phase.RETURN;
                    homeTarget = startPos;
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.chests_done_returning"), false);
                    return false;
                }
            } else if (currentPhase == Phase.MANUAL_SCAN) {
                if (message.equalsIgnoreCase("stop")) {
                    currentPhase = Phase.RETURN;
                    homeTarget = startPos;
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.chests_done_returning"), false);
                    return false;
                }
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (currentPhase == Phase.IDLE || !DownloadManager.isRecording() || client.player == null || client.world == null) {
                currentPhase = Phase.IDLE;
                return;
            }

            ClientPlayerEntity player = client.player;
            
            boolean dropping = false;
            if (currentPhase == Phase.RETURN && homeTarget != null) {
                double dx = homeTarget.x - player.getX();
                double dz = homeTarget.z - player.getZ();
                if (dx * dx + dz * dz < 25.0 && !player.verticalCollision) {
                    dropping = true;
                }
            } else if (currentPhase == Phase.DROP) {
                dropping = true;
            }

            // Critical NoFall fix:
            // The server calculates fall damage based on the Y-distance traveled since the last onGround=true.
            // If we send onGround=true while falling faster than 3.0 blocks/tick, the server calculates damage BEFORE resetting!
            // We MUST cap vertical velocity to -2.5 so the tick-by-tick fall distance never exceeds the 3.0 damage threshold.
            if (player.getVelocity().y < -2.5) {
                player.setVelocity(player.getVelocity().x, -2.5, player.getVelocity().z);
            }

            if (!dropping) {
                player.getAbilities().flying = true;
                player.fallDistance = 0.0f; 
                if (player.getVelocity().y < 0) {
                    client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
                }
            } else {
                player.getAbilities().flying = false;
                player.fallDistance = 0.0f;
                // Send onGround every tick while dropping. Since velocity is capped at -2.5, server damage is always 0.
                client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
            }

            if (currentPhase == Phase.RETURN) {
                handleReturningHome(client, player);
                return;
            }

            if (currentPhase == Phase.CHUNKS) {
                handleChunkScan(client, player);
                return;
            }

            if (currentPhase == Phase.DROP) {
                if ((player.verticalCollision && player.getVelocity().y >= -0.1) || player.isTouchingWater() || player.isInLava()) {
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.landing_success"), false);
                    if (com.teoe.wdl.ConfigManager.saveChests) {
                        currentPhase = Phase.CHESTS;
                        currentChestTarget = null;
                        currentChestPath = null;
                        ChestTracker.ignoredChests.clear();
                    } else {
                        currentPhase = Phase.RETURN;
                        homeTarget = startPos;
                    }
                } else {
                    player.setVelocity(0, player.getVelocity().y, 0); // Drop straight down
                }
                return;
            }

            if (currentPhase == Phase.CHESTS) {
                handleChestScan(client, player);
                return;
            }

            if (currentPhase == Phase.MANUAL_PROMPT) {
                // Just wait for user input
                player.setVelocity(0, player.getVelocity().y, 0); // Drop straight down
                return;
            }

            if (currentPhase == Phase.MANUAL_SCAN) {
                // Check if all ignored chests are saved now
                int remaining = 0;
                for (BlockPos pos : ChestTracker.ignoredChests) {
                    if (!ChestTracker.savedChests.contains(pos)) {
                        remaining++;
                    }
                }
                if (remaining == 0) {
                    currentPhase = Phase.RETURN;
                    homeTarget = startPos;
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.chests_done_returning"), false);
                } else {
                    // ESP for remaining chests
                    for (BlockPos pos : ChestTracker.ignoredChests) {
                        if (!ChestTracker.savedChests.contains(pos)) {
                            if (client.world.random.nextInt(3) == 0) {
                                client.particleManager.addParticle(
                                    net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                                    pos.getX() + 0.5 + (client.world.random.nextDouble() - 0.5),
                                    pos.getY() + 0.5 + (client.world.random.nextDouble() - 0.5),
                                    pos.getZ() + 0.5 + (client.world.random.nextDouble() - 0.5),
                                    0, 0, 0
                                );
                            }
                        }
                    }
                }
                return;
            }

            if (currentPhase == Phase.GOTO_PORTAL) {
                handleGotoPortal(client, player);
                return;
            }

            if (currentPhase == Phase.WAIT_PORTAL) {
                handleWaitPortal(client, player);
                return;
            }
        });
    }

    private static void handleChunkScan(MinecraftClient client, ClientPlayerEntity player) {

        if (currentChunkTarget == null) {
            while (!chunkQueue.isEmpty()) {
                ChunkPos pos = chunkQueue.poll();
                if (!DownloadManager.historicallySavedChunks.contains(pos)) {
                    currentChunkTarget = pos;
                    ticksStuck = 0;
                    break;
                }
            }

            if (currentChunkTarget == null) {
                if (com.teoe.wdl.ConfigManager.saveChests) {
                    currentPhase = Phase.DROP;
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.scan_done_dropping"), false);
                } else {
                    currentPhase = Phase.RETURN;
                    homeTarget = startPos;
                    MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.scan_done_returning"), false);
                }
                return;
            }
        }

        double targetX = currentChunkTarget.getStartX() + 8.0;
        double targetY = 500.0;
        double targetZ = currentChunkTarget.getStartZ() + 8.0;

        Vec3d targetVec = new Vec3d(targetX, targetY, targetZ);
        Vec3d currentVec = player.getPos();
        
        if (player.getChunkPos().equals(currentChunkTarget)) {
            currentChunkTarget = null;
            return;
        }

        Vec3d diff = targetVec.subtract(currentVec);
        Vec3d dir = diff.normalize();
        
        player.setVelocity(dir.multiply(3.0));

        if (lastPos != null && lastPos.squaredDistanceTo(currentVec) < 0.1) {
            ticksStuck++;
            if (ticksStuck > 40) {
                currentChunkTarget = null;
                ticksStuck = 0;
            }
        } else {
            ticksStuck = 0;
        }
        lastPos = currentVec;
    }

    private static void handleChestScan(MinecraftClient client, ClientPlayerEntity player) {

        if (currentChestTarget == null) {
            // Dynamically refresh chestList from discoveredChests
            chestList.clear();
            for (BlockPos pos : DownloadManager.discoveredChests) {
                if (!ChestTracker.savedChests.contains(pos) && !ChestTracker.ignoredChests.contains(pos)) {
                    // Check if chunk is ignored by the ChunkFilter stick
                    if (ChunkFilter.ignoredChunks.contains(new net.minecraft.util.math.ChunkPos(pos))) {
                        continue;
                    }

                    // Pre-filter chests that are physically impossible to open
                    net.minecraft.block.BlockState upState = client.world.getBlockState(pos.up());
                    if (upState.isSolidBlock(client.world, pos.up())) {
                        ChestTracker.ignoredChests.add(pos);
                        continue;
                    }

                    double distSq = (pos.getX() - startPos.x) * (pos.getX() - startPos.x) + 
                                    (pos.getZ() - startPos.z) * (pos.getZ() - startPos.z);
                    if (distSq <= scanRadius * scanRadius) {
                        chestList.add(pos);
                    }
                }
            }
            
            if (chestList.isEmpty()) {
                int remaining = 0;
                for (BlockPos pos : ChestTracker.ignoredChests) {
                    if (!ChestTracker.savedChests.contains(pos)) remaining++;
                }
                
                if (remaining > 0) {
                    currentPhase = Phase.MANUAL_PROMPT;
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.manual_prompt", remaining), false);
                } else {
                    currentPhase = Phase.RETURN;
                    homeTarget = startPos;
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.chests_done_returning"), false);
                }
                return;
            }

            // Find nearest chest dynamically
            Vec3d playerPos = player.getPos();
            BlockPos nearest = null;
            double minDist = Double.MAX_VALUE;
            
            for (BlockPos pos : chestList) {
                double dist = pos.getSquaredDistance(playerPos);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = pos;
                }
            }
            
            currentChestTarget = nearest;
            chestList.remove(nearest);
            chestWaitTicks = 0;
            ticksStuck = 0;
            ticksStuckXz = 0;
            lastXzPos = null;
            recalcAttempts = 0;
            currentChestPath = null;
            
            // Check if we can already reach the chest directly BEFORE pathfinding!
            Vec3d chestVec = Vec3d.ofCenter(currentChestTarget);
            double distSq = player.getPos().squaredDistanceTo(chestVec);
            boolean inRange = false;
            
            if (distSq < 18.0) { // Max potential reach 4.2 blocks
                net.minecraft.util.hit.HitResult result = client.world.raycast(new net.minecraft.world.RaycastContext(
                    player.getCameraPosVec(1.0F), chestVec,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, player
                ));
                
                boolean hasLineOfSight = (result.getType() == net.minecraft.util.hit.HitResult.Type.MISS || 
                   (result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && ((net.minecraft.util.hit.BlockHitResult)result).getBlockPos().equals(currentChestTarget)));
                   
                if (hasLineOfSight) {
                    inRange = true; // Clear line of sight, 4.2 reach is fine
                } else if (distSq < 7.0) { // ~2.6 blocks max for through-wall interactions to prevent false positives
                    inRange = true; // Blocked, but close enough to punch through the wall
                }
            }
            
            if (inRange) {
                // We can reach it! Just stop and let ChestAura do the work.
                player.setVelocity(0, 0, 0);
                chestWaitTicks++;
                if (chestWaitTicks > 100) { // 5 seconds timeout
                    ChestTracker.ignoredChests.add(currentChestTarget);
                    currentChestTarget = null;
                }
                return;
            }

            // Attempt to find path to chest synchronously (max 5000 nodes, ~1-2ms)
            // findPath will now return the CLOSEST possible path even if it fails to reach the target exactly
            currentChestPath = PathFinder.findPath(client.world, player.getBlockPos(), currentChestTarget, 30000);
            if (currentChestPath == null || currentChestPath.isEmpty()) {
                ChestTracker.ignoredChests.add(currentChestTarget);
                // player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.path_fail_buried", currentChestTarget.toShortString()), false);
                currentChestTarget = null;
            }
            return;
        }

        // Check for nearby players to evade detection
        boolean playerNear = false;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != player && p.squaredDistanceTo(Vec3d.ofCenter(currentChestTarget)) < 2500.0) { // Within 50 blocks
                if (!com.teoe.wdl.ConfigManager.botPrefix.isEmpty() && p.getName().getString().startsWith(com.teoe.wdl.ConfigManager.botPrefix)) {
                    continue; // Ignore bots
                }
                playerNear = true;
                break;
            }
        }

        if (playerNear) {
            ChestTracker.ignoredChests.add(currentChestTarget);
            player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.player_nearby", currentChestTarget.toShortString()), false);
            currentChestTarget = null;
            return;
        }

        // Check if ChestAura has already processed it
        if (ChestTracker.savedChests.contains(currentChestTarget) || ChestTracker.ignoredChests.contains(currentChestTarget)) {
            currentChestTarget = null;
            return;
        }

        // If ChestAura is currently working on it, just hover and wait
            if (ChestAura.currentTarget != null && ChestAura.currentTarget.equals(currentChestTarget)) {
                player.setVelocity(0, 0, 0);
                return;
            }

            // Check if we can already reach the chest directly! If so, wait for ChestAura
            if (currentChestPath == null || currentChestPath.isEmpty()) {
                Vec3d chestVec = Vec3d.ofCenter(currentChestTarget);
                double distSq = player.getPos().squaredDistanceTo(chestVec);
                boolean inRange = false;
                
                if (distSq < 18.0) { // Max potential reach 4.2 blocks
                    net.minecraft.util.hit.HitResult result = client.world.raycast(new net.minecraft.world.RaycastContext(
                        player.getCameraPosVec(1.0F), chestVec,
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, player
                    ));
                    
                    boolean hasLineOfSight = (result.getType() == net.minecraft.util.hit.HitResult.Type.MISS || 
                       (result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && ((net.minecraft.util.hit.BlockHitResult)result).getBlockPos().equals(currentChestTarget)));
                       
                    if (hasLineOfSight) {
                        inRange = true; // Clear line of sight, 4.2 reach is fine
                    } else if (distSq < 7.0) { // ~2.6 blocks max for through-wall interactions
                        inRange = true; // Blocked, but close enough to punch through the wall
                    }
                }
                
                if (inRange) {
                    // Reached it! Stop and let ChestAura open it
                    player.setVelocity(0, 0, 0);
                    chestWaitTicks++;
                    if (chestWaitTicks > 100) { // 5 seconds timeout
                        ChestTracker.ignoredChests.add(currentChestTarget);
                        player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.open_timeout", currentChestTarget.toShortString()), false);
                        currentChestTarget = null;
                    }
                    return;
                } 
                
                // Drifted off or path ended prematurely. Recalculate!
                currentChestPath = PathFinder.findPath(client.world, player.getBlockPos(), currentChestTarget, 30000);
                if (currentChestPath == null || currentChestPath.isEmpty()) {
                    ChestTracker.ignoredChests.add(currentChestTarget);
                    // player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.path_fail", currentChestTarget.toShortString()), false);
                    currentChestTarget = null;
                    return;
                }
            } else {
                chestWaitTicks = 0; // Reset wait ticks while walking
            }

            // Follow path
            if (currentChestPath != null && !currentChestPath.isEmpty()) {
            BlockPos nextNode = currentChestPath.get(0);
            
            // Preemptively open closed doors/gates in the next node to prevent getting stuck
            boolean doorBlocking = false;
            BlockPos cp = player.getBlockPos();
            for (BlockPos p : new BlockPos[]{nextNode.down(), nextNode, nextNode.up(), cp.down(), cp, cp.up()}) {
                net.minecraft.block.BlockState state = client.world.getBlockState(p);
                net.minecraft.block.Block block = state.getBlock();
                if (block instanceof net.minecraft.block.DoorBlock || 
                    block instanceof net.minecraft.block.TrapdoorBlock || 
                    block instanceof net.minecraft.block.FenceGateBlock) {
                    if (state.contains(net.minecraft.state.property.Properties.OPEN) && !state.get(net.minecraft.state.property.Properties.OPEN)) {
                        if (tryOpenDoor(client, player, p, state)) {
                            doorBlocking = true;
                        }
                    }
                } else if (p.equals(nextNode.up()) && !state.getCollisionShape(client.world, p).isEmpty()) {
                    // Try to break blocks that are in our head space and causing pathfinding failure
                    // This handles cases where trapdoors or other blocks are in our way but not registered as doors
                    doorBlocking = true;
                }
            }

            if (doorBlocking) {
                player.setVelocity(0, 0, 0);
                return; // Wait for door to open!
            }

            Vec3d targetVec = new Vec3d(nextNode.getX() + 0.5, nextNode.getY(), nextNode.getZ() + 0.5); // Target feet to floor to trigger pressure plates
            
            boolean needsSneak = false;
            BlockPos currentPos = player.getBlockPos();
            
            // Check clearance for current pos
            net.minecraft.block.BlockState currentDownState = client.world.getBlockState(currentPos.down());
            double currentFloorY = 0;
            if (!currentDownState.getCollisionShape(client.world, currentPos.down()).isEmpty()) {
                currentFloorY = currentDownState.getCollisionShape(client.world, currentPos.down()).getMax(net.minecraft.util.math.Direction.Axis.Y) - 1.0;
                if (currentFloorY < 0) currentFloorY = 0;
            }
            
            net.minecraft.block.BlockState currentUpState = client.world.getBlockState(currentPos.up(2));
            double currentCeilY = 2.0;
            if (!currentUpState.getCollisionShape(client.world, currentPos.up(2)).isEmpty()) {
                double minUp = currentUpState.getCollisionShape(client.world, currentPos.up(2)).getMin(net.minecraft.util.math.Direction.Axis.Y);
                if (!Double.isInfinite(minUp) && !Double.isNaN(minUp)) {
                    currentCeilY = 2.0 + minUp;
                }
            }
            net.minecraft.block.BlockState currentPosUpState = client.world.getBlockState(currentPos.up());
            if (!currentPosUpState.getCollisionShape(client.world, currentPos.up()).isEmpty()) {
                double minUp = currentPosUpState.getCollisionShape(client.world, currentPos.up()).getMin(net.minecraft.util.math.Direction.Axis.Y);
                if (!Double.isInfinite(minUp) && !Double.isNaN(minUp)) {
                    currentCeilY = Math.min(currentCeilY, 1.0 + minUp);
                }
            }
            
            if (currentCeilY - currentFloorY < 1.8) {
                needsSneak = true;
            }

            net.minecraft.block.BlockState downState = client.world.getBlockState(nextNode.down());
            double nextFloorY = 0;
            if (!downState.getCollisionShape(client.world, nextNode.down()).isEmpty()) {
                double downMaxY = downState.getCollisionShape(client.world, nextNode.down()).getMax(net.minecraft.util.math.Direction.Axis.Y);
                targetVec = new Vec3d(targetVec.x, nextNode.getY() - 1 + downMaxY, targetVec.z);
                nextFloorY = downMaxY - 1.0;
                if (nextFloorY < 0) nextFloorY = 0;
            }
            
            net.minecraft.block.BlockState nextUpState = client.world.getBlockState(nextNode.up(2));
            double nextCeilY = 2.0;
            if (!nextUpState.getCollisionShape(client.world, nextNode.up(2)).isEmpty()) {
                double minUp = nextUpState.getCollisionShape(client.world, nextNode.up(2)).getMin(net.minecraft.util.math.Direction.Axis.Y);
                if (!Double.isInfinite(minUp) && !Double.isNaN(minUp)) {
                    nextCeilY = 2.0 + minUp;
                }
            }
            net.minecraft.block.BlockState posUpState = client.world.getBlockState(nextNode.up());
            if (!posUpState.getCollisionShape(client.world, nextNode.up()).isEmpty()) {
                double minUp = posUpState.getCollisionShape(client.world, nextNode.up()).getMin(net.minecraft.util.math.Direction.Axis.Y);
                if (!Double.isInfinite(minUp) && !Double.isNaN(minUp)) {
                    nextCeilY = Math.min(nextCeilY, 1.0 + minUp);
                }
            }
            
            if (nextCeilY - nextFloorY < 1.8) {
                needsSneak = true;
            }
            
            client.options.sneakKey.setPressed(needsSneak);
            player.setSneaking(needsSneak); // Force pose update instantly

            Vec3d currentVec = player.getPos();

            if (currentVec.squaredDistanceTo(targetVec) < 0.6) { // Reached node
                currentChestPath.remove(0);
                ticksStuck = 0;
                ticksStuckXz = 0;
                client.options.sneakKey.setPressed(false);
                if (currentChestPath.isEmpty()) {
                    currentChestPath = null;
                }
                return;
            }
            
            Vec3d diff = targetVec.subtract(currentVec);
            
            // Fast Anti-Stuck (0.05 dist). Put this BEFORE velocity calculation so we can override it!
            if (lastPos != null && lastPos.squaredDistanceTo(currentVec) < 0.05) {
                ticksStuck++;
            } else {
                ticksStuck = 0;
            }

            // Anti-stuck XZ (5 seconds)
            Vec3d currentXz = new Vec3d(currentVec.x, 0, currentVec.z);
            if (lastXzPos != null && lastXzPos.squaredDistanceTo(currentXz) < 0.1) {
                ticksStuckXz++;
                if (ticksStuckXz > 100) { // 5 seconds
                    if (recalcAttempts < 3) {
                        currentChestPath = null;
                        ticksStuckXz = 0;
                        recalcAttempts++;
                        player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.stuck_recalc", recalcAttempts), false);
                    } else {
                        ChestTracker.ignoredChests.add(currentChestTarget);
                        player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.stuck_skip", currentChestTarget.toShortString()), false);
                        currentChestTarget = null;
                    }
                }
            } else {
                ticksStuckXz = 0;
                recalcAttempts = 0;
            }
            lastXzPos = currentXz;
            
            // Move towards next node
            double speed = Math.min(1.2, diff.length()); // Increased speed to 1.2
            Vec3d moveDir = diff.normalize().multiply(speed); 
            
            if (ticksStuck > 10) {
                // We are stuck! Override the normal movement and boost UP and FORWARD to clear the obstacle
                if (currentCeilY - currentFloorY > 2.0) {
                    player.setVelocity(moveDir.x, 0.4, moveDir.z); // Push up and keep pushing forward
                } else {
                    player.setVelocity(moveDir.x, 0, moveDir.z); // No space to push up
                }
            } else if (diff.y > 0.2) {
                // Need to ascend! Prioritize vertical movement to clear block edges horizontally
                player.setVelocity(moveDir.x * 0.4, Math.min(0.6, diff.y * 2.0), moveDir.z * 0.4);
            } else {
                // Normal flying. IMPORTANT: When flying is true, player ignores gravity. We MUST set downward velocity manually.
                player.setVelocity(moveDir.x, moveDir.y, moveDir.z);
            }
        }
        lastPos = player.getPos();
    }

    private static void handleReturningHome(MinecraftClient client, ClientPlayerEntity player) {
        if (homeTarget == null) {
            stopScanning();
            return;
        }

        boolean safe = false;
        int attempts = 0;
        
        while (!safe && attempts < 100) {
            safe = true;
            for (net.minecraft.entity.player.PlayerEntity p : client.world.getPlayers()) {
                if (p != player && p.squaredDistanceTo(homeTarget) < 10000.0) { // Within 100 blocks
                    if (!com.teoe.wdl.ConfigManager.botPrefix.isEmpty() && p.getName().getString().startsWith(com.teoe.wdl.ConfigManager.botPrefix)) {
                        continue; // Ignore bots
                    }
                    safe = false;
                    double angle = Math.random() * Math.PI * 2;
                    homeTarget = homeTarget.add(Math.cos(angle) * 300, 0, Math.sin(angle) * 300); // Offset by 300 blocks
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.player_offset"), false);
                    break;
                }
            }
            attempts++;
        }

        Vec3d currentVec = player.getPos();
        
        double dx = homeTarget.x - currentVec.x;
        double dz = homeTarget.z - currentVec.z;
        double dy = homeTarget.y - currentVec.y;
        
        // Arrived at home position horizontally, now descend
        if (dx * dx + dz * dz < 25.0) { // Within 5 blocks horizontally
            if ((player.verticalCollision && player.getVelocity().y >= -0.1) || player.isTouchingWater() || player.isInLava()) {
                // Arrived completely
                String dim = client.world.getRegistryKey().getValue().getPath();
                if (com.teoe.wdl.ConfigManager.scanNether && (dim.equals("overworld") || dim.equals("the_nether")) && !netherPortals.isEmpty()) {
                    currentPhase = Phase.GOTO_PORTAL;
                    currentPortalTarget = null;
                    if (dim.equals("overworld")) {
                        player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.overworld_done_nether"), false);
                    } else {
                        player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.nether_done_overworld"), false);
                    }
                } else {
                    stopScanning();
                    player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.all_done"), false);
                }
                return;
            } else {
                // Disable horizontal drift, let gravity handle the drop
                player.setVelocity(0, player.getVelocity().y, 0); 
            }
        } else {
            // Fly towards home at a safe altitude
            double targetY = 315.0;
            double flyDy = targetY - currentVec.y;
            
            if (currentVec.y < 300.0) {
                // Need to ascend. If we hit something (like a ceiling), try to drift outwards
                if (player.verticalCollision) {
                    player.setVelocity(new Vec3d(dx, 0, dz).normalize().multiply(1.5));
                } else if (player.horizontalCollision) {
                    player.setVelocity(new Vec3d(-dz, 2.0, dx).normalize().multiply(1.5));
                } else {
                    player.setVelocity(0, 2.0, 0); // Ascend straight up first
                }
            } else {
                // Safe altitude reached, cruise horizontally
                player.setVelocity(new Vec3d(dx, 0, dz).normalize().multiply(2.0));
            }
        }
    }

    private static void handleGotoPortal(MinecraftClient client, ClientPlayerEntity player) {
        if (currentPortalTarget == null) {
            double minDist = Double.MAX_VALUE;
            for (BlockPos p : netherPortals) {
                double d = p.getSquaredDistance(player.getPos());
                if (d < minDist) { minDist = d; currentPortalTarget = p; }
            }
            if (currentPortalTarget == null) {
                stopScanning();
                return;
            }
            currentChestPath = null;
        }

        double distSq = player.getPos().squaredDistanceTo(Vec3d.ofCenter(currentPortalTarget));
        if (distSq < 4.0) {
            currentPhase = Phase.WAIT_PORTAL;
            player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.waiting_portal"), false);
            return;
        }

        // If close enough, use PathFinder
        if (distSq < 2500.0) { // 50 blocks
            if (currentChestPath == null || currentChestPath.isEmpty()) {
                currentChestPath = PathFinder.findPath(client.world, player.getBlockPos(), currentPortalTarget, 5000);
                if (currentChestPath == null || currentChestPath.isEmpty()) {
                    netherPortals.remove(currentPortalTarget);
                    currentPortalTarget = null;
                    return; // Try next portal
                }
            }
            // Follow path
            BlockPos nextNode = currentChestPath.get(0);

            // Preemptively open closed doors/gates
            boolean doorBlocking = false;
            BlockPos cp = player.getBlockPos();
            for (BlockPos p : new BlockPos[]{nextNode.down(), nextNode, nextNode.up(), cp.down(), cp, cp.up()}) {
                net.minecraft.block.BlockState state = client.world.getBlockState(p);
                net.minecraft.block.Block block = state.getBlock();
                if (block instanceof net.minecraft.block.DoorBlock || 
                    block instanceof net.minecraft.block.TrapdoorBlock || 
                    block instanceof net.minecraft.block.FenceGateBlock) {
                    if (state.contains(net.minecraft.state.property.Properties.OPEN) && !state.get(net.minecraft.state.property.Properties.OPEN)) {
                        if (tryOpenDoor(client, player, p, state)) {
                            doorBlocking = true;
                        }
                    }
                }
            }

            if (doorBlocking) {
                player.setVelocity(0, 0, 0);
                return; // Wait for door to open!
            }

            Vec3d targetVec = new Vec3d(nextNode.getX() + 0.5, nextNode.getY(), nextNode.getZ() + 0.5); // Target feet to floor to trigger pressure plates
            net.minecraft.block.BlockState downState = client.world.getBlockState(nextNode.down());
            boolean needsSneak = false;
            if (!downState.getCollisionShape(client.world, nextNode.down()).isEmpty()) {
                double downMaxY = downState.getCollisionShape(client.world, nextNode.down()).getMax(net.minecraft.util.math.Direction.Axis.Y);
                targetVec = new Vec3d(targetVec.x, nextNode.getY() - 1 + downMaxY, targetVec.z);
                
                if (downMaxY > 1.0) {
                    BlockPos blockAbove = nextNode.up(2);
                    net.minecraft.block.BlockState upState = client.world.getBlockState(blockAbove);
                    if (!upState.getCollisionShape(client.world, blockAbove).isEmpty()) {
                        double minUpY = upState.getCollisionShape(client.world, blockAbove).getMin(net.minecraft.util.math.Direction.Axis.Y);
                        if (!Double.isInfinite(minUpY) && !Double.isNaN(minUpY)) {
                            double clearance = (2.0 + minUpY) - downMaxY;
                            if (clearance < 1.8) {
                                needsSneak = true;
                            }
                        }
                    }
                }
            }
            client.options.sneakKey.setPressed(needsSneak);

            if (player.getPos().squaredDistanceTo(targetVec) < 0.6) {
                currentChestPath.remove(0);
                ticksStuck = 0;
                client.options.sneakKey.setPressed(false);
                if (currentChestPath.isEmpty()) return;
                nextNode = currentChestPath.get(0);
                targetVec = new Vec3d(nextNode.getX() + 0.5, nextNode.getY(), nextNode.getZ() + 0.5);
                downState = client.world.getBlockState(nextNode.down());
                if (!downState.getCollisionShape(client.world, nextNode.down()).isEmpty()) {
                    double downMaxY = downState.getCollisionShape(client.world, nextNode.down()).getMax(net.minecraft.util.math.Direction.Axis.Y);
                    targetVec = new Vec3d(targetVec.x, nextNode.getY() - 1 + downMaxY, targetVec.z);
                }
            }
            
            Vec3d diff = targetVec.subtract(player.getPos());
            if (diff.y > 0.1) {
                diff = diff.add(0, 0.3, 0);
            }
            
            if (lastPos != null && lastPos.squaredDistanceTo(player.getPos()) < 0.05) {
                ticksStuck++;
                if (ticksStuck > 10) {
                    player.setVelocity(0, 0.6, 0);
                    if (ticksStuck > 30) {
                        currentChestPath = null;
                        ticksStuck = 0;
                    }
                } else {
                    player.setVelocity(diff.normalize().multiply(0.6));
                }
            } else {
                ticksStuck = 0;
                player.setVelocity(diff.normalize().multiply(0.6));
            }
            lastPos = player.getPos();
        } else {
            // Fly high and move towards X/Z
            double targetX = currentPortalTarget.getX() + 0.5;
            double targetZ = currentPortalTarget.getZ() + 0.5;
            double targetY = 150.0; // safe height

            double dx = targetX - player.getX();
            double dz = targetZ - player.getZ();
            
            if (dx * dx + dz * dz < 25.0) {
                // Above portal, descend
                targetY = currentPortalTarget.getY() + 2.0; 
            }

            Vec3d targetVec = new Vec3d(targetX, targetY, targetZ);
            Vec3d currentVec = player.getPos();
            Vec3d diff = targetVec.subtract(currentVec);

            // Anti-stuck
            if (lastPos != null && lastPos.squaredDistanceTo(currentVec) < 0.01) {
                ticksStuck++;
                if (ticksStuck > 10) {
                    player.setVelocity(0, 1.0, 0); // Fly up
                } else {
                    player.setVelocity(diff.normalize().multiply(1.5));
                }
            } else {
                ticksStuck = 0;
                player.setVelocity(diff.normalize().multiply(1.5));
            }
            lastPos = currentVec;
        }
    }

    private static void handleWaitPortal(MinecraftClient client, ClientPlayerEntity player) {
        String currentDim = client.world.getRegistryKey().getValue().getPath();
        if (!currentDim.equals(startingDimension)) {
            scanNether = false; // Prevent loop
            netherPortals.clear();
            com.teoe.wdl.DownloadManager.historicallySavedChunks.clear();
            com.teoe.wdl.server.MapGenerator.clear();
            startScanning(scanRadius * 2);
            if (currentDim.equals("the_nether")) {
                player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.enter_nether"), false);
            } else if (currentDim.equals("overworld")) {
                player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.enter_overworld"), false);
            } else {
                player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.enter_dimension"), false);
            }
            return;
        }
        
        // Keep player in the portal to trigger teleport
        Vec3d targetVec = Vec3d.ofCenter(currentPortalTarget);
        player.setPosition(targetVec.x, targetVec.y, targetVec.z);
        player.setVelocity(0, 0, 0);
    }

    public static void startScanning(int diameter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        if (currentPhase == Phase.IDLE) {
            startingDimension = client.world.getRegistryKey().getValue().getPath();
        }

        com.teoe.wdl.ConfigManager.diameter = diameter;
        com.teoe.wdl.ConfigManager.save();

        chunkQueue.clear();
        chestList.clear();
        currentChunkTarget = null;
        currentChestTarget = null;
        currentPhase = Phase.CHUNKS;
        startPos = client.player.getPos();
        scanRadius = diameter / 2;

        ChunkPos startChunk = client.player.getChunkPos();
        int chunkRadius = scanRadius / 16;

        // Generate spiral chunk coordinates
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        int t = Math.max(chunkRadius * 2 + 1, chunkRadius * 2 + 1);
        int maxI = t * t;

        for (int i = 0; i < maxI; i++) {
            if (-chunkRadius <= x && x <= chunkRadius && -chunkRadius <= z && z <= chunkRadius) {
                ChunkPos pos = new ChunkPos(startChunk.x + x, startChunk.z + z);
                
                // Only add if the chunk is strictly within the radius distance (circular bounding)
                double distSq = (pos.getStartX() + 8 - startPos.x) * (pos.getStartX() + 8 - startPos.x) + 
                                (pos.getStartZ() + 8 - startPos.z) * (pos.getStartZ() + 8 - startPos.z);
                
                if (distSq <= scanRadius * scanRadius && !DownloadManager.historicallySavedChunks.contains(pos)) {
                    chunkQueue.add(pos);
                }
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
        }

        client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.started", diameter, chunkQueue.size()), false);
        ChunkFilter.materializeVirtualBlocks(true);
    }

    public static void stopScanning() {
        currentPhase = Phase.IDLE;
        chunkQueue.clear();
        chestList.clear();
        currentChunkTarget = null;
        currentChestTarget = null;
        currentPortalTarget = null;
        ChunkFilter.materializeVirtualBlocks(false);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.getAbilities().flying = false;
            client.options.sneakKey.setPressed(false);
            client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.scanner.stopped"), false);
        }
        com.teoe.wdl.DownloadManager.stopRecording();
    }

    public static boolean isActive() {
        return currentPhase != Phase.IDLE;
    }
    
    public static int getRemainingChunks() {
        if (currentPhase == Phase.CHUNKS) {
            return chunkQueue.size() + (currentChunkTarget != null ? 1 : 0);
        } else if (currentPhase == Phase.CHESTS) {
            return chestList.size() + (currentChestTarget != null ? 1 : 0);
        }
        return 0;
    }

    public static String getPhaseName() {
        return currentPhase.name();
    }

    public static List<BlockPos> getCurrentChestPath() {
        return currentChestPath;
    }

    private static boolean tryOpenDoor(MinecraftClient client, ClientPlayerEntity player, BlockPos p, net.minecraft.block.BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        if (block instanceof net.minecraft.block.DoorBlock || 
            block instanceof net.minecraft.block.TrapdoorBlock || 
            block instanceof net.minecraft.block.FenceGateBlock) {
            if (state.contains(net.minecraft.state.property.Properties.OPEN) && !state.get(net.minecraft.state.property.Properties.OPEN)) {
                if (block == net.minecraft.block.Blocks.IRON_DOOR || block == net.minecraft.block.Blocks.IRON_TRAPDOOR) {
                    // Search for nearby button or lever around the player
                    boolean found = false;
                    BlockPos playerPos = player.getBlockPos();
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -1; dy <= 2; dy++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                BlockPos searchPos = playerPos.add(dx, dy, dz);
                                net.minecraft.block.BlockState searchState = client.world.getBlockState(searchPos);
                                net.minecraft.block.Block searchBlock = searchState.getBlock();
                                if (searchBlock instanceof net.minecraft.block.ButtonBlock || searchBlock instanceof net.minecraft.block.LeverBlock) {
                                    // Don't click already powered buttons/levers to avoid spamming
                                    if (searchState.contains(net.minecraft.state.property.Properties.POWERED) && searchState.get(net.minecraft.state.property.Properties.POWERED)) {
                                        continue;
                                    }
                                    client.interactionManager.interactBlock(player, net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(searchPos), net.minecraft.util.math.Direction.UP, searchPos, false));
                                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                                    found = true;
                                    break;
                                }
                            }
                            if (found) break;
                        }
                        if (found) break;
                    }
                    return found;
                } else {
                    client.interactionManager.interactBlock(player, net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(p), net.minecraft.util.math.Direction.UP, p, false));
                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    return true;
                }
            }
        }
        return false;
    }
}