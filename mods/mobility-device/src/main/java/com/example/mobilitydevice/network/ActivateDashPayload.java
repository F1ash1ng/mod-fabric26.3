package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ActivateDashPayload(byte forward, byte strafe, float yaw) implements CustomPacketPayload {
    public static final Type<ActivateDashPayload> TYPE = new Type<>(MobilityDeviceMod.id("activate_dash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateDashPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, ActivateDashPayload::forward,
            ByteBufCodecs.BYTE, ActivateDashPayload::strafe,
            ByteBufCodecs.FLOAT, ActivateDashPayload::yaw,
            ActivateDashPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
