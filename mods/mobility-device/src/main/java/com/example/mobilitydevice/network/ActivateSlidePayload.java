package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ActivateSlidePayload(byte forward, byte strafe, float yaw) implements CustomPacketPayload {
    public static final Type<ActivateSlidePayload> TYPE = new Type<>(MobilityDeviceMod.id("activate_slide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateSlidePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, ActivateSlidePayload::forward,
            ByteBufCodecs.BYTE, ActivateSlidePayload::strafe,
            ByteBufCodecs.FLOAT, ActivateSlidePayload::yaw,
            ActivateSlidePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
