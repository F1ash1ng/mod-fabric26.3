package com.example.mobilitydevice.client;

import com.example.mobilitydevice.item.WaistJetItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;

public final class MobilitySettingsShortcut {
    private MobilitySettingsShortcut() {}

    public static boolean open(Screen parent, Slot hoveredSlot, KeyEvent event) {
        if (event.key() != InputConstants.KEY_LSHIFT && event.key() != InputConstants.KEY_RSHIFT)
            return false;
        if (hoveredSlot == null || !hoveredSlot.hasItem()
                || !hoveredSlot.getItem().is(WaistJetItems.WAIST_JET)) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        client.gui.setScreen(new MobilitySettingsScreen(parent));
        return true;
    }
}
