package com.fireworkshaders.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The authoritative design being edited, plus the workstation's nine design-memory slots.
 *
 * <p>Sent when the GUI opens and after every edit action. The client mirrors it exactly, so what the
 * preview pane draws is what the server will actually assemble — there is no client-side prediction
 * of the design anywhere.
 */
public final class S2CWorkstationSyncPacket {

    private final BlockPos pos;
    private final CompoundTag design;
    private final CompoundTag saved;
    private final int flash;

    public S2CWorkstationSyncPacket(BlockPos pos, CompoundTag design, CompoundTag saved, int flash) {
        this.pos = pos;
        this.design = design;
        this.saved = saved;
        this.flash = flash;
    }

    public static void encode(S2CWorkstationSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeNbt(packet.design);
        buf.writeNbt(packet.saved);
        buf.writeVarInt(packet.flash);
    }

    public static S2CWorkstationSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag design = buf.readNbt();
        CompoundTag saved = buf.readNbt();
        return new S2CWorkstationSyncPacket(pos, design == null ? new CompoundTag() : design,
                saved == null ? new CompoundTag() : saved, buf.readVarInt());
    }

    public static void handle(S2CWorkstationSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.fireworkshaders.client.state.WorkstationClientState.accept(
                        packet.pos, packet.design, packet.saved, packet.flash)));
        context.setPacketHandled(true);
    }
}
