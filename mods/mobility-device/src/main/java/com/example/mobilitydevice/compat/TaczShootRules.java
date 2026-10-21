package com.example.mobilitydevice.compat;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.ability.SlideAbility;
import com.example.mobilitydevice.ability.WallRunAbility;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import net.minecraft.server.level.ServerPlayer;

public final class TaczShootRules {
    private TaczShootRules() {}

    public static boolean canHipFire(ServerPlayer player) {
        if (!WaistJetItems.isEquipped(player) || !MobilitySettings.enabled(player, MobilitySettings.TACZ)) return false;
        if (WallRunAbility.isActive(player) || DashAbility.isActive(player)
                || SlideAbility.isActive(player)) return true;
        // ServerPlayer is moved by client position packets while running, so its
        // delta movement can be zero even though the player is visibly moving.
        // TaCZ only calls the shooting gate after receiving a fire request.
        return player.onGround();
    }
}
