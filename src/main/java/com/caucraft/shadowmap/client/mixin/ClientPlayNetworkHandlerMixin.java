package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.client.ShadowMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onDeathMessage(Lnet/minecraft/network/packet/s2c/play/DeathMessageS2CPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;showsDeathScreen()Z",
                    shift = At.Shift.BY, by = -2))
    private void injectOnDeathMessage(DeathMessageS2CPacket packet, CallbackInfo callback) {
        MinecraftClient client = this.client;
        ClientPlayerEntity player = client.player;
        ShadowMap.getInstance().onPlayerDeath(client, player);
    }
}
