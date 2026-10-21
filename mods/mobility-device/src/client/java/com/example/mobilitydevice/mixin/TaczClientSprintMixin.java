package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.MobilityDeviceClient;
import com.example.mobilitydevice.item.MobilitySettings;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerSprint", remap = false)
public abstract class TaczClientSprintMixin {
    @Shadow @Final private LocalPlayer player;

    @Inject(method = "getProcessedSprintStatus", at = @At("HEAD"), cancellable = true)
    private void mobility$keepSprintDuringHipFire(boolean sprinting,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (MobilitySettings.enabled(player, MobilitySettings.TACZ)
                && MobilityDeviceClient.shouldForceSprint(player)) cir.setReturnValue(true);
    }
}
