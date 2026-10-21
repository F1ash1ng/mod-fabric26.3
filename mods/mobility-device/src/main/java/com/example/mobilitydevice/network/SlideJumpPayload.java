package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SlideJumpPayload() implements CustomPacketPayload {
    public static final Type<SlideJumpPayload> TYPE = new Type<>(MobilityDeviceMod.id("slide_jump"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideJumpPayload> CODEC =
            StreamCodec.unit(new SlideJumpPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
