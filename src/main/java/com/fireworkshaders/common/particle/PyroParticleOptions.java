package com.fireworkshaders.common.particle;

import com.fireworkshaders.pyro.PyroFlags;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * Particle options for everything this mod renders.
 *
 * <p>Why one options class instead of the usual type-per-effect explosion: a shell is composed at
 * runtime (colour, size, lifetime, trail/crackle/strobe/spectrum behaviour), and a design can
 * produce any combination of those. Registering a particle type per combination is impossible;
 * passing a full {@code ParticleOptions} per spark to hundreds of sparks is wasteful. So the plan
 * carries the geometry (server side, once), and each particle carries only:
 *
 * <pre>{@code kind | rgb | exposure | scale | life | flags}</pre>
 *
 * <p>which is 16 bytes on the wire. When the network is not involved at all (the usual case — the
 * client rebuilds the whole burst locally from the design and seed) it costs nothing.
 */
public final class PyroParticleOptions implements ParticleOptions {

    public enum Kind {
        /** Full-size star. */
        SPARK,
        /** Small, fast, short-lived crackle/strobe fleck. */
        GLITTER,
        /** Slow heavy ember that outlives the break. */
        EMBER,
        /** Soft metallic dust: wide and dim. */
        DUST,
        /** Lingering smoke puff. */
        SMOKE,
        /** Core flash. */
        FLASH,
        /** One billboard of the expanding pressure shell. */
        SHOCKWAVE,
        /** Hue-cycling streamer. */
        SPECTRUM;

        public static Kind byOrdinal(int ordinal) {
            Kind[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, ordinal))];
        }
    }

    private final Kind kind;
    private final int rgb;
    private final float exposure;
    private final float scale;
    private final int life;
    private final int flags;

    public PyroParticleOptions(Kind kind, int rgb, float exposure, float scale, int life, int flags) {
        this.kind = kind;
        this.rgb = rgb & 0xFFFFFF;
        this.exposure = exposure;
        this.scale = scale;
        this.life = life;
        this.flags = flags;
    }

    public Kind kind() {
        return this.kind;
    }

    public int rgb() {
        return this.rgb;
    }

    /** 0.3 (binder) .. 1.5 (magnesium). Scales vertex colour gain. */
    public float exposure() {
        return this.exposure;
    }

    public float scale() {
        return this.scale;
    }

    public int life() {
        return this.life;
    }

    public int flags() {
        return this.flags;
    }

    public boolean has(int flag) {
        return PyroFlags.has(this.flags, flag);
    }

    @Override
    public ParticleType<?> getType() {
        return com.fireworkshaders.registry.FSParticles.typeFor(this.kind);
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeByte(this.kind.ordinal());
        buf.writeInt(this.rgb);
        buf.writeFloat(this.exposure);
        buf.writeFloat(this.scale);
        buf.writeVarInt(this.life);
        buf.writeVarInt(this.flags);
    }

    public static PyroParticleOptions read(FriendlyByteBuf buf) {
        return new PyroParticleOptions(Kind.byOrdinal(buf.readByte()), buf.readInt(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s rgb=%d exposure=%.3f scale=%.3f life=%d flags=%d",
                com.fireworkshaders.registry.FSParticles.nameFor(this.kind), this.rgb, this.exposure,
                this.scale, this.life, this.flags);
    }

    /** Codec for one kind; the type is fixed per registered particle type. */
    public static Codec<PyroParticleOptions> codec(Kind kind) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("rgb").orElse(0xFFFFFF).forGetter(PyroParticleOptions::rgb),
                Codec.FLOAT.fieldOf("exposure").orElse(1.0F).forGetter(PyroParticleOptions::exposure),
                Codec.FLOAT.fieldOf("scale").orElse(1.0F).forGetter(PyroParticleOptions::scale),
                Codec.INT.fieldOf("life").orElse(40).forGetter(PyroParticleOptions::life),
                Codec.INT.fieldOf("flags").orElse(0).forGetter(PyroParticleOptions::flags)
        ).apply(instance, (rgb, exposure, scale, life, flags) ->
                new PyroParticleOptions(kind, rgb, exposure, scale, life, flags)));
    }

    /**
     * Command syntax, e.g.
     * {@code /particle fireworkshaders:pyro_spark ~ ~1 ~ 0 0 0 0.4 200 rgb=0xFFAA00 life=60}.
     *
     * <p>All arguments are optional; omitting everything gives a white, one-second spark, which is
     * exactly what you want when you are poking at the effect from a command block.
     */
    public static PyroParticleOptions fromCommand(Kind kind, StringReader reader)
            throws CommandSyntaxException {
        int rgb = 0xFFFFFF;
        float exposure = 1.0F;
        float scale = 1.0F;
        int life = 40;
        int flags = PyroFlags.FULLBRIGHT | PyroFlags.WIND | PyroFlags.TRAIL;
        while (true) {
            reader.skipWhitespace();
            if (!reader.canRead()) {
                break;
            }
            String key = reader.readString();
            if (!reader.canRead() || reader.peek() != '=') {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .createWithContext(reader);
            }
            reader.skip();
            switch (key.toLowerCase(Locale.ROOT)) {
                case "rgb" -> rgb = parseColour(reader);
                case "exposure" -> exposure = reader.readFloat();
                case "scale" -> scale = reader.readFloat();
                case "life" -> life = reader.readInt();
                case "flags" -> flags = reader.readInt();
                default -> throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .createWithContext(reader);
            }
        }
        return new PyroParticleOptions(kind, rgb, exposure, scale, life, flags);
    }

    private static int parseColour(StringReader reader) throws CommandSyntaxException {
        String token = reader.readUnquotedString();
        if (token.startsWith("#")) {
            token = token.substring(1);
        } else if (token.startsWith("0x") || token.startsWith("0X")) {
            token = token.substring(2);
        }
        try {
            return (int) Long.parseLong(token, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerInvalidHexNumber()
                    .createWithContext(reader);
        }
    }
}
