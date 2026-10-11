package com.fireworkshaders.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Launch pad state for the programming screen and the show HUD: the program, the arm state, the
 * countdown, and a one-line summary of the nine loaded rockets (so the screen can name them without
 * needing the pad's container contents synchronised).
 */
public final class S2CPadSyncPacket {

    private final BlockPos pos;
    private final CompoundTag program;
    private final ListTag rockets;     // list of design compounds (or empty compounds for empty slots)
    private final int state;
    private final int ticksToStart;
    private final int selectedCue;

    public S2CPadSyncPacket(BlockPos pos, CompoundTag program, ListTag rockets, int state,
                            int ticksToStart, int selectedCue) {
        this.pos = pos;
        this.program = program;
        this.rockets = rockets;
        this.state = state;
        this.ticksToStart = ticksToStart;
        this.selectedCue = selectedCue;
    }

    public static void encode(S2CPadSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeNbt(packet.program);
        buf.writeCollection(packet.rockets, (b, tag) -> b.writeNbt((CompoundTag) tag));
        buf.writeVarInt(packet.state);
        buf.writeVarInt(packet.ticksToStart);
        buf.writeVarInt(packet.selectedCue);
    }

    public static S2CPadSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag program = buf.readNbt();
        // ListTag has no (int) constructor, so collect into an ArrayList and wrap.
        List<Tag> tags = buf.readCollection(ArrayList::new, b -> {
            CompoundTag tag = b.readNbt();
            return tag == null ? new CompoundTag() : tag;
        });
        ListTag rockets = new ListTag();
        rockets.addAll(tags);
        return new S2CPadSyncPacket(pos, program == null ? new CompoundTag() : program, rockets,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CPadSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.fireworkshaders.client.state.ShowClientState.accept(packet.pos,
                        packet.program, packet.rockets, packet.state, packet.ticksToStart,
                        packet.selectedCue)));
        context.setPacketHandled(true);
    }
}
