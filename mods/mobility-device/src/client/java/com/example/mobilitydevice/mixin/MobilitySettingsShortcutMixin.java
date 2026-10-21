package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.MobilitySettingsShortcut;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MobilitySettingsShortcutMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mobility$openSettings(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)
                && !((Object) this instanceof CreativeModeInventoryScreen)) return;
        if (MobilitySettingsShortcut.open((Screen) (Object) this, hoveredSlot, event))
            cir.setReturnValue(true);
    }
}
