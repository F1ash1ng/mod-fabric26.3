package com.example.mobilitydevice.client;

import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class TaczClientShootRules {
    private TaczClientShootRules() {}

    public static boolean canHipFire(LocalPlayer player) {
        if (!WaistJetItems.isEquipped(player) || !MobilitySettings.enabled(player, MobilitySettings.TACZ)) return false;
        if (WallRunClient.isActive() || MobilityDeviceClient.isDashActive()
                || SlideClient.isSlideMotionActive()) return true;
        return player.onGround() && MobilityDeviceClient.hasMovementInput(player);
    }

    public static boolean useCrouchedGunPose(LocalPlayer player) {
        if (!WaistJetItems.isEquipped(player) || !MobilitySettings.enabled(player, MobilitySettings.TACZ)
                || SlideClient.isSlideMotionActive()) return false;
        if (WallRunClient.isActive()) return true;
        return Minecraft.getInstance().options.keyAttack.isDown() && canHipFire(player);
    }
}
