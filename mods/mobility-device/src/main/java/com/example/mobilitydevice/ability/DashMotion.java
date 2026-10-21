package com.example.mobilitydevice.ability;

public final class DashMotion {
    public static final double TOTAL_DISTANCE = 12.0;
    private static final double ARC_HEIGHT = 0.65;
    private static final double EXIT_SPEED = 0.34;

    private DashMotion() {}

    public static double progress(double x, double z, double startX, double startZ,
                                  double directionX, double directionZ) {
        return Math.max(0, (x - startX) * directionX + (z - startZ) * directionZ);
    }

    public static double heightAt(double progress) {
        double fraction = Math.clamp(progress / TOTAL_DISTANCE, 0, 1);
        return ARC_HEIGHT * Math.sin(Math.PI * fraction);
    }

    public static double nextSpeed(double progress) {
        double remaining = TOTAL_DISTANCE - progress;
        if (remaining <= 0) return 0;

        double speed;
        if (progress < 1.8) {
            speed = 1.40 - 0.50 * smoothstep(progress / 1.8);
        } else if (progress < 9.0) {
            speed = 0.90;
        } else {
            speed = EXIT_SPEED + (0.90 - EXIT_SPEED)
                    * (1.0 - smoothstep((progress - 9.0) / 3.0));
        }
        return Math.min(speed, remaining);
    }

    public static double exitSpeed() {
        return EXIT_SPEED;
    }

    private static double smoothstep(double fraction) {
        double x = Math.clamp(fraction, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
