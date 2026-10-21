package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.mixin.ExperienceOrbRepairInvoker;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;

public final class KillStreak {
    public static final int WINDOW_TICKS = 140;

    public static final AttachmentType<Integer> COUNT = AttachmentRegistry.create(
            MobilityDeviceMod.id("kill_streak_count"), builder -> builder
                    .initializer(() -> 0)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    public static final AttachmentType<Long> EXPIRES_AT = AttachmentRegistry.create(
            MobilityDeviceMod.id("kill_streak_expires_at"), builder -> builder
                    .initializer(() -> 0L)
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

    private KillStreak() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (count(player) > 0 && (!player.isAlive()
                        || !WaistJetItems.isEquipped(player)
                        || player.level().getGameTime() >= expiresAt(player))) reset(player);
            }
        });
    }

    public static int count(Player player) {
        return player.getAttachedOrElse(COUNT, 0);
    }

    public static long expiresAt(Player player) {
        return player.getAttachedOrElse(EXPIRES_AT, 0L);
    }

    public static void award(ServerPlayer player, int vanillaExperience) {
        if (!WaistJetItems.isEquipped(player)) return;
        long now = player.level().getGameTime();
        int previous = count(player);
        int consecutive = previous > 0 && now < expiresAt(player)
                ? (previous == Integer.MAX_VALUE ? previous : previous + 1) : 1;
        player.setAttached(COUNT, consecutive);
        player.setAttached(EXPIRES_AT, now + WINDOW_TICKS);
        if (vanillaExperience <= 0) return;

        // XP is integral in vanilla; round the exact 1 + n * 10% reward once per kill.
        long reward = Math.round(vanillaExperience * (1.0 + consecutive * 0.1));
        int experience = (int) Math.min(Integer.MAX_VALUE, reward);

        if (MobilitySettings.mendingEnabled(player)) {
            // Reuse the experience orb's own Mending allocation without placing
            // an orb in the world or imposing the ordinary pickup delay.
            ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(),
                    player.getZ(), experience);
            experience = ((ExperienceOrbRepairInvoker) orb).mobility$repairPlayerItems(player, experience);
        }
        if (experience > 0) player.giveExperiencePoints(experience);
    }

    public static void reset(ServerPlayer player) {
        if (count(player) == 0 && expiresAt(player) == 0L) return;
        player.setAttached(COUNT, 0);
        player.setAttached(EXPIRES_AT, 0L);
    }
}
