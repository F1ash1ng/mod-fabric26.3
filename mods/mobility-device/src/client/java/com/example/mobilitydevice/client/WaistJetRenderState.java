package com.example.mobilitydevice.client;

public interface WaistJetRenderState {
    void mobility$setWaistJet(boolean equipped, int direction);
    boolean mobility$hasWaistJet();
    int mobility$waistJetDirection();
}
