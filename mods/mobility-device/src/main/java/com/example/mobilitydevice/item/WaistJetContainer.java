package com.example.mobilitydevice.item;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WaistJetContainer implements Container {
    private final Player owner;

    public WaistJetContainer(Player owner) {
        this.owner = owner;
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? WaistJetItems.getEquipped(owner) : ItemStack.EMPTY; }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack original = getItem(0);
        if (original.isEmpty()) return ItemStack.EMPTY;
        WaistJetItems.setEquipped(owner, ItemStack.EMPTY);
        return original.copy();
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack original = getItem(0);
        WaistJetItems.setEquipped(owner, ItemStack.EMPTY);
        return original;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (slot == 0) WaistJetItems.setEquipped(owner, stack);
    }
    @Override public void setChanged() { }
    @Override public boolean stillValid(Player player) { return player == owner; }
    @Override public void clearContent() { WaistJetItems.setEquipped(owner, ItemStack.EMPTY); }
}
