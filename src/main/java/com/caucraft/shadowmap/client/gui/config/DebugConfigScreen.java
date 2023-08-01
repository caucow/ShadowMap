package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.DebugConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class DebugConfigScreen extends Screen {
    private final Screen parentScreen;
    private final DebugConfig config;
    private final RecustomTextFieldWidget surfaceMax;
    private final RecustomTextFieldWidget caveMax;
    private final RecustomTextFieldWidget[] surfaceShades;
    private final RecustomTextFieldWidget[] caveShades;
    private final RecustomIconButtonWidget done;

    public DebugConfigScreen(Screen parentScreen) {
        super(Text.of("Debug"));
        this.parentScreen = parentScreen;
        this.config = ShadowMap.getInstance().getConfig().debugConfig;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        surfaceMax = new RecustomTextFieldWidget(textRenderer, 0, 0, 40, 16, null);
        surfaceMax.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        surfaceMax.setText(Integer.toString(config.surfaceMax.get()));
        surfaceMax.setTypedChangeListener((str) -> {
            try {
                config.surfaceMax.set(Integer.parseInt(str));
            } catch (NumberFormatException ignore) {}
        });
        caveMax = new RecustomTextFieldWidget(textRenderer, 0, 0, 40, 16, null);
        caveMax.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        caveMax.setText(Integer.toString(config.caveMax.get()));
        caveMax.setTypedChangeListener((str) -> {
            try {
                config.caveMax.set(Integer.parseInt(str));
            } catch (NumberFormatException ignore) {}
        });
        surfaceShades = new RecustomTextFieldWidget[24];
        caveShades = new RecustomTextFieldWidget[24];
        for (int i = 0; i < 24; i++) {
            final int fi = i;
            surfaceShades[i] = new RecustomTextFieldWidget(textRenderer, 0, 0, 40, 16, null);
            surfaceShades[i].setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
            surfaceShades[i].setText(Integer.toString(config.surfaceShades[i].get()));
            surfaceShades[i].setTypedChangeListener((str) -> {
                try {
                    config.surfaceShades[fi].set(Integer.parseInt(str));
                } catch (NumberFormatException ignore) {}
            });
            caveShades[i] = new RecustomTextFieldWidget(textRenderer, 0, 0, 40, 16, null);
            caveShades[i].setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
            caveShades[i].setText(Integer.toString(config.caveShades[i].get()));
            caveShades[i].setTypedChangeListener((str) -> {
                try {
                    config.caveShades[fi].set(Integer.parseInt(str));
                } catch (NumberFormatException ignore) {}
            });
        }
        done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", (btn) -> MinecraftClient.getInstance().setScreen(parentScreen));
    }

    @Override
    protected void init() {
        addDrawableChild(surfaceMax);
        addDrawableChild(caveMax);
        for (int i = 0; i < 24; i++) {
            addDrawableChild(surfaceShades[i]);
            addDrawableChild(caveShades[i]);
        }
        addDrawableChild(done);

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        final int midX = width / 2;
        int y = (height - 240) / 3 + 10;

        surfaceMax.setPos(midX - 150, y);
        caveMax.setPos(midX + 4, y);
        y += 22;

        for (int i = 0; i < 8; i++, y += 22) {
            surfaceShades[i * 3].setPos(midX - 150, y);
            surfaceShades[i * 3 + 1].setPos(midX - 100, y);
            surfaceShades[i * 3 + 2].setPos(midX - 50, y);
            caveShades[i * 3].setPos(midX + 4, y);
            caveShades[i * 3 + 1].setPos(midX + 54, y);
            caveShades[i * 3 + 2].setPos(midX + 104, y);
        }

        y = (height - 240) / 3 + 210;
        done.setPos(midX - 75, y);
    }
}
