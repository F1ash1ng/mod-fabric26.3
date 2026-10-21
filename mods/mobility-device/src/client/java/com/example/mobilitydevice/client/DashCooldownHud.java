package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class DashCooldownHud {
    private static final Identifier OFFHAND_SLOT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");

    private DashCooldownHud() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                MobilityDeviceMod.id("dash_cooldown_hud"), DashCooldownHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isSpectator()) return;
        ItemStack device = WaistJetItems.getEquipped(client.player);
        if (!device.is(WaistJetItems.WAIST_JET)) return;

        // The extra slot sits beyond the vanilla hotbar and its possible offhand slot.
        int slotX = Math.min(graphics.guiWidth() - 29, graphics.guiWidth() / 2 + 123);
        int slotY = graphics.guiHeight() - 23;
        int iconX = slotX + 10;
        int iconY = slotY + 4;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND_SLOT, slotX, slotY, 29, 24);
        graphics.item(device, iconX, iconY);

        if (!MobilitySettings.enabled(client.player, MobilitySettings.DASH)) return;

        long cooldownEnd = client.player.getAttachedOrElse(DashAbility.COOLDOWN_END, 0L);
        double remainingTicks = Math.max(0.0, cooldownEnd - client.level.getGameTime()
                - deltaTracker.getGameTimeDeltaPartialTick(true));
        if (remainingTicks <= 0.0) return;

        // Match the vanilla item cooldown overlay, which retreats toward the bottom.
        double fraction = Math.clamp(remainingTicks / DashAbility.COOLDOWN_TICKS, 0.0, 1.0);
        int top = iconY + (int) Math.floor(16.0 * (1.0 - fraction));
        int bottom = top + (int) Math.ceil(16.0 * fraction);
        graphics.fill(RenderPipelines.GUI, iconX, top, iconX + 16, bottom, Integer.MAX_VALUE);

        String seconds = String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0);
        graphics.text(client.font, seconds, slotX + (29 - client.font.width(seconds)) / 2,
                slotY + 13, 0xFFFFFFFF);
    }
}
