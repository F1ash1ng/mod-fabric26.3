package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.MobilityDeviceClient;
import com.example.mobilitydevice.client.SlideClient;
import com.example.mobilitydevice.client.WallRunClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mobility$lockInputDuringDash(CallbackInfo ci) {
        if (!MobilityDeviceClient.isDashActive() && !SlideClient.isSlideMotionActive()
                && !WallRunClient.isActive()) return;
        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
