package com.teoe.wdl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.teoe.wdl.tracker.ChestAura;
import com.teoe.wdl.tracker.ChestTracker;
import com.teoe.wdl.tracker.AutoScanner;
import com.teoe.wdl.tracker.ChestESP;
import com.teoe.wdl.tracker.ChunkFilter;
import com.teoe.wdl.server.WdlWebServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class TeoeWDL implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        ModLogger.log("TeoeWDL initialized");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("downloadserver")
                .then(ClientCommandManager.literal("start")
                    .executes(context -> {
                        DownloadManager.startRecording();
                        context.getSource().sendFeedback(Text.translatable("teoe.wdl.command.record_start"));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("seed")
                    .then(ClientCommandManager.argument("seedValue", LongArgumentType.longArg())
                        .executes(context -> {
                            long seed = LongArgumentType.getLong(context, "seedValue");
                            DownloadManager.currentSeed = seed;
                            context.getSource().sendFeedback(Text.translatable("teoe.wdl.command.seed_set", seed));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("localhost")
                    .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1024, 65535))
                        .executes(context -> {
                            int port = IntegerArgumentType.getInteger(context, "port");
                            WdlWebServer.start(port);
                            context.getSource().sendFeedback(Text.translatable("teoe.wdl.command.web_ui", port));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("auto")
                    .then(ClientCommandManager.literal("start")
                        .then(ClientCommandManager.argument("diameter", IntegerArgumentType.integer(100, 100000))
                            .executes(context -> {
                                if (!DownloadManager.isRecording()) {
                                    DownloadManager.startRecording();
                                    context.getSource().sendFeedback(Text.translatable("teoe.wdl.command.auto_start"));
                                }
                                int diameter = IntegerArgumentType.getInteger(context, "diameter");
                                AutoScanner.startScanning(diameter);
                                return 1;
                            })
                        )
                    )
                    .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            AutoScanner.stopScanning();
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("end")
                    .executes(context -> {
                        if (!DownloadManager.isRecording()) {
                            context.getSource().sendFeedback(Text.literal("§c[TeoeWDL] No recording in progress."));
                            return 0;
                        }
                        context.getSource().sendFeedback(Text.translatable("teoe.wdl.command.record_stop"));
                        DownloadManager.stopRecording();
                        WdlWebServer.stop();
                        AutoScanner.stopScanning();
                        return 1;
                    })
                )
            );
        });

        ChestAura.init();
        AutoScanner.init();
        ChestESP.init();
        ChunkFilter.init();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (DownloadManager.isRecording()) {
                ModLogger.log("Player disconnected, emergency saving recorded data...");
                DownloadManager.stopRecording();
            }
        });
    }
}