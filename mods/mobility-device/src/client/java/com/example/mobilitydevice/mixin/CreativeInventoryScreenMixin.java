package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.network.CreativeEquipPayload;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin {
    private static final int WAIST_SLOT = 46;
    private static final int WAIST_X = 126;
    private static final int WAIST_Y = 6;
    private static final Identifier INVENTORY_TEXTURE = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/gui/container/creative_inventory/tab_inventory.png");

    @Shadow private static CreativeModeTab selectedTab;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void mobility$positionWaistSlot(CreativeModeTab tab, CallbackInfo ci) {
        if (tab.getType() != CreativeModeTab.Type.INVENTORY) return;
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        if (screen.getMenu().slots.size() <= WAIST_SLOT) return;
        Slot slot = screen.getMenu().slots.get(WAIST_SLOT);
        ((SlotPositionAccessor) slot).mobility$setX(WAIST_X);
        ((SlotPositionAccessor) slot).mobility$setY(WAIST_Y);
    }

    @Inject(method = "slotClicked", at = @At("TAIL"))
    private void mobility$syncCreativeWaistSlot(Slot slot, int slotId, int button,
                                                 ContainerInput action, CallbackInfo ci) {
        if (slot == null || slot.getContainerSlot() != WAIST_SLOT || selectedTab == null
                || selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientPlayNetworking.send(new CreativeEquipPayload(WaistJetItems.getEquipped(client.player).copy()));
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mobility$drawWaistSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;
        ScreenPositionAccessor screen = (ScreenPositionAccessor) this;
        int x = screen.mobility$getLeftPos() + WAIST_X - 1;
        int y = screen.mobility$getTopPos() + WAIST_Y - 1;
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x, y, 8.0f, 53.0f, 18, 18, 256, 256);
    }
}
