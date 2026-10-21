package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.DashScreenEffect;
import com.example.mobilitydevice.client.MobilityDeviceClient;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Unique private float mobility$dashProgress = -1;

    @Inject(method = "extract", at = @At("TAIL"))
    private void mobility$extractDashEffect(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        mobility$dashProgress = renderLevel
                ? MobilityDeviceClient.dashVisualProgress(delta.getGameTimeDeltaPartialTick(false)) : -1;
    }

    // Vanilla applies world post effects here, before drawing the GUI and crosshair.
    @Inject(method = "applyPostEffects", at = @At("TAIL"))
    private void mobility$renderDashEffect(CallbackInfo ci) {
        DashScreenEffect.render(minecraft, mainRenderTarget, resourcePool, mobility$dashProgress);
    }
}
