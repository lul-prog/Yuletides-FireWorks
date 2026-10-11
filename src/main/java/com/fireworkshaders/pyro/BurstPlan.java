package com.fireworkshaders.pyro;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A fully computed explosion: absolute particle spawn instructions plus the sub-shells the server
 * should actually spawn as entities.
 *
 * <p>Produced by {@link BurstPlanner}, consumed by the client (spawn the particles) and by the
 * server (spawn the child shells, pulse the lights). Both sides build the same plan from the same
 * design and seed, so the plan is never sent over the wire — only {@code (design, seed, origin)}.
 */
public final class BurstPlan {

    public enum Kind {
        /** Full-size star with physics. */
        SPARK,
        /** Small, fast, very short-lived glitter: crackle pops, trail sparks, strobe flecks. */
        GLITTER,
        /** Slow, heavy, long-lived ember that survives the break by several seconds. */
        EMBER,
        /** Soft metallic dust: wide, dim, dense. */
        DUST,
        /** Lingering smoke puff. */
        SMOKE,
        /** The white core flash. */
        FLASH,
        /** One billboard of the expanding pressure shell. */
        SHOCKWAVE,
        /** Hue-cycling streamer (Prismatic Cascade). */
        SPECTRUM
    }

    public record Spawn(double x, double y, double z,
                        double vx, double vy, double vz,
                        Kind kind, int rgb, float exposure, float scale, int life, int flags) {
    }

    /** A child shell the server must instantiate; the client sees it as a normal entity. */
    public record SubShell(double vx, double vy, double vz, int fuse) {
    }

    private final long seed;
    private final Vec3 origin;
    private final float power;
    private final int lightPulses;
    private final List<Spawn> spawns;
    private final List<SubShell> subShells;

    public BurstPlan(long seed, Vec3 origin, float power, int lightPulses, List<Spawn> spawns,
                     List<SubShell> subShells) {
        this.seed = seed;
        this.origin = origin;
        this.power = power;
        this.lightPulses = lightPulses;
        this.spawns = List.copyOf(spawns);
        this.subShells = List.copyOf(subShells);
    }

    public long seed() {
        return this.seed;
    }

    public Vec3 origin() {
        return this.origin;
    }

    public float power() {
        return this.power;
    }

    /** How many temporary light blocks the server should scatter through the break. */
    public int lightPulses() {
        return this.lightPulses;
    }

    public List<Spawn> spawns() {
        return this.spawns;
    }

    public List<SubShell> subShells() {
        return this.subShells;
    }

    public int size() {
        return this.spawns.size();
    }
}
