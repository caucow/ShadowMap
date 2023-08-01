package com.caucraft.shadowmap.client.mixin;

import com.caucraft.shadowmap.client.util.MapAbstractBlock;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin implements MapAbstractBlock {
    @Shadow
    @Final
    protected boolean collidable;

    @Override @Unique
    public boolean shadowMap$isCollidable() {
        return this.collidable;
    }
}
