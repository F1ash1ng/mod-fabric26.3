package dev.smartcrafter.client;

import dev.smartcrafter.network.CrafterUiPayloads.EditTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.RecipeCatalog;
import dev.smartcrafter.network.CrafterUiPayloads.RequestCatalog;
import dev.smartcrafter.network.CrafterUiPayloads.SaveResult;
import dev.smartcrafter.network.CrafterUiPayloads.SaveTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.SelectRecipe;
import dev.smartcrafter.network.CrafterUiPayloads.RequestCurrentTemplate;
import dev.smartcrafter.network.CrafterUiPayloads.ResetTemplate;
import dev.smartcrafter.mixin.client.ScreenWidgetInvoker;
import dev.smartcrafter.template.CrafterTemplate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SmartCrafterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UiLanguage.initialize();
        ScreenEvents.AFTER_INIT.register((client, screen, screenWidth, screenHeight) -> {
            if (!(screen instanceof CrafterScreen crafterScreen)) {
                return;
            }

            int left = (screenWidth - 176) / 2;
            int top = (screenHeight - 166) / 2;
            Identifier normal = Identifier.withDefaultNamespace("recipe_book/button");
            Identifier highlighted = Identifier.withDefaultNamespace("recipe_book/button_highlighted");
            ImageButton recipeButton = new ImageButton(
                    left + 153,
                    top + 4,
                    20,
                    20,
                    new WidgetSprites(normal, highlighted),
                    button -> {
                        int containerId = crafterScreen.getMenu().containerId;
                        client.gui.setScreen(new RecipeBrowserScreen(crafterScreen, containerId));
                        ClientPlayNetworking.send(new RequestCatalog(containerId));
                    },
                    Component.literal(UiLanguage.text("选择合成配方", "Select a Recipe"))
            );
            recipeButton.setTooltip(Tooltip.create(Component.literal(UiLanguage.text("选择合成配方", "Select a Recipe"))));
            ((ScreenWidgetInvoker) (Object) screen).smartcrafter$addRenderableWidget(recipeButton);
        });

        ClientPlayNetworking.registerGlobalReceiver(RecipeCatalog.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof RecipeBrowserScreen browser
                    && browser.containerId() == payload.containerId()) {
                browser.setCatalog(payload.currentTemplate(), payload.recipes());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(EditTemplate.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof RecipeBrowserScreen browser
                    && browser.containerId() == payload.containerId()) {
                browser.openEditor(payload.template(), payload.resultPreview());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(SaveResult.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof TemplateEditorScreen editor
                    && editor.containerId() == payload.containerId()) {
                editor.onSaveResult(payload.accepted());
            }
        }));
    }

    static void requestCurrentTemplate(int containerId) {
        ClientPlayNetworking.send(new RequestCurrentTemplate(containerId));
    }

    static void selectRecipe(int containerId, Identifier recipeId) {
        ClientPlayNetworking.send(new SelectRecipe(containerId, recipeId));
    }

    static void saveTemplate(int containerId, CrafterTemplate template) {
        ClientPlayNetworking.send(new SaveTemplate(containerId, template));
    }

    static void resetTemplate(int containerId) {
        ClientPlayNetworking.send(new ResetTemplate(containerId));
    }
}
