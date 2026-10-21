package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.WallRunClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class WallRunCameraMixin {
    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void mobility$tiltFirstPersonOnWall(float yaw, float pitch, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || client.options == null
                || client.gameRenderer.mainCamera() != (Object) this
                || !client.options.getCameraType().isFirstPerson()) return;
        float roll = WallRunClient.cameraRollDegrees();
        if (roll == 0) return;
        rotation.rotateZ((float) Math.toRadians(roll));
        FORWARDS.rotate(rotation, forwards);
        UP.rotate(rotation, up);
        LEFT.rotate(rotation, left);
    }
}
