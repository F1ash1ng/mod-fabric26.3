package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.client.MobilityDeviceClient;
import com.example.mobilitydevice.item.WaistJetItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/** Exercises the real V input, server acceptance, GPU post effect and stop conditions. */
public final class DashScreenEffectTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                BlockPos origin = player.blockPosition();
                // A textured wall makes the radial streaks easy to review, and blocks dash movement.
                for (int x = -12; x <= 12; x++) {
                    for (int y = -6; y <= 12; y++) {
                        player.level().setBlockAndUpdate(origin.offset(x, y, 8),
                                ((x + y) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.BRICKS).defaultBlockState());
                    }
                }
                WaistJetItems.setEquipped(player, new ItemStack(WaistJetItems.WAIST_JET));
            });
            context.waitFor(client -> WaistJetItems.isEquipped(client.player));
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.getInput().lookAt(0, 0);
            world.getConnection().waitForChunksRender();
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) < 0),
                    "Effect must be off before V");
            context.runOnClient(client -> check(client.getShaderManager().isPostEffectValid(
                    MobilityDeviceMod.id("dash_blur"), Set.of(PostChain.MAIN_TARGET_ID)), "Post effect failed to load"));
            context.takeScreenshot("dash-before");

            pressDash(context);
            context.waitFor(client -> MobilityDeviceClient.isDashActive());
            context.waitTicks(4);
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) >= 0),
                    "Accepted dash must enable blur");
            context.takeScreenshot("dash-active");
            context.waitTicks(20);
            check(context.computeOnClient(client -> !MobilityDeviceClient.isDashActive()
                    && MobilityDeviceClient.dashVisualProgress(0) < 0), "Dash end must remove blur");
            context.takeScreenshot("dash-after");

            pressDash(context);
            context.waitTicks(3);
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) < 0),
                    "Rejected cooldown input must not enable blur");

            resetCooldown(context, world);
            pressDash(context);
            context.waitFor(client -> MobilityDeviceClient.isDashActive());
            context.setScreen(() -> new InventoryScreen(world.getConnection().getClientPlayer()));
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) < 0),
                    "Inventory must remain clear");
            context.setScreen(() -> null);
            world.getServer().runOnServer(server -> WaistJetItems.setEquipped(
                    world.getConnection().getServerPlayer(), ItemStack.EMPTY));
            context.waitFor(client -> !MobilityDeviceClient.isDashActive());
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) < 0),
                    "Unequipping must remove blur");

            world.getServer().runOnServer(server -> WaistJetItems.setEquipped(
                    world.getConnection().getServerPlayer(), new ItemStack(WaistJetItems.WAIST_JET)));
            context.waitFor(client -> WaistJetItems.isEquipped(client.player));
            resetCooldown(context, world);
            pressDash(context);
            context.waitFor(client -> MobilityDeviceClient.isDashActive());
            context.runOnClient(client -> client.player.setHealth(0));
            context.waitTick();
            check(context.computeOnClient(client -> MobilityDeviceClient.dashVisualProgress(0) < 0),
                    "Death must remove blur");
        }
        check(context.computeOnClient(client -> !MobilityDeviceClient.isDashActive()
                && MobilityDeviceClient.dashVisualProgress(0) < 0), "Disconnect must clear effect state");
    }

    private static void resetCooldown(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> world.getConnection().getServerPlayer()
                .setAttached(DashAbility.COOLDOWN_END, 0L));
        context.waitTick();
    }

    private static void pressDash(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_V);
        context.waitTicks(2);
        context.getInput().releaseKey(InputConstants.KEY_V);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
