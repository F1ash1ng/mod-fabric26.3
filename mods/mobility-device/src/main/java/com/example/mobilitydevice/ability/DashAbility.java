package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.network.ActivateDashPayload;
import com.example.mobilitydevice.network.DashStartedPayload;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DashAbility {
    public static final int COOLDOWN_TICKS = 140;
    public static final int BOOST_TICKS = 46;

    public static final AttachmentType<Long> COOLDOWN_END = AttachmentRegistry.create(
            MobilityDeviceMod.id("cooldown_end"), builder -> builder
                    .initializer(() -> 0L)
                    .persistent(Codec.LONG)
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
                    .copyOnDeath());
    public static final AttachmentType<Integer> ACTIVE_DIRECTION = AttachmentRegistry.create(
            MobilityDeviceMod.id("active_direction"), builder -> builder
                    .initializer(() -> -1)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final Map<UUID, RuntimeDash> ACTIVE = new HashMap<>();

    private DashAbility() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DashAbility::tick);
    }

    public static void activate(ServerPlayer player, ActivateDashPayload request) {
        if (player.level().getServer() == null || !player.isAlive() || player.isSpectator()
                || !WaistJetItems.isEquipped(player)) return;
        byte forward = request.forward();
        byte strafe = request.strafe();
        if (forward < -1 || forward > 1 || strafe < -1 || strafe > 1
                || !Float.isFinite(request.yaw())) return;
        UUID id = player.getUUID();
        long now = gameTime(player.level().getServer());
        if (ACTIVE.containsKey(id) || now < player.getAttachedOrElse(COOLDOWN_END, 0L)) return;

        if (forward == 0 && strafe == 0) forward = 1;
        double yaw = Math.toRadians(request.yaw());
        double x = -Math.sin(yaw) * forward - Math.cos(yaw) * strafe;
        double z = Math.cos(yaw) * forward - Math.sin(yaw) * strafe;
        double length = Math.hypot(x, z);
        if (length < 0.001) {
            x = -Math.sin(yaw);
            z = Math.cos(yaw);
            forward = 1;
            strafe = 0;
            length = 1;
        }
        x /= length;
        z /= length;
        int direction = directionIndex(forward, strafe);
        boolean previousNoGravity = player.isNoGravity();
        double startY = player.getY();
        double launchSpeed = DashMotion.nextSpeed(0);
        player.setOnGround(false);
        player.setNoGravity(true);
        player.setSprinting(true);
        player.setDeltaMovement(x * launchSpeed, DashMotion.heightAt(launchSpeed), z * launchSpeed);
        player.syncVelocity = true;
        ACTIVE.put(id, new RuntimeDash(player, x, z, player.getX(), player.getZ(),
                startY, previousNoGravity));
        player.setAttached(COOLDOWN_END, now + COOLDOWN_TICKS);
        player.setAttached(ACTIVE_DIRECTION, direction);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, BOOST_TICKS, 0, false, true, true));
        ServerPlayNetworking.send(player, new DashStartedPayload((float) x, (float) z,
                player.getX(), player.getZ(), startY, previousNoGravity));
    }

    private static int directionIndex(int forward, int strafe) {
        if (forward > 0) return strafe > 0 ? 1 : strafe < 0 ? 7 : 0;
        if (forward < 0) return strafe > 0 ? 3 : strafe < 0 ? 5 : 4;
        return strafe > 0 ? 2 : 6;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            RuntimeDash dash = ACTIVE.get(id);
            if (dash != null && dash.owner != player) {
                stop(id, dash, true);
                dash = null;
            }
            if (!player.isAlive() || !WaistJetItems.isEquipped(player)) {
                if (dash != null) stop(id, dash, true);
                continue;
            }
            player.setSprinting(true);
            if (dash == null) continue;
            double progress = DashMotion.progress(player.getX(), player.getZ(), dash.startX,
                    dash.startZ, dash.x, dash.z);
            if (progress >= DashMotion.TOTAL_DISTANCE - 0.001) {
                stop(id, dash, false);
                continue;
            }
            dash.stalledTicks = progress <= dash.lastProgress + 0.015 ? dash.stalledTicks + 1 : 0;
            dash.lastProgress = progress;
            if (dash.stalledTicks >= 3) {
                stop(id, dash, true);
                continue;
            }
            player.setNoGravity(true);
            player.setOnGround(false);
            double targetY = dash.startY + DashMotion.heightAt(progress);
            player.move(MoverType.SELF, new Vec3(0, targetY - player.getY(), 0));
            double speed = DashMotion.nextSpeed(progress);
            double verticalSpeed = DashMotion.heightAt(progress + speed) - DashMotion.heightAt(progress);
            player.setDeltaMovement(dash.x * speed, verticalSpeed, dash.z * speed);
            player.syncVelocity = true;
        }
        ACTIVE.entrySet().removeIf(entry -> {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) return false;
            restoreGravity(entry.getValue());
            return true;
        });
    }

    private static void stop(UUID id, RuntimeDash dash, boolean interrupted) {
        ACTIVE.remove(id);
        restoreGravity(dash);
        dash.owner.setAttached(ACTIVE_DIRECTION, -1);
        if (interrupted) {
            dash.owner.setDeltaMovement(0, 0, 0);
            dash.owner.syncVelocity = true;
        } else {
            dash.owner.setDeltaMovement(dash.x * DashMotion.exitSpeed(),
                    Math.min(dash.owner.getDeltaMovement().y, 0), dash.z * DashMotion.exitSpeed());
            dash.owner.syncVelocity = true;
        }
    }

    private static void restoreGravity(RuntimeDash dash) {
        dash.owner.setNoGravity(dash.previousNoGravity);
    }

    public static void cancel(ServerPlayer player) {
        RuntimeDash dash = ACTIVE.get(player.getUUID());
        if (dash != null && dash.owner == player) stop(player.getUUID(), dash, true);
    }

    public static void onLanding(ServerPlayer player, double distance) {
        if (distance > 3.5 && WaistJetItems.isEquipped(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, BOOST_TICKS, 0, false, true, true));
        }
    }

    public static void onKill(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer killer) || killer == victim
                || killer.level().getServer() == null || !WaistJetItems.isEquipped(killer)) return;
        long now = gameTime(killer.level().getServer());
        if (now < killer.getAttachedOrElse(COOLDOWN_END, 0L)) killer.setAttached(COOLDOWN_END, 0L);
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static final class RuntimeDash {
        final ServerPlayer owner;
        final double x, z;
        final double startX, startZ, startY;
        final boolean previousNoGravity;
        double lastProgress;
        int stalledTicks;

        RuntimeDash(ServerPlayer owner, double x, double z, double startX, double startZ,
                    double startY, boolean previousNoGravity) {
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.startX = startX;
            this.startZ = startZ;
            this.startY = startY;
            this.previousNoGravity = previousNoGravity;
        }
    }
}
