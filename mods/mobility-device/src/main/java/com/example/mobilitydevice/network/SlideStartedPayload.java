package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SlideStartedPayload(float x, float z, double startX, double startZ, float yaw)
        implements CustomPacketPayload {
    public static final Type<SlideStartedPayload> TYPE = new Type<>(MobilityDeviceMod.id("slide_started"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideStartedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SlideStartedPayload::x,
            ByteBufCodecs.FLOAT, SlideStartedPayload::z,
            ByteBufCodecs.DOUBLE, SlideStartedPayload::startX,
            ByteBufCodecs.DOUBLE, SlideStartedPayload::startZ,
            ByteBufCodecs.FLOAT, SlideStartedPayload::yaw,
            SlideStartedPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
