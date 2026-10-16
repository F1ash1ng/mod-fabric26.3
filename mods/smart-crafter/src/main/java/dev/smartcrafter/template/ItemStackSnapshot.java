package dev.smartcrafter.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** An immutable, defensive-copy wrapper around an item stack used for template display data. */
public final class ItemStackSnapshot {
    public static final Codec<ItemStackSnapshot> CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty()
                    ? DataResult.error(() -> "Template display stack cannot be empty")
                    : DataResult.success(new ItemStackSnapshot(stack)),
            snapshot -> DataResult.success(snapshot.copy())
    );

    private final ItemStack stack;

    public ItemStackSnapshot(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Template display stack cannot be empty");
        }
        this.stack = stack.copy();
    }

    public ItemStack copy() {
        return stack.copy();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemStackSnapshot snapshot
                && ItemStack.matches(stack, snapshot.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
