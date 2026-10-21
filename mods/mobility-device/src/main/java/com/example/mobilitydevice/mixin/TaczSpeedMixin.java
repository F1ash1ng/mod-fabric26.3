package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntitySpeedModifier", remap = false)
public abstract class TaczSpeedMixin {
    private static final Identifier WEIGHT = Identifier.fromNamespaceAndPath("tacz", "weight_speed_modifier");
    private static final Identifier EXTRA = Identifier.fromNamespaceAndPath("tacz", "extra_gun_speed_modifier");

    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "updateSpeedModifier", at = @At("HEAD"), cancellable = true)
    private void mobility$ignoreGunSlowdown(CallbackInfo ci) {
        if (!(shooter instanceof Player player) || !WaistJetItems.isEquipped(player)
                || !MobilitySettings.enabled(player, MobilitySettings.TACZ)) return;
        AttributeInstance speed = shooter.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(WEIGHT);
            speed.removeModifier(EXTRA);
        }
        ci.cancel();
    }
}
