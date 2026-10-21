package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.SprintIntent;
import com.example.mobilitydevice.item.MobilitySettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntitySprint", remap = false)
public abstract class TaczServerSprintMixin {
    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "getProcessedSprintStatus", at = @At("HEAD"), cancellable = true)
    private void mobility$keepSprintDuringHipFire(boolean sprinting,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof ServerPlayer player
                && MobilitySettings.enabled(player, MobilitySettings.TACZ)
                && SprintIntent.shouldForceSprint(player))
            cir.setReturnValue(true);
    }
}
