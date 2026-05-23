package com.teoe.wdl.tracker;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ChestESP {
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || !com.teoe.wdl.DownloadManager.isRecording()) return;

            // Show current target chest
            BlockPos target = AutoScanner.currentChestTarget;
            if (target == null) target = ChestAura.currentTarget;

            if (target != null) {
                // Spawn gold/yellow particles around the target
                double x = target.getX() + 0.5;
                double y = target.getY() + 0.5;
                double z = target.getZ() + 0.5;
                
                client.particleManager.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 0.0, 0.05, 0.0);
                client.particleManager.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
            }

            // Show path if exists
            List<BlockPos> path = AutoScanner.getCurrentChestPath();
            if (path != null && !path.isEmpty()) {
                for (BlockPos node : path) {
                    client.particleManager.addParticle(ParticleTypes.SOUL, node.getX() + 0.5, node.getY() + 0.2, node.getZ() + 0.5, 0.0, 0.01, 0.0);
                }
            }
        });
    }
}