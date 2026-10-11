package com.fireworkshaders.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One edit to the design in a workstation. Discrete edits use {@code clickMenuButton} instead (they
 * are cheaper and come with the vanilla container-click debounce); this packet exists for the two
 * things an int cannot express: the rocket's name and the show's name.
 */
public final class C2SWorkstationEditPacket {

    private static final int MAX_TEXT = 64;

    private final BlockPos pos;
    private final int action;
    private final String text;

    public C2SWorkstationEditPacket(BlockPos pos, int action, String text) {
        this.pos = pos;
        this.action = action;
        this.text = text == null ? "" : text;
    }

    public static void encode(C2SWorkstationEditPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.action);
        buf.writeUtf(packet.text, MAX_TEXT);
    }

    public static C2SWorkstationEditPacket decode(FriendlyByteBuf buf) {
        return new C2SWorkstationEditPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(MAX_TEXT));
    }

    public static void handle(C2SWorkstationEditPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> com.fireworkshaders.common.menu.WorkstationEditor
                    .apply(player, packet.pos, packet.action, packet.text));
        }
        context.setPacketHandled(true);
    }
}
