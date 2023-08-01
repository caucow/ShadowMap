package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.client.ShadowMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "<init>(Lnet/minecraft/client/RunArgs;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/debug/DebugRenderer;<init>(Lnet/minecraft/client/MinecraftClient;)V", ordinal = 0))
    private void injectPreResourceRegistration(RunArgs args, CallbackInfo callback) {
        ShadowMap.getInstance().onPreResourceRegistration();
    }

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;showsDeathScreen()Z",
                    shift = At.Shift.BY, by = -3))
    private void injectSetScreen(@Nullable Screen screen, CallbackInfo callback) {
        MinecraftClient client = ((MinecraftClient) (Object) this);
        ShadowMap.getInstance().onPlayerDeath(client, client.player);
    }

    @Inject(method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lnet/minecraft/resource/ResourcePackManager;Lnet/minecraft/server/SaveLoader;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;disconnect()V", shift = At.Shift.AFTER))
    private void injectStartIntegratedServer(String levelName, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, boolean newWorld, CallbackInfo callback) {
        ShadowMap.getInstance().setServerKey(ServerKey.newKey(ServerKey.ServerType.SINGLEPLAYER, levelName, -1));
    }

    @Inject(method = "joinWorld(Lnet/minecraft/client/world/ClientWorld;)V",
            at = @At("HEAD"))
    private void injectJoinWorld(ClientWorld world, CallbackInfo callback) {
        ShadowMap.getInstance().getMapManager().onWorldChanged(world);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V",
            at = @At("HEAD"))
    private void injectDisconnect(Screen screen, CallbackInfo callback) {
        ShadowMap scmap = ShadowMap.getInstance();
        scmap.getMapManager().onWorldChanged(null);
        scmap.setServerKey(null);
    }
}
