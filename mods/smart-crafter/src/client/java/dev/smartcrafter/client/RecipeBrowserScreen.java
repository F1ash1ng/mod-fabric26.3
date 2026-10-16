package dev.smartcrafter.client;

import dev.smartcrafter.network.CrafterUiPayloads.RecipeChoice;
import dev.smartcrafter.client.jei.SmartCrafterJeiCompat;
import dev.smartcrafter.template.CrafterTemplate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RecipeBrowserScreen extends Screen {
    private static final int ROWS_PER_PAGE = 7;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 270;

    private final CrafterScreen crafterScreen;
    private final int containerId;
    private CrafterTemplate currentTemplate = CrafterTemplate.empty();
    private List<RecipeChoice> recipes = List.of();
    private final List<Button> recipeButtons = new ArrayList<>();
    private EditBox searchField;
    private String searchText = "";
    private Button previousButton;
    private Button nextButton;
    private boolean catalogReceived;
    private int page;

    RecipeBrowserScreen(CrafterScreen crafterScreen, int containerId) {
        super(Component.literal(UiLanguage.text("合成器配方", "Crafter Recipes")));
        this.crafterScreen = crafterScreen;
        this.containerId = containerId;
    }

    int containerId() {
        return containerId;
    }

    void setCatalog(CrafterTemplate template, List<RecipeChoice> recipes) {
        currentTemplate = template;
        this.recipes = List.copyOf(recipes);
        catalogReceived = true;
        page = 0;
        refreshRecipeRows();
    }

    void openEditor(CrafterTemplate template) {
        openEditor(template, resultPreview(template));
    }

    void openEditor(CrafterTemplate template, ItemStack preview) {
        ItemStack resolvedPreview = preview.isEmpty() ? resultPreview(template) : preview;
        minecraft.gui.setScreen(new TemplateEditorScreen(this, crafterScreen, containerId, template, resolvedPreview));
    }

    ItemStack resultPreview(CrafterTemplate template) {
        if (template.recipeId().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack jeiOutput = SmartCrafterJeiCompat.getCraftingOutput(template.recipeId().get());
        if (!jeiOutput.isEmpty()) {
            return jeiOutput;
        }
        return recipes.stream()
                .filter(choice -> choice.recipeId().equals(template.recipeId().get()))
                .map(choice -> choice.icon().copy())
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        if (searchField != null) {
            searchText = searchField.getValue();
        }
        recipeButtons.clear();
        previousButton = null;
        nextButton = null;
        int panelLeft = (width - PANEL_WIDTH) / 2;
        int panelTop = (height - PANEL_HEIGHT) / 2;

        addRenderableWidget(Button.builder(Component.literal(UiLanguage.toggleLabel()), button -> {
                    UiLanguage.toggle();
                    clearWidgets();
                    init();
                })
                .bounds(panelLeft + 232, panelTop + 6, 52, 20)
                .tooltip(Tooltip.create(Component.literal(UiLanguage.text("切换界面语言", "Switch interface language"))))
                .build());

        searchField = new EditBox(font, panelLeft + 18, panelTop + 34, 220, 20,
                Component.literal(UiLanguage.text("搜索配方", "Search recipes")));
        searchField.setHint(Component.literal(UiLanguage.text("输入物品名称或配方 ID…", "Search item name or recipe ID...")));
        searchField.setMaxLength(128);
        searchField.setValue(searchText);
        searchField.setResponder(ignored -> {
            searchText = ignored;
            page = 0;
            refreshRecipeRows();
        });
        addRenderableWidget(searchField);
        addRenderableWidget(Button.builder(Component.literal(UiLanguage.text("编辑", "Edit")), button ->
                        openEditor(currentTemplate))
                .bounds(panelLeft + 244, panelTop + 34, 38, 20)
                .tooltip(Tooltip.create(Component.literal(UiLanguage.text(
                        "编辑这台 Crafter 当前保存的模板", "Edit this Crafter's saved template"))))
                .build());

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int y = panelTop + 64 + row * 23;
            Button recipeButton = Button.builder(Component.empty(), button -> {
                        int recipeIndex = page * ROWS_PER_PAGE + recipeButtons.indexOf(button);
                        List<RecipeChoice> filtered = filteredRecipes();
                        if (recipeIndex >= 0 && recipeIndex < filtered.size()) {
                            SmartCrafterClient.selectRecipe(containerId, filtered.get(recipeIndex).recipeId());
                        }
                    })
                    .bounds(panelLeft + 40, y, 242, 20)
                    .build();
            recipeButtons.add(recipeButton);
            addRenderableWidget(recipeButton);
        }

        previousButton = Button.builder(Component.literal(UiLanguage.text("上一页", "Previous")), button -> {
                    page = Math.max(0, page - 1);
                    refreshRecipeRows();
                })
                .bounds(panelLeft + 18, panelTop + 238, 84, 20)
                .build();
        addRenderableWidget(previousButton);
        nextButton = Button.builder(Component.literal(UiLanguage.text("下一页", "Next")), button -> {
                    if ((page + 1) * ROWS_PER_PAGE < filteredRecipes().size()) {
                        page++;
                        refreshRecipeRows();
                    }
                })
                .bounds(panelLeft + 198, panelTop + 238, 84, 20)
                .build();
        addRenderableWidget(nextButton);
        refreshRecipeRows();
        setInitialFocus(searchField);
    }

    private List<RecipeChoice> filteredRecipes() {
        if (searchField == null || searchField.getValue().isBlank()) {
            return recipes;
        }
        String query = searchField.getValue().toLowerCase(Locale.ROOT).trim();
        return recipes.stream()
                .filter(choice -> choice.name().getString().toLowerCase(Locale.ROOT).contains(query)
                        || choice.recipeId().toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void refreshRecipeRows() {
        if (searchField == null || recipeButtons.isEmpty()) {
            return;
        }
        List<RecipeChoice> filtered = filteredRecipes();
        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < recipeButtons.size(); row++) {
            Button button = recipeButtons.get(row);
            int recipeIndex = start + row;
            boolean visible = catalogReceived && recipeIndex < filtered.size();
            button.visible = visible;
            button.active = visible;
            if (visible) {
                RecipeChoice choice = filtered.get(recipeIndex);
                button.setMessage(choice.name());
                button.setTooltip(Tooltip.create(Component.literal(choice.recipeId().toString())));
            }
        }
        if (previousButton != null) {
            previousButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = (page + 1) * ROWS_PER_PAGE < filtered.size();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181818);
        graphics.outline(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF777777);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.centeredText(font, Component.literal(UiLanguage.text("合成器配方", "Crafter Recipes")), width / 2, top + 12, 0xFFFFFFFF);
        if (!catalogReceived) {
            graphics.centeredText(font, Component.literal(UiLanguage.text("正在读取配方…", "Loading recipes...")), width / 2, top + 150, 0xFFAAAAAA);
        } else if (filteredRecipes().isEmpty()) {
            String message = searchField.getValue().isBlank()
                    ? UiLanguage.text("当前没有可用的合成配方", "No crafting recipes are available")
                    : UiLanguage.text("没有匹配的配方", "No matching recipes");
            graphics.centeredText(font, Component.literal(message), width / 2, top + 145, 0xFFAAAAAA);
        } else {
            List<RecipeChoice> filtered = filteredRecipes();
            int from = page * ROWS_PER_PAGE;
            int to = Math.min(from + ROWS_PER_PAGE, filtered.size());
            for (int row = from; row < to; row++) {
                RecipeChoice choice = filtered.get(row);
                int y = top + 65 + (row - from) * 23;
                graphics.item(choice.icon(), left + 20, y + 1);
            }
            graphics.centeredText(font, Component.literal((page + 1) + " / " + Math.max(1, (filtered.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)), width / 2, top + 243, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(crafterScreen);
    }
}
