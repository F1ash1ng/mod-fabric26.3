package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SlideStoppedPayload(boolean trapped) implements CustomPacketPayload {
    public static final Type<SlideStoppedPayload> TYPE = new Type<>(MobilityDeviceMod.id("slide_stopped"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideStoppedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SlideStoppedPayload::trapped,
            SlideStoppedPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
