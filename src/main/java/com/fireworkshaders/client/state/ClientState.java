package com.fireworkshaders.client.state;

import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.show.ShowProgram;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client mirrors of the two server-owned pieces of state. Screens and HUD overlays read from here
 * and never from the network directly, which keeps the "what is on screen" question answerable from
 * one place and makes it trivial to clear everything on a disconnect.
 *
 * <p>These maps are keyed by block position and are deliberately small: a server never syncs a
 * workstation or a pad to a player who is not in range, and the maps are pruned on logout.
 */
public final class ClientState {

    private ClientState() {}

    // ------------------------------------------------------------------ workstation

    public record Workstation(RocketDesign design, List<RocketDesign> saved, int selectedMemory,
                              int flash, long receivedAt) {
    }

    public static final class Workstations {
        private static final Map<BlockPos, Workstation> MAP = new ConcurrentHashMap<>();

        private Workstations() {}

        public static void accept(BlockPos pos, CompoundTag design, CompoundTag saved, int flash) {
            List<RocketDesign> designs = new ArrayList<>(java.util.Collections.nCopies(9, null));
            for (int i = 0; i < 9; i++) {
                String key = "s" + i;
                if (saved.contains(key)) {
                    designs.set(i, RocketDesign.fromTag(saved.getCompound(key)));
                }
            }
            MAP.put(pos.immutable(), new Workstation(RocketDesign.fromTag(design), designs, -1, flash,
                    System.currentTimeMillis()));
        }

        @Nullable
        public static Workstation get(BlockPos pos) {
            return MAP.get(pos);
        }

        public static void clear() {
            MAP.clear();
        }
    }

    // ------------------------------------------------------------------ launch pad

    public record Pad(ShowProgram program, List<RocketDesign> rockets,
                      LaunchPadBlockEntity.PadState state, int ticksToStart, int selectedCue,
                      long receivedAt) {

        @Nullable
        public RocketDesign rocket(int slot) {
            return slot >= 0 && slot < this.rockets.size() ? this.rockets.get(slot) : null;
        }
    }

    public static final class Pads {
        private static final Map<BlockPos, Pad> MAP = new ConcurrentHashMap<>();

        private Pads() {}

        public static void accept(BlockPos pos, CompoundTag program, ListTag rockets, int state,
                                  int ticksToStart, int selectedCue) {
            List<RocketDesign> designs = new ArrayList<>(LaunchPadBlockEntity.SLOTS);
            for (int i = 0; i < rockets.size(); i++) {
                CompoundTag tag = rockets.getCompound(i);
                designs.add(tag.isEmpty() ? null : RocketDesign.fromTag(tag));
            }
            LaunchPadBlockEntity.PadState padState = LaunchPadBlockEntity.PadState.values()[
                    Math.max(0, Math.min(LaunchPadBlockEntity.PadState.values().length - 1, state))];
            MAP.put(pos.immutable(), new Pad(ShowProgram.fromTag(program), designs, padState,
                    ticksToStart, selectedCue, System.currentTimeMillis()));
        }

        @Nullable
        public static Pad get(BlockPos pos) {
            return MAP.get(pos);
        }

        /**
         * The pad whose show is closest to firing, for the countdown HUD. Falls back to any pad
         * that is merely armed so a player standing next to a primed pad still sees its state.
         */
        @Nullable
        public static Pad activePadWithin(BlockPos from, int radius) {
            Pad best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Map.Entry<BlockPos, Pad> entry : MAP.entrySet()) {
                Pad pad = entry.getValue();
                if (pad.state() != LaunchPadBlockEntity.PadState.ARMED
                        && pad.state() != LaunchPadBlockEntity.PadState.RUNNING) {
                    continue;
                }
                double distance = entry.getKey().distSqr(from);
                if (distance <= (double) radius * radius && distance < bestDistance) {
                    bestDistance = distance;
                    best = pad;
                }
            }
            return best;
        }

        public static void clear() {
            MAP.clear();
        }
    }

    public static void clearAll() {
        Workstations.clear();
        Pads.clear();
    }

    /** Defensive decode used by the screen when a memory slot is empty. */
    public static List<RocketDesign> emptyMemory() {
        return Arrays.asList(new RocketDesign[9]);
    }
}
