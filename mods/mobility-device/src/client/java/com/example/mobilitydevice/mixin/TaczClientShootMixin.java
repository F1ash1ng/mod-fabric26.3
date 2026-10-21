package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.TaczClientShootRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public abstract class TaczClientShootMixin {
    @Shadow @Final private LocalPlayer player;

    @ModifyExpressionValue(method = "shoot", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/entity/IGunOperator;getSynSprintTime()F"))
    private float mobility$allowSprintHipFire(float sprintTime) {
        return TaczClientShootRules.canHipFire(player) ? 0.0F : sprintTime;
    }
}
