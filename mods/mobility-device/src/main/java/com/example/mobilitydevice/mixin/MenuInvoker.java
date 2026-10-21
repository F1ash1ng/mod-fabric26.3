package com.example.mobilitydevice.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface MenuInvoker {
    @Invoker("addSlot") Slot mobility$addSlot(Slot slot);
    @Invoker("moveItemStackTo") boolean mobility$moveItemStackTo(ItemStack stack, int start, int end, boolean reverse);
}
