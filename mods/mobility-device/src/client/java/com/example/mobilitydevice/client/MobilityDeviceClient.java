package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.ability.DashMotion;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.network.ActivateDashPayload;
import com.example.mobilitydevice.network.DashStartedPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class MobilityDeviceClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MobilityDeviceMod.id("controls"));
    private static KeyMapping dashKey;
    private static boolean wasDown;
    private static double dashX, dashZ, dashStartX, dashStartZ, dashStartY;
    private static double lastProgress;
    private static int stalledTicks;
    private static boolean previousNoGravity;
    private static boolean dashGravityOverride;
    private static LocalPlayer dashPlayer;
    private static ClientLevel dashLevel;

    @Override
    public void onInitializeClient() {
        dashKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobility_device.dash", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(DashStartedPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.player == null) return;
            stopDash(client, true);
            dashPlayer = client.player;
            dashLevel = client.level;
            dashX = payload.x();
            dashZ = payload.z();
            dashStartX = payload.startX();
            dashStartZ = payload.startZ();
            dashStartY = payload.startY();
            previousNoGravity = payload.previousNoGravity();
            dashGravityOverride = true;
            client.player.setOnGround(false);
            client.player.setNoGravity(true);
            double progress = progress(client.player);
            double speed = DashMotion.nextSpeed(progress);
            client.player.setDeltaMovement(dashX * speed,
                    DashMotion.heightAt(progress + speed) - DashMotion.heightAt(progress), dashZ * speed);
            lastProgress = progress;
            stalledTicks = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(MobilityDeviceClient::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopDash(client, true));
        WaistJetRenderer.register();
    }

    private static void onTick(Minecraft client) {
        boolean down = dashKey.isDown();
        if (down && !wasDown && client.player != null && client.gui.screen() == null
                && WaistJetItems.isEquipped(client.player)) {
            byte forward = (byte) ((client.options.keyUp.isDown() ? 1 : 0)
                    - (client.options.keyDown.isDown() ? 1 : 0));
            byte strafe = (byte) ((client.options.keyRight.isDown() ? 1 : 0)
                    - (client.options.keyLeft.isDown() ? 1 : 0));
            ClientPlayNetworking.send(new ActivateDashPayload(forward, strafe, client.player.getYRot()));
        }
        wasDown = down;
        if (dashGravityOverride && (client.player != dashPlayer || client.level != dashLevel)) {
            stopDash(client, true);
        }
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !WaistJetItems.isEquipped(client.player)) {
            stopDash(client, true);
            return;
        }
        client.player.setSprinting(true);
        if (dashGravityOverride) {
            double progress = progress(client.player);
            if (progress >= DashMotion.TOTAL_DISTANCE - 0.001) {
                stopDash(client, false);
                return;
            }
            stalledTicks = progress <= lastProgress + 0.015 ? stalledTicks + 1 : 0;
            lastProgress = progress;
            if (stalledTicks >= 3) {
                stopDash(client, true);
                return;
            }
            client.player.setNoGravity(true);
            client.player.setOnGround(false);
            double targetY = dashStartY + DashMotion.heightAt(progress);
            client.player.move(MoverType.SELF, new Vec3(0, targetY - client.player.getY(), 0));
            double speed = DashMotion.nextSpeed(progress);
            double verticalSpeed = DashMotion.heightAt(progress + speed) - DashMotion.heightAt(progress);
            client.player.setDeltaMovement(dashX * speed, verticalSpeed, dashZ * speed);
        }
    }

    public static boolean isDashActive() {
        return dashGravityOverride;
    }

    /** Negative means off. Capture once per frame so extraction and drawing share a snapshot. */
    public static float dashVisualProgress(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!dashGravityOverride || client.player != dashPlayer || client.level != dashLevel
                || client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !WaistJetItems.isEquipped(client.player) || client.gui.screen() != null) return -1;
        double current = progress(client.player);
        double speed = Math.max(0, client.player.getDeltaMovement().x * dashX
                + client.player.getDeltaMovement().z * dashZ);
        return (float) Math.clamp(current + speed * partialTick, 0, DashMotion.TOTAL_DISTANCE);
    }

    private static double progress(LocalPlayer player) {
        return DashMotion.progress(player.getX(), player.getZ(), dashStartX, dashStartZ, dashX, dashZ);
    }

    private static void stopDash(Minecraft client, boolean interrupted) {
        if (dashGravityOverride && dashPlayer != null) {
            dashPlayer.setNoGravity(previousNoGravity);
            if (interrupted) {
                dashPlayer.setDeltaMovement(Vec3.ZERO);
            } else {
                dashPlayer.setDeltaMovement(dashX * DashMotion.exitSpeed(),
                        Math.min(dashPlayer.getDeltaMovement().y, 0), dashZ * DashMotion.exitSpeed());
            }
        }
        dashGravityOverride = false;
        stalledTicks = 0;
        dashPlayer = null;
        dashLevel = null;
    }
}
