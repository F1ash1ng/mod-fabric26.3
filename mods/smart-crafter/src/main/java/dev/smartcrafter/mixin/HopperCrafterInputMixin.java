package dev.smartcrafter.mixin;

import dev.smartcrafter.hopper.SmartCrafterHopperInput;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts only the shared inventory insertion helper; non-template transfers stay vanilla. */
@Mixin(HopperBlockEntity.class)
abstract class HopperCrafterInputMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void smartcrafter$distributeIntoConfiguredCrafter(
            Container source,
            Container destination,
            ItemStack offered,
            Direction direction,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack remainder = SmartCrafterHopperInput.tryInsert(destination, offered, direction);
        if (remainder != null) {
            cir.setReturnValue(remainder);
        }
    }
}
