package dev.smartcrafter.client.jei;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JEI-free bridge used by the editor; the optional plugin populates it when JEI is present. */
public final class SmartCrafterJeiCompat {
    private static final Map<Identifier, ItemStack> CRAFTING_OUTPUTS = new ConcurrentHashMap<>();

    private SmartCrafterJeiCompat() {
    }

    public static void setCraftingOutputs(Map<Identifier, ItemStack> outputs) {
        CRAFTING_OUTPUTS.clear();
        outputs.forEach((id, stack) -> {
            if (!stack.isEmpty()) {
                CRAFTING_OUTPUTS.put(id, stack.copy());
            }
        });
    }

    public static ItemStack getCraftingOutput(Identifier recipeId) {
        ItemStack stack = CRAFTING_OUTPUTS.get(recipeId);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
