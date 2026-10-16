package dev.smartcrafter.client;

import dev.smartcrafter.template.CrafterTemplate;
import dev.smartcrafter.template.TemplateSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class TemplateEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 318;
    private static final int SLOT_SIZE = 20;
    private static final int GRID_SPACING = 24;

    private final RecipeBrowserScreen browserScreen;
    private final CrafterScreen crafterScreen;
    private final int containerId;
    private final ItemStack resultPreview;
    private CrafterTemplate template;
    private ItemStack carriedStack = ItemStack.EMPTY;
    private boolean saving;
    private String message = "";
    private int dragButton = -1;
    private int lastDragSlot = -1;

    TemplateEditorScreen(RecipeBrowserScreen browserScreen, CrafterScreen crafterScreen, int containerId,
                         CrafterTemplate template, ItemStack resultPreview) {
        super(Component.literal(UiLanguage.text("合成模板编辑器", "Crafter Template Editor")));
        this.browserScreen = browserScreen;
        this.crafterScreen = crafterScreen;
        this.containerId = containerId;
        this.template = template;
        this.resultPreview = resultPreview.copy();
    }

    int containerId() {
        return containerId;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal(UiLanguage.text("取消", "Cancel")), button ->
                        minecraft.gui.setScreen(browserScreen))
                .bounds(left + 10, top + 281, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(UiLanguage.text("保存", "Save")), button -> save())
                .bounds(left + 234, top + 281, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(UiLanguage.text("重置", "Reset")), button -> reset())
                .bounds(left + 122, top + 281, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(UiLanguage.toggleLabel()), button -> {
                    UiLanguage.toggle();
                    clearWidgets();
                    init();
                })
                .bounds(left + 270, top + 6, 60, 20)
                .tooltip(Tooltip.create(Component.literal(UiLanguage.text("切换界面语言", "Switch interface language"))))
                .build());
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
        graphics.centeredText(font, Component.literal(UiLanguage.text("合成模板编辑器", "Crafter Template Editor")), width / 2, top + 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(UiLanguage.text(
                "拖动物品到输入槽；点击背包可放回光标物品。",
                "Drag items to an input; click inventory to return the cursor item.")), width / 2,
                top + 32, 0xFFCCCCCC);
        graphics.centeredText(font, Component.literal(UiLanguage.text(
                "背包左键拿整组、右键拿一半；槽位左键放整组、右键放 1 个。",
                "Inventory: left-click takes all, right-click takes half; inputs: left places all, right places 1.")), width / 2,
                top + 44, 0xFFCCCCCC);

        int gridLeft = left + 36;
        int gridTop = top + 67;
        graphics.centeredText(font, Component.literal(UiLanguage.text("输入", "Inputs")), gridLeft + 34, top + 57, 0xFFFFFFFF);
        for (int slotIndex = 0; slotIndex < CrafterTemplate.SLOT_COUNT; slotIndex++) {
            int x = gridLeft + (slotIndex % 3) * GRID_SPACING;
            int y = gridTop + (slotIndex / 3) * GRID_SPACING;
            drawTemplateSlot(graphics, x, y, template.slot(slotIndex), mouseX, mouseY);
        }

        graphics.centeredText(font, Component.literal("→"), left + 155, gridTop + GRID_SPACING, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(UiLanguage.text("成品", "Result")), left + 274, top + 57, 0xFFFFFFFF);
        int resultX = left + 264;
        int resultY = gridTop + GRID_SPACING;
        drawCell(graphics, resultX, resultY, mouseX, mouseY);
        if (!resultPreview.isEmpty()) {
            drawStack(graphics, resultPreview, resultX, resultY);
            if (isInside(mouseX, mouseY, resultX, resultY)) {
                graphics.setTooltipForNextFrame(font, resultPreview, mouseX, mouseY);
            }
        }

        graphics.centeredText(font, Component.literal(UiLanguage.text("背包物品", "Inventory")), width / 2, top + 151,
                0xFFFFFFFF);
        if (minecraft.player != null) {
            for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                int x = left + 60 + (inventorySlot % 9) * GRID_SPACING;
                int y = top + 168 + (inventorySlot / 9) * GRID_SPACING;
                drawCell(graphics, x, y, mouseX, mouseY);
                ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
                if (!stack.isEmpty()) {
                    drawStack(graphics, stack, x, y);
                    if (isInside(mouseX, mouseY, x, y)) {
                        graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                    }
                }
            }
        }

        if (!carriedStack.isEmpty()) {
            int cursorX = mouseX - 8;
            int cursorY = mouseY - 8;
            graphics.fakeItem(carriedStack, cursorX, cursorY);
            graphics.itemDecorations(font, carriedStack, cursorX, cursorY);
        }
        if (!message.isEmpty()) {
            graphics.centeredText(font, Component.literal(message), width / 2, top + 263, 0xFFFF5555);
        }
    }

    private void drawTemplateSlot(GuiGraphicsExtractor graphics, int x, int y, TemplateSlot slot, int mouseX, int mouseY) {
        drawCell(graphics, x, y, mouseX, mouseY);
        slot.displayStack().ifPresent(snapshot -> drawStack(graphics, snapshot.copy(), x, y));
        if (!slot.enabled()) {
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xAA222222);
            graphics.fill(x + 4, y + 9, x + SLOT_SIZE - 4, y + 11, 0xFFFF5555);
        }
        if (isInside(mouseX, mouseY, x, y)) {
            Component tooltip = Component.literal(slot.enabled()
                    ? UiLanguage.text("槽位已启用", "Slot enabled")
                    : UiLanguage.text("槽位已关闭", "Slot disabled"));
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private void drawStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.item(stack, x + 2, y + 2);
        graphics.itemDecorations(font, stack, x + 2, y + 2);
    }

    private void drawCell(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF202020);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF8B8B8B);
        if (isInside(mouseX, mouseY, x, y)) {
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x667FFFFF);
        }
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int slotIndex = templateSlotAt(mouseX, mouseY, left, top);
        if (slotIndex >= 0) {
            if (button == 2) {
                TemplateSlot slot = template.slot(slotIndex);
                template = template.withSlot(slotIndex, slot.withEnabled(!slot.enabled()));
            } else if (button == 0 || button == 1) {
                placeCarriedInSlot(slotIndex, button);
                dragButton = button;
                lastDragSlot = slotIndex;
            }
            return true;
        }

        int inventorySlot = inventorySlotAt(mouseX, mouseY, left, top);
        if (inventorySlot >= 0 && minecraft.player != null && (button == 0 || button == 1)) {
            if (!carriedStack.isEmpty()) {
                // The inventory is a source for template previews, not a live container. Its
                // original stack was never removed, so clicking it again returns the cursor copy.
                carriedStack = ItemStack.EMPTY;
                dragButton = -1;
                lastDragSlot = -1;
                return true;
            }
            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
                int amount = button == 0 ? stack.getCount() : (stack.getCount() + 1) / 2;
                carriedStack = stack.copyWithCount(amount);
                dragButton = button;
                lastDragSlot = -1;
                message = "";
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == dragButton && !carriedStack.isEmpty()) {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            int slotIndex = templateSlotAt(mouseX, mouseY, left, top);
            if (slotIndex >= 0 && slotIndex != lastDragSlot) {
                placeCarriedInSlot(slotIndex, button);
                lastDragSlot = slotIndex;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == dragButton) {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            int slotIndex = templateSlotAt(mouseX, mouseY, left, top);
            if (slotIndex >= 0 && slotIndex != lastDragSlot) {
                placeCarriedInSlot(slotIndex, button);
            }
            dragButton = -1;
            lastDragSlot = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private int templateSlotAt(double mouseX, double mouseY, int left, int top) {
        int gridLeft = left + 36;
        int gridTop = top + 67;
        int gridX = (int) mouseX - gridLeft;
        int gridY = (int) mouseY - gridTop;
        if (gridX < 0 || gridY < 0 || gridX >= 3 * GRID_SPACING || gridY >= 3 * GRID_SPACING) {
            return -1;
        }
        int column = gridX / GRID_SPACING;
        int row = gridY / GRID_SPACING;
        return gridX % GRID_SPACING < SLOT_SIZE && gridY % GRID_SPACING < SLOT_SIZE
                ? row * 3 + column
                : -1;
    }

    private int inventorySlotAt(double mouseX, double mouseY, int left, int top) {
        int inventoryX = (int) mouseX - (left + 60);
        int inventoryY = (int) mouseY - (top + 168);
        if (inventoryX < 0 || inventoryY < 0 || inventoryX >= 9 * GRID_SPACING || inventoryY >= 4 * GRID_SPACING) {
            return -1;
        }
        int column = inventoryX / GRID_SPACING;
        int row = inventoryY / GRID_SPACING;
        return inventoryX % GRID_SPACING < SLOT_SIZE && inventoryY % GRID_SPACING < SLOT_SIZE
                ? row * 9 + column
                : -1;
    }

    private void placeCarriedInSlot(int slotIndex, int button) {
        TemplateSlot slot = template.slot(slotIndex);
        if (button == 1) {
            if (carriedStack.isEmpty()) {
                ItemStack current = slot.displayStack().map(snapshot -> snapshot.copy()).orElse(ItemStack.EMPTY);
                if (!current.isEmpty()) {
                    int taken = (current.getCount() + 1) / 2;
                    carriedStack = current.copyWithCount(taken);
                    int remaining = current.getCount() - taken;
                    template = template.withSlot(slotIndex, remaining > 0
                            ? slot.withStack(current.copyWithCount(remaining))
                            : slot.clearStack());
                }
            } else {
                ItemStack current = slot.displayStack().map(snapshot -> snapshot.copy()).orElse(ItemStack.EMPTY);
                if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, carriedStack)
                        && current.getCount() < current.getMaxStackSize()) {
                    current.grow(1);
                    template = template.withSlot(slotIndex, slot.withStack(current));
                } else {
                    template = template.withSlot(slotIndex, slot.withStack(carriedStack.copyWithCount(1)));
                }
                carriedStack.shrink(1);
            }
            return;
        }

        if (carriedStack.isEmpty()) {
            ItemStack current = slot.displayStack().map(snapshot -> snapshot.copy()).orElse(ItemStack.EMPTY);
            if (!current.isEmpty()) {
                carriedStack = current;
                template = template.withSlot(slotIndex, slot.clearStack());
            }
            return;
        }
        template = template.withSlot(slotIndex, slot.withStack(carriedStack));
        carriedStack = ItemStack.EMPTY;
    }

    private void save() {
        if (!saving) {
            saving = true;
            SmartCrafterClient.saveTemplate(containerId, template);
            message = UiLanguage.text("正在保存…", "Saving...");
        }
    }

    private void reset() {
        if (!saving) {
            saving = true;
            SmartCrafterClient.resetTemplate(containerId);
            message = UiLanguage.text("正在重置…", "Resetting...");
        }
    }

    void onSaveResult(boolean accepted) {
        if (accepted) {
            minecraft.gui.setScreen(crafterScreen);
        } else {
            saving = false;
            message = UiLanguage.text("保存失败：请重新打开合成器后重试", "Save failed. Reopen the Crafter and try again.");
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(browserScreen);
    }
}
