package com.example.mobilitydevice.network;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record CreativeEquipPayload(ItemStack stack) implements CustomPacketPayload {
    public static final Type<CreativeEquipPayload> TYPE = new Type<>(MobilityDeviceMod.id("creative_equip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeEquipPayload> CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, CreativeEquipPayload::stack, CreativeEquipPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
