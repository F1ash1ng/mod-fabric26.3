package com.example.mobilitydevice.client;

public interface SlideRenderState {
    void mobility$setSlidePose(boolean sliding, float yaw);
    boolean mobility$hasSlidePose();
    float mobility$slideYaw();
}
