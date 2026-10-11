package com.fireworkshaders.pyro;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Modifiers. Each one is a discrete, priced change to what a shell does — this enum is the whole
 * "modular crafting" contract between the GUI, the recipe cost and the burst generator.
 *
 * <p>Costs are in "grains" (1 grain = 1 gunpowder-equivalent item) and are deliberately
 * non-linear: {@link #SHELL_OF_SHELLS} alone costs more than three cheap modifiers, because a
 * multi-break shell is not a cosmetic upgrade, it is a different firework.
 */
public enum EffectModule {

    /** Every spark sheds a micro-trail. The single biggest realism-per-cost win available. */
    TRAIL("trail", 2, 1, "Sheds a glittering micro-trail behind every spark"),

    /** Titanium/antimony glitter that pops with bright white flashes at the end of the burn. */
    CRACKLE("crackle", 3, 1, "Pops and crackles as the sparks burn out"),

    /** On/off strobing at 6-12 Hz, like a magnesium strobe shell. */
    STROBE("strobe", 3, 2, "Sparks strobe on and off in flight"),

    /** Extra heavy smoke, and the smoke is tinted by the shell's own colours. */
    SMOKE("smoke", 2, 1, "Doubles the lingering smoke cloud"),

    /** Audible rise: a whistle whose pitch climbs with altitude. */
    WHISTLE("whistle", 2, 1, "Whistles on the way up, pitch rising with altitude"),

    /** Sonic Boom only: a shriek at roughly Mach one, plus the two-stage flash. */
    SHRIEK("shriek", 6, 3, "Supersonic shriek, and the shell climbs at 2.4x lift speed"),

    /** A blinding white core flash, bright enough to throw the camera's exposure. */
    FLASH("flash", 4, 2, "Blinding white core flash at the moment of break"),

    /** An expanding spherical shockwave shell plus a ground-hugging ring. */
    SHOCKWAVE("shockwave", 6, 3, "Expanding pressure shell and a ground-hugging ring"),

    /** Multi-break: the shell bursts, and its eight children arc down and burst again. */
    SHELL_OF_SHELLS("shell_of_shells", 9, 4, "Splits into 8 timed sub-shells"),

    /** Willow drape: sparks hang at the top of the break, then are pulled down slowly. */
    DRAPE("drape", 2, 1, "Sparks hang, then droop slowly like a weeping willow"),

    /** Prismatic Cascade only: hue sweeps the whole visible spectrum during the fall. */
    SPECTRUM("spectrum", 5, 3, "Colour sweeps the entire RGB spectrum as the sparks fall"),

    /** Ground illumination: real light pulses plus additive light pools on the terrain. */
    GROUND_GLOW("ground_glow", 3, 2, "Illuminates the ground below with real light pulses"),

    /** Slow, heavy embers that outlive the sparks and drift down for several seconds. */
    LINGER("linger", 3, 2, "Leaves slow heavy embers that outlive the spark burn"),

    /** Fine metallic dust: a soft, wide, closely packed break instead of discrete stars. */
    DUST("dust", 2, 1, "Fine metallic dust — a soft, wide, dense break");

    private final String id;
    private final int grainCost;
    private final int tier;
    private final String tooltip;

    EffectModule(String id, int grainCost, int tier, String tooltip) {
        this.id = id;
        this.grainCost = grainCost;
        this.tier = tier;
        this.tooltip = tooltip;
    }

    public String id() {
        return this.id;
    }

    /** How much propellant/binder this module consumes when the shell is assembled. */
    public int grainCost() {
        return this.grainCost;
    }

    /** 1 = simple, 4 = needs a proper pyrotechnician. Gates the GUI's advanced panel. */
    public int tier() {
        return this.tier;
    }

    public String tooltip() {
        return this.tooltip;
    }

    public String translationKey() {
        return "module." + FireworkShadersId.NS + "." + this.id;
    }

    public static EffectModule byId(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        for (EffectModule m : values()) {
            if (m.id.equals(n) || m.name().toLowerCase(Locale.ROOT).equals(n)) {
                return m;
            }
        }
        return null;
    }

    /** Bitmask persistence — a design serialises its modules into one int rather than a list tag. */
    public static int toMask(EnumSet<EffectModule> set) {
        int mask = 0;
        for (EffectModule m : set) {
            mask |= 1 << m.ordinal();
        }
        return mask;
    }

    public static EnumSet<EffectModule> fromMask(int mask) {
        EnumSet<EffectModule> set = EnumSet.noneOf(EffectModule.class);
        for (EffectModule m : values()) {
            if ((mask & (1 << m.ordinal())) != 0) {
                set.add(m);
            }
        }
        return set;
    }

    public static int totalCost(EnumSet<EffectModule> set) {
        int total = 0;
        for (EffectModule m : set) {
            total += m.grainCost();
        }
        return total;
    }

    /** Tier of the most advanced module in the set; used to price the assembled rocket. */
    public static int maxTier(EnumSet<EffectModule> set) {
        int tier = 1;
        for (EffectModule m : set) {
            tier = Math.max(tier, m.tier());
        }
        return tier;
    }
}
