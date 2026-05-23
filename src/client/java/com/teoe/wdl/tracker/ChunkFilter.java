package com.teoe.wdl.tracker;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.ChunkPos;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

public class ChunkFilter {
    public static final Set<ChunkPos> ignoredChunks = new HashSet<>();
    private static boolean wasHoldingIgnoreStick = false;
    private static boolean previousChunkBorderState = false;
    private static boolean wasAttackPressed = false;

    public static final java.util.Set<net.minecraft.util.math.BlockPos> virtualBlocks = new java.util.HashSet<>();
    
    public static void materializeVirtualBlocks(boolean materialize) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        for (net.minecraft.util.math.BlockPos pos : virtualBlocks) {
            if (materialize) {
                client.world.setBlockState(pos, net.minecraft.block.Blocks.RED_STAINED_GLASS.getDefaultState(), 3);
            } else {
                client.world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            }
        }
    }
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            ItemStack stack = client.player.getMainHandStack();
            boolean isHoldingIgnoreStick = stack.isOf(Items.STICK) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equals("ignore");
            
            if (isHoldingIgnoreStick) {
                boolean isAttackPressed = client.options.attackKey.isPressed();
                if (isAttackPressed && !wasAttackPressed) {
                    VHit hit = customRaycast(client.player, 5.0);
                    if (hit != null) {
                        if (client.player.isSneaking()) {
                            if (hit.isVirtual) {
                                virtualBlocks.remove(hit.pos);
                                client.player.sendMessage(net.minecraft.text.Text.literal("§cRemoved virtual block"), true);
                            }
                        } else {
                            net.minecraft.util.math.BlockPos newPos = hit.isVirtual ? hit.pos.offset(hit.side) : hit.pos.offset(hit.side);
                            virtualBlocks.add(newPos);
                            client.player.sendMessage(net.minecraft.text.Text.literal("§aAdded virtual block"), true);
                        }
                    }
                }
                wasAttackPressed = isAttackPressed;
            } else {
                wasAttackPressed = false;
            }
            
            if (isHoldingIgnoreStick && !wasHoldingIgnoreStick) {
                // Just started holding
                boolean newState = client.debugRenderer.toggleShowChunkBorder();
                previousChunkBorderState = !newState; // It was the opposite of the new state
                if (!newState) {
                    client.debugRenderer.toggleShowChunkBorder(); // If it turned off, turn it back on
                }
                wasHoldingIgnoreStick = true;
            } else if (!isHoldingIgnoreStick && wasHoldingIgnoreStick) {
                // Stopped holding
                if (!previousChunkBorderState) {
                    client.debugRenderer.toggleShowChunkBorder(); // Turn it off if it was off before
                }
                wasHoldingIgnoreStick = false;
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient && hand == net.minecraft.util.Hand.MAIN_HAND) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isOf(Items.STICK) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equals("ignore")) {
                    ChunkPos pos = new ChunkPos(hitResult.getBlockPos());
                    if (ignoredChunks.contains(pos)) {
                        ignoredChunks.remove(pos);
                        player.sendMessage(net.minecraft.text.Text.literal("§aRemoved chunk from ignore list: " + pos), true);
                    } else {
                        ignoredChunks.add(pos);
                        player.sendMessage(net.minecraft.text.Text.literal("§cAdded chunk to ignore list: " + pos), true);
                    }
                    return ActionResult.SUCCESS; // Cancel block interaction
                }
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient && hand == net.minecraft.util.Hand.MAIN_HAND) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isOf(Items.STICK) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equals("ignore")) {
                    return ActionResult.SUCCESS; // Cancel block breaking when holding the ignore stick
                }
            }
            return ActionResult.PASS;
        });

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || ignoredChunks.isEmpty()) return;

            ItemStack stack = client.player.getMainHandStack();
            if (!(stack.isOf(Items.STICK) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equals("ignore"))) {
                return; // Only render when holding the stick
            }

            MatrixStack matrices = context.matrixStack();
            Vec3d cameraPos = context.camera().getPos();
            VertexConsumer vertexConsumer = context.consumers().getBuffer(RenderLayer.getDebugLineStrip(2.0));

            for (ChunkPos pos : ignoredChunks) {
                // Render a blue box around the chunk
                double minX = pos.getStartX() - cameraPos.x;
                double minZ = pos.getStartZ() - cameraPos.z;
                double maxX = pos.getEndX() + 1 - cameraPos.x;
                double maxZ = pos.getEndZ() + 1 - cameraPos.z;
                
                // Draw from y=-64 to 320
                double minY = -64 - cameraPos.y;
                double maxY = 320 - cameraPos.y;

                Matrix4f model = matrices.peek().getPositionMatrix();
                
                // Bottom square
                drawLine(model, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, 0, 0, 1, 1);
                
                // Top square
                drawLine(model, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, minX, maxY, maxZ, minX, maxY, minZ, 0, 0, 1, 1);
                
                // Vertical lines
                drawLine(model, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, 0, 0, 1, 1);
                
                // Draw a big X in the middle at player height for visibility
                double midY = client.player.getY() - cameraPos.y;
                drawLine(model, vertexConsumer, minX, midY, minZ, maxX, midY, maxZ, 0, 0, 1, 1);
                drawLine(model, vertexConsumer, minX, midY, maxZ, maxX, midY, minZ, 0, 0, 1, 1);
            }
            // Render virtual blocks
            for (net.minecraft.util.math.BlockPos pos : virtualBlocks) {
                double minX = pos.getX() - cameraPos.x;
                double minY = pos.getY() - cameraPos.y;
                double minZ = pos.getZ() - cameraPos.z;
                double maxX = minX + 1;
                double maxY = minY + 1;
                double maxZ = minZ + 1;

                Matrix4f model = matrices.peek().getPositionMatrix();
                
                // Draw red cube outline
                drawLine(model, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, 1, 0, 0, 1);
                
                drawLine(model, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, minX, maxY, maxZ, minX, maxY, minZ, 1, 0, 0, 1);
                
                drawLine(model, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, 1, 0, 0, 1);
                drawLine(model, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, 1, 0, 0, 1);
            }
        });
    }
    
    private static class VHit {
        net.minecraft.util.math.BlockPos pos;
        net.minecraft.util.math.Direction side;
        boolean isVirtual;
        double distance;
    }

    private static VHit customRaycast(net.minecraft.client.network.ClientPlayerEntity player, double maxDist) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d dir = player.getRotationVec(1.0F);
        Vec3d end = start.add(dir.multiply(maxDist));

        VHit bestHit = null;

        net.minecraft.util.hit.HitResult realHit = player.clientWorld.raycast(new net.minecraft.world.RaycastContext(start, end, net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        if (realHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            net.minecraft.util.hit.BlockHitResult bhr = (net.minecraft.util.hit.BlockHitResult) realHit;
            bestHit = new VHit();
            bestHit.pos = bhr.getBlockPos();
            bestHit.side = bhr.getSide();
            bestHit.isVirtual = false;
            bestHit.distance = start.distanceTo(bhr.getPos());
        }

        for (net.minecraft.util.math.BlockPos vp : virtualBlocks) {
            Box box = new Box(vp);
            java.util.Optional<Vec3d> vHitOpt = box.raycast(start, end);
            if (vHitOpt.isPresent()) {
                Vec3d hitPos = vHitOpt.get();
                double dist = start.distanceTo(hitPos);
                if (bestHit == null || dist < bestHit.distance) {
                    bestHit = new VHit();
                    bestHit.pos = vp;
                    bestHit.side = net.minecraft.util.math.Direction.UP;
                    if (Math.abs(hitPos.x - box.minX) < 0.001) bestHit.side = net.minecraft.util.math.Direction.WEST;
                    else if (Math.abs(hitPos.x - box.maxX) < 0.001) bestHit.side = net.minecraft.util.math.Direction.EAST;
                    else if (Math.abs(hitPos.y - box.minY) < 0.001) bestHit.side = net.minecraft.util.math.Direction.DOWN;
                    else if (Math.abs(hitPos.y - box.maxY) < 0.001) bestHit.side = net.minecraft.util.math.Direction.UP;
                    else if (Math.abs(hitPos.z - box.minZ) < 0.001) bestHit.side = net.minecraft.util.math.Direction.NORTH;
                    else if (Math.abs(hitPos.z - box.maxZ) < 0.001) bestHit.side = net.minecraft.util.math.Direction.SOUTH;
                    bestHit.isVirtual = true;
                    bestHit.distance = dist;
                }
            }
        }
        return bestHit;
    }

    private static void drawLine(Matrix4f model, VertexConsumer vertexConsumer, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        vertexConsumer.vertex(model, (float)x1, (float)y1, (float)z1).color(r, g, b, a).normal(0, 1, 0);
        vertexConsumer.vertex(model, (float)x2, (float)y2, (float)z2).color(r, g, b, a).normal(0, 1, 0);
    }
}
