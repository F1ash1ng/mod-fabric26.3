package com.example.mobilitydevice.client;

import com.example.mobilitydevice.ability.WallRunMotion;
import com.example.mobilitydevice.ability.WallRunVisuals;
import net.minecraft.world.entity.player.Player;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.WallRunControlPayload;
import com.example.mobilitydevice.network.WallRunStartedPayload;
import com.example.mobilitydevice.network.WallRunStoppedPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class WallRunClient {
    private static LocalPlayer owner;
    private static ClientLevel level;
    private static byte wall, side, lastSentForward;
    private static double height, speed;
    private static Vec3 recentMotion = Vec3.ZERO;
    private static boolean previousNoGravity, active, wasJumpDown;
    private static int recentMotionTicks, recentSpeedTicks, pendingTicks, missingWallTicks;

    private WallRunClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WallRunStartedPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.player == null || !WaistJetItems.isEquipped(client.player)
                    || !MobilitySettings.enabled(client.player, MobilitySettings.WALL_RUN)) return;
            stop();
            MobilityDeviceClient.interruptForWallRun();
            owner = client.player;
            level = client.level;
            wall = payload.wall();
            side = payload.side();
            height = payload.height();
            speed = payload.speed();
            previousNoGravity = payload.previousNoGravity();
            active = true;
            pendingTicks = 0;
            missingWallTicks = 0;
            lastSentForward = 2;
            owner.setNoGravity(true);
            owner.setOnGround(false);
            moveVelocity(inputForward(client));
        });
        ClientPlayNetworking.registerGlobalReceiver(WallRunStoppedPayload.TYPE,
                (payload, context) -> stop());
        ClientTickEvents.END_CLIENT_TICK.register(WallRunClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick(Minecraft client) {
        boolean jumpDown = client.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;
        if (owner != null && (owner != client.player || level != client.level)) reset();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !WaistJetItems.isEquipped(client.player)) {
            reset();
            return;
        }
        if (!MobilitySettings.enabled(client.player, MobilitySettings.WALL_RUN)) {
            stop();
            return;
        }
        if (pendingTicks > 0) pendingTicks--;
        Vec3 currentMotion = client.player.getDeltaMovement();
        double currentSpeed = Math.hypot(currentMotion.x, currentMotion.z);
        recentSpeedTicks = currentSpeed >= WallRunMotion.MIN_APPROACH_SPEED
                ? 3 : Math.max(0, recentSpeedTicks - 1);
        if (Math.hypot(currentMotion.x, currentMotion.z) > 0.05) {
            recentMotion = currentMotion;
            recentMotionTicks = 2;
        } else {
            recentMotionTicks = Math.max(0, recentMotionTicks - 1);
        }

        if (active) {
            byte currentSide = WallRunMotion.side(client.player, wall);
            if (currentSide == 0 || currentSide != side) {
                stop();
                return;
            }
            if (jumpPressed && client.gui.screen() == null) {
                byte jumpWall = wall;
                ClientPlayNetworking.send(new WallRunControlPayload(
                        WallRunControlPayload.JUMP, jumpWall, (byte) 0, (byte) 0));
                stop();
                client.player.setDeltaMovement(WallRunMotion.wallJump(client.player, jumpWall));
                client.player.setOnGround(false);
                return;
            }
            missingWallTicks = WallRunMotion.hasRunnableWall(client.player, wall)
                    ? 0 : missingWallTicks + 1;
            if (MobilityDeviceClient.isDashActive() || client.player.onGround()
                    || missingWallTicks >= 3) {
                stop();
                return;
            }
            byte forward = inputForward(client);
            if (forward != lastSentForward) {
                ClientPlayNetworking.send(new WallRunControlPayload(
                        WallRunControlPayload.MOVE, wall, forward, (byte) 0));
                lastSentForward = forward;
            }
            client.player.setNoGravity(true);
            client.player.setOnGround(false);
            client.player.move(MoverType.SELF, new Vec3(0, height - client.player.getY(), 0));
            moveVelocity(forward);
            return;
        }

        if (!jumpPressed || pendingTicks > 0 || client.gui.screen() != null
                || client.player.onGround()
                || SlideClient.isSlideMotionActive()) return;
        byte forward = inputForward(client);
        if (forward < 0) return;
        byte strafe = (byte) ((client.options.keyRight.isDown() ? 1 : 0)
                - (client.options.keyLeft.isDown() ? 1 : 0));
        int bestWall = -1;
        double bestToward = 0.2;
        for (int candidate = 0; candidate < 4; candidate++) {
            byte candidateSide = WallRunMotion.side(client.player, candidate);
            if (candidateSide == 0
                    || !WallRunMotion.hasRunnableWall(client.player, candidate)) continue;
            if (recentSpeedTicks > 0) {
                double toward = WallRunMotion.towardFromKeys(client.player.getYRot(),
                        forward, strafe, candidate);
                if (toward > bestToward) {
                    bestToward = toward;
                    bestWall = candidate;
                }
            }
        }
        if (bestWall < 0) {
            double bestSpeed = WallRunMotion.VANILLA_WALK_BLOCKS_PER_TICK;
            for (int candidate = 0; candidate < 4; candidate++) {
                byte candidateSide = WallRunMotion.side(client.player, candidate);
                if (candidateSide == 0
                        || !WallRunMotion.hasRunnableWall(client.player, candidate)) continue;
                double forwardSpeed = WallRunMotion.parallelForwardSpeed(currentMotion,
                        candidate, candidateSide);
                if (recentMotionTicks > 0) forwardSpeed = Math.max(forwardSpeed,
                        WallRunMotion.parallelForwardSpeed(recentMotion, candidate, candidateSide));
                if (forwardSpeed > bestSpeed) {
                    bestSpeed = forwardSpeed;
                    bestWall = candidate;
                }
            }
        }
        if (bestWall >= 0) {
            ClientPlayNetworking.send(new WallRunControlPayload(WallRunControlPayload.START,
                    (byte) bestWall, forward, strafe));
            pendingTicks = 10;
        }
    }

    private static byte inputForward(Minecraft client) {
        return client.gui.screen() == null ? (byte) ((client.options.keyUp.isDown() ? 1 : 0)
                - (client.options.keyDown.isDown() ? 1 : 0)) : 0;
    }

    private static void moveVelocity(byte forward) {
        if (owner == null) return;
        double along = speed * forward;
        owner.setDeltaMovement(WallRunMotion.tangentX(wall, side) * along
                        + WallRunMotion.normalX(wall) * 0.035,
                0,
                WallRunMotion.tangentZ(wall, side) * along
                        + WallRunMotion.normalZ(wall) * 0.035);
    }

    private static void stop() {
        if (active && owner != null) owner.setNoGravity(previousNoGravity);
        active = false;
        owner = null;
        level = null;
        missingWallTicks = 0;
    }

    public static void interruptForDash() {
        stop();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isWallRunPosture(Player player) {
        if (!player.isAlive() || player.isSpectator() || !WaistJetItems.isEquipped(player)) return false;
        if (player == Minecraft.getInstance().player && !active) return false;
        return player.getAttachedOrElse(WallRunVisuals.STATE, WallRunVisuals.OFF).side() != 0;
    }

    // Positive local Z roll leans the camera left; a right-hand wall uses +20 degrees.
    public static float cameraRollDegrees() {
        return active ? side * 20.0F : 0.0F;
    }

    private static void reset() {
        stop();
        wasJumpDown = false;
        pendingTicks = 0;
        recentMotionTicks = 0;
        recentSpeedTicks = 0;
        recentMotion = Vec3.ZERO;
    }
}
