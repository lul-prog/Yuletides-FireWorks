package com.fireworkshaders.client.gui;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.menu.PyrotechnicWorkstationMenu;
import com.fireworkshaders.common.menu.WorkstationEditor;
import com.fireworkshaders.common.net.C2SWorkstationEditPacket;
import com.fireworkshaders.common.net.FSAction;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.client.state.ClientState;
import com.fireworkshaders.pyro.Chemical;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.pyro.ShellPresets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * The Pyrotechnic Workstation.
 *
 * <p>Layout, left to right, in the order a shell is actually built:
 * <ol>
 *   <li><b>the hopper</b> — reagent, propellant and paper slots,</li>
 *   <li><b>the module board</b> — fourteen modifier toggles, each a real change to the burst,</li>
 *   <li><b>the design bench</b> — shape, preset, caliber, fuse, colours, memory slots, and the
 *       live burst preview,</li>
 *   <li><b>the press</b> — assemble, which pays for the design and produces the rocket.</li>
 * </ol>
 *
 * <p>Everything here is server-authoritative: this screen sends intents, never designs. The design
 * it draws is the mirror the server pushed to it, so the preview can never lie about what will be
 * built.
 */
public class PyrotechnicWorkstationScreen extends AbstractContainerScreen<PyrotechnicWorkstationMenu> {

    private static final net.minecraft.resources.ResourceLocation TEXTURE =
            new net.minecraft.resources.ResourceLocation(FireworkShaders.MOD_ID,
                    "textures/gui/pyrotechnic_workstation.png");

    private static final int PANEL_X = 178;
    private static final int MODULE_X0 = 180;
    private static final int MODULE_X1 = 214;
    private static final int MODULE_Y0 = 28;
    private static final int MODULE_STEP = 16;
    private static final int MODULE_W = 32;
    private static final int MODULE_H = 14;
    private static final int CONTROL_RIGHT = 250;
    private static final int PREVIEW_X = 252;
    private static final int PREVIEW_Y = 18;
    private static final int PREVIEW_SIZE = 80;
    private static final int BENCH_W = 78;

    private EditBox nameField;
    private String lastSentName = "";

    public PyrotechnicWorkstationScreen(PyrotechnicWorkstationMenu menu, Inventory inventory,
                                        Component title) {
        super(menu, inventory, title);
        this.imageWidth = 340;
        this.imageHeight = 236;
        this.inventoryLabelY = 128;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.nameField = null;

        // ---- module board --------------------------------------------------------------------
        EffectModule[] modules = EffectModule.values();
        for (int i = 0; i < modules.length; i++) {
            EffectModule module = modules[i];
            int column = i % 2;
            int row = i / 2;
            addRenderableWidget(Button.builder(Component.literal(abbreviate(module)), button -> {
                        // SHELL_OF_SHELLS is special: toggling it also loads a payload child, which
                        // the editor handles (see WorkstationEditor).
                        send(FSAction.WS_MODULE_BASE + module.ordinal());
                    })
                    .bounds(this.leftPos + (column == 0 ? MODULE_X0 : MODULE_X1),
                            this.topPos + MODULE_Y0 + row * MODULE_STEP, MODULE_W, MODULE_H)
                    .tooltip(Tooltip.create(Component.literal(module.tooltip())))
                    .build());
        }

        // ---- bench: shape / preset / caliber / fuse ------------------------------------------
        addButton(CONTROL_RIGHT, 102, 18, 14, "<", FSAction.WS_SHAPE_PREV);
        addButton(CONTROL_RIGHT + 62, 102, 18, 14, ">", FSAction.WS_SHAPE_NEXT);
        addButton(CONTROL_RIGHT, 118, 18, 14, "<", FSAction.WS_PRESET_PREV);
        addButton(CONTROL_RIGHT + 62, 118, 18, 14, ">", FSAction.WS_PRESET_NEXT);
        addButton(CONTROL_RIGHT, 134, 18, 14, "-", FSAction.WS_CALIBER_DOWN);
        addButton(CONTROL_RIGHT + 62, 134, 18, 14, "+", FSAction.WS_CALIBER_UP);
        addButton(CONTROL_RIGHT, 150, 18, 14, "-", FSAction.WS_FUSE_DOWN);
        addButton(CONTROL_RIGHT + 62, 150, 18, 14, "+", FSAction.WS_FUSE_UP);

        // ---- colours ---------------------------------------------------------------------------
        Chemical[] palette = Chemical.colourPalette();
        for (int i = 0; i < Math.min(4, palette.length); i++) {
            Chemical chemical = palette[i];
            addRenderableWidget(Button.builder(Component.literal(abbrevChemical(chemical)), button ->
                            send(FSAction.WS_COLOUR_BASE + chemical.ordinal()))
                    .bounds(this.leftPos + CONTROL_RIGHT + i * 19, this.topPos + 166, 18, 16)
                    .tooltip(Tooltip.create(chemical.nameComponent()
                            .copy().append(" \u2014 ").append(chemical.description())))
                    .build());
        }
        addButton(CONTROL_RIGHT, 186, 42, 14, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".clear_colours").getString(), FSAction.WS_COLOUR_CLEAR);

