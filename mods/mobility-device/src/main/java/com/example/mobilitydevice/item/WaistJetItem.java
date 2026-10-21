package com.example.mobilitydevice.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import java.util.function.Consumer;

public final class WaistJetItem extends Item {
    public WaistJetItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.mobility_device.settings_hint")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || WaistJetItems.isEquipped(player))
            return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(this)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            WaistJetItems.setEquipped(player, held.copyWithCount(1));
            held.shrink(1);
            player.inventoryMenu.broadcastChanges();
        }
        return InteractionResult.SUCCESS;
    }
}
