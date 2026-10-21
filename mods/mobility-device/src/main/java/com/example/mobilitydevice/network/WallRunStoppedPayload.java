package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WallRunStoppedPayload() implements CustomPacketPayload {
    public static final Type<WallRunStoppedPayload> TYPE = new Type<>(MobilityDeviceMod.id("wall_run_stopped"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WallRunStoppedPayload> CODEC =
            StreamCodec.unit(new WallRunStoppedPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