        // ---- the press ---------------------------------------------------------------------------
        addRenderableWidget(Button.builder(Component.translatable("gui." + FireworkShaders.MOD_ID
                        + ".assemble"), button -> send(FSAction.WS_ASSEMBLE))
                .bounds(this.leftPos + CONTROL_RIGHT + 46, this.topPos + 184, 78, 16)
                .tooltip(Tooltip.create(Component.translatable("gui." + FireworkShaders.MOD_ID
                        + ".assemble_tip")))
                .build());

        // ---- memory ------------------------------------------------------------------------------
        addButton(PANEL_X, 178, 16, 14, "<", FSAction.WS_MEMORY_PREV);
        addButton(PANEL_X + 18, 178, 16, 14, ">", FSAction.WS_MEMORY_NEXT);
        addButton(PANEL_X, 194, 34, 14, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".save").getString(), FSAction.WS_SAVE_BASE);
        addButton(PANEL_X + 36, 194, 40, 14, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".load").getString(), FSAction.WS_LOAD_BASE);
        addButton(PANEL_X, 210, 76, 14, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".payload_saved").getString(), FSAction.WS_PAYLOAD_FROM_SAVED);

        // ---- name --------------------------------------------------------------------------------
        this.nameField = new EditBox(this.font, this.leftPos + PANEL_X, this.topPos + 158, 76, 14,
                Component.translatable("gui." + FireworkShaders.MOD_ID + ".name"));
        this.nameField.setMaxLength(RocketDesign.MAX_NAME_LENGTH);
        this.nameField.setBordered(true);
        ClientState.Workstation workstation = ClientState.Workstations.get(this.menu.getPos());
        if (workstation != null) {
            this.nameField.setValue(workstation.design().rawName());
            this.lastSentName = workstation.design().rawName();
        }
        addRenderableWidget(this.nameField);
    }

    private void addButton(int x, int y, int width, int height, String label, int action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> send(action))
                .bounds(this.leftPos + x, this.topPos + y, width, height)
                .build());
    }

    private void send(int action) {
        if (this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, action);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField != null && this.nameField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                this.nameField.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                commitName();
                this.nameField.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitName() {
        if (this.nameField == null) {
            return;
        }
        String value = this.nameField.getValue();
        if (value.equals(this.lastSentName)) {
            return;
        }
        this.lastSentName = value;
        FSNetwork.sendToServer(new C2SWorkstationEditPacket(this.menu.getPos(),
                WorkstationEditor.ACTION_SET_NAME, value));
    }

    @Override
    public void removed() {
        commitName();
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RocketDesign design = currentDesign();
        ClientState.Workstation workstation = ClientState.Workstations.get(this.menu.getPos());
        int memory = workstation == null ? 0 : workstation.selectedMemory();

        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE8E8F0, false);

        final int label = 0x9AA1B0;
        final int value = 0xF0F2F8;
        final int x = CONTROL_RIGHT;
        final int textX = x + 22;

        graphics.drawString(this.font, shortName(design.shape().id()), textX, 106, value, false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".shape_label").getString(), x, 92, label, false);
        graphics.drawString(this.font, presetName(workstation), textX, 122, value, false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".preset_label").getString(), x, 108, label, false);
        graphics.drawString(this.font, String.format(Locale.ROOT, "%.2f", design.caliber()), textX, 138,
                value, false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".caliber_label").getString(), x, 124, label, false);
        graphics.drawString(this.font, String.format(Locale.ROOT, "%.1fs", design.fuse() / 20.0F),
                textX, 154, value, false);
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".fuse_label").getString(), x, 140, label, false);

        // palette status
        StringBuilder colours = new StringBuilder();
        for (Chemical chemical : design.palette()) {
            if (colours.length() > 0) {
                colours.append(' ');
            }
            colours.append(abbrevChemical(chemical));
        }
        graphics.drawString(this.font, colours.toString(), x, 158 + 8, value, false);

        // memory index
        graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                + ".memory", memory + 1).getString(), PANEL_X + 38, 182, label, false);

        // the shopping list — this is the whole reason the machine exists
        List<Component> missing = this.menu.getWorkstation() == null ? List.of()
                : this.menu.getWorkstation().missingIngredients();
        if (missing.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                            + ".ready").getString(), PANEL_X, 228, 0x66FF8C, false);
        } else {
            graphics.drawString(this.font, missing.get(0).getString(), PANEL_X, 228, 0xFF8A7A, false);
            if (missing.size() > 1) {
                graphics.drawString(this.font, Component.translatable("gui." + FireworkShaders.MOD_ID
                        + ".and_more", missing.size() - 1).getString(), PANEL_X + 92, 228, 0x9AA1B0,
                        false);
            }
        }
        graphics.drawString(this.font, WorkstationEditor.costComponent(design).getString(), PANEL_X,
                220, 0xC8CEDC, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        RocketDesign design = currentDesign();
        BurstPreview.render(graphics, design,
                this.leftPos + PREVIEW_X, this.topPos + PREVIEW_Y, PREVIEW_SIZE,
                (this.minecraft == null ? 0.0F : this.minecraft.getFrameTime())
                        + (this.minecraft == null || this.minecraft.level == null ? 0.0F
                        : this.minecraft.level.getGameTime() / 20.0F));
        // The preview frame is drawn last so the stars stay inside it.
        graphics.renderOutline(this.leftPos + PREVIEW_X - 1, this.topPos + PREVIEW_Y - 1,
                PREVIEW_SIZE + 2, PREVIEW_SIZE + 2, 0xFF2A2E3A);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** The design the server says we are editing, or a safe default before the first sync. */
    private RocketDesign currentDesign() {
        ClientState.Workstation workstation = ClientState.Workstations.get(this.menu.getPos());
        return workstation == null ? RocketDesign.defaultDesign() : workstation.design();
    }

    private String presetName(ClientState.Workstation workstation) {
        if (workstation == null || workstation.flash() < 0) {
            return Component.translatable("gui." + FireworkShaders.MOD_ID + ".custom").getString();
        }
        return ShellPresets.byIndex(workstation.flash()).name().getString();
    }

    private String shortName(String id) {
        return id.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String abbreviate(EffectModule module) {
        String id = module.id();
        if (id.equals("shell_of_shells")) {
            return "SoS";
        }
        return id.substring(0, Math.min(3, id.length())).toUpperCase(Locale.ROOT);
    }

    private static String abbrevChemical(Chemical chemical) {
        String name = chemical.name();
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || part.equals("SALT")) {
                continue;
            }
            sb.append(part.charAt(0));
        }
        return sb.length() == 0 ? "?" : sb.substring(0, Math.min(3, sb.length()));
    }
}
