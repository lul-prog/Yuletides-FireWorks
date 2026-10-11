package com.fireworkshaders.common.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The Remote Igniter never tells the server <em>which</em> pads to fire — it just says "fire".
 * The server reads the held igniter's own link list, which means:
 *
 * <ul>
 *   <li>a player can only fire pads they have physically linked (linking requires standing at the
 *       pad with the igniter),</li>
 *   <li>a modified client cannot aim a show at someone else's build,</li>
 *   <li>and revoking access is as simple as taking the item away.</li>
 * </ul>
 */
public final class C2SIgniterActionPacket {

    private final int action;
    private final int handIndex;

    public C2SIgniterActionPacket(int action, int handIndex) {
        this.action = action;
        this.handIndex = handIndex;
    }

    public static void encode(C2SIgniterActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action);
        buf.writeVarInt(packet.handIndex);
    }

    public static C2SIgniterActionPacket decode(FriendlyByteBuf buf) {
        return new C2SIgniterActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SIgniterActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> com.fireworkshaders.common.menu.RemoteIgniterController
                    .apply(player, packet.action, packet.handIndex));
        }
        context.setPacketHandled(true);
    }
}
