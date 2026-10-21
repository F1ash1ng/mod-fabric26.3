package com.example.mobilitydevice.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ExperienceOrb.class)
public interface ExperienceOrbRepairInvoker {
    @Invoker("repairPlayerItems")
    int mobility$repairPlayerItems(ServerPlayer player, int experience);
}
