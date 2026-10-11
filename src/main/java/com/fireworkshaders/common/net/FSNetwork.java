package com.fireworkshaders.common.net;

import com.fireworkshaders.FireworkShaders;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Six packets. That is the whole network surface of a mod that can put four thousand particles on
 * screen at once, because bursts are generated, not transmitted.
 *
 * <ul>
 *   <li>{@link S2CBurstPacket} — the only high-frequency message, one per shell break.</li>
 *   <li>{@link S2CWorkstationSyncPacket} / {@link C2SWorkstationEditPacket} — design editing.</li>
 *   <li>{@link S2CPadSyncPacket} / {@link C2SPadProgramPacket} — show programming.</li>
 *   <li>{@link C2SIgniterActionPacket} — the Remote Igniter.</li>
 * </ul>
 */
public final class FSNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            FireworkShaders.id("main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    private FSNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CBurstPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBurstPacket::encode)
                .decoder(S2CBurstPacket::decode)
                .consumerMainThread(S2CBurstPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CWorkstationSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CWorkstationSyncPacket::encode)
                .decoder(S2CWorkstationSyncPacket::decode)
                .consumerMainThread(S2CWorkstationSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SWorkstationEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWorkstationEditPacket::encode)
                .decoder(C2SWorkstationEditPacket::decode)
                .consumerMainThread(C2SWorkstationEditPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CPadSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CPadSyncPacket::encode)
                .decoder(S2CPadSyncPacket::decode)
                .consumerMainThread(S2CPadSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SPadProgramPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SPadProgramPacket::encode)
                .decoder(C2SPadProgramPacket::decode)
                .consumerMainThread(C2SPadProgramPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SIgniterActionPacket.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SIgniterActionPacket::encode)
                .decoder(C2SIgniterActionPacket::decode)
                .consumerMainThread(C2SIgniterActionPacket::handle)
                .add();

        FireworkShaders.LOGGER.info("Yuletide Fireworks network registered ({} packets)", id + 1);
    }

    /**
     * Sends one packet to one player. {@code SimpleChannel.sendTo} is the raw-connection form; the
     * player-targeted form is a {@link PacketDistributor} target, which is what we want here.
     */
    public static <MSG> void sendTo(MSG packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
