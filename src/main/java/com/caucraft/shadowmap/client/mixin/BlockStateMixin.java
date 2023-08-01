package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.client.util.MapBlockStateMutable;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockState.class)
public class BlockStateMixin implements MapBlockStateMutable {

    @Unique
    private boolean scmapTinted;
    @Unique
    private int scmapArgb;
    @Unique
    private boolean scmapOpacitySet;
    @Unique
    private boolean scmapOpaque;
    @Unique
    private int scmapMaxOpacity;

    @Override @Unique
    public void shadowMap$setTinted(boolean tinted) {
        this.scmapTinted = tinted;
    }

    @Override @Unique
    public boolean shadowMap$isTinted() {
        return scmapTinted;
    }

    @Override @Unique
    public void shadowMap$setColorARGB(int argb) {
        this.scmapArgb = argb;
    }

    @Override @Unique
    public boolean shadowMap$isOpacitySet() {
        return scmapOpacitySet;
    }

    @Override @Unique
    public void shadowMap$setOpacity(boolean opaque, int maxOpacity) {
        this.scmapOpacitySet = true;
        this.scmapOpaque = opaque;
        this.scmapMaxOpacity = Math.max(64, maxOpacity);
    }

    @Override @Unique
    public int shadowMap$getColorARGB() {
        return scmapArgb;
    }

    @Override @Unique
    public boolean shadowMap$isOpaque() {
        return scmapOpaque;
    }

    @Override@Unique
    public int shadowMap$getMaxOpacity() {
        return scmapMaxOpacity;
    }
}
