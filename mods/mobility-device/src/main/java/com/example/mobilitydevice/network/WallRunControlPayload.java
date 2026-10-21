package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WallRunControlPayload(byte action, byte wall, byte forward, byte strafe)
        implements CustomPacketPayload {
    public static final byte START = 0, JUMP = 1, MOVE = 2;
    public static final Type<WallRunControlPayload> TYPE = new Type<>(MobilityDeviceMod.id("wall_run_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WallRunControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WallRunControlPayload::action,
            ByteBufCodecs.BYTE, WallRunControlPayload::wall,
            ByteBufCodecs.BYTE, WallRunControlPayload::forward,
            ByteBufCodecs.BYTE, WallRunControlPayload::strafe,
            WallRunControlPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
