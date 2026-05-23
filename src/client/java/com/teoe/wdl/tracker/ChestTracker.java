package com.teoe.wdl.tracker;

import com.teoe.wdl.DownloadManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class ChestTracker {
    public static BlockPos lastClickedBlock = null;
    public static final Set<BlockPos> savedChests = new HashSet<>();
    public static Set<BlockPos> ignoredChests = new HashSet<>();

    public static void saveContainerDirectly(BlockPos targetPos, java.util.List<net.minecraft.item.ItemStack> contents) {
        if (!DownloadManager.isRecording() || !com.teoe.wdl.ConfigManager.saveChests) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockEntity be = client.world.getBlockEntity(targetPos);
        if (be instanceof Inventory inv) {
            int containerSize = inv.size();

            if (be instanceof LootableContainerBlockEntity lootable) {
                lootable.setLootTable(null); // 清除战利品表，这样它就会保存里面的物品
            }

            int realSize = contents.size() - 36;
            if (realSize <= 0) return; // 忽略不是真实容器的发包
            
            for (int i = 0; i < containerSize && i < realSize; i++) {
                inv.setStack(i, contents.get(i).copy());
            }

            // 处理大箱子
            if (realSize > containerSize && be instanceof net.minecraft.block.entity.ChestBlockEntity) {
                net.minecraft.block.BlockState state = client.world.getBlockState(targetPos);
                if (state.getBlock() instanceof net.minecraft.block.ChestBlock) {
                    net.minecraft.util.math.Direction facing = state.get(net.minecraft.block.ChestBlock.FACING);
                    net.minecraft.block.enums.ChestType type = state.get(net.minecraft.block.ChestBlock.CHEST_TYPE);
                    
                    BlockPos otherPos = null;
                    if (type == net.minecraft.block.enums.ChestType.RIGHT) {
                        otherPos = targetPos.offset(facing.rotateYCounterclockwise());
                    } else if (type == net.minecraft.block.enums.ChestType.LEFT) {
                        otherPos = targetPos.offset(facing.rotateYClockwise());
                    }

                    if (otherPos != null) {
                        BlockEntity otherBe = client.world.getBlockEntity(otherPos);
                        if (otherBe instanceof Inventory otherInv) {
                            if (otherBe instanceof LootableContainerBlockEntity otherLootable) {
                                otherLootable.setLootTable(null);
                            }
                            for (int i = 0; i < otherInv.size() && (i + containerSize) < realSize; i++) {
                                otherInv.setStack(i, contents.get(i + containerSize).copy());
                            }
                            savedChests.add(otherPos);
                            NbtCompound nbt = otherBe.createNbtWithIdentifyingData(client.world.getRegistryManager());
                            DownloadManager.savedBlockEntitiesCache.put(otherPos, nbt);
                            DownloadManager.saveChunk(new net.minecraft.util.math.ChunkPos(otherPos));
                        }
                    }
                }
            }

            savedChests.add(targetPos);
            NbtCompound nbt = be.createNbtWithIdentifyingData(client.world.getRegistryManager());
            DownloadManager.savedBlockEntitiesCache.put(targetPos, nbt);
            DownloadManager.saveChunk(new net.minecraft.util.math.ChunkPos(targetPos));
            
            if (AutoScanner.getCurrentPhase() == AutoScanner.Phase.MANUAL_SCAN) {
                int remaining = 0;
                for (BlockPos pos : ignoredChests) {
                    if (!savedChests.contains(pos)) remaining++;
                }
                client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chest.saved.remaining", remaining), true);
            } else {
                client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chest.saved", targetPos.toShortString()), true);
            }
        }
    }
    public static void saveCurrentContainer(ScreenHandler handler) {
        if (!DownloadManager.isRecording() || lastClickedBlock == null || !com.teoe.wdl.ConfigManager.saveChests) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockEntity be = client.world.getBlockEntity(lastClickedBlock);
        if (be instanceof Inventory inv) {
            int containerSize = inv.size();
            if (handler.slots.size() < containerSize) return;

            if (be instanceof LootableContainerBlockEntity lootable) {
                lootable.setLootTable(null); // 清除战利品表，这样它就会保存里面的物品
            }

            // 获取屏幕发来的真实容器大小（刨除玩家背包 36 格）
            int realSize = handler.slots.size() - 36;
            if (realSize <= 0) return; // 忽略不是真实容器的发包
            
            for (int i = 0; i < containerSize && i < realSize; i++) {
                inv.setStack(i, handler.getSlot(i).getStack().copy());
            }

            // 处理大箱子：如果传来的数据大于当前点击的单个箱子
            if (realSize > containerSize && be instanceof net.minecraft.block.entity.ChestBlockEntity) {
                net.minecraft.block.BlockState state = client.world.getBlockState(lastClickedBlock);
                if (state.getBlock() instanceof net.minecraft.block.ChestBlock) {
                    net.minecraft.util.math.Direction facing = state.get(net.minecraft.block.ChestBlock.FACING);
                    net.minecraft.block.enums.ChestType type = state.get(net.minecraft.block.ChestBlock.CHEST_TYPE);
                    
                    BlockPos otherPos = null;
                    if (type == net.minecraft.block.enums.ChestType.RIGHT) {
                        otherPos = lastClickedBlock.offset(facing.rotateYCounterclockwise());
                    } else if (type == net.minecraft.block.enums.ChestType.LEFT) {
                        otherPos = lastClickedBlock.offset(facing.rotateYClockwise());
                    }

                    if (otherPos != null) {
                        BlockEntity otherBe = client.world.getBlockEntity(otherPos);
                        if (otherBe instanceof Inventory otherInv) {
                            if (otherBe instanceof LootableContainerBlockEntity otherLootable) {
                                otherLootable.setLootTable(null);
                            }
                            for (int i = 0; i < otherInv.size() && (i + containerSize) < realSize; i++) {
                                otherInv.setStack(i, handler.getSlot(i + containerSize).getStack().copy());
                            }
                            savedChests.add(otherPos);
                            NbtCompound nbt = otherBe.createNbtWithIdentifyingData(client.world.getRegistryManager());
                            DownloadManager.savedBlockEntitiesCache.put(otherPos, nbt);
                            DownloadManager.saveChunk(new net.minecraft.util.math.ChunkPos(otherPos));
                        }
                    }
                }
            }

            savedChests.add(lastClickedBlock);
            NbtCompound nbt = be.createNbtWithIdentifyingData(client.world.getRegistryManager());
            DownloadManager.savedBlockEntitiesCache.put(lastClickedBlock, nbt);
            DownloadManager.saveChunk(new net.minecraft.util.math.ChunkPos(lastClickedBlock));
            
            if (AutoScanner.getCurrentPhase() == AutoScanner.Phase.MANUAL_SCAN) {
                int remaining = 0;
                for (BlockPos pos : ignoredChests) {
                    if (!savedChests.contains(pos)) remaining++;
                }
                client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chest.saved.remaining", remaining), true);
            } else {
                client.player.sendMessage(net.minecraft.text.Text.translatable("teoe.wdl.chest.saved", lastClickedBlock.toShortString()), true);
            }
        }
        
        lastClickedBlock = null;
    }
}