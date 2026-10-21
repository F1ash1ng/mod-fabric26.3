package com.example.mobilitydevice.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotPositionAccessor {
    @Mutable @Accessor("x") void mobility$setX(int x);
    @Mutable @Accessor("y") void mobility$setY(int y);
}
