package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.WaistJetRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements WaistJetRenderState {
    private boolean mobility$equipped;
    private int mobility$direction;

    @Override
    public void mobility$setWaistJet(boolean equipped, int direction) {
        mobility$equipped = equipped;
        mobility$direction = direction;
    }

    @Override public boolean mobility$hasWaistJet() { return mobility$equipped; }
    @Override public int mobility$waistJetDirection() { return mobility$direction; }
}
