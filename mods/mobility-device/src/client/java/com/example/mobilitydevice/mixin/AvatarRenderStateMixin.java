package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.WaistJetRenderState;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.WallRunRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements WaistJetRenderState, SlideRenderState, WallRunRenderState {
    private int mobility$wallSide, mobility$wallMovement;
    private float mobility$wallSeconds;

    @Override public void mobility$setWallRun(int side, int movement, float seconds) {
        mobility$wallSide = side;
        mobility$wallMovement = movement;
        mobility$wallSeconds = seconds;
    }
    @Override public int mobility$wallSide() { return mobility$wallSide; }
    @Override public int mobility$wallMovement() { return mobility$wallMovement; }
    @Override public float mobility$wallAnimationSeconds() { return mobility$wallSeconds; }
    private boolean mobility$equipped;
    private int mobility$direction;
    private boolean mobility$slidePose;
    private float mobility$slideYaw;

    @Override
    public void mobility$setWaistJet(boolean equipped, int direction) {
        mobility$equipped = equipped;
        mobility$direction = direction;
    }

    @Override public boolean mobility$hasWaistJet() { return mobility$equipped; }
    @Override public int mobility$waistJetDirection() { return mobility$direction; }

    @Override
    public void mobility$setSlidePose(boolean sliding, float yaw) {
        mobility$slidePose = sliding;
        mobility$slideYaw = yaw;
    }

    @Override public boolean mobility$hasSlidePose() { return mobility$slidePose; }
    @Override public float mobility$slideYaw() { return mobility$slideYaw; }
}
