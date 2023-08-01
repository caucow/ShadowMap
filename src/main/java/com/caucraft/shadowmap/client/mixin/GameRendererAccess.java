package com.caucraft.shadowmap.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccess {
    @Invoker("getFov") double shadowMap$getFov(Camera camera, float tickDelta, boolean changingFov);
}
