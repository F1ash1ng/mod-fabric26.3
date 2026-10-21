package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DashStartedPayload(float x, float z, double startX, double startZ, double startY,
                                 boolean previousNoGravity) implements CustomPacketPayload {
    public static final Type<DashStartedPayload> TYPE = new Type<>(MobilityDeviceMod.id("dash_started"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DashStartedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DashStartedPayload::x,
            ByteBufCodecs.FLOAT, DashStartedPayload::z,
            ByteBufCodecs.DOUBLE, DashStartedPayload::startX,
            ByteBufCodecs.DOUBLE, DashStartedPayload::startZ,
            ByteBufCodecs.DOUBLE, DashStartedPayload::startY,
            ByteBufCodecs.BOOL, DashStartedPayload::previousNoGravity,
            DashStartedPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
