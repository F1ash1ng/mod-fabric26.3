package com.example.mobilitydevice.ability;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A clock anchor changes only on entry, a direction change, and exit, not every tick. */
public record WallRunVisuals(int wall, int side, int movement, float phaseTicks, long updatedAt) {
    public static final WallRunVisuals OFF = new WallRunVisuals(-1, 0, 0, 0, 0);
    public static final AttachmentType<WallRunVisuals> STATE = AttachmentRegistry.create(
            MobilityDeviceMod.id("wall_run_visuals"), builder -> builder.initializer(() -> OFF)
                    .syncWith(StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, WallRunVisuals::wall,
                            ByteBufCodecs.VAR_INT, WallRunVisuals::side,
                            ByteBufCodecs.VAR_INT, WallRunVisuals::movement,
                            ByteBufCodecs.FLOAT, WallRunVisuals::phaseTicks,
                            ByteBufCodecs.VAR_LONG, WallRunVisuals::updatedAt,
                            WallRunVisuals::new), AttachmentSyncPredicate.all()));

    public float seconds(long now, float partialTick) {
        return (phaseTicks + ((float) (now - updatedAt) + partialTick) * movement) / 20.0F;
    }

    public static void register() { /* Force attachment registration during mod initialization. */ }

    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-WallRunMotion.tangentX(wall, side),
                WallRunMotion.tangentZ(wall, side)));
    }
}
