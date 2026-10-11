package com.fireworkshaders.client.gui;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.client.state.ClientState;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The countdown. A fireworks display is a timed performance, and a pyro technician's single most
 * useful instrument is knowing <em>how long until the next cue</em>.
 *
 * <p>Drawn near the top of the screen for the closest armed pad within 48 blocks. It renders in
 * "show time" format (MM:SS.d) because that is how a show script is written, and it turns amber
 * under five seconds, which is when you want to already be looking up.
 */
public final class ShowHudOverlay {

    private ShowHudOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width,
                              int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        ClientState.Pad pad = ClientState.Pads.activePadWithin(origin, 48);
        if (pad == null) {
            return;
        }

        Font font = minecraft.font;
        int centre = width / 2;
        int y = 12;

        boolean running = pad.state() == LaunchPadBlockEntity.PadState.RUNNING;
        int ticksUntil = pad.ticksToStart();
        int elapsed = running ? Math.max(0, -ticksUntil) : 0;

        String headline;
        int colour;
        if (running && ticksUntil <= 0) {
            headline = String.format("%s  \u2022  %s / %s",
                    Component.translatable("hud." + FireworkShaders.MOD_ID + ".show_running").getString(),
                    format(elapsed), format(pad.program().durationTicks()));
            colour = 0x66FF8C;
        } else if (ticksUntil > 0) {
            headline = String.format("%s  %s",
                    Component.translatable("hud." + FireworkShaders.MOD_ID + ".t_minus").getString(),
                    format(ticksUntil));
            colour = ticksUntil < 100 ? 0xFFC24D : 0xFFE9B0;
        } else {
            headline = Component.translatable("hud." + FireworkShaders.MOD_ID + ".armed").getString();
            colour = 0xFFB347;
        }

        int textWidth = font.width(headline);
        graphics.fill(centre - textWidth / 2 - 4, y - 2, centre + textWidth / 2 + 4,
                y + font.lineHeight + 2, 0x66000000);
        graphics.drawString(font, headline, centre - textWidth / 2, y, colour, true);

        String detail = Component.translatable("hud." + FireworkShaders.MOD_ID + ".loaded",
                pad.program().cues().stream().filter(c -> c.enabled()).count(),
                pad.rockets().stream().filter(java.util.Objects::nonNull).count()).getString();
        graphics.drawString(font, detail, centre - font.width(detail) / 2, y + font.lineHeight + 4,
                ChatFormatting.GRAY.getColor() == null ? 0xAAAAAA : 0xAAAAAA, true);
    }

    private static String format(int ticks) {
        int total = Math.max(0, ticks);
        int seconds = total / 20;
        int tenths = (total % 20) / 2;
        return String.format("%02d:%02d.%d", seconds / 60, seconds % 60, tenths);
    }
}
