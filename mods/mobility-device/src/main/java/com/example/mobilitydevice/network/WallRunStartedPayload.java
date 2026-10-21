package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WallRunStartedPayload(byte wall, byte side, double height, float speed,
                                    boolean previousNoGravity) implements CustomPacketPayload {
    public static final Type<WallRunStartedPayload> TYPE = new Type<>(MobilityDeviceMod.id("wall_run_started"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WallRunStartedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WallRunStartedPayload::wall,
            ByteBufCodecs.BYTE, WallRunStartedPayload::side,
            ByteBufCodecs.DOUBLE, WallRunStartedPayload::height,
            ByteBufCodecs.FLOAT, WallRunStartedPayload::speed,
            ByteBufCodecs.BOOL, WallRunStartedPayload::previousNoGravity,
            WallRunStartedPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
