package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.client.WaistJetRenderState;
import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void mobility$extractWaistJet(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((WaistJetRenderState) state).mobility$setWaistJet(
                WaistJetItems.isEquipped(avatar), avatar.getAttachedOrElse(DashAbility.ACTIVE_DIRECTION, -1));
    }
}
