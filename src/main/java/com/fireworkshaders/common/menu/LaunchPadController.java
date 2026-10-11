package com.fireworkshaders.common.menu;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.net.FSAction;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.common.net.S2CPadSyncPacket;
import com.fireworkshaders.common.show.ShowProgram;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Server-side show programmer and the pad state broadcaster. */
public final class LaunchPadController {

    private static final double SYNC_RANGE = 64.0;

    private LaunchPadController() {}

    @Nullable
    private static LaunchPadBlockEntity pad(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0 * 64.0) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof LaunchPadBlockEntity launchPad
                ? launchPad : null;
    }

    // ------------------------------------------------------------------ sync

    public static S2CPadSyncPacket packetFor(LaunchPadBlockEntity pad) {
        ListTag rockets = new ListTag();
        for (CompoundTag tag : pad.rocketTags()) {
            rockets.add(tag);
        }
        return new S2CPadSyncPacket(pad.getBlockPos(), pad.getProgram().toTag(), rockets,
                pad.getPadState().ordinal(), pad.ticksToStart(), pad.getSelectedCue());
    }

    public static void syncTo(ServerPlayer player, LaunchPadBlockEntity pad) {
        FSNetwork.sendTo(packetFor(pad), player);
    }

    public static void syncNearby(LaunchPadBlockEntity pad) {
        if (pad.getLevel() == null || pad.getLevel().isClientSide) {
            return;
        }
        S2CPadSyncPacket packet = packetFor(pad);
        Vec3 centre = Vec3.atCenterOf(pad.getBlockPos());
        for (ServerPlayer player : ((net.minecraft.server.level.ServerLevel) pad.getLevel()).players()) {
            if (player.distanceToSqr(centre) <= SYNC_RANGE * SYNC_RANGE) {
                FSNetwork.sendTo(packet, player);
            }
        }
    }

    /** Sends a state change to everyone in the area, including the player who caused it. */
    public static void announce(LaunchPadBlockEntity pad, Component message) {
        if (!(pad.getLevel() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pad.getBlockPos());
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(centre) <= SYNC_RANGE * SYNC_RANGE) {
                player.displayClientMessage(message, true);
                FSNetwork.sendTo(packetFor(pad), player);
            }
        }
    }

    // ------------------------------------------------------------------ actions

    public static void apply(ServerPlayer player, BlockPos pos, int action, int value) {
        LaunchPadBlockEntity pad = pad(player, pos);
        if (pad == null) {
            return;
        }
        ShowProgram program = pad.getProgram();
        switch (action) {
            case FSAction.PAD_ADD_CUE -> {
                int slot = Math.max(0, Math.min(LaunchPadBlockEntity.SLOTS - 1, value));
                int tick = program.durationTicks() + 40;
                if (program.add(slot, tick, true)) {
                    pad.setSelectedCue(program.cues().size() - 1);
                }
            }
            case FSAction.PAD_REMOVE_CUE -> program.remove(pad.getSelectedCue());
            case FSAction.PAD_NEXT_CUE -> pad.setSelectedCue(pad.getSelectedCue() + 1);
            case FSAction.PAD_PREV_CUE -> pad.setSelectedCue(pad.getSelectedCue() - 1);
            case FSAction.PAD_CUE_LATER -> program.nudgeTick(pad.getSelectedCue(), Math.max(1, value));
            case FSAction.PAD_CUE_EARLIER -> program.nudgeTick(pad.getSelectedCue(), -Math.max(1, value));
            case FSAction.PAD_CUE_TOGGLE -> program.toggle(pad.getSelectedCue());
            case FSAction.PAD_LOAD_DEMO -> {
                program.clear();
                ShowProgram demo = ShowProgram.demo();
                for (ShowProgram.Cue cue : demo.cues()) {
                    program.add(cue.slot(), cue.tick(), cue.enabled());
                }
                podMessage(player, "program_demo");
            }
            case FSAction.PAD_CLEAR -> {
                program.clear();
                podMessage(player, "program_cleared");
            }
            case FSAction.PAD_LEAD_IN_UP -> program.setLeadInTicks(program.leadInTicks() + 20);
            case FSAction.PAD_LEAD_IN_DOWN -> program.setLeadInTicks(program.leadInTicks() - 20);
            case FSAction.PAD_ARM -> {
                pad.arm();
                announce(pad, Component.translatable(msg("armed")).withStyle(ChatFormatting.GOLD));
                return;
            }
            case FSAction.PAD_SAFE -> {
                pad.safe();
                announce(pad, Component.translatable(msg("safe")).withStyle(ChatFormatting.RED));
                return;
            }
            case FSAction.PAD_GO -> {
                start(pad);
                return;
            }
            default -> {
                return;
            }
        }
        pad.setChanged();
        syncNearby(pad);
    }

    /** Shared by the programmer screen and the Remote Igniter so a show always starts identically. */
    public static void start(LaunchPadBlockEntity pad) {
        if (pad.loadedCount() == 0) {
            announce(pad, Component.translatable(msg("no_rockets")).withStyle(ChatFormatting.RED));
            return;
        }
        long t0 = pad.go();
        if (t0 < 0) {
            announce(pad, Component.translatable(msg("already_running")).withStyle(ChatFormatting.RED));
            return;
        }
        int ticks = pad.ticksToStart();
        announce(pad, Component.translatable(msg("go"), String.format("%.1f", ticks / 20.0F),
                pad.getProgram().enabledCount()).withStyle(ChatFormatting.GREEN));
    }

    private static void podMessage(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(msg(key)).withStyle(ChatFormatting.AQUA), true);
    }

    private static String msg(String suffix) {
        return "message." + FireworkShaders.MOD_ID + ".pad_" + suffix;
    }
}
