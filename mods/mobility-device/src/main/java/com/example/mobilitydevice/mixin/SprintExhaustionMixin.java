package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerPlayer.class)
public abstract class SprintExhaustionMixin {
    @ModifyConstant(method = "checkMovementStatistics", constant = @Constant(floatValue = 0.1F))
    private float mobility$reduceRunningExhaustion(float original) {
        return WaistJetItems.isEquipped((ServerPlayer) (Object) this) ? original * 0.5F : original;
    }

    @ModifyConstant(method = "jumpFromGround", constant = @Constant(floatValue = 0.2F))
    private float mobility$reduceSprintJumpExhaustion(float original) {
        return WaistJetItems.isEquipped((ServerPlayer) (Object) this) ? original * 0.5F : original;
    }
}
