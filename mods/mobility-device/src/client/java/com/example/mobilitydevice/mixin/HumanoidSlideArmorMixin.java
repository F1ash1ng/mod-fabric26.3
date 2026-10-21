package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.SlidePlayerPose;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.WallRunRenderState;
import com.example.mobilitydevice.client.WallRunPlayerPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidSlideArmorMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void mobility$restoreWallRunArmor(HumanoidRenderState state, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerModel)) WallRunPlayerPose.restore((HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("TAIL"))
    private void mobility$poseSlideArmor(HumanoidRenderState state, CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel || !(state instanceof AvatarRenderState avatar)) return;
        if (((WallRunRenderState) avatar).mobility$wallSide() != 0) {
            WallRunPlayerPose.applyArmor((HumanoidModel<?>) (Object) this, avatar);
            return;
        }
        if (!((SlideRenderState) avatar).mobility$hasSlidePose()) return;
        SlidePlayerPose.applyArmor((HumanoidModel<?>) (Object) this);
    }
}
