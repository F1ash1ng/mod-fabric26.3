package dev.smartcrafter.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Optional JEI plugin. It is only loaded by JEI when JEI is installed. */
@JeiPlugin
public final class SmartCrafterJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("smartcrafter/jei");

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("smartcrafter", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        Map<Identifier, ItemStack> outputs = new HashMap<>();
        runtime.getRecipeManager()
                .createRecipeLookup(RecipeTypes.CRAFTING)
                .get()
                .forEach(holder -> {
                    ItemStack output = recipeOutput(holder);
                    if (!output.isEmpty()) {
                        outputs.put(holder.id().identifier(), output);
                    }
                });
        SmartCrafterJeiCompat.setCraftingOutputs(outputs);
        LOGGER.info("Loaded {} crafting outputs from JEI", outputs.size());
    }

    @Override
    public void onRuntimeUnavailable() {
        SmartCrafterJeiCompat.setCraftingOutputs(Map.of());
    }

    private static ItemStack recipeOutput(RecipeHolder<CraftingRecipe> holder) {
        try {
            for (RecipeDisplay display : holder.value().display()) {
                ItemStack output = display.result().resolveForFirstStack(ContextMap.EMPTY);
                if (!output.isEmpty()) {
                    return output;
                }
            }

            CraftingRecipe recipe = holder.value();
            ArrayList<ItemStack> inputItems = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            var ingredients = recipe.placementInfo().ingredients();
            var slotsToIngredient = recipe.placementInfo().slotsToIngredientIndex();
            for (int slotIndex = 0; slotIndex < 9 && slotIndex < slotsToIngredient.size(); slotIndex++) {
                int ingredientIndex = slotsToIngredient.getInt(slotIndex);
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    Ingredient ingredient = ingredients.get(ingredientIndex);
                    ItemStack input = ingredient.items().findFirst().map(itemHolder -> new ItemStack(itemHolder)).orElse(ItemStack.EMPTY);
                    if (!input.isEmpty()) {
                        inputItems.set(slotIndex, input);
                    }
                }
            }
            return recipe.assemble(CraftingInput.of(3, 3, inputItems));
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }
}
