package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {
    @Inject(method = "loadChunkFromPacket(IILnet/minecraft/network/PacketByteBuf;Lnet/minecraft/nbt/NbtCompound;Ljava/util/function/Consumer;)Lnet/minecraft/world/chunk/WorldChunk;",
            at = @At("TAIL"))
    private void injectLoadChunkFromPacket(int chunkX, int chunkZ, PacketByteBuf buf, NbtCompound nbt, Consumer<ChunkData.BlockEntityVisitor> consumer, CallbackInfoReturnable<WorldChunk> callback) {
        WorldChunk chunk = callback.getReturnValue();
        if (chunk == null) {
            return;
        }
        MapManagerImpl mapManager = ShadowMap.getInstance().getMapManager();
        ShadowMap.getInstance().getMapManager().scheduleUpdateChunk(chunk);
        int present = 0;
        World world = chunk.getWorld();
        for (int oz = 0; oz < 5; oz++) {
            for (int ox = 0; ox < 5; ox++) {
                Chunk other = world.getChunk(chunkX + ox - 2, chunkZ + oz - 2, ChunkStatus.FULL, false);
                if (other != null) {
                    present |= 1 << (5 * oz + ox);
                }
            }
        }
        for (int oz = 1; oz < 4; oz++) {
            for (int ox = 1; ox < 4; ox++) {
                int nearby = present >>> (oz * 5 + ox - 6) & 0x007 | present >>> (oz * 5 + ox - 4) & 0x038 | present >>> (oz * 5 + ox - 2) & 0x1C0;
                if (nearby == 0x01FF) {
                    mapManager.scheduleUpdateSurroundedChunk(world.getChunk(chunkX + ox - 2, chunkZ + oz - 2));
                }
            }
        }
    }
}
