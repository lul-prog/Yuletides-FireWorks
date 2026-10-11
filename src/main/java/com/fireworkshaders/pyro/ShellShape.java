package com.fireworkshaders.pyro;

import java.util.Locale;

/**
 * Break geometry. The shape only declares <em>intent</em> (how many stars, whether the break is
 * spherical or a flat disc, how the stars are distributed) — the actual point cloud is generated
 * per explosion by {@link BurstPatterns} from a seed, so two "rings" never look identical.
 */
public enum ShellShape {

    /** Classic sphere of stars. The reference every other shape is judged against. */
    PEONY("peony", 1.00F, false, "A classic, evenly packed sphere of stars"),

    /** Denser, longer-burning sphere with heavy trails. */
    CHRYSANTHEMUM("chrysanthemum", 1.18F, false, "Dense sphere with long glittering trails"),

    /** Huge slow canopy: fewer, bigger stars, deliberately sparse at the centre. */
    SPHERE("sphere", 1.30F, false, "A vast, evenly spread canopy of stars"),

    /** Weeping willow: stars reach the top of the break and are then pulled down. */
    WILLOW("willow", 1.22F, false, "Golden stars that hang, then weep slowly downwards"),

    /** Flat equatorial ring, as seen from below. */
    RING("ring", 1.00F, true, "A flat ring of fire, best seen from underneath"),

    /** Two concentric rings at different radii and colours. */
    DOUBLE_RING("double_ring", 1.12F, true, "Two concentric rings in different colours"),

    /** Waterfall: a downward-biased hemisphere that pours out and hangs. */
    CASCADE("cascade", 1.28F, false, "A waterfall of stars pouring out and falling"),

    /** A dozen thick rays. Reads as a sea anemone; needs trails to look right. */
    PALM("palm", 0.62F, false, "A dozen thick rays with heavy tails"),

    /** Two rings crossing at right angles, with secondary crackle at each crossing. */
    CROSSETTE("crossette", 0.92F, false, "Crossing rings that split into crackling stars"),

    /** A sphere of stars orbited by a flat ring in a second colour. */
    SATURN("saturn", 1.24F, false, "A sphere orbited by a coloured ring"),

    /** Parametric heart curve, thrown as a flat face. Guaranteed screenshot material. */
    HEART("heart", 0.88F, true, "A flat, unmistakable heart"),

    /** Five-pointed star outline with a sparse interior fill. */
    STAR("star", 0.94F, true, "A five-pointed star with a sparse fill"),

    /** Single heavy comet tail with a bright head — the cheapest, punchiest shell. */
    COMET("comet", 0.40F, false, "A single heavy tail with a burning head");

    private final String id;
    private final float countMultiplier;
    private final boolean flat;
    private final String tooltip;

    ShellShape(String id, float countMultiplier, boolean flat, String tooltip) {
        this.id = id;
        this.countMultiplier = countMultiplier;
        this.flat = flat;
        this.tooltip = tooltip;
    }

    public String id() {
        return this.id;
    }

    /** Multiplier on the star count for a given caliber. */
    public float countMultiplier() {
        return this.countMultiplier;
    }

    /** True when the break is a disc rather than a volume — the GUI preview draws these edge-on. */
    public boolean isFlat() {
        return this.flat;
    }

    public String tooltip() {
        return this.tooltip;
    }

    public String translationKey() {
        return "shape." + FireworkShadersId.NS + "." + this.id;
    }

    public ShellShape next() {
        ShellShape[] v = values();
        return v[(this.ordinal() + 1) % v.length];
    }

    public ShellShape previous() {
        ShellShape[] v = values();
        return v[(this.ordinal() + v.length - 1) % v.length];
    }

    public static ShellShape byId(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        for (ShellShape s : values()) {
            if (s.id.equals(n) || s.name().toLowerCase(Locale.ROOT).equals(n)) {
                return s;
            }
        }
        return PEONY;
    }
}
