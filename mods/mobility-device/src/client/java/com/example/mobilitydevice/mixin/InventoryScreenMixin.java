package com.example.mobilitydevice.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    private static final Identifier INVENTORY_TEXTURE = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/gui/container/inventory.png");

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mobility$drawWaistSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        ScreenPositionAccessor screen = (ScreenPositionAccessor) this;
        int x = screen.mobility$getLeftPos() + 76;
        int y = screen.mobility$getTopPos() + 7;
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x, y, 7.0f, 83.0f, 18, 18, 256, 256);
    }
}
