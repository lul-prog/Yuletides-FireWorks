package com.fireworkshaders.common.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One registered {@link ParticleType} per particle kind. The kind is baked into the type, so the
 * provider registration can be a plain {@code type -> provider} map and the options never have to
 * carry a discriminator on the hot path.
 *
 * <p>{@code overrideLimiter = true}: firework particles are spawned in a single tick and would
 * otherwise be silently dropped by the client's particle limiter — which is exactly the bug that
 * makes vanilla fireworks look thin when two shells break at once.
 */
public class PyroParticleType extends ParticleType<PyroParticleOptions> {

    private final PyroParticleOptions.Kind kind;
    private final Codec<PyroParticleOptions> codec;

    public PyroParticleType(PyroParticleOptions.Kind kind) {
        super(true, new ParticleOptions.Deserializer<PyroParticleOptions>() {
            @Override
            public PyroParticleOptions fromCommand(ParticleType<PyroParticleOptions> type,
                                                   StringReader reader) throws CommandSyntaxException {
                return PyroParticleOptions.fromCommand(kind, reader);
            }

            @Override
            public PyroParticleOptions fromNetwork(ParticleType<PyroParticleOptions> type,
                                                   FriendlyByteBuf buf) {
                return PyroParticleOptions.read(buf);
            }
        });
        this.kind = kind;
        this.codec = PyroParticleOptions.codec(kind);
    }

    public PyroParticleOptions.Kind kind() {
        return this.kind;
    }

    @Override
    public Codec<PyroParticleOptions> codec() {
        return this.codec;
    }
}
