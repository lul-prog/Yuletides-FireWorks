package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.particle.PyroParticleOptions;
import com.fireworkshaders.common.particle.PyroParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One registered particle type per particle kind.
 *
 * <p>The generic gymnastics in {@link #register} exist because {@code DeferredRegister} for a
 * registry of {@code ParticleType<?>} can only hand back {@code RegistryObject<ParticleType<?>>},
 * while the provider registration needs the narrower {@code ParticleType<PyroParticleOptions>}.
 * The cast is unchecked but sound: every type registered here is constructed by
 * {@link PyroParticleType}, whose options type is fixed.
 *
 * <p>Each type has a matching {@code assets/fireworkshaders/particles/<name>.json} listing exactly
 * one sprite. A single sprite per type is deliberate: it keeps the particle atlas small, keeps all
 * particles of a kind in one batch, and — importantly for shader packs — means a particle's
 * appearance is driven by vertex colour, which is what the chemistry model feeds.
 */
public final class FSParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FireworkShaders.MOD_ID);

    private static final Map<PyroParticleOptions.Kind, RegistryObject<ParticleType<PyroParticleOptions>>>
            BY_KIND = Collections.synchronizedMap(
            new EnumMap<>(PyroParticleOptions.Kind.class));

    public static final RegistryObject<ParticleType<PyroParticleOptions>> SPARK =
            register(PyroParticleOptions.Kind.SPARK, "pyro_spark");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> GLITTER =
            register(PyroParticleOptions.Kind.GLITTER, "pyro_glitter");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> EMBER =
            register(PyroParticleOptions.Kind.EMBER, "pyro_ember");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> DUST =
            register(PyroParticleOptions.Kind.DUST, "pyro_dust");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> SMOKE =
            register(PyroParticleOptions.Kind.SMOKE, "pyro_smoke");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> FLASH =
            register(PyroParticleOptions.Kind.FLASH, "pyro_flash");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> SHOCKWAVE =
            register(PyroParticleOptions.Kind.SHOCKWAVE, "pyro_shockwave");
    public static final RegistryObject<ParticleType<PyroParticleOptions>> SPECTRUM =
            register(PyroParticleOptions.Kind.SPECTRUM, "pyro_spectrum");

    private static RegistryObject<ParticleType<PyroParticleOptions>> register(
            PyroParticleOptions.Kind kind, String name) {
        RegistryObject<ParticleType<?>> holder = PARTICLE_TYPES.register(name,
                () -> new PyroParticleType(kind));
        @SuppressWarnings("unchecked")
        RegistryObject<ParticleType<PyroParticleOptions>> typed =
                (RegistryObject<ParticleType<PyroParticleOptions>>) (RegistryObject<?>) holder;
        BY_KIND.put(kind, typed);
        return typed;
    }

    /** The registered type for a kind. Used by the options' {@code getType()} and by commands. */
    public static ParticleType<PyroParticleOptions> typeFor(PyroParticleOptions.Kind kind) {
        RegistryObject<ParticleType<PyroParticleOptions>> holder = BY_KIND.get(kind);
        return holder == null ? null : holder.get();
    }

    public static String nameFor(PyroParticleOptions.Kind kind) {
        return switch (kind) {
            case SPARK -> "pyro_spark";
            case GLITTER -> "pyro_glitter";
            case EMBER -> "pyro_ember";
            case DUST -> "pyro_dust";
            case SMOKE -> "pyro_smoke";
            case FLASH -> "pyro_flash";
            case SHOCKWAVE -> "pyro_shockwave";
            case SPECTRUM -> "pyro_spectrum";
        };
    }

    private FSParticles() {}
}
