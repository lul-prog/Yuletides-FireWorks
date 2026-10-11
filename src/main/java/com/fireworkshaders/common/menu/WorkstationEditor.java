package com.fireworkshaders.common.menu;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.PyrotechnicWorkstationBlockEntity;
import com.fireworkshaders.common.net.FSAction;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.common.net.S2CWorkstationSyncPacket;
import com.fireworkshaders.pyro.Chemical;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.PyroMath;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.pyro.ShellPresets;
import com.fireworkshaders.pyro.ShellShape;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The one place a {@link RocketDesign} is ever mutated on the server.
 *
 * <p>Everything the workstation GUI can do is a case in {@link #apply}. Keeping it in a single
 * switch is not laziness: it is the audit surface. If you want to know what a player can do to a
 * design, you read one method, and every action is bounded (palette size, caliber range, fuse
 * range, module set) by the design's own builder.
 */
public final class WorkstationEditor {

    /** Name edits need a string, so they get the one packet-based action. */
    public static final int ACTION_SET_NAME = FSAction.WS_SET_NAME;

    private WorkstationEditor() {}

    @Nullable
    private static PyrotechnicWorkstationBlockEntity workstation(ServerPlayer player, BlockPos pos) {
        if (!player.level().hasChunkAt(pos)) {
            return null;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return null;   // 8 blocks: the same reach the vanilla container check uses
        }
        return player.level().getBlockEntity(pos) instanceof PyrotechnicWorkstationBlockEntity ws
                ? ws : null;
    }

    public static void syncTo(ServerPlayer player, PyrotechnicWorkstationBlockEntity workstation) {
        FSNetwork.sendTo(new S2CWorkstationSyncPacket(workstation.getBlockPos(),
                workstation.getDesign().toTag(), savedTag(workstation), workstation.getPresetIndex()),
                player);
    }

    private static CompoundTag savedTag(PyrotechnicWorkstationBlockEntity workstation) {
        CompoundTag tag = new CompoundTag();
        List<RocketDesign> saved = workstation.savedDesigns();
        for (int i = 0; i < saved.size(); i++) {
            RocketDesign design = saved.get(i);
            if (design != null) {
                tag.put("s" + i, design.toTag());
            }
        }
        return tag;
    }

    // ------------------------------------------------------------------ actions

    public static void apply(ServerPlayer player, BlockPos pos, int action, @Nullable String text) {
        PyrotechnicWorkstationBlockEntity workstation = workstation(player, pos);
        if (workstation == null) {
            return;
        }
        RocketDesign design = workstation.getDesign();
        RocketDesign.Builder builder = design.toBuilder();
        boolean changed = true;

        if (action == ACTION_SET_NAME) {
            builder.name(text == null ? "" : text);
        } else if (action == FSAction.WS_ASSEMBLE) {
            assemble(player, workstation);
            syncTo(player, workstation);
            return;
        } else if (action == FSAction.WS_COLOUR_CLEAR) {
            builder.clearColours();
        } else if (action == FSAction.WS_SHAPE_NEXT) {
            builder.shape(design.shape().next());
        } else if (action == FSAction.WS_SHAPE_PREV) {
            builder.shape(design.shape().previous());
        } else if (action == FSAction.WS_CALIBER_UP) {
            builder.caliber(design.caliber() + 0.2F);
        } else if (action == FSAction.WS_CALIBER_DOWN) {
            builder.caliber(design.caliber() - 0.2F);
        } else if (action == FSAction.WS_FUSE_UP) {
            builder.fuse(design.fuse() + 5);
        } else if (action == FSAction.WS_FUSE_DOWN) {
            builder.fuse(design.fuse() - 5);
        } else if (action == FSAction.WS_PRESET_NEXT || action == FSAction.WS_PRESET_PREV) {
            int index = workstation.getPresetIndex();
            index += action == FSAction.WS_PRESET_NEXT ? 1 : -1;
            ShellPresets.Preset preset = ShellPresets.byIndex(index);
            workstation.setPresetIndex(ShellPresets.indexOf(preset.id()));
            builder = preset.design().toBuilder();
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".preset_loaded", preset.name()).withStyle(ChatFormatting.AQUA), true);
        } else if (action == FSAction.WS_MEMORY_NEXT || action == FSAction.WS_MEMORY_PREV) {
            workstation.setSelectedMemory(workstation.getSelectedMemory()
                    + (action == FSAction.WS_MEMORY_NEXT ? 1 : -1));
            changed = false;
        } else if (action == FSAction.WS_PAYLOAD_CLEAR) {
            builder.payload(null);
        } else if (action == FSAction.WS_PAYLOAD_FROM_SAVED) {
            RocketDesign saved = workstation.getSaved(workstation.getSelectedMemory());
            builder.payload(saved);
        } else if (action == FSAction.WS_PAYLOAD_TOGGLE_SHELL) {
            builder.toggle(EffectModule.SHELL_OF_SHELLS);
            if (design.payload() == null) {
                // Turning multi-break on with nothing loaded would produce a shell that breaks and
                // drops nothing: give it a sensible child instead of a dud.
                builder.payload(RocketDesign.builder()
                        .palette(design.palette())
                        .shape(ShellShape.PEONY)
                        .caliber(Math.max(0.8F, design.caliber() * 0.55F))
                        .modules(java.util.EnumSet.of(EffectModule.TRAIL))
                        .build());
            }
        } else if (action >= FSAction.WS_SAVE_BASE && action < FSAction.WS_SAVE_BASE + 9) {
            int slot = action - FSAction.WS_SAVE_BASE;
            workstation.setSaved(slot, design);
            workstation.setSelectedMemory(slot);
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".design_saved", slot + 1).withStyle(ChatFormatting.GREEN), true);
            changed = false;
        } else if (action >= FSAction.WS_LOAD_BASE && action < FSAction.WS_LOAD_BASE + 9) {
            int slot = action - FSAction.WS_LOAD_BASE;
            RocketDesign saved = workstation.getSaved(slot);
            workstation.setSelectedMemory(slot);
            if (saved != null) {
                builder = saved.toBuilder();
            } else {
                changed = false;
            }
        } else if (action >= FSAction.WS_COLOUR_BASE
                && action < FSAction.WS_COLOUR_BASE + Chemical.values().length) {
            Chemical chemical = Chemical.byOrdinal(action - FSAction.WS_COLOUR_BASE);
            if (design.palette().contains(chemical)) {
                // Clicking a loaded colour removes it; the palette is ordered by use, so removing
                // the primary promotes the next colour — which is the intuitive behaviour.
                java.util.List<Chemical> remaining = new java.util.ArrayList<>(design.palette());
                remaining.remove(chemical);
                if (remaining.isEmpty()) {
                    remaining.add(Chemical.STRONTIUM_SALT);
                }
                builder.palette(remaining);
            } else {
                builder.addColour(chemical);
            }
        } else if (action >= FSAction.WS_MODULE_BASE
                && action < FSAction.WS_MODULE_BASE + EffectModule.values().length) {
            EffectModule module = EffectModule.values()[action - FSAction.WS_MODULE_BASE];
            if (module == EffectModule.SHELL_OF_SHELLS) {
                apply(player, pos, FSAction.WS_PAYLOAD_TOGGLE_SHELL, null);
                return;
            }
            builder.toggle(module);
        } else {
            changed = false;
        }

        if (changed) {
            workstation.setDesign(builder.build());
        }
        syncTo(player, workstation);
    }

    private static void assemble(ServerPlayer player, PyrotechnicWorkstationBlockEntity workstation) {
        List<Component> missing = workstation.missingIngredients();
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".missing_header").withStyle(ChatFormatting.RED), false);
            for (Component line : missing.subList(0, Math.min(4, missing.size()))) {
                player.displayClientMessage(Component.literal(" \u2022 ").append(line)
                        .withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }
        if (workstation.assemble(player)) {
            ItemStack built = workstation.getItem(PyrotechnicWorkstationBlockEntity.SLOT_OUTPUT);
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                            + ".assembled", built.getHoverName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** The shopping list shown in the GUI: what this design costs, in the player's own terms. */
    public static Component costComponent(RocketDesign design) {
        int grains = design.grainCost();
        int chemicals = 0;
        for (Chemical chemical : design.palette()) {
            chemicals += Math.max(1, Math.round(2.0F * design.caliber()));
        }
        return Component.translatable("gui." + FireworkShaders.MOD_ID + ".cost",
                grains, chemicals, 1 + design.modules().size() / 3,
                PyroMath.clampInt(design.modules().size(), 0, 99));
    }
}
