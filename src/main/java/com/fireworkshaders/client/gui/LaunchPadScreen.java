package com.fireworkshaders.client.gui;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.client.state.ClientState;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.menu.LaunchPadMenu;
import com.fireworkshaders.common.net.C2SPadProgramPacket;
import com.fireworkshaders.common.net.FSAction;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.common.show.ShowProgram;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * The show programmer: nine tubes on the left, the cue timeline on the right.
 *
 * <p>The timeline is the product. Rockets go in the tubes; each cue binds a tube to an absolute
 * offset from T-0; the lead-in sets how much warning the audience gets. Press GO and the pad counts
 * down out loud (the pad plays a rising ping at T-60, T-40, T-20 and T-10) and then fires the
 * timeline. Because cues are absolute, two pads started by the same igniter press stay locked to
 * each other for the whole show.
 */
public class LaunchPadScreen extends AbstractContainerScreen<LaunchPadMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(FireworkShaders.MOD_ID,
            "textures/gui/launch_pad.png");

    private static final int RIGHT_X = 176;
    private static final int CUE_ROWS = 11;

    public LaunchPadScreen(LaunchPadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 220;
        this.inventoryLabelY = 108;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        // Cue programming
        button(RIGHT_X, 22, 34, 14, "+ SAME", FSAction.PAD_ADD_CUE, 0,
                "gui." + FireworkShaders.MOD_ID + ".cue_add_tip");
        button(RIGHT_X + 38, 22, 34, 14, "DELETE", FSAction.PAD_REMOVE_CUE, 0,
                "gui." + FireworkShaders.MOD_ID + ".cue_delete_tip");
        button(RIGHT_X, 40, 16, 14, "<", FSAction.PAD_PREV_CUE, 0, null);
        button(RIGHT_X + 18, 40, 16, 14, ">", FSAction.PAD_NEXT_CUE, 0, null);
        button(RIGHT_X + 36, 40, 18, 14, "-1s", FSAction.PAD_CUE_EARLIER, 20, null);
        button(RIGHT_X + 56, 40, 18, 14, "+1s", FSAction.PAD_CUE_LATER, 20, null);
        button(RIGHT_X, 58, 74, 14, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".cue_toggle").getString(), FSAction.PAD_CUE_TOGGLE, 0, null);
        button(RIGHT_X, 76, 34, 14, "DEMO", FSAction.PAD_LOAD_DEMO, 0, null);
        button(RIGHT_X + 38, 76, 36, 14, "CLEAR", FSAction.PAD_CLEAR, 0, null);
        button(RIGHT_X, 94, 34, 14, "LEAD -", FSAction.PAD_LEAD_IN_DOWN, 0, null);
        button(RIGHT_X + 38, 94, 36, 14, "LEAD +", FSAction.PAD_LEAD_IN_UP, 0, null);

        // Show control
        button(RIGHT_X, 114, 74, 16, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".arm").getString(), FSAction.PAD_ARM, 0, null);
        button(RIGHT_X, 134, 74, 16, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".safe").getString(), FSAction.PAD_SAFE, 0, null);
        button(RIGHT_X, 156, 74, 22, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".go").getString(), FSAction.PAD_GO, 0, null);
    }

    private void button(int x, int y, int width, int height, String label, int action, int value,
                        String tooltipKey) {
        Button.Builder builder = Button.builder(Component.literal(label), button -> send(action, value))
                .bounds(this.leftPos + x, this.topPos + y, width, height);
        if (tooltipKey != null) {
            builder.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        addRenderableWidget(builder.build());
    }

    private void send(int action, int value) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        // The pad menu is not a container the player interacts with per-slot, so a small packet is
        // clearer than overloading the container-button channel with a payload.
        FSNetwork.sendToServer(new C2SPadProgramPacket(this.menu.getPos(), action, value));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE8E8F0, false);
        ClientState.Pad pad = ClientState.Pads.get(this.menu.getPos());

        int textX = 80;
        int y = 24;
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".timeline").getString(), textX, y, 0x9AA1B0, false);
        y += 12;

        if (pad == null) {
            graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                    + ".syncing").getString(), textX, y, 0x808898, false);
            return;
        }

        ShowProgram program = pad.program();
        List<ShowProgram.Cue> timeline = program.timeline();
        int selected = pad.selectedCue();
        for (int i = 0; i < timeline.size() && i < CUE_ROWS; i++) {
            ShowProgram.Cue cue = timeline.get(i);
            RocketDesign design = pad.rocket(cue.slot());
            String name = design == null ? Component.translatable("gui." + FireworkShaders.MOD_ID
                    + ".empty_tube").getString() : design.displayName().getString();
            if (name.length() > 14) {
                name = name.substring(0, 13) + "\u2026";
            }
            String line = String.format(Locale.ROOT, "%s  T%03d  %s%s",
                    cue.enabled() ? "\u25cf" : "\u25cb", cue.tick(), tube(cue.slot()), name);
            boolean isSelected = i == selected;
            int colour = cue.enabled() ? (isSelected ? 0xFFFFFF : 0xC8CEDC) : 0x707880;
            graphics.drawString(this.font, line, textX, y + i * 10, colour, false);
            if (isSelected) {
                graphics.renderOutline(textX - 2, y + i * 10 - 1, 88, 9, 0x60FFE9B0);
            }
        }

        // State summary
        int stateY = 172;
        Component state = Component.translatable("gui." + FireworkShaders.MOD_ID + ".pad_state",
                Component.translatable("padstate." + FireworkShaders.MOD_ID + "."
                        + pad.state().name().toLowerCase(Locale.ROOT)));
        graphics.drawString(this.font, state, textX, stateY, stateColour(pad.state()), false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                        + ".pad_loaded", pad.rockets().stream().filter(java.util.Objects::nonNull).count())
                .getString(), textX, stateY + 12, 0x9AA1B0, false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".pad_lead", String.format(Locale.ROOT, "%.1f", program.leadInTicks() / 20.0F))
                .getString(), textX, stateY + 24, 0x9AA1B0, false);
        if (pad.ticksToStart() > 0) {
            graphics.drawString(this.font, String.format(Locale.ROOT, "T-%04d", pad.ticksToStart()),
                    textX, stateY + 36, 0xFFC24D, false);
        }
    }

    private static String tube(int slot) {
        return "[" + (slot + 1) + "]";
    }

    private static int stateColour(LaunchPadBlockEntity.PadState state) {
        return switch (state) {
            case IDLE -> 0x9AA1B0;
            case ARMED -> 0xFFC24D;
            case RUNNING -> 0x66FF8C;
            case SAFE -> 0xFF8A7A;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // A dim warning banner while armed: screens are where accidents happen.
        ClientState.Pad pad = ClientState.Pads.get(this.menu.getPos());
        if (pad != null && pad.state() == LaunchPadBlockEntity.PadState.ARMED) {
            graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                    + ".armed_warning").withStyle(ChatFormatting.GOLD), this.leftPos + 24,
                    this.topPos + 4, 0xFFC24D, false);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
