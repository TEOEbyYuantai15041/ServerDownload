package com.teoe.wdl.mixin;

import com.teoe.wdl.DownloadManager;
import com.teoe.wdl.tracker.ChestTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

@Mixin(ClientPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    
    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (DownloadManager.isRecording() && com.teoe.wdl.tracker.ChestAura.currentTarget != null) {
            if (com.teoe.wdl.tracker.ChestAura.waitingForSync) {
                com.teoe.wdl.tracker.ChestAura.currentSyncId = packet.getSyncId();
                com.teoe.wdl.tracker.ChestAura.waitingForSync = false;
                com.teoe.wdl.tracker.ChestAura.waitingForInventory = true;
                ci.cancel(); // Prevent the screen from opening
            }
        }
    }

    @Inject(method = "onInventory", at = @At("HEAD"), cancellable = true)
    private void onInventoryHead(InventoryS2CPacket packet, CallbackInfo ci) {
        if (DownloadManager.isRecording() && com.teoe.wdl.tracker.ChestAura.currentTarget != null) {
            if (com.teoe.wdl.tracker.ChestAura.waitingForInventory && packet.syncId() == com.teoe.wdl.tracker.ChestAura.currentSyncId) {
                // We got the inventory data silently!
                ChestTracker.saveContainerDirectly(com.teoe.wdl.tracker.ChestAura.currentTarget, packet.contents());
                
                // Close the screen on the server
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(packet.syncId()));
                }
                
                com.teoe.wdl.tracker.ChestAura.currentTarget = null;
                com.teoe.wdl.tracker.ChestAura.waitingForInventory = false;
                ci.cancel(); // Prevent the inventory from updating the local screen handler (which doesn't exist)
            }
        }
    }

    @Inject(method = "onInventory", at = @At("RETURN"))
    private void onInventoryReceived(InventoryS2CPacket packet, CallbackInfo ci) {
        if (DownloadManager.isRecording()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.currentScreenHandler != null) {
                // Ignore player inventory syncs to avoid overriding chest saves
                if (client.player.currentScreenHandler == client.player.playerScreenHandler) {
                    return;
                }
                
                // If the syncId matches, it means the inventory is fully loaded
                if (packet.syncId() == client.player.currentScreenHandler.syncId) {
                    ChestTracker.saveCurrentContainer(client.player.currentScreenHandler);
                    
                    // If ChestAura opened this automatically, close it immediately now that we have the items
                    if (com.teoe.wdl.tracker.ChestAura.currentTarget != null) {
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.closeHandledScreen();
                            }
                            if (client.currentScreen != null) {
                                client.setScreen(null);
                            }
                        });
                    }
                }
            }
        }
    }
}