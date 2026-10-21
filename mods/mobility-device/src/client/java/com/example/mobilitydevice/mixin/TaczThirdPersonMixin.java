package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.SlideClient;
import com.example.mobilitydevice.client.WallRunClient;
import com.example.mobilitydevice.client.TaczSlideCompatibility;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels TaCZ's bone edits and PAL playback only for players using our slide posture. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.animation.third.InnerThirdPersonManager", remap = false)
public abstract class TaczThirdPersonMixin {
    @Inject(method = "setRotationAnglesHead(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;F)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void mobility$skipGunPoseDuringSlide(LivingEntity entity, ModelPart rightArm,
                                                          ModelPart leftArm, ModelPart body, ModelPart head,
                                                          float walkSpeed, CallbackInfo ci) {
        if (entity instanceof Player player && (SlideClient.isSlidePosture(player)
                || WallRunClient.isWallRunPosture(player))) {
            TaczSlideCompatibility.stopAnimations(entity);
            ci.cancel();
        }
    }
}
