package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.SlideAbility;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerSlidePoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void mobility$holdLowSlidePose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.isAlive() && !player.isSpectator()
                && player.getAttachedOrElse(SlideAbility.POSTURE, false)
                && !SlideAbility.isClientPostureReleased(player)) {
            player.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
