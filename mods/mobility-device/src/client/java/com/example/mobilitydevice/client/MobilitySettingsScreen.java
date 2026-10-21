package com.example.mobilitydevice.client;

import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.UpdateSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MobilitySettingsScreen extends Screen {
    private static final int[] FEATURES = {
            MobilitySettings.DASH, MobilitySettings.SLIDE,
            MobilitySettings.WALL_RUN, MobilitySettings.TACZ,
            MobilitySettings.MENDING_OFF
    };
    private static final String[] NAMES = {
            "screen.mobility_device.dash", "screen.mobility_device.slide",
            "screen.mobility_device.wall_run", "screen.mobility_device.tacz",
            "screen.mobility_device.mending"
    };
    private final Screen parent;

    public MobilitySettingsScreen(Screen parent) {
        super(Component.translatable("screen.mobility_device.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - 220) / 2;
        int top = (height - 204) / 2;
        for (int i = 0; i < FEATURES.length; i++) {
            final int feature = FEATURES[i];
            final String name = NAMES[i];
            Button button = Button.builder(label(name, feature), pressed -> {
                if (minecraft.player == null) return;
                int flags = MobilitySettings.flags(minecraft.player) ^ feature;
                MobilitySettings.set(minecraft.player, flags);
                ClientPlayNetworking.send(new UpdateSettingsPayload(flags));
                pressed.setMessage(label(name, feature));
            }).bounds(left + 20, top + 30 + i * 27, 180, 20).build();
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(left + 60, top + 175, 100, 20).build());
    }

    private Component label(String name, int feature) {
        boolean enabled = minecraft.player != null && (feature == MobilitySettings.MENDING_OFF
                ? MobilitySettings.mendingEnabled(minecraft.player)
                : MobilitySettings.enabled(minecraft.player, feature));
        return Component.translatable("screen.mobility_device.option",
                Component.translatable(name), Component.translatable(enabled
                        ? "screen.mobility_device.on" : "screen.mobility_device.off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - 220) / 2;
        int top = (height - 204) / 2;
        // Flat inventory-gray panel with the same dark/light pixel border as vanilla menus.
        graphics.fill(left, top, left + 220, top + 204, 0xFF000000);
        graphics.fill(left + 1, top + 1, left + 219, top + 203, 0xFF8B8B8B);
        graphics.fill(left + 2, top + 2, left + 218, top + 202, 0xFFC6C6C6);
        graphics.fill(left + 3, top + 3, left + 217, top + 201, 0xFF555555);
        graphics.fill(left + 4, top + 4, left + 216, top + 200, 0xFFC6C6C6);
        graphics.text(font, title, left + (220 - font.width(title)) / 2,
                top + 11, 0xFF404040, false);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
