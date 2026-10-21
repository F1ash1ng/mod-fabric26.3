package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.TaczClientShootRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Use TaCZ's normal held-gun animation in the same cases where crouching would select it. */
@Mixin(targets = "com.tacz.guns.client.event.TickAnimationEvent", remap = false)
public abstract class TaczMovementAnimationMixin {
    @ModifyArg(method = "lambda$tickAnimation$0", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V"),
            index = 0)
    private static String mobility$selectHipFireAnimation(String input) {
        if (!"run".equals(input)) return input;
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && TaczClientShootRules.useCrouchedGunPose(player) ? "idle" : input;
    }
}
