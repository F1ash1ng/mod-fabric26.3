package dev.smartcrafter.network;

import dev.smartcrafter.data.CrafterTemplateAttachments;
import dev.smartcrafter.network.CrafterUiPayloads.EditTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.RecipeCatalog;
import dev.smartcrafter.network.CrafterUiPayloads.RecipeChoice;
import dev.smartcrafter.network.CrafterUiPayloads.RequestCatalog;
import dev.smartcrafter.network.CrafterUiPayloads.RequestCurrentTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.ResetTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.SaveResult;
import dev.smartcrafter.network.CrafterUiPayloads.SaveTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.SelectRecipe;
import dev.smartcrafter.template.CrafterTemplate;
import dev.smartcrafter.template.ItemStackSnapshot;
import dev.smartcrafter.template.TemplateSlot;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.util.context.ContextMap;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class CrafterUiNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("smartcrafter/network");

    private CrafterUiNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(RequestCatalog.TYPE, RequestCatalog.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestCurrentTemplate.TYPE, RequestCurrentTemplate.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SelectRecipe.TYPE, SelectRecipe.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveTemplate.TYPE, SaveTemplate.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResetTemplate.TYPE, ResetTemplate.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RecipeCatalog.TYPE, RecipeCatalog.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EditTemplate.TYPE, EditTemplate.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SaveResult.TYPE, SaveResult.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestCatalog.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            Optional<CrafterBlockEntity> crafter = activeCrafter(player, payload.containerId());
            if (crafter.isEmpty()) {
                return;
            }

            List<RecipeChoice> choices = context.server().getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.value() instanceof CraftingRecipe)
                    .filter(holder -> !holder.value().isSpecial())
                    .map(CrafterUiNetworking::toChoice)
                    .toList();

            ServerPlayNetworking.send(player, new RecipeCatalog(
                    payload.containerId(),
                    CrafterTemplateAttachments.getOrEmpty(crafter.get()),
                    choices
            ));
        }));

        ServerPlayNetworking.registerGlobalReceiver(RequestCurrentTemplate.TYPE, (payload, context) -> context.server().execute(() -> {
            activeCrafter(context.player(), payload.containerId()).ifPresent(crafter -> {
                CrafterTemplate template = CrafterTemplateAttachments.getOrEmpty(crafter);
                ItemStack output = findRecipeOutput(context.server().getRecipeManager().getRecipes(), template.recipeId());
                ServerPlayNetworking.send(context.player(), new EditTemplate(payload.containerId(), template, output));
            });
        }));

        ServerPlayNetworking.registerGlobalReceiver(SelectRecipe.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (activeCrafter(player, payload.containerId()).isEmpty()) {
                return;
            }

            context.server().getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().equals(payload.recipeId()))
                    .filter(holder -> holder.value() instanceof CraftingRecipe)
                    .findFirst()
                    .ifPresent(holder -> {
                        CrafterTemplate template = templateFromRecipe(holder, (CraftingRecipe) holder.value());
                        ItemStack output = recipeOutput(holder, (CraftingRecipe) holder.value());
                        ServerPlayNetworking.send(player, new EditTemplate(payload.containerId(), template, output));
                    });
        }));

        ServerPlayNetworking.registerGlobalReceiver(SaveTemplate.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            Optional<CrafterBlockEntity> crafter = activeCrafter(player, payload.containerId());
            boolean accepted = crafter.isPresent();
            if (accepted) {
                CrafterTemplateAttachments.set(crafter.get(), payload.template());
            }
            ServerPlayNetworking.send(player, new SaveResult(payload.containerId(), accepted));
        }));

        ServerPlayNetworking.registerGlobalReceiver(ResetTemplate.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            Optional<CrafterBlockEntity> crafter = activeCrafter(player, payload.containerId());
            boolean accepted = crafter.isPresent();
            if (accepted) {
                // Remove only Smart Crafter's per-block template. Vanilla inventory, disabled
                // slots, redstone state, and block state remain entirely under Minecraft's control.
                CrafterTemplateAttachments.clear(crafter.get());
            }
            ServerPlayNetworking.send(player, new SaveResult(payload.containerId(), accepted));
        }));
    }

    private static Optional<CrafterBlockEntity> activeCrafter(ServerPlayer player, int containerId) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || !(menu instanceof CrafterMenu crafterMenu) || !menu.stillValid(player)) {
            return Optional.empty();
        }

        if (!(crafterMenu.getContainer() instanceof BlockEntity blockEntity)
                || !(blockEntity instanceof CrafterBlockEntity crafter)
                || blockEntity.getLevel() != player.level()) {
            return Optional.empty();
        }
        return Optional.of(crafter);
    }

    private static RecipeChoice toChoice(RecipeHolder<?> holder) {
        ItemStack icon = ItemStack.EMPTY;
        if (holder.value() instanceof CraftingRecipe recipe) {
            icon = recipeOutput(holder, recipe);
        }
        try {
            if (icon.isEmpty()) {
                for (RecipeDisplay display : holder.value().display()) {
                    icon = display.result().resolveForFirstStack(ContextMap.EMPTY);
                    if (!icon.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not resolve display output for recipe {}", holder.id().identifier(), exception);
        }
        if (icon.isEmpty()) {
            try {
                icon = holder.value().placementInfo().ingredients().stream()
                        .findFirst()
                        .map(CrafterUiNetworking::firstIngredientStack)
                        .orElse(ItemStack.EMPTY);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not resolve an ingredient icon for recipe {}", holder.id().identifier(), exception);
            }
        }
        Identifier recipeId = holder.id().identifier();
        Component localizedName = icon.isEmpty()
                ? net.minecraft.network.chat.Component.literal(recipeId.getPath().replace('_', ' '))
                : icon.getHoverName().copy();
        localizedName = localizedName.copy().append(
                net.minecraft.network.chat.Component.literal("  [" + recipeId.getNamespace() + "]"));
        return new RecipeChoice(recipeId, localizedName, icon);
    }

    private static ItemStack findRecipeOutput(Collection<RecipeHolder<?>> recipes, Optional<Identifier> recipeId) {
        if (recipeId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipes.stream()
                .filter(holder -> holder.id().identifier().equals(recipeId.get()))
                .filter(holder -> holder.value() instanceof CraftingRecipe)
                .map(holder -> recipeOutput(holder, (CraftingRecipe) holder.value()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack recipeOutput(RecipeHolder<?> holder, CraftingRecipe recipe) {
        try {
            for (RecipeDisplay display : holder.value().display()) {
                ItemStack displayResult = display.result().resolveForFirstStack(ContextMap.EMPTY);
                if (!displayResult.isEmpty()) {
                    return displayResult;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not resolve display output for recipe {}", holder.id().identifier(), exception);
        }
        return craftingOutput(recipe);
    }

    private static ItemStack craftingOutput(CraftingRecipe recipe) {
        try {
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            it.unimi.dsi.fastutil.ints.IntList slotsToIngredient = recipe.placementInfo().slotsToIngredientIndex();
            ArrayList<ItemStack> inputItems = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            for (int slotIndex = 0; slotIndex < CrafterTemplate.SLOT_COUNT && slotIndex < slotsToIngredient.size(); slotIndex++) {
                int ingredientIndex = slotsToIngredient.getInt(slotIndex);
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    ItemStack stack = firstIngredientStack(ingredients.get(ingredientIndex));
                    if (!stack.isEmpty()) {
                        inputItems.set(slotIndex, stack);
                    }
                }
            }
            return recipe.assemble(CraftingInput.of(3, 3, inputItems));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not assemble a preview for the crafting recipe", exception);
            return ItemStack.EMPTY;
        }
    }

    private static CrafterTemplate templateFromRecipe(RecipeHolder<?> holder, CraftingRecipe recipe) {
        CrafterTemplate template = CrafterTemplate.empty();
        List<Ingredient> ingredients;
        it.unimi.dsi.fastutil.ints.IntList slotsToIngredient;
        try {
            ingredients = recipe.placementInfo().ingredients();
            slotsToIngredient = recipe.placementInfo().slotsToIngredientIndex();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read ingredient layout for recipe {}", holder.id().identifier(), exception);
            return template.withRecipeMetadata(Optional.of(holder.id().identifier()), Optional.empty());
        }

        for (int slotIndex = 0; slotIndex < CrafterTemplate.SLOT_COUNT && slotIndex < slotsToIngredient.size(); slotIndex++) {
            int ingredientIndex = slotsToIngredient.getInt(slotIndex);
            if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
                continue;
            }
            Ingredient ingredient = ingredients.get(ingredientIndex);
            Optional<ItemStack> display = ingredient.items().findFirst().map(CrafterUiNetworking::stackFromHolder);
            template = template.withSlot(slotIndex, TemplateSlot.forIngredient(ingredient, display));
        }

        Identifier recipeId = holder.id().identifier();
        return template.withRecipeMetadata(Optional.of(recipeId), Optional.empty());
    }

    private static ItemStack firstIngredientStack(Ingredient ingredient) {
        return ingredient.items().findFirst().map(CrafterUiNetworking::stackFromHolder).orElse(ItemStack.EMPTY);
    }

    private static ItemStack stackFromHolder(Holder<Item> holder) {
        return new ItemStack(holder);
    }
}
