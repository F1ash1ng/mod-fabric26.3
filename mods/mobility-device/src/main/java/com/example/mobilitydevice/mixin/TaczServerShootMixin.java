package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.compat.TaczShootRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczServerShootMixin {
    @Shadow @Final private LivingEntity shooter;

    @ModifyExpressionValue(method = "shootInternal", at = @At(value = "FIELD",
            target = "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;sprintTimeS:F"))
    private float mobility$allowSprintHipFire(float sprintTime) {
        return shooter instanceof ServerPlayer player && TaczShootRules.canHipFire(player)
                ? 0.0F : sprintTime;
    }
}
