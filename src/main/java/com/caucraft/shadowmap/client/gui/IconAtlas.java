package com.caucraft.shadowmap.client.gui;

import com.caucraft.shadowmap.client.ShadowMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasHolder;
import net.minecraft.util.Identifier;

public class IconAtlas extends SpriteAtlasHolder implements IdentifiableResourceReloadListener {
    public static final Identifier RESOURCE_ID = new Identifier(ShadowMap.MOD_ID, "atlas_icons");
    public static final Identifier TEX_ID = new Identifier(ShadowMap.MOD_ID, "textures/atlas/icons.png");

    public IconAtlas() {
        super(MinecraftClient.getInstance().getTextureManager(), TEX_ID, new Identifier(ShadowMap.MOD_ID, "icons"));
    }

    public Sprite getIcon(Icons icon) {
        return getSprite(icon.id);
    }

    @Override
    public Identifier getFabricId() {
        return RESOURCE_ID;
    }
}
