package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.WallRunControlPayload;
import com.example.mobilitydevice.network.WallRunStartedPayload;
import com.example.mobilitydevice.network.WallRunStoppedPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WallRunAbility {
    private static final Map<UUID, Run> ACTIVE = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();
    private static final Map<UUID, RecentSpeed> RECENT_SPEED = new HashMap<>();
    private static final Map<UUID, RecentMotion> RECENT_MOTION = new HashMap<>();

    private WallRunAbility() {}

    public static void register() {
        WallRunVisuals.register();
        ServerTickEvents.END_SERVER_TICK.register(WallRunAbility::tick);
    }

    public static void control(ServerPlayer player, WallRunControlPayload request) {
        if (!MobilitySettings.enabled(player, MobilitySettings.WALL_RUN)) return;
        if (request.action() == WallRunControlPayload.START) start(player, request);
        else if (request.action() == WallRunControlPayload.JUMP) jump(player);
        else if (request.action() == WallRunControlPayload.MOVE) {
            Run run = ACTIVE.get(player.getUUID());
            if (run != null && run.owner == player && request.forward() >= -1 && request.forward() <= 1) {
                if (run.forward != request.forward()) {
                    WallRunVisuals previous = player.getAttachedOrElse(WallRunVisuals.STATE, WallRunVisuals.OFF);
                    long now = player.level().getGameTime();
                    player.setAttached(WallRunVisuals.STATE, new WallRunVisuals(run.wall, run.side,
                            request.forward(), previous.seconds(now, 0) * 20, now));
                }
                run.forward = request.forward();
            }
        }
    }

    private static void start(ServerPlayer player, WallRunControlPayload request) {
        int wall = request.wall();
        if (!player.isAlive() || player.isSpectator() || !WaistJetItems.isEquipped(player)
                || !MobilitySettings.enabled(player, MobilitySettings.WALL_RUN)
                || player.onGround() || SlideAbility.isActive(player)
                || ACTIVE.containsKey(player.getUUID()) || !WallRunMotion.validWall(wall)
                || !WallRunMotion.hasRunnableWall(player, wall)) return;
        if (request.forward() < 0 || request.forward() > 1
                || request.strafe() < -1 || request.strafe() > 1) return;
        byte side = WallRunMotion.side(player, wall);
        if (side == 0) return;
        Vec3 velocity = player.getDeltaMovement();
        double approachSpeed = Math.hypot(velocity.x, velocity.z);
        RecentSpeed recentSpeed = RECENT_SPEED.get(player.getUUID());
        if (recentSpeed != null && recentSpeed.expiresAt >= player.level().getGameTime()) {
            approachSpeed = Math.max(approachSpeed, recentSpeed.value);
        }
        boolean approaching = approachSpeed >= WallRunMotion.MIN_APPROACH_SPEED
                && WallRunMotion.towardFromKeys(player.getYRot(), request.forward(),
                        request.strafe(), wall) >= 0.2;

        double forwardSpeed = WallRunMotion.parallelForwardSpeed(velocity, wall, side);
        RecentMotion recent = RECENT_MOTION.get(player.getUUID());
        if (recent != null && recent.expiresAt >= player.level().getGameTime()) {
            forwardSpeed = Math.max(forwardSpeed,
                    WallRunMotion.parallelForwardSpeed(recent.velocity, wall, side));
        }
        boolean runningAlongWall = forwardSpeed > WallRunMotion.VANILLA_WALK_BLOCKS_PER_TICK;
        if (!approaching && !runningAlongWall) return;

        // The wall has accepted the jump; relinquish dash gravity and velocity first.
        DashAbility.cancel(player);
        boolean previousNoGravity = player.isNoGravity();
        Run run = new Run(player, (byte) wall, side, player.getY(),
                WallRunMotion.speed(player), previousNoGravity, request.forward(), player.level().dimension());
        ACTIVE.put(player.getUUID(), run);
        player.setAttached(WallRunVisuals.STATE, new WallRunVisuals(run.wall, run.side,
                run.forward, 0, player.level().getGameTime()));
        player.setNoGravity(true);
        player.setOnGround(false);
        moveVelocity(run);
        ServerPlayNetworking.send(player, new WallRunStartedPayload(run.wall, run.side,
                run.height, (float) run.speed, previousNoGravity));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            ResourceKey<Level> previousDimension = LAST_DIMENSION.put(id, player.level().dimension());
            boolean worldChanged = previousDimension != null
                    && !previousDimension.equals(player.level().dimension());
            if (worldChanged) {
                LAST_POSITION.remove(id);
                RECENT_SPEED.remove(id);
                RECENT_MOTION.remove(id);
            }
            Vec3 last = LAST_POSITION.put(id, player.position());
            Vec3 velocity = player.getDeltaMovement();
            Vec3 displacement = last == null ? Vec3.ZERO
                    : new Vec3(player.getX() - last.x, 0, player.getZ() - last.z);
            double speed = Math.max(Math.hypot(velocity.x, velocity.z),
                    Math.hypot(displacement.x, displacement.z));
            if (!worldChanged && speed >= WallRunMotion.MIN_APPROACH_SPEED) {
                RECENT_SPEED.put(id, new RecentSpeed(speed, player.level().getGameTime() + 3));
            }
            // Actual displacement keeps the along-wall component after collision with the wall.
            Vec3 motion = Math.hypot(displacement.x, displacement.z) > 0.05
                    ? displacement : velocity;
            if (!worldChanged && Math.hypot(motion.x, motion.z) > 0.05) {
                RECENT_MOTION.put(id, new RecentMotion(motion, player.level().getGameTime() + 2));
            }

            Run run = ACTIVE.get(id);
            if (run == null) continue;
            if (run.owner != player || !run.dimension.equals(player.level().dimension())) {
                ACTIVE.remove(id);
                run.owner.setNoGravity(run.previousNoGravity);
                run.owner.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
                continue;
            }
            byte currentSide = WallRunMotion.side(player, run.wall);
            if (!player.isAlive() || player.isSpectator()
                    || !WaistJetItems.isEquipped(player)
                    || !MobilitySettings.enabled(player, MobilitySettings.WALL_RUN)
                    || DashAbility.isActive(player)
                    || player.onGround() || !WallRunMotion.hasRunnableWall(player, run.wall)
                    || currentSide == 0 || currentSide != run.side
                    || Math.abs(player.getY() - run.height) > 0.5) {
                stop(run);
                continue;
            }
            player.setNoGravity(true);
            player.setOnGround(false);
            player.move(MoverType.SELF, new Vec3(0, run.height - player.getY(), 0));
            moveVelocity(run);
        }
        LAST_POSITION.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        LAST_DIMENSION.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        RECENT_SPEED.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null
                || entry.getValue().expiresAt < server.overworld().getGameTime());
        RECENT_MOTION.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null
                || entry.getValue().expiresAt < server.overworld().getGameTime());
        ACTIVE.entrySet().removeIf(entry -> {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) return false;
            entry.getValue().owner.setNoGravity(entry.getValue().previousNoGravity);
            entry.getValue().owner.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
            return true;
        });
    }

    private static void moveVelocity(Run run) {
        ServerPlayer player = run.owner;
        double along = run.speed * run.forward;
        player.setDeltaMovement(WallRunMotion.tangentX(run.wall, run.side) * along
                        + WallRunMotion.normalX(run.wall) * 0.035,
                0,
                WallRunMotion.tangentZ(run.wall, run.side) * along
                        + WallRunMotion.normalZ(run.wall) * 0.035);
        player.syncVelocity = true;
    }

    private static void jump(ServerPlayer player) {
        Run run = ACTIVE.get(player.getUUID());
        if (run == null || run.owner != player || !player.isAlive()
                || !WaistJetItems.isEquipped(player) || !WallRunMotion.hasRunnableWall(player, run.wall)
                || WallRunMotion.side(player, run.wall) != run.side) return;
        stop(run);
        player.setDeltaMovement(WallRunMotion.wallJump(player, run.wall));
        player.setOnGround(false);
        player.syncVelocity = true;
    }

    private static void stop(Run run) {
        if (ACTIVE.remove(run.owner.getUUID()) != run) return;
        run.owner.setNoGravity(run.previousNoGravity);
        run.owner.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
        ServerPlayNetworking.send(run.owner, new WallRunStoppedPayload());
    }

    public static void interruptForDash(ServerPlayer player) {
        Run run = ACTIVE.get(player.getUUID());
        if (run != null && run.owner == player) stop(run);
    }

    public static boolean isActive(ServerPlayer player) {
        Run run = ACTIVE.get(player.getUUID());
        return run != null && run.owner == player;
    }

    public static void cancel(ServerPlayer player) {
        Run run = ACTIVE.get(player.getUUID());
        if (run != null && run.owner == player) {
            ACTIVE.remove(player.getUUID());
            player.setNoGravity(run.previousNoGravity);
            player.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
        }
        LAST_POSITION.remove(player.getUUID());
        LAST_DIMENSION.remove(player.getUUID());
        RECENT_SPEED.remove(player.getUUID());
        RECENT_MOTION.remove(player.getUUID());
    }

    private static final class Run {
        final ServerPlayer owner;
        final byte wall;
        final byte side;
        final double height, speed;
        final boolean previousNoGravity;
        final ResourceKey<Level> dimension;
        byte forward;

        Run(ServerPlayer owner, byte wall, byte side, double height, double speed,
            boolean previousNoGravity, byte forward, ResourceKey<Level> dimension) {
            this.owner = owner;
            this.wall = wall;
            this.side = side;
            this.height = height;
            this.speed = speed;
            this.previousNoGravity = previousNoGravity;
            this.dimension = dimension;
            this.forward = forward;
        }
    }

    private record RecentSpeed(double value, long expiresAt) {}

    private record RecentMotion(Vec3 velocity, long expiresAt) {}
}
