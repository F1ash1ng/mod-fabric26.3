package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.ActivateSlidePayload;
import com.example.mobilitydevice.network.SlideStartedPayload;
import com.example.mobilitydevice.network.SlideStoppedPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SlideAbility {
    public static final AttachmentType<Boolean> POSTURE = AttachmentRegistry.create(
            MobilityDeviceMod.id("slide_posture"), builder -> builder
                    .initializer(() -> false)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
    public static final AttachmentType<Float> YAW = AttachmentRegistry.create(
            MobilityDeviceMod.id("slide_yaw"), builder -> builder
                    .initializer(() -> 0.0F)
                    .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

    private static final Map<UUID, RuntimeSlide> ACTIVE = new HashMap<>();
    private static final Map<UUID, RecentFinish> RECENT_FINISH = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();
    private static final Set<UUID> CLIENT_POSTURE_RELEASE = new HashSet<>();

    private SlideAbility() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SlideAbility::tick);
    }

    public static void activate(ServerPlayer player, ActivateSlidePayload request) {
        if (!player.isAlive() || player.isSpectator() || !WaistJetItems.isEquipped(player)
                || !MobilitySettings.enabled(player, MobilitySettings.SLIDE)
                || DashAbility.isActive(player) || ACTIVE.containsKey(player.getUUID())
                || !player.onGround() || !hasSolidSupport(player)
                || !Float.isFinite(request.yaw())) return;
        byte forward = request.forward();
        byte strafe = request.strafe();
        if (forward < -1 || forward > 1 || strafe < -1 || strafe > 1) return;
        if (!hasStartingSpeed(player)) return;
        if (forward == 0 && strafe == 0) forward = 1;

        double yaw = Math.toRadians(request.yaw());
        double x = -Math.sin(yaw) * forward - Math.cos(yaw) * strafe;
        double z = Math.cos(yaw) * forward - Math.sin(yaw) * strafe;
        double length = Math.hypot(x, z);
        if (length < 0.001) return;
        x /= length;
        z /= length;
        float slideYaw = (float) Math.toDegrees(Math.atan2(-x, z));

        player.setAttached(POSTURE, true);
        RECENT_FINISH.remove(player.getUUID());
        player.setAttached(YAW, slideYaw);
        player.setPose(Pose.SWIMMING);
        player.setDeltaMovement(x * SlideMotion.nextSpeed(0), player.getDeltaMovement().y,
                z * SlideMotion.nextSpeed(0));
        player.syncVelocity = true;
        ACTIVE.put(player.getUUID(), new RuntimeSlide(player, x, z, player.getX(), player.getZ(),
                player.level().dimension()));
        ServerPlayNetworking.send(player, new SlideStartedPayload((float) x, (float) z,
                player.getX(), player.getZ(), slideYaw));
    }

    public static boolean hasSolidSupport(Player player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.06,
                player.getZ());
        return player.level().getFluidState(below).isEmpty()
                && !player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty();
    }

    private static boolean hasStartingSpeed(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        double speed = Math.hypot(velocity.x, velocity.z);
        Vec3 last = LAST_POSITION.get(player.getUUID());
        if (last != null) speed = Math.max(speed,
                Math.hypot(player.getX() - last.x, player.getZ() - last.z));
        return speed >= SlideMotion.MIN_START_SPEED;
    }

    public static boolean canStand(Player player) {
        AABB standing = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7);
        return player.level().noCollision(player, standing);
    }

    public static void setClientPostureReleased(Player player, boolean released) {
        if (released) CLIENT_POSTURE_RELEASE.add(player.getUUID());
        else CLIENT_POSTURE_RELEASE.remove(player.getUUID());
    }

    public static boolean isClientPostureReleased(Player player) {
        return player.level().isClientSide() && CLIENT_POSTURE_RELEASE.contains(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            ResourceKey<Level> previousDimension = LAST_DIMENSION.put(id, player.level().dimension());
            if (previousDimension != null && !previousDimension.equals(player.level().dimension())) {
                LAST_POSITION.remove(id);
                RECENT_FINISH.remove(id);
            }
            RuntimeSlide slide = ACTIVE.get(id);
            if (slide != null && (slide.owner != player || !slide.dimension.equals(player.level().dimension()))) {
                ACTIVE.remove(id);
                player.setAttached(POSTURE, false);
                if (canStand(player)) player.setPose(Pose.STANDING);
                slide = null;
            }
            if (slide != null) {
                if (!player.isAlive() || player.isSpectator() || !WaistJetItems.isEquipped(player)
                        || !MobilitySettings.enabled(player, MobilitySettings.SLIDE)
                        || DashAbility.isActive(player) || !player.onGround() || !hasSolidSupport(player)) {
                    finish(player, slide, true);
                } else {
                    double progress = SlideMotion.progress(player.getX(), player.getZ(),
                            slide.startX, slide.startZ, slide.x, slide.z);
                    if (progress >= SlideMotion.DISTANCE - 0.001) {
                        finish(player, slide, false);
                    } else {
                        slide.stalledTicks = progress <= slide.lastProgress + 0.015
                                ? slide.stalledTicks + 1 : 0;
                        slide.lastProgress = progress;
                        if (slide.stalledTicks >= 3) {
                            finish(player, slide, true);
                        } else {
                            player.setPose(Pose.SWIMMING);
                            double speed = SlideMotion.nextSpeed(progress);
                            player.setDeltaMovement(slide.x * speed, player.getDeltaMovement().y,
                                    slide.z * speed);
                            player.syncVelocity = true;
                        }
                    }
                }
            }
            if (ACTIVE.get(id) == null && player.getAttachedOrElse(POSTURE, false)) {
                if (canStand(player)) {
                    player.setAttached(POSTURE, false);
                    player.setPose(Pose.STANDING);
                } else if (player.isAlive()) {
                    player.setPose(Pose.SWIMMING);
                }
            }
            LAST_POSITION.put(id, player.position());
        }
        LAST_POSITION.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        LAST_DIMENSION.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        ACTIVE.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null);
        RECENT_FINISH.entrySet().removeIf(entry ->
                server.getPlayerList().getPlayer(entry.getKey()) == null
                        || entry.getValue().expiresAt < entry.getValue().owner.level().getGameTime());
    }

    private static void finish(ServerPlayer player, RuntimeSlide slide, boolean interrupted) {
        ACTIVE.remove(player.getUUID());
        boolean trapped = !canStand(player);
        player.setAttached(POSTURE, trapped);
        if (!trapped) player.setPose(Pose.STANDING);
        if (!interrupted) {
            RECENT_FINISH.put(player.getUUID(), new RecentFinish(player,
                    player.level().getGameTime() + 4));
            player.setDeltaMovement(slide.x * SlideMotion.EXIT_SPEED, player.getDeltaMovement().y,
                    slide.z * SlideMotion.EXIT_SPEED);
            player.syncVelocity = true;
        }
        ServerPlayNetworking.send(player, new SlideStoppedPayload(trapped));
    }

    public static void interruptForDash(ServerPlayer player) {
        RuntimeSlide slide = ACTIVE.get(player.getUUID());
        if (slide != null && slide.owner == player) finish(player, slide, true);
    }

    public static boolean isActive(ServerPlayer player) {
        RuntimeSlide slide = ACTIVE.get(player.getUUID());
        return slide != null && slide.owner == player;
    }

    public static void interruptForJump(ServerPlayer player) {
        RuntimeSlide slide = ACTIVE.get(player.getUUID());
        if (!player.isAlive() || !player.onGround() || !WaistJetItems.isEquipped(player)
                || DashAbility.isActive(player) || !canStand(player)) return;
        if (slide == null) {
            RecentFinish recent = RECENT_FINISH.remove(player.getUUID());
            if (recent == null || recent.owner != player
                    || recent.expiresAt < player.level().getGameTime()) return;
            player.jumpFromGround();
            player.syncVelocity = true;
            return;
        }
        if (slide.owner != player) return;
        double progress = SlideMotion.progress(player.getX(), player.getZ(),
                slide.startX, slide.startZ, slide.x, slide.z);
        // Allow one movement tick of client/server position difference.
        if (progress + SlideMotion.nextSpeed(progress) < SlideMotion.JUMP_START_DISTANCE) return;
        double exitSpeed = Math.max(SlideMotion.EXIT_SPEED,
                Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z));
        finish(player, slide, true);
        player.jumpFromGround();
        player.setDeltaMovement(slide.x * exitSpeed, player.getDeltaMovement().y,
                slide.z * exitSpeed);
        player.syncVelocity = true;
    }

    public static void cancel(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        RECENT_FINISH.remove(player.getUUID());
        LAST_POSITION.remove(player.getUUID());
        LAST_DIMENSION.remove(player.getUUID());
        player.setAttached(POSTURE, false);
    }

    private static final class RuntimeSlide {
        final ServerPlayer owner;
        final double x, z, startX, startZ;
        final ResourceKey<Level> dimension;
        double lastProgress;
        int stalledTicks;

        RuntimeSlide(ServerPlayer owner, double x, double z, double startX, double startZ,
                     ResourceKey<Level> dimension) {
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.startX = startX;
            this.startZ = startZ;
            this.dimension = dimension;
        }
    }

    private record RecentFinish(ServerPlayer owner, long expiresAt) {}
}
