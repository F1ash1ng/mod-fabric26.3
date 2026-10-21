package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.DashAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerLandingMixin {
    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void mobility$boostOnLanding(double distance, float multiplier, DamageSource source,
                                          CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) DashAbility.onLanding(player, distance);
    }
}
