package com.caucraft.shadowmap.client;

import com.caucraft.shadowmap.client.gui.config.CoreConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuReentry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CoreConfigScreen::new;
    }
}
