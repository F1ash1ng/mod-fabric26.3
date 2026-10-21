package com.example.mobilitydevice.item;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.util.Prediction;

public final class WaistJetItems {
    private static final ResourceKey<Item> WAIST_JET_KEY = ResourceKey.create(
            BuiltInRegistries.ITEM.key(), MobilityDeviceMod.id("waist_jet"));
    public static final Item WAIST_JET = Registry.register(BuiltInRegistries.ITEM, WAIST_JET_KEY,
            new Item(new Item.Properties().setId(WAIST_JET_KEY).stacksTo(1)));

    public static final AttachmentType<ItemStack> EQUIPPED = AttachmentRegistry.create(
            MobilityDeviceMod.id("equipped_waist_jet"), builder -> builder
                    .initializer(() -> ItemStack.EMPTY)
                    .persistent(ItemStack.OPTIONAL_CODEC)
                    .syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all())
                    .copyOnDeath());

    private WaistJetItems() {}

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(WAIST_JET));
    }

    public static boolean isEquipped(LivingEntity entity) {
        return entity.getAttachedOrElse(EQUIPPED, ItemStack.EMPTY).is(WAIST_JET);
    }

    public static ItemStack getEquipped(LivingEntity entity) {
        return entity.getAttachedOrElse(EQUIPPED, ItemStack.EMPTY);
    }

    public static void setEquipped(LivingEntity entity, ItemStack stack) {
        entity.setAttached(EQUIPPED, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public static void onDeath(LivingEntity victim) {
        if (!(victim instanceof ServerPlayer player) || player.level().getServer() == null) return;
        if (player.level().getServer().getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        ItemStack stack = getEquipped(player);
        if (!stack.isEmpty()) {
            setEquipped(player, ItemStack.EMPTY);
            player.drop(stack.copy(), true, Prediction.SERVER_ONLY);
        }
    }
}
