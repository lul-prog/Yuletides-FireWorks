package com.fireworkshaders.pyro;

/**
 * Behaviour bits packed into a single int and shipped to the client inside the particle options.
 *
 * <p>This is the trick that keeps the network cost of a 900-particle shell at one packet: the
 * design and the flags travel once, the client re-derives the positions and velocities from the
 * shared seed, and each particle only ever carries the handful of bits that describe how it should
 * behave once it exists.
 */
public final class PyroFlags {

    /** Shed micro-trail sparks. */
    public static final int TRAIL = 1;
    /** White glitter pops at the end of the burn. */
    public static final int CRACKLE = 1 << 1;
    /** On/off strobing. */
    public static final int STROBE = 1 << 2;
    /** Drift with the level's wind field. */
    public static final int WIND = 1 << 3;
    /** Willow drape: hang at the apex, then accelerate downwards. */
    public static final int DRAPE = 1 << 4;
    /** Hue cycles through the spectrum over the particle's life. */
    public static final int SPECTRUM = 1 << 5;
    /** Project an additive light pool onto the terrain below. */
    public static final int GROUND_GLOW = 1 << 6;
    /** Render at full brightness regardless of world light (emissive). */
    public static final int FULLBRIGHT = 1 << 7;
    /** Leave smoke behind as the particle slows (heavy embers, shells). */
    public static final int SMOKY = 1 << 8;
    /** Bounce off the ground once instead of vanishing. */
    public static final int BOUNCE = 1 << 9;
    /** Star-shaped sprite sheet variant (sparkler star instead of a round star). */
    public static final int SHAPED = 1 << 10;
    /** The particle is a "core" flash: it also contributes to the screen flash and camera punch. */
    public static final int FLASH_CORE = 1 << 11;
    /** Ignore the soft particle budget (flash/shockwave must never be culled mid-show). */
    public static final int PRIORITY = 1 << 12;

    private PyroFlags() {}

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
