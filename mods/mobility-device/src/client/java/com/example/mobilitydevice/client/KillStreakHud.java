package com.example.mobilitydevice.client;

import com.example.mobilitydevice.ability.KillStreak;
import com.example.mobilitydevice.item.WaistJetItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class KillStreakHud {
    private static final Identifier BAR_BACKGROUND = Identifier.withDefaultNamespace(
            "hud/experience_bar_background");
    private static final Identifier BAR_PROGRESS = Identifier.withDefaultNamespace(
            "hud/experience_bar_progress");
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    private KillStreakHud() {}

    public static boolean shouldShow() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null && !client.player.isSpectator()
                && WaistJetItems.isEquipped(client.player)
                && KillStreak.count(client.player) >= 3
                && KillStreak.expiresAt(client.player) > client.level.getGameTime();
    }

    public static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldShow()) return;
        double remaining = Math.max(0.0, KillStreak.expiresAt(client.player)
                - client.level.getGameTime() - deltaTracker.getGameTimeDeltaPartialTick(true));
        double fraction = Math.clamp(remaining / KillStreak.WINDOW_TICKS, 0.0, 1.0);
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() - 29;

        // Vanilla bar pixels and dimensions, tinted dark red. The lit section retreats
        // from both outer ends to the middle across the seven-second window.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND,
                x, y, BAR_WIDTH, BAR_HEIGHT, 0xFFFF5555);
        graphics.fill(RenderPipelines.GUI, x + 1, y + 1, x + BAR_WIDTH - 1, y + 4, 0x665B0909);
        int retreat = (int) Math.floor((1.0 - fraction) * BAR_WIDTH / 2.0);
        int litWidth = BAR_WIDTH - retreat * 2;
        if (litWidth > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_PROGRESS,
                    BAR_WIDTH, BAR_HEIGHT, retreat, 0, x + retreat, y,
                    litWidth, BAR_HEIGHT, 0xFFFF2424);
            graphics.fill(RenderPipelines.GUI, x + Math.max(1, retreat), y + 1,
                    x + Math.min(BAR_WIDTH - 1, retreat + litWidth), y + 4, 0x55FF1010);
        }

        String seconds = String.format(Locale.ROOT, "%.1f", remaining / 20.0);
        int textX = (graphics.guiWidth() - client.font.width(seconds)) / 2;
        int textY = graphics.guiHeight() - 35;
        graphics.text(client.font, seconds, textX + 1, textY, 0xFF000000, false);
        graphics.text(client.font, seconds, textX - 1, textY, 0xFF000000, false);
        graphics.text(client.font, seconds, textX, textY + 1, 0xFF000000, false);
        graphics.text(client.font, seconds, textX, textY - 1, 0xFF000000, false);
        graphics.text(client.font, seconds, textX, textY, 0xFFFF4444, false);
    }
}
