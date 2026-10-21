package com.example.mobilitydevice.ability;

public final class SlideMotion {
    public static final double DISTANCE = 5.0;
    public static final double JUMP_START_DISTANCE = DISTANCE - 2.0;
    public static final double MIN_START_SPEED = 0.08;
    public static final double EXIT_SPEED = 0.20;

    private SlideMotion() {}

    public static double progress(double x, double z, double startX, double startZ,
                                  double directionX, double directionZ) {
        return Math.max(0, (x - startX) * directionX + (z - startZ) * directionZ);
    }

    public static double nextSpeed(double progress) {
        double remaining = DISTANCE - progress;
        if (remaining <= 0) return 0;
        double fraction = Math.clamp(progress / DISTANCE, 0, 1);
        double eased = fraction * fraction * (3 - 2 * fraction);
        return Math.min(0.72 - 0.42 * eased, remaining);
    }
}
