package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.TaczClientShootRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.animation.statemachine.GunAnimationStateContext", remap = false)
public abstract class TaczGunPoseMixin {
    @Inject(method = "isCrouching", at = @At("RETURN"), cancellable = true)
    private void mobility$useHipFirePose(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && TaczClientShootRules.useCrouchedGunPose(player))
            cir.setReturnValue(true);
    }
}
