package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class FeatherFallingMixin {
    @Inject(method = "getDamageProtection", at = @At("RETURN"), cancellable = true)
    private static void mobility$addFeatherFallingTwo(ServerLevel level, LivingEntity entity,
                                                       DamageSource source, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof Player && WaistJetItems.isEquipped(entity)
                && source.is(DamageTypeTags.IS_FALL)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Feather Falling II contributes 3 protection points per level; vanilla caps the total at 20.
            cir.setReturnValue(cir.getReturnValueF() + 6.0F);
        }
    }
}
