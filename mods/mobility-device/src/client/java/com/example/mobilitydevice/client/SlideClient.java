package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.ability.SlideAbility;
import com.example.mobilitydevice.ability.SlideMotion;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.ActivateSlidePayload;
import com.example.mobilitydevice.network.SlideStartedPayload;
import com.example.mobilitydevice.network.SlideJumpPayload;
import com.example.mobilitydevice.network.SlideStoppedPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class SlideClient {
    private static KeyMapping slideKey;
    private static boolean wasDown;
    private static boolean wasJumpDown;
    private static boolean active;
    private static boolean trapped;
    private static int postureReleaseTicks;
    private static double directionX, directionZ, startX, startZ, lastProgress;
    private static float yaw;
    private static int stalledTicks;
    private static LocalPlayer owner;
    private static ClientLevel level;

    private SlideClient() {}

    public static void register(KeyMapping.Category category) {
        slideKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobility_device.slide", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_LCONTROL, category));
        ClientPlayNetworking.registerGlobalReceiver(SlideStartedPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.player == null) return;
            owner = client.player;
            SlideAbility.setClientPostureReleased(owner, false);
            postureReleaseTicks = 0;
            level = client.level;
            directionX = payload.x();
            directionZ = payload.z();
            startX = payload.startX();
            startZ = payload.startZ();
            yaw = payload.yaw();
            lastProgress = progress(owner);
            stalledTicks = 0;
            active = true;
            trapped = false;
            owner.setPose(Pose.SWIMMING);
            double speed = SlideMotion.nextSpeed(lastProgress);
            owner.setDeltaMovement(directionX * speed, owner.getDeltaMovement().y, directionZ * speed);
        });
        ClientPlayNetworking.registerGlobalReceiver(SlideStoppedPayload.TYPE, (payload, context) -> {
            stop(context.client(), payload.trapped(), false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(SlideClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick(Minecraft client) {
        boolean down = slideKey.isDown();
        boolean jumpDown = client.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;
        if (down && !wasDown && client.player != null && client.gui.screen() == null
                && !active && !MobilityDeviceClient.isDashActive()
                && WaistJetItems.isEquipped(client.player)
                && MobilitySettings.enabled(client.player, MobilitySettings.SLIDE)
                && client.player.onGround()
                && SlideAbility.hasSolidSupport(client.player)
                && horizontalSpeed(client.player) >= SlideMotion.MIN_START_SPEED) {
            byte forward = (byte) ((client.options.keyUp.isDown() ? 1 : 0)
                    - (client.options.keyDown.isDown() ? 1 : 0));
            byte strafe = (byte) ((client.options.keyRight.isDown() ? 1 : 0)
                    - (client.options.keyLeft.isDown() ? 1 : 0));
            ClientPlayNetworking.send(new ActivateSlidePayload(forward, strafe, client.player.getYRot()));
        }
        wasDown = down;

        if (owner != null && (client.player != owner || client.level != level)) reset();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()) {
            reset();
            return;
        }
        if (postureReleaseTicks > 0 && --postureReleaseTicks == 0 && owner != null) {
            SlideAbility.setClientPostureReleased(owner, false);
        }
        if (active && (!WaistJetItems.isEquipped(client.player)
                || !MobilitySettings.enabled(client.player, MobilitySettings.SLIDE)
                || MobilityDeviceClient.isDashActive()
                || !client.player.onGround() || !SlideAbility.hasSolidSupport(client.player))) {
            stop(client, !SlideAbility.canStand(client.player), false);
        }
        if (active) {
            double progress = progress(client.player);
            if (jumpPressed && client.gui.screen() == null && client.player.onGround()
                    && progress >= SlideMotion.JUMP_START_DISTANCE
                    && progress < SlideMotion.DISTANCE - 0.001
                    && SlideAbility.canStand(client.player)) {
                ClientPlayNetworking.send(new SlideJumpPayload());
                double exitSpeed = Math.max(SlideMotion.EXIT_SPEED, horizontalSpeed(client.player));
                stop(client, false, false);
                client.player.jumpFromGround();
                client.player.setDeltaMovement(directionX * exitSpeed,
                        client.player.getDeltaMovement().y, directionZ * exitSpeed);
                return;
            }
            if (progress >= SlideMotion.DISTANCE - 0.001) {
                boolean canJump = jumpPressed && client.gui.screen() == null
                        && client.player.onGround() && SlideAbility.canStand(client.player);
                if (canJump) ClientPlayNetworking.send(new SlideJumpPayload());
                stop(client, !SlideAbility.canStand(client.player), true);
                if (canJump) client.player.jumpFromGround();
            } else {
                stalledTicks = progress <= lastProgress + 0.015 ? stalledTicks + 1 : 0;
                lastProgress = progress;
                if (stalledTicks >= 3) {
                    stop(client, !SlideAbility.canStand(client.player), false);
                } else {
                    client.player.setPose(Pose.SWIMMING);
                    double speed = SlideMotion.nextSpeed(progress);
                    client.player.setDeltaMovement(directionX * speed, client.player.getDeltaMovement().y,
                            directionZ * speed);
                }
            }
        } else if (trapped) {
            if (SlideAbility.canStand(client.player)) {
                trapped = false;
            } else {
                client.player.setPose(Pose.SWIMMING);
            }
        }
    }

    private static double horizontalSpeed(Player player) {
        return Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
    }

    private static double progress(Player player) {
        return SlideMotion.progress(player.getX(), player.getZ(), startX, startZ,
                directionX, directionZ);
    }

    private static void stop(Minecraft client, boolean underCeiling, boolean completedDistance) {
        if (active && completedDistance && owner != null) {
            owner.setDeltaMovement(directionX * SlideMotion.EXIT_SPEED, owner.getDeltaMovement().y,
                    directionZ * SlideMotion.EXIT_SPEED);
        }
        active = false;
        trapped = underCeiling;
        stalledTicks = 0;
        if (owner != null) {
            SlideAbility.setClientPostureReleased(owner, !underCeiling);
            postureReleaseTicks = underCeiling ? 0 : 20;
            if (!underCeiling && client.player == owner) owner.setPose(Pose.STANDING);
        }
    }

    public static void interruptForDash(Minecraft client) {
        if (active) stop(client, client.player != null && !SlideAbility.canStand(client.player), false);
    }

    public static boolean isSlideMotionActive() {
        return active;
    }

    public static boolean isSlidePosture(Player player) {
        if (player == owner && (active || trapped)) return true;
        if (!player.getAttachedOrElse(SlideAbility.POSTURE, false)) return false;
        // A locally predicted stop can precede the server's stop packet. During
        // that gap, render the slide model if the server still holds the player
        // in the low pose, even though pose enforcement was locally released.
        return player != owner || !SlideAbility.isClientPostureReleased(player)
                || player.getPose() == Pose.SWIMMING;
    }

    public static float slideYaw(Player player) {
        return player == owner && (active || trapped) ? yaw
                : player.getAttachedOrElse(SlideAbility.YAW, 0.0F);
    }

    private static void reset() {
        if (owner != null) SlideAbility.setClientPostureReleased(owner, false);
        active = false;
        trapped = false;
        postureReleaseTicks = 0;
        wasJumpDown = false;
        owner = null;
        level = null;
        stalledTicks = 0;
    }
}
