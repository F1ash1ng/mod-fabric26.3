package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.ability.WallRunVisuals;
import com.example.mobilitydevice.client.WallRunRenderState;
import com.example.mobilitydevice.client.WallRunClient;
import com.example.mobilitydevice.client.WallRunPlayerPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import com.example.mobilitydevice.client.SlideClient;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.WaistJetRenderState;
import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void mobility$restoreFirstPersonHand(PoseStack pose, SubmitNodeCollector collector, int light,
                                                Identifier texture, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        WallRunPlayerPose.restore(((AvatarRenderer<?>) (Object) this).getModel());
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void mobility$extractWaistJet(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((WaistJetRenderState) state).mobility$setWaistJet(
                WaistJetItems.isEquipped(avatar), avatar.getAttachedOrElse(DashAbility.ACTIVE_DIRECTION, -1));
        boolean sliding = avatar instanceof Player player && SlideClient.isSlidePosture(player);
        ((SlideRenderState) state).mobility$setSlidePose(sliding,
                avatar instanceof Player player ? SlideClient.slideYaw(player) : 0.0F);
        WallRunVisuals wallRun = avatar instanceof Player player && !sliding
                && WallRunClient.isWallRunPosture(player)
                ? avatar.getAttachedOrElse(WallRunVisuals.STATE, WallRunVisuals.OFF) : WallRunVisuals.OFF;
        ((WallRunRenderState) state).mobility$setWallRun(wallRun.side(), wallRun.movement(),
                wallRun.side() == 0 ? 0 : wallRun.seconds(avatar.level().getGameTime(), partialTick));
        if (wallRun.side() != 0) {
            state.pose = Pose.STANDING;
            state.swimAmount = 0;
            state.isVisuallySwimming = false;
            state.isCrouching = false;
            state.bodyRot = wallRun.yaw();
            state.showCape = false;
        }
        if (sliding) {
            state.pose = Pose.STANDING;
            state.swimAmount = 0;
            state.isVisuallySwimming = false;
            state.isCrouching = false;
            state.showCape = false;
            state.bodyRot = ((SlideRenderState) state).mobility$slideYaw();
        }
    }
}
