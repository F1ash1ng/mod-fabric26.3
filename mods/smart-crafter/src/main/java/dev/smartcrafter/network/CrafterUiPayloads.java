package dev.smartcrafter.network;

import com.mojang.serialization.Codec;
import dev.smartcrafter.SmartCrafterMod;
import dev.smartcrafter.template.CrafterTemplate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CrafterUiPayloads {
    private CrafterUiPayloads() {
    }

    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(Codec<T> codec) {
        return ByteBufCodecs.fromCodecWithRegistries(codec);
    }

    public record RequestCatalog(int containerId) implements CustomPacketPayload {
        public static final Type<RequestCatalog> TYPE = new Type<>(SmartCrafterMod.id("request_crafter_recipe_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCatalog> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, RequestCatalog::containerId, RequestCatalog::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestCurrentTemplate(int containerId) implements CustomPacketPayload {
        public static final Type<RequestCurrentTemplate> TYPE = new Type<>(SmartCrafterMod.id("request_current_template"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCurrentTemplate> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, RequestCurrentTemplate::containerId, RequestCurrentTemplate::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SelectRecipe(int containerId, Identifier recipeId) implements CustomPacketPayload {
        public static final Type<SelectRecipe> TYPE = new Type<>(SmartCrafterMod.id("select_crafter_recipe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, SelectRecipe::containerId,
                Identifier.STREAM_CODEC, SelectRecipe::recipeId,
                SelectRecipe::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveTemplate(int containerId, CrafterTemplate template) implements CustomPacketPayload {
        public static final Type<SaveTemplate> TYPE = new Type<>(SmartCrafterMod.id("save_crafter_template"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveTemplate> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, SaveTemplate::containerId,
                codec(CrafterTemplate.CODEC), SaveTemplate::template,
                SaveTemplate::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ResetTemplate(int containerId) implements CustomPacketPayload {
        public static final Type<ResetTemplate> TYPE = new Type<>(SmartCrafterMod.id("reset_crafter_template"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetTemplate> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, ResetTemplate::containerId, ResetTemplate::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RecipeChoice(Identifier recipeId, Component name, ItemStack icon) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoice> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, RecipeChoice::recipeId,
                ComponentSerialization.STREAM_CODEC, RecipeChoice::name,
                ItemStack.OPTIONAL_STREAM_CODEC, RecipeChoice::icon,
                RecipeChoice::new
        );

        public RecipeChoice {
            icon = icon.copy();
        }

        @Override
        public ItemStack icon() {
            return icon.copy();
        }
    }

    public record RecipeCatalog(int containerId, CrafterTemplate currentTemplate, List<RecipeChoice> recipes)
            implements CustomPacketPayload {
        public static final Type<RecipeCatalog> TYPE = new Type<>(SmartCrafterMod.id("crafter_recipe_catalog"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeChoice>> RECIPE_LIST_CODEC =
                ByteBufCodecs.collection(ArrayList::new, RecipeChoice.STREAM_CODEC, 4096);
        public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCatalog> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, RecipeCatalog::containerId,
                codec(CrafterTemplate.CODEC), RecipeCatalog::currentTemplate,
                RECIPE_LIST_CODEC, RecipeCatalog::recipes,
                RecipeCatalog::new
        );

        public RecipeCatalog {
            recipes = List.copyOf(recipes);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditTemplate(int containerId, CrafterTemplate template, ItemStack resultPreview) implements CustomPacketPayload {
        public static final Type<EditTemplate> TYPE = new Type<>(SmartCrafterMod.id("edit_crafter_template"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EditTemplate> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, EditTemplate::containerId,
                codec(CrafterTemplate.CODEC), EditTemplate::template,
                ItemStack.OPTIONAL_STREAM_CODEC, EditTemplate::resultPreview,
                EditTemplate::new
        );

        public EditTemplate {
            resultPreview = resultPreview.copy();
        }

        @Override
        public ItemStack resultPreview() {
            return resultPreview.copy();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveResult(int containerId, boolean accepted) implements CustomPacketPayload {
        public static final Type<SaveResult> TYPE = new Type<>(SmartCrafterMod.id("crafter_template_save_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.CONTAINER_ID, SaveResult::containerId,
                ByteBufCodecs.BOOL, SaveResult::accepted,
                SaveResult::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
