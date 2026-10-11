package com.fireworkshaders.common.net;

import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "A shell broke here, and this is what it was."
 *
 * <p>Payload: the design NBT, the origin, the seed, the audience facing and a budget scale. That is
 * every input the client needs to rebuild the explosion — about 120 bytes, whether the break is 40
 * stars or 900 (a sub-shell passes a smaller budget scale so the eight children of a Meteor Shower
 * do not each cost a full break).
 *
 * <p>The client never trusts this blindly: a malformed or oversized design is rejected by
 * {@link RocketDesign#fromTag} falling back to a default, and the particle budget is clamped by
 * the <em>client's</em> own common config, so a griefing server cannot use this packet to brick a
 * client's frame rate.
 */
public final class S2CBurstPacket {

    private final CompoundTag design;
    private final double x;
    private final double y;
    private final double z;
    private final long seed;
    private final float facingX;
    private final float facingY;
    private final float facingZ;
    private final float budgetScale;

    public S2CBurstPacket(CompoundTag design, double x, double y, double z, long seed,
                          double facingX, double facingY, double facingZ, float budgetScale) {
        this.design = design;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
        this.facingX = (float) facingX;
        this.facingY = (float) facingY;
        this.facingZ = (float) facingZ;
        this.budgetScale = budgetScale;
    }

    public static void encode(S2CBurstPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.design);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeLong(packet.seed);
        buf.writeFloat(packet.facingX);
        buf.writeFloat(packet.facingY);
        buf.writeFloat(packet.facingZ);
        buf.writeFloat(packet.budgetScale);
    }

    public static S2CBurstPacket decode(FriendlyByteBuf buf) {
        CompoundTag design = buf.readNbt();
        return new S2CBurstPacket(design == null ? new CompoundTag() : design,
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CBurstPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.fireworkshaders.client.fx.ClientBurst.execute(
                        RocketDesign.fromTag(packet.design),
                        new net.minecraft.world.phys.Vec3(packet.x, packet.y, packet.z),
                        packet.seed,
                        new net.minecraft.world.phys.Vec3(packet.facingX, packet.facingY, packet.facingZ),
                        packet.budgetScale)));
        context.setPacketHandled(true);
    }
}
