package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.KillStreakHud;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class KillStreakHudMixin {
    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void mobility$replaceExperienceBar(ContextualBar bar, GuiGraphicsExtractor graphics,
                                               DeltaTracker deltaTracker, Operation<Void> original) {
        if (KillStreakHud.shouldShow()) KillStreakHud.draw(graphics, deltaTracker);
        else original.call(bar, graphics, deltaTracker);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void mobility$hideVanillaLevel(GuiGraphicsExtractor graphics, Font font,
                                           int level, Operation<Void> original) {
        if (!KillStreakHud.shouldShow()) original.call(graphics, font, level);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void mobility$hideOtherBarDetails(ContextualBar bar, GuiGraphicsExtractor graphics,
                                              DeltaTracker deltaTracker, Operation<Void> original) {
        if (!KillStreakHud.shouldShow()) original.call(bar, graphics, deltaTracker);
    }
}
