package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.item.WaistJetContainer;
import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    private static final int WAIST_SLOT = 46;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobility$addWaistSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        Slot slot = new Slot(new WaistJetContainer(owner), 0, 77, 8) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(WaistJetItems.WAIST_JET); }
            @Override public int getMaxStackSize() { return 1; }
        };
        ((MenuInvoker) this).mobility$addSlot(slot);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mobility$quickMoveWaist(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        if (index < 0 || index >= menu.slots.size()) return;
        Slot source = menu.getSlot(index);
        if (!source.hasItem()) return;
        ItemStack moving = source.getItem();
        boolean fromWaist = index == WAIST_SLOT;
        if (!fromWaist && !(index >= InventoryMenu.INV_SLOT_START && index < InventoryMenu.USE_ROW_SLOT_END
                && moving.is(WaistJetItems.WAIST_JET))) return;
        ItemStack original = moving.copy();
        boolean moved = fromWaist
                ? ((MenuInvoker) this).mobility$moveItemStackTo(moving, InventoryMenu.INV_SLOT_START,
                        InventoryMenu.USE_ROW_SLOT_END, false)
                : ((MenuInvoker) this).mobility$moveItemStackTo(moving, WAIST_SLOT, WAIST_SLOT + 1, false);
        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (moving.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
        else source.setChanged();
        cir.setReturnValue(original);
    }
}
