package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.ability.SlideAbility;
import com.example.mobilitydevice.ability.WallRunVisuals;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.WaistJetRenderState;
import com.example.mobilitydevice.client.WallRunRenderState;
import com.example.mobilitydevice.item.WaistJetItems;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Exercises the render path used for another player's avatar, not the local camera. */
public final class RemotePlayerRenderTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                RemotePlayer remote = new RemotePlayer(client.level,
                        new GameProfile(UUID.randomUUID(), "RemotePilot"));
                remote.setId(1_000_000);
                WaistJetItems.setEquipped(remote, new ItemStack(WaistJetItems.WAIST_JET));
                var renderer = client.getEntityRenderDispatcher().getRenderer(remote);
                for (int direction = 0; direction < 8; direction++) {
                    remote.setAttached(DashAbility.ACTIVE_DIRECTION, direction);
                    AvatarRenderState state = (AvatarRenderState) renderer.createRenderState(remote, 1);
                    check(((WaistJetRenderState) state).mobility$hasWaistJet(),
                            "Remote player's device must render");
                    check(((WaistJetRenderState) state).mobility$waistJetDirection() == direction,
                            "Remote player's jet direction must match server state " + direction);
                }

                remote.setAttached(DashAbility.ACTIVE_DIRECTION, -1);
                remote.setAttached(SlideAbility.POSTURE, true);
                remote.setAttached(SlideAbility.YAW, 45.0F);
                AvatarRenderState sliding = (AvatarRenderState) renderer.createRenderState(remote, 1);
                check(((SlideRenderState) sliding).mobility$hasSlidePose(),
                        "Remote player's slide model must activate");
                check(Math.abs(((SlideRenderState) sliding).mobility$slideYaw() - 45.0F) < .001F,
                        "Remote player's slide direction must match server state");

                remote.setAttached(SlideAbility.POSTURE, false);
                for (int side : new int[]{-1, 1}) {
                    remote.setAttached(WallRunVisuals.STATE,
                            new WallRunVisuals(0, side, 1, 0, client.level.getGameTime()));
                    AvatarRenderState running = (AvatarRenderState) renderer.createRenderState(remote, 1);
                    check(((WallRunRenderState) running).mobility$wallSide() == side,
                            "Remote player's wall-run pose must reflect wall side " + side);
                    check(!((SlideRenderState) running).mobility$hasSlidePose(),
                            "Remote wall-run must not retain slide pose");
                }
                remote.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
                AvatarRenderState standing = (AvatarRenderState) renderer.createRenderState(remote, 1);
                check(((WallRunRenderState) standing).mobility$wallSide() == 0,
                        "Remote player must return to standing pose");
                check(((WaistJetRenderState) standing).mobility$hasWaistJet(),
                        "Remote device must remain visible after wall-run");
            });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
