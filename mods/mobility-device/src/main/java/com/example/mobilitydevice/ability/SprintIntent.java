package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

public final class SprintIntent {
    private SprintIntent() {}

    public static boolean hasMovementInput(ServerPlayer player) {
        Input input = player.getLastClientInput();
        return input != null && (input.forward() || input.backward()
                || input.left() || input.right());
    }

    public static boolean shouldForceSprint(ServerPlayer player) {
        return WaistJetItems.isEquipped(player) && (hasMovementInput(player)
                || DashAbility.isActive(player) || SlideAbility.isActive(player)
                || WallRunAbility.isActive(player));
    }
}
