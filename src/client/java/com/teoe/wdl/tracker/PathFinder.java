package com.teoe.wdl.tracker;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PathFinder {
    
    private static class Node implements Comparable<Node> {
        BlockPos pos;
        Node parent;
        double g;
        double h;

        Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }

        double getF() { return g + h * 1.5; } // Greedy Best-First A* for speed

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.getF(), o.getF());
        }
    }

    public static List<BlockPos> findPath(ClientWorld world, BlockPos start, BlockPos target, int maxNodes) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, getHeuristic(start, target));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        Node bestNode = startNode;

        int nodesEvaluated = 0;
        
        List<int[]> dirs = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    dirs.add(new int[]{dx, dy, dz});
                }
            }
        }

        while (!openSet.isEmpty() && nodesEvaluated < maxNodes) {
            Node current = openSet.poll();
            closedSet.add(current.pos);
            nodesEvaluated++;
            
            if (current.h < bestNode.h) {
                bestNode = current;
            }

            // Stop if we are strictly adjacent (or diagonal) to the target chest
            int dx = Math.abs(current.pos.getX() - target.getX());
            int dy = Math.abs(current.pos.getY() - target.getY());
            int dz = Math.abs(current.pos.getZ() - target.getZ());
            if (dx <= 1 && dy <= 1 && dz <= 1) {
                return reconstructPath(current);
            }

            for (int[] dir : dirs) {
                BlockPos neighborPos = current.pos.add(dir[0], dir[1], dir[2]);

                if (closedSet.contains(neighborPos)) continue;

                double cost1 = getPassableCost(world, neighborPos, current.pos);
                double cost2 = getPassableCost(world, neighborPos.up(), current.pos.up());

                // Check if passable (need 2 blocks of vertical space for player's body)
                if (cost1 < 0 || cost2 < 0) {
                    continue;
                }
                
                // Prevent diagonal corner cutting through solid walls
                int changes = Math.abs(dir[0]) + Math.abs(dir[1]) + Math.abs(dir[2]);
                if (changes == 3) {
                    continue; // Disallow 3D diagonals completely to prevent complex clipping
                } else if (changes == 2) {
                    if (dir[0] != 0 && dir[2] != 0) { // XZ diagonal
                        if (getPassableCost(world, current.pos.add(dir[0], 0, 0), current.pos) < 0 || 
                            getPassableCost(world, current.pos.add(0, 0, dir[2]), current.pos) < 0 ||
                            getPassableCost(world, current.pos.add(dir[0], 1, 0), current.pos.up()) < 0 || 
                            getPassableCost(world, current.pos.add(0, 1, dir[2]), current.pos.up()) < 0) {
                            continue;
                        }
                    } else if (dir[0] != 0 && dir[1] != 0) { // XY diagonal
                        if (getPassableCost(world, current.pos.add(dir[0], 0, 0), current.pos) < 0 || 
                            getPassableCost(world, current.pos.add(0, dir[1], 0), current.pos) < 0 ||
                            getPassableCost(world, current.pos.add(dir[0], 1, 0), current.pos.up()) < 0 || 
                            getPassableCost(world, current.pos.add(0, 1 + dir[1], 0), current.pos.up()) < 0) {
                            continue;
                        }
                    } else if (dir[1] != 0 && dir[2] != 0) { // YZ diagonal
                        if (getPassableCost(world, current.pos.add(0, 0, dir[2]), current.pos) < 0 || 
                            getPassableCost(world, current.pos.add(0, dir[1], 0), current.pos) < 0 ||
                            getPassableCost(world, current.pos.add(0, 1, dir[2]), current.pos.up()) < 0 || 
                            getPassableCost(world, current.pos.add(0, 1 + dir[1], 0), current.pos.up()) < 0) {
                            continue;
                        }
                    }
                }

                double stepCost = Math.sqrt(dir[0]*dir[0] + dir[1]*dir[1] + dir[2]*dir[2]) + cost1 + cost2;
                double tentativeG = current.g + stepCost;
                Node neighborNode = allNodes.get(neighborPos);

                if (neighborNode == null) {
                    neighborNode = new Node(neighborPos, current, tentativeG, getHeuristic(neighborPos, target));
                    openSet.add(neighborNode);
                    allNodes.put(neighborPos, neighborNode);
                } else if (tentativeG < neighborNode.g) {
                    neighborNode.parent = current;
                    neighborNode.g = tentativeG;
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }
        
        if (bestNode != startNode) {
            return reconstructPath(bestNode); // Return the closest path we found
        }
        return null; // Failed completely
    }

    private static double getHeuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double getPassableCost(ClientWorld world, BlockPos pos, BlockPos fromPos) {
        if (com.teoe.wdl.tracker.ChunkFilter.virtualBlocks.contains(pos) || 
            com.teoe.wdl.tracker.ChunkFilter.virtualBlocks.contains(pos.up()) || 
            com.teoe.wdl.tracker.ChunkFilter.virtualBlocks.contains(pos.up(2))) {
            return -1.0; // Impassable virtual barrier
        }

        net.minecraft.block.BlockState state = world.getBlockState(pos);
        net.minecraft.block.Block block = state.getBlock();

        if (block == net.minecraft.block.Blocks.LANTERN || 
            block == net.minecraft.block.Blocks.SOUL_LANTERN || 
            world.getBlockState(pos.up()).getBlock() == net.minecraft.block.Blocks.LANTERN ||
            world.getBlockState(pos.up()).getBlock() == net.minecraft.block.Blocks.SOUL_LANTERN ||
            world.getBlockState(pos.up(2)).getBlock() == net.minecraft.block.Blocks.LANTERN ||
            world.getBlockState(pos.up(2)).getBlock() == net.minecraft.block.Blocks.SOUL_LANTERN) {
            return -1.0;
        }

        // Check for dangerous blocks (lava, fire, etc.) that have no collision but kill the player
        if (block == net.minecraft.block.Blocks.LAVA || 
            block == net.minecraft.block.Blocks.FIRE || 
            block == net.minecraft.block.Blocks.SOUL_FIRE || 
            block == net.minecraft.block.Blocks.CAMPFIRE || 
            block == net.minecraft.block.Blocks.SOUL_CAMPFIRE || 
            block == net.minecraft.block.Blocks.MAGMA_BLOCK || 
            block == net.minecraft.block.Blocks.SWEET_BERRY_BUSH || 
            block == net.minecraft.block.Blocks.CACTUS ||
            !state.getFluidState().isEmpty() && state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
            return -1.0;
        }

        boolean isPassable = state.getCollisionShape(world, pos).isEmpty();
        
        // Let's also check if it's a fence or wall and we are walking on it,
        // it shouldn't be passable because its collision goes up to 1.5.
        // If we are at Y=1, and the fence is at Y=0, the fence collision is from Y=0 to Y=1.5.
        // Wait, if pos is Y=1, the block at pos is air.
        // But what if pos is Y=0? The block is fence, collision is not empty, so isPassable is false.
        // This is correct.
        
        // Wait! In the user's image, the player is blocked by a fence.
        // If the player tries to fly over the fence, it would try to move to Y=1 or Y=2.
        // But if the roof is above the fence, the player cannot fit.
        // Let's check vertical clearance explicitly.
        // A player needs 1.8 blocks of height.
        // For a path node, the space from pos to pos.up() must be empty.
        // Actually, cost1 is pos, cost2 is pos.up().
        // If pos is air, cost1=0. If pos.up() is air, cost2=0.
        // But what about the block BELOW pos?
        // If pos is Y=1, downBlock is Y=0 (fence).
        // Fence collision goes up to Y=1.5.
        // The space available from Y=1.5 to the roof at Y=3 is 1.5 blocks!
        // Player height is 1.8, so the player CANNOT FIT between the fence and the roof!
        // But the A* algorithm checks `getPassableCost(pos)` (Y=1) which is AIR (cost1=0),
        // and `getPassableCost(pos.up())` (Y=2) which is AIR (cost2=0).
        // It DOES NOT check if the fence's collision box intrudes into `pos`.
        // So the A* algorithm thinks it can stand on the fence, even though there's only 1.5 blocks of clearance!

        // Let's add a check for the actual collision height of the block below.
        net.minecraft.block.BlockState downState = world.getBlockState(pos.down());
        double floorY = 0;
        if (!downState.getCollisionShape(world, pos.down()).isEmpty()) {
            floorY = downState.getCollisionShape(world, pos.down()).getMax(net.minecraft.util.math.Direction.Axis.Y) - 1.0;
            if (floorY < 0) floorY = 0;
        }
        
        // Calculate ceiling height from pos.up() or pos.up(2)
        net.minecraft.block.BlockState upState = world.getBlockState(pos.up(2));
        double ceilY = 2.0;
        if (!upState.getCollisionShape(world, pos.up(2)).isEmpty()) {
            double minUp = upState.getCollisionShape(world, pos.up(2)).getMin(net.minecraft.util.math.Direction.Axis.Y);
            // In case the collision shape is weird
            if (!Double.isInfinite(minUp) && !Double.isNaN(minUp)) {
                ceilY = 2.0 + minUp;
            }
        }
        
        net.minecraft.block.BlockState posState = world.getBlockState(pos.up());
        if (!posState.getCollisionShape(world, pos.up()).isEmpty()) {
            double minPos = posState.getCollisionShape(world, pos.up()).getMin(net.minecraft.util.math.Direction.Axis.Y);
            if (!Double.isInfinite(minPos) && !Double.isNaN(minPos)) {
                ceilY = Math.min(ceilY, 1.0 + minPos);
            }
        }
        
        // If the clearance is less than 1.5 blocks (sneaking height), it's impassable!
        if (ceilY - floorY < 1.5) {
            return -1.0;
        }

        if (isPassable) {
            if (block == net.minecraft.block.Blocks.WATER || (!state.getFluidState().isEmpty() && state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER))) {
                return 15.0; // High penalty for water, only use if absolutely necessary
            }

            // Extra safety: don't fly directly over lava or fire if we are just moving normally
            net.minecraft.block.Block downBlock = world.getBlockState(pos.down()).getBlock();
            if (downBlock == net.minecraft.block.Blocks.LAVA || 
                downBlock == net.minecraft.block.Blocks.MAGMA_BLOCK || 
                downBlock == net.minecraft.block.Blocks.FIRE ||
                (!world.getBlockState(pos.down()).getFluidState().isEmpty() && world.getBlockState(pos.down()).getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA))) {
                return -1.0;
            }
            
            // Add penalty to fences and walls to encourage routing around them
            if (downBlock instanceof net.minecraft.block.FenceBlock || 
                downBlock instanceof net.minecraft.block.WallBlock || 
                downBlock instanceof net.minecraft.block.PaneBlock) {
                return 5.0; // High penalty
            }
            
            return 0.0;
        }
        
        if (block instanceof net.minecraft.block.DoorBlock || 
            block instanceof net.minecraft.block.TrapdoorBlock || 
            block instanceof net.minecraft.block.FenceGateBlock) {
            if (block == net.minecraft.block.Blocks.IRON_DOOR || 
                block == net.minecraft.block.Blocks.IRON_TRAPDOOR) {
                if (state.contains(net.minecraft.state.property.Properties.OPEN) && state.get(net.minecraft.state.property.Properties.OPEN)) {
                    return 0.0;
                }
                boolean canTrigger = false;
                if (fromPos != null) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                net.minecraft.block.Block b = world.getBlockState(fromPos.add(dx, dy, dz)).getBlock();
                                if (b instanceof net.minecraft.block.ButtonBlock || 
                                    b instanceof net.minecraft.block.LeverBlock) {
                                    canTrigger = true;
                                    break;
                                }
                            }
                            if (canTrigger) break;
                        }
                        if (canTrigger) break;
                    }
                }
                
                // If there's a pressure plate on the path we are taking, it will open the door!
                if (!canTrigger && fromPos != null) {
                    net.minecraft.block.Block fromBlock = world.getBlockState(fromPos).getBlock();
                    net.minecraft.block.Block fromDownBlock = world.getBlockState(fromPos.down()).getBlock();
                    if (fromBlock instanceof net.minecraft.block.PressurePlateBlock || 
                        fromDownBlock instanceof net.minecraft.block.PressurePlateBlock) {
                        canTrigger = true;
                    }
                }

                if (canTrigger) {
                    return 2.0; // Penalty for needing to wait for redstone
                }
                return -1.0; // Impassable if no trigger nearby
            }
            return 1.0; // Penalty for doors/gates
        }
        return -1.0; // Impassable
    }

    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node current = node;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
}