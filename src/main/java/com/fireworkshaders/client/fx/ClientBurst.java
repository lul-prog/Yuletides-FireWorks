package com.fireworkshaders.client.fx;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.client.audio.FireworkAudio;
import com.fireworkshaders.client.particle.PyroParticles;
import com.fireworkshaders.common.particle.PyroParticleOptions;
import com.fireworkshaders.pyro.BurstPlan;
import com.fireworkshaders.pyro.BurstPlanner;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Executes a shell break on the client: builds the identical plan the server built and turns it into
 * particles, then hands off to the audio and camera layers.
 *
 * <p>This is the hot path of the whole mod, so it is written to do exactly one thing per spawn:
 * no allocation beyond the options record, no matrix maths, no world queries. Everything expensive
 * (pattern generation) happened once, before the loop.
 */
public final class ClientBurst {

    /** Beyond this the only thing visible is the flash, and the particles would be sub-pixel. */
    private static final double CULL_DISTANCE = 420.0;

    private ClientBurst() {}

    public static void execute(RocketDesign design, Vec3 origin, long seed, Vec3 facing,
                              float budgetScale) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        double distance = Math.sqrt(minecraft.player.distanceToSqr(origin));
        if (distance > CULL_DISTANCE) {
            return;
        }

        int budget = Math.max(24, Math.round(FSConfig.COMMON.maxParticlesPerBurst.get() * budgetScale));
        BurstPlan plan = BurstPlanner.plan(design, origin, seed, facing, budget);

        for (BurstPlan.Spawn spawn : plan.spawns()) {
            PyroParticleOptions.Kind kind = switch (spawn.kind()) {
                case SPARK -> PyroParticleOptions.Kind.SPARK;
                case GLITTER -> PyroParticleOptions.Kind.GLITTER;
                case EMBER -> PyroParticleOptions.Kind.EMBER;
                case DUST -> PyroParticleOptions.Kind.DUST;
                case SMOKE -> PyroParticleOptions.Kind.SMOKE;
                case FLASH -> PyroParticleOptions.Kind.FLASH;
                case SHOCKWAVE -> PyroParticleOptions.Kind.SHOCKWAVE;
                case SPECTRUM -> PyroParticleOptions.Kind.SPECTRUM;
            };
            int rgb = spawn.rgb();
            if (kind == PyroParticleOptions.Kind.SPECTRUM) {
                // The spectrum shader (the particle's own colour ramp) needs a per-particle phase,
                // otherwise the whole shell cycles in lockstep and reads as one flashing blob.
                rgb = (int) (seed % 0xFFFF);
            }
            level.addParticle(new PyroParticleOptions(kind, rgb, spawn.exposure(), spawn.scale(),
                            spawn.life(), spawn.flags()),
                    spawn.x(), spawn.y(), spawn.z(), spawn.vx(), spawn.vy(), spawn.vz());
        }

        // Audio and camera are distance-scaled here rather than at spawn time.
        FireworkAudio.playBurst(design, origin, distance);
        ScreenFx.onBurst(design, origin, distance);
    }

    /** Diagnostics for the F3-less among us: how much of the budget is in use right now. */
    public static String debugStatus() {
        return PyroParticles.Budget.live() + " live / " + FSConfig.CLIENT.particleBudget.get();
    }
}
