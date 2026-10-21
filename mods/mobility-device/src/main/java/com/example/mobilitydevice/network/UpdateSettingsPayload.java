package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateSettingsPayload(int flags) implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE = new Type<>(MobilityDeviceMod.id("update_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSettingsPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UpdateSettingsPayload::flags,
                    UpdateSettingsPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
