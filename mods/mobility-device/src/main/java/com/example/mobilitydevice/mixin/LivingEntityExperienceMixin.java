package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.ability.KillStreak;
import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityExperienceMixin {
    @Shadow protected abstract boolean isAlwaysExperienceDropper();

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void mobility$awardKillExperienceDirectly(ServerLevel level, Entity attacker, CallbackInfo ci) {
        if (!((Object) this instanceof Mob mob) || !(attacker instanceof ServerPlayer killer)
                || !WaistJetItems.isEquipped(killer)) return;
        LivingEntity victim = (LivingEntity) (Object) this;

        // Match vanilla's eligibility before replacing orb creation with direct XP.
        boolean vanillaWouldAward = !victim.wasExperienceConsumed()
                && (isAlwaysExperienceDropper()
                    || (victim.getLastHurtByPlayerMemoryTime() > 0
                        && victim.shouldDropExperience()
                        && level.getGameRules().get(GameRules.MOB_DROPS)));
        int baseExperience = vanillaWouldAward ? victim.getExperienceReward(level, attacker) : 0;
        KillStreak.award(killer, baseExperience);
        ci.cancel();
    }
}
