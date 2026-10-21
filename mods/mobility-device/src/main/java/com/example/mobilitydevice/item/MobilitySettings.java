package com.example.mobilitydevice.item;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

public final class MobilitySettings {
    public static final int DASH = 1;
    public static final int SLIDE = 2;
    public static final int WALL_RUN = 4;
    public static final int TACZ = 8;
    // Inverted so saved settings from before this switch keep Mending enabled.
    public static final int MENDING_OFF = 16;
    public static final int ALL = DASH | SLIDE | WALL_RUN | TACZ | MENDING_OFF;
    public static final int DEFAULT = DASH | SLIDE | WALL_RUN;

    public static final AttachmentType<Integer> FLAGS = AttachmentRegistry.create(
            MobilityDeviceMod.id("settings"), builder -> builder
                    .initializer(() -> DEFAULT)
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
                    .copyOnDeath());

    private MobilitySettings() {}

    public static void register() {}

    public static int flags(Player player) {
        return player.getAttachedOrElse(FLAGS, DEFAULT) & ALL;
    }

    public static boolean enabled(Player player, int feature) {
        return (flags(player) & feature) != 0;
    }

    public static boolean mendingEnabled(Player player) {
        return (flags(player) & MENDING_OFF) == 0;
    }

    public static void set(Player player, int flags) {
        player.setAttached(FLAGS, flags & ALL);
    }
}
