package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/** Stops TaCZ's optional third-person gun animation while the slide pose owns the player bones. */
public final class TaczSlideCompatibility {
    private static final boolean TACZ_LOADED = FabricLoader.getInstance().isModLoaded("tacz");
    private static Method stopAllAnimation;
    private static boolean unavailable;

    private TaczSlideCompatibility() {}

    public static void stopAnimations(AvatarRenderState state) {
        if (!TACZ_LOADED || unavailable) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(state.id);
        if (!(entity instanceof LivingEntity living)) return;
        stopAnimations(living);
    }

    public static void stopAnimations(LivingEntity living) {
        if (!TACZ_LOADED || unavailable) return;
        try {
            if (stopAllAnimation == null) {
                Class<?> compat = Class.forName("com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat");
                stopAllAnimation = compat.getMethod("stopAllAnimation", LivingEntity.class, int.class);
            }
            // A one-tick fade avoids zero-length interpolation in Player Animation Library.
            stopAllAnimation.invoke(null, living, 1);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            unavailable = true;
            MobilityDeviceMod.LOGGER.warn("TaCZ slide animation suppression is unavailable; keeping the slide pose", e);
        }
    }
}
