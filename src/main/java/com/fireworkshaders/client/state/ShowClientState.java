package com.fireworkshaders.client.state;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Entry point for {@code S2CPadSyncPacket}: the show program, the nine loaded rockets, the pad state
 * and the countdown that drives the HUD.
 */
@OnlyIn(Dist.CLIENT)
public final class ShowClientState {

    private ShowClientState() {}

    public static void accept(BlockPos pos, CompoundTag program, ListTag rockets, int state,
                              int ticksToStart, int selectedCue) {
        ClientState.Pads.accept(pos, program, rockets, state, ticksToStart, selectedCue);
    }
}
