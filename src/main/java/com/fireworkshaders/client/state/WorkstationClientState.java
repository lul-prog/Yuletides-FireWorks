package com.fireworkshaders.client.state;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Entry point for {@code S2CWorkstationSyncPacket}.
 *
 * <p>It exists so the packet handler can name a client class without the common package having to
 * know where the mirrors live; the state itself is kept in {@link ClientState}, which is the one
 * place screens read from.
 */
@OnlyIn(Dist.CLIENT)
public final class WorkstationClientState {

    private WorkstationClientState() {}

    public static void accept(BlockPos pos, CompoundTag design, CompoundTag saved, int flash) {
        ClientState.Workstations.accept(pos, design, saved, flash);
    }
}
