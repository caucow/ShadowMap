package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.api.util.ServerKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;disconnect()V", shift = At.Shift.AFTER))
    private static void injectConnect(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, CallbackInfo callback) {
        if (info.isLocal()) {
            ShadowMap.getInstance().setServerKey(ServerKey.newKey(ServerKey.ServerType.LAN, info.name, -1));
        } else {
            ShadowMap.getInstance().setServerKey(ServerKey.newKey(ServerKey.ServerType.MULTIPLAYER, address.getAddress(), address.getPort()));
        }
    }
}
