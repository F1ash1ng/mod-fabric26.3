package com.example.mobilitydevice.ability;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WallRunMotion {
    public static final double MIN_APPROACH_SPEED = 0.07;
    public static final double VANILLA_WALK_BLOCKS_PER_TICK = 0.216;
    private static final double VANILLA_WALK_ATTRIBUTE = 0.1;
    private static final double OVER_SPRINT_FACTOR = 1.75;
    private static final double WALL_REACH = 0.35;

    private WallRunMotion() {}

    // +X, -X, +Z, -Z. These are directions from the player toward the wall.
    public static int normalX(int wall) {
        return wall == 0 ? 1 : wall == 1 ? -1 : 0;
    }

    public static int normalZ(int wall) {
        return wall == 2 ? 1 : wall == 3 ? -1 : 0;
    }

    public static boolean validWall(int wall) {
        return wall >= 0 && wall < 4;
    }

    public static byte side(Player player, int wall) {
        double yaw = Math.toRadians(player.getYRot());
        double rightDot = -Math.cos(yaw) * normalX(wall) - Math.sin(yaw) * normalZ(wall);
        return Math.abs(rightDot) >= 0.45 ? (byte) (rightDot > 0 ? 1 : -1) : 0;
    }

    public static double parallelForwardSpeed(Vec3 motion, int wall, int side) {
        return motion.x * tangentX(wall, side) + motion.z * tangentZ(wall, side);
    }

    public static double towardFromKeys(float yawDegrees, byte forward, byte strafe, int wall) {
        double yaw = Math.toRadians(yawDegrees);
        double x = -Math.sin(yaw) * forward - Math.cos(yaw) * strafe;
        double z = Math.cos(yaw) * forward - Math.sin(yaw) * strafe;
        double length = Math.hypot(x, z);
        return length < 0.001 ? 0 : (x * normalX(wall) + z * normalZ(wall)) / length;
    }

    public static boolean hasRunnableWall(Player player, int wall) {
        if (!validWall(wall)) return false;
        AABB body = player.getBoundingBox();
        int footLayer = (int) Math.floor(player.getY());
        int alongBlock = (int) Math.floor(normalX(wall) != 0 ? player.getZ() : player.getX());
        // Include a wall beginning below the feet after the player has jumped.
        // All nine probes must lie in one contiguous 3-by-3 section of wall.
        for (int baseLayer = footLayer - 1; baseLayer <= footLayer; baseLayer++) {
            for (int start = -2; start <= 0; start++) {
                if (threeByThreeAt(player, body, wall, baseLayer, alongBlock + start)) return true;
            }
        }
        return false;
    }

    private static boolean threeByThreeAt(Player player, AABB body, int wall,
                                          int baseLayer, int startAlongBlock) {
        for (int along = 0; along < 3; along++) {
            double alongMin = startAlongBlock + along + 0.25;
            double alongMax = alongMin + 0.5;
            for (int high = 0; high < 3; high++) {
                double y = baseLayer + 0.35 + high;
                AABB probe = switch (wall) {
                    case 0 -> new AABB(body.maxX + 0.01, y, alongMin,
                            body.maxX + WALL_REACH, y + 0.25, alongMax);
                    case 1 -> new AABB(body.minX - WALL_REACH, y, alongMin,
                            body.minX - 0.01, y + 0.25, alongMax);
                    case 2 -> new AABB(alongMin, y, body.maxZ + 0.01,
                            alongMax, y + 0.25, body.maxZ + WALL_REACH);
                    default -> new AABB(alongMin, y, body.minZ - WALL_REACH,
                            alongMax, y + 0.25, body.minZ - 0.01);
                };
                if (player.level().noBlockCollision(player, probe)) return false;
            }
        }
        return true;
    }

    public static double tangentX(int wall, int side) {
        return side * normalZ(wall);
    }

    public static double tangentZ(int wall, int side) {
        return -side * normalX(wall);
    }

    public static double speed(Player player) {
        // Movement speed includes vanilla's sprint modifier. Convert its 0.1 base attribute
        // to the usual walking displacement, then add 75% to the resulting sprint speed.
        return player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * (VANILLA_WALK_BLOCKS_PER_TICK / VANILLA_WALK_ATTRIBUTE)
                * OVER_SPRINT_FACTOR;
    }

    public static Vec3 wallJump(Player player, int wall) {
        Vec3 look = player.getLookAngle();
        double nx = normalX(wall), nz = normalZ(wall);
        double toward = look.x * nx + look.z * nz;
        // The jump follows the crosshair while always gaining enough outward speed to leave the wall.
        double outwardCorrection = Math.max(0, 0.30 + 0.65 * toward);
        return new Vec3(look.x * 0.65 - nx * outwardCorrection,
                Math.max(0.32, 0.42 + look.y * 0.32),
                look.z * 0.65 - nz * outwardCorrection);
    }
}
