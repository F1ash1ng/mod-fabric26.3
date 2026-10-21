package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.client.SlidePlayerPose;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.TaczSlideCompatibility;
import com.example.mobilitydevice.client.WallRunPlayerPose;
import com.example.mobilitydevice.client.WallRunRenderState;
import com.example.mobilitydevice.client.WallRunPlayerModelKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 100)
public abstract class PlayerSlideModelMixin implements WallRunPlayerModelKind {
    @Unique private boolean mobility$armorModel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mobility$rememberModelKind(ModelPart root, boolean slim, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // Inspect the baked mesh before animation/CPM can modify it. Every armor slot has empty base parts.
        for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            if (((ModelPartAccessor) (Object) part).mobility$cubes().isEmpty()) {
                mobility$armorModel = true;
                break;
            }
        }
    }

    @Override public boolean mobility$isArmorModel() { return mobility$armorModel; }

    @Unique private PartPose[] mobility$beforeSlide;
    @Unique private ModelPart[] mobility$beforeSlideParts;
    @Unique private boolean[] mobility$beforeVisibility;
    @Unique private boolean mobility$reportedSlidePose;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("HEAD"))
    private void mobility$restoreBeforeAnimation(AvatarRenderState state, CallbackInfo ci) {
        WallRunPlayerPose.restore((PlayerModel) (Object) this);
        if (mobility$beforeSlide == null) return;
        for (int i = 0; i < mobility$beforeSlideParts.length; i++) {
            mobility$beforeSlideParts[i].loadPose(mobility$beforeSlide[i]);
            mobility$beforeSlideParts[i].visible = mobility$beforeVisibility[i];
        }
        mobility$beforeSlide = null;
        mobility$beforeSlideParts = null;
        mobility$beforeVisibility = null;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"))
    private void mobility$applySlideBones(AvatarRenderState state, CallbackInfo ci) {
        if (((WallRunRenderState) state).mobility$wallSide() != 0) {
            TaczSlideCompatibility.stopAnimations(state);
            WallRunPlayerPose.apply((PlayerModel) (Object) this, state);
            return;
        }
        if (!((SlideRenderState) state).mobility$hasSlidePose()) return;
        PlayerModel model = (PlayerModel) (Object) this;
        ModelPart[] parts = SlidePlayerPose.affectedParts(model);
        mobility$beforeSlideParts = parts;
        mobility$beforeSlide = new PartPose[parts.length];
        mobility$beforeVisibility = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            mobility$beforeSlide[i] = parts[i].storePose();
            mobility$beforeVisibility[i] = parts[i].visible;
        }
        TaczSlideCompatibility.stopAnimations(state);
        SlidePlayerPose.apply(model);
        if (!mobility$reportedSlidePose) {
            MobilityDeviceMod.LOGGER.info("huachan2 pose applied to player model bones");
            mobility$reportedSlidePose = true;
        }
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void mobility$wallRunHand(AvatarRenderState state, HumanoidArm arm, PoseStack pose, CallbackInfo ci) {
        if (((WallRunRenderState) state).mobility$wallSide() == 0) return;
        WallRunPlayerPose.translateToHand((PlayerModel) (Object) this, arm, pose);
        ci.cancel();
    }
}
