package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.MobilitySettingsShortcut;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeSettingsShortcutMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mobility$openSettingsFromCreativeSearch(KeyEvent event,
                                                          CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (MobilitySettingsShortcut.open(screen,
                ((HoveredSlotAccessor) this).mobility$getHoveredSlot(), event))
            cir.setReturnValue(true);
    }
}
