package com.caucraft.shadowmap.api.example;

import com.caucraft.shadowmap.api.ui.MapDecorator;
import com.caucraft.shadowmap.api.ui.MapRenderContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Vector2d;

public class MapDecoratorDemo implements MapDecorator {
    @Override
    public void preRenderMap(MapRenderContext context) {

    }

    @Override
    public void postRenderMap(MapRenderContext context) {
        // Draw black grid lines around every chunk if zoomed in enough, and
        // white around every region.

        // This renders on the map's framebuffer and will be cropped if the map
        // is cropped.

        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        int regionMinX = context.regionBounds.x1;
        int regionMinZ = context.regionBounds.z1;
        int regionMaxX = context.regionBounds.x2;
        int regionMaxZ = context.regionBounds.z2;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        if (context.zoom >= 0.5) {
            double min = regionMinX << 9;
            double max = (regionMaxX + 1) << 9;
            int limit = (regionMaxZ + 1) << 9;
            for (int z = regionMinZ << 9; z <= limit; z += 16) {
                context.worldVertex(min, z, 0).color(0, 0, 0, 64).next();
                context.worldVertex(max, z, 0).color(0, 0, 0, 64).next();
            }

            min = regionMinZ << 9;
            max = (regionMaxZ + 1) << 9;
            limit = (regionMaxX + 1) << 9;
            for (int x = regionMinX << 9; x <= limit; x += 16) {
                context.worldVertex(x, min, 0).color(0, 0, 0, 64).next();
                context.worldVertex(x, max, 0).color(0, 0, 0, 64).next();
            }
        }

        double min = regionMinX << 9;
        double max = (regionMaxX + 1) << 9;
        for (int rz = regionMinZ; rz <= regionMaxZ; rz++) {
            float z = (float) (rz << 9);
            context.worldVertex(min, z, 0).color(255, 255, 255, 128).next();
            context.worldVertex(max, z, 0).color(255, 255, 255, 128).next();
        }

        min = regionMinZ << 9;
        max = (regionMaxZ + 1) << 9;
        for (int rx = regionMinX; rx <= regionMaxX; rx++) {
            float x = (float) (rx << 9);
            context.worldVertex(x, min, 0).color(255, 255, 255, 128).next();
            context.worldVertex(x, max, 0).color(255, 255, 255, 128).next();
        }

        tess.draw();
    }

    @Override
    public void renderDecorations(MapRenderContext context) {
        // This renders on the window framebuffer, so even if the position isn't
        // "on the map", it will still be visible (ex the (0, 0) markers).
        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Draw a square around blocks [(-1, -1) -> (1, 1)] in the world.
        context.worldVertex(-1, -1, 0).color(255, 0, 0, 255).next();
        context.worldVertex(-1, 2, 0).color(255, 0, 0, 255).next();
        context.worldVertex(-1, 2, 0).color(255, 0, 0, 255).next();
        context.worldVertex(2, 2, 0).color(255, 0, 0, 255).next();
        context.worldVertex(2, 2, 0).color(255, 0, 0, 255).next();
        context.worldVertex(2, -1, 0).color(255, 0, 0, 255).next();
        context.worldVertex(2, -1, 0).color(255, 0, 0, 255).next();
        context.worldVertex(-1, -1, 0).color(255, 0, 0, 255).next();

        // Draw a pointer in the center of the map, towards the top of the
        // screen. The center of the map is the center of the universe.
        // Note: the center of the fullscreen map is not the player!
        context.uiVertex(-5, 0, 0).color(0, 255, 0, 255).next();
        context.uiVertex(0, 5, 0).color(0, 255, 0, 255).next();
        context.uiVertex(0, 5, 0).color(0, 255, 0, 255).next();
        context.uiVertex(5, 0, 0).color(0, 255, 0, 255).next();
        context.uiVertex(5, 0, 0).color(0, 255, 0, 255).next();
        context.uiVertex(0, -15, 0).color(0, 255, 0, 255).next();
        context.uiVertex(0, -15, 0).color(0, 255, 0, 255).next();
        context.uiVertex(-5, 0, 0).color(0, 255, 0, 255).next();

        // Get the ui coordinates of the block at (0, 0), then draw an arrow
        // pointing to it towards the bottom of the screen.
        Vector2d vec = new Vector2d(48.5, 24.5);
        context.worldToUi.transformPosition(vec);
        context.uiVertex(vec.x, vec.y, 0)           .color(192, 0, 0, 255).next();
        context.uiVertex(vec.x - 4, vec.y - 12, 0)  .color(192, 0, 0, 255).next();
        context.uiVertex(vec.x, vec.y, 0)           .color(192, 0, 0, 255).next();
        context.uiVertex(vec.x + 4, vec.y - 12, 0)  .color(192, 0, 0, 255).next();
        context.uiVertex(vec.x - 4, vec.y - 12, 0)  .color(192, 0, 0, 255).next();
        context.uiVertex(vec.x + 4, vec.y - 12, 0)  .color(192, 0, 0, 255).next();

        // Get the block coordinates at the center of the map, then draw a box
        // around the 5x5 of blocks around that block.
        vec.set(0, 0);
        context.uiToWorld.transformPosition(vec);
        vec.floor();
        context.worldVertex(vec.x - 2, vec.y - 2, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x - 2, vec.y + 3, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x - 2, vec.y + 3, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x + 3, vec.y + 3, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x + 3, vec.y + 3, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x + 3, vec.y - 2, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x + 3, vec.y - 2, 0).color(128, 255, 128, 255).next();
        context.worldVertex(vec.x - 2, vec.y - 2, 0).color(128, 255, 128, 255).next();
        tess.draw();
    }
}
