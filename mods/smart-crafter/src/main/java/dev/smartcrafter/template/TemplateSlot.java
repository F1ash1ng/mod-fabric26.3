package dev.smartcrafter.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Objects;
import java.util.Optional;

/** One of the nine configurable slots in a Crafter template. */
public record TemplateSlot(
        boolean enabled,
        Optional<Ingredient> ingredient,
        Optional<ItemStackSnapshot> displayStack
) {
    public static final Codec<TemplateSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(TemplateSlot::enabled),
            Ingredient.CODEC.optionalFieldOf("ingredient").forGetter(TemplateSlot::ingredient),
            ItemStackSnapshot.CODEC.optionalFieldOf("display_stack").forGetter(TemplateSlot::displayStack)
    ).apply(instance, TemplateSlot::new));

    public TemplateSlot {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(displayStack, "displayStack");
        if (ingredient.isEmpty() && displayStack.isPresent()) {
            throw new IllegalArgumentException("A display stack requires an ingredient");
        }
    }

    public static TemplateSlot openEmpty() {
        return new TemplateSlot(true, Optional.empty(), Optional.empty());
    }

    public static TemplateSlot disabled() {
        return new TemplateSlot(false, Optional.empty(), Optional.empty());
    }

    public static TemplateSlot forStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a template slot from an empty stack");
        }
        return forIngredient(Ingredient.of(stack.getItem()), Optional.of(stack));
    }

    public static TemplateSlot forIngredient(Ingredient ingredient, Optional<ItemStack> displayStack) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(displayStack, "displayStack");
        return new TemplateSlot(
                true,
                Optional.of(ingredient),
                displayStack.filter(stack -> !stack.isEmpty()).map(ItemStackSnapshot::new)
        );
    }

    public TemplateSlot withEnabled(boolean value) {
        return value == enabled ? this : new TemplateSlot(value, ingredient, displayStack);
    }

    public TemplateSlot withStack(ItemStack stack) {
        return forStack(stack).withEnabled(enabled);
    }

    public TemplateSlot clearStack() {
        return ingredient.isEmpty() ? this : new TemplateSlot(enabled, Optional.empty(), Optional.empty());
    }
}
