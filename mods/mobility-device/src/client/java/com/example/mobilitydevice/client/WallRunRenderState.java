package com.example.mobilitydevice.client;

public interface WallRunRenderState {
    void mobility$setWallRun(int side, int movement, float seconds);
    int mobility$wallSide();
    int mobility$wallMovement();
    float mobility$wallAnimationSeconds();
}
