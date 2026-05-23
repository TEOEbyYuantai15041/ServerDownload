package com.teoe.wdl.mixin;

import com.teoe.wdl.DownloadManager;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkManager.class)
public class ChunkDataMixin {
    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void onChunkLoad(int x, int z, net.minecraft.network.PacketByteBuf buf, java.util.Map heightmaps, java.util.function.Consumer consumer, CallbackInfoReturnable<WorldChunk> cir) {
        if (DownloadManager.isRecording()) {
            WorldChunk chunk = cir.getReturnValue();
            if (chunk != null) {
                DownloadManager.onChunkReceived(chunk);
            }
        }
    }
}