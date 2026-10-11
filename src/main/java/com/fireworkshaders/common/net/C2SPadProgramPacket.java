package com.fireworkshaders.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A discrete edit to a launch pad's show program (see {@link FSAction}). */
public final class C2SPadProgramPacket {

    private final BlockPos pos;
    private final int action;
    private final int value;

    public C2SPadProgramPacket(BlockPos pos, int action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    public static void encode(C2SPadProgramPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.action);
        buf.writeVarInt(packet.value);
    }

    public static C2SPadProgramPacket decode(FriendlyByteBuf buf) {
        return new C2SPadProgramPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SPadProgramPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> com.fireworkshaders.common.menu.LaunchPadController
                    .apply(player, packet.pos, packet.action, packet.value));
        }
        context.setPacketHandled(true);
    }
}
