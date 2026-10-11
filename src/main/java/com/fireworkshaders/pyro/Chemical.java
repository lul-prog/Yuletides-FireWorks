package com.fireworkshaders.pyro;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The periodic table, as far as a firework is concerned.
 *
 * <p>Real pyrotechnics get their colour from the emission spectrum of a metal salt, not from a dye
 * bottle, and the wavelengths are not negotiable:
 *
 * <ul>
 *   <li>strontium 606 nm (crimson), barium 524 nm (green), copper halide 455 nm (azure),</li>
 *   <li>sodium 589 nm (amber, and it washes everything else out — hence "sodium blindness"),</li>
 *   <li>magnesium and aluminium burn white-hot at 3000+ °C and are what make a shell look bright.</li>
 * </ul>
 *
 * <p>{@link #intensity} is therefore not decoration: it drives the vertex-colour gain and the
 * number of micro-trail sparks a colour contributes.
 */
public enum Chemical {

    // ---- colour producers -------------------------------------------------------------------
    STRONTIUM_SALT("strontium_nitrate", "Strontium Nitrate", 0xFF3524, 0.92F, 2500, Role.COLOUR,
            "Sr(NO3)2", "606 nm crimson line"),
    BARIUM_SALT("barium_nitrate", "Barium Nitrate", 0x3FFF63, 0.98F, 2400, Role.COLOUR,
            "Ba(NO3)2", "524 nm green line"),
    COPPER_HALIDE("copper_chloride", "Copper(I) Chloride", 0x35A6FF, 0.95F, 1345, Role.COLOUR,
            "CuCl", "455 nm azure band"),
    SODIUM_SALT("sodium_nitrate", "Sodium Nitrate", 0xFFB324, 1.05F, 2600, Role.COLOUR,
            "NaNO3", "589 nm D-line (washes out other colours)"),
    CALCIUM_SALT("calcium_chloride", "Calcium Chloride", 0xFF7A1E, 0.90F, 1800, Role.COLOUR,
            "CaCl2", "622 nm orange band"),
    POTASSIUM_SALT("potassium_nitrate", "Potassium Nitrate", 0xB05CFF, 0.86F, 1300, Role.COLOUR,
            "KNO3", "404 nm violet line, plus the oxidiser"),

    // ---- brightness / sparkle ----------------------------------------------------------------
    MAGNESIUM("magnesium_powder", "Magnesium Powder", 0xFFFBE8, 1.35F, 3100, Role.BRIGHTENER,
            "Mg", "white-hot, 3100 °C"),
    ALUMINIUM("aluminium_powder", "Aluminium Powder", 0xFFF0C2, 1.22F, 2900, Role.BRIGHTENER,
            "Al", "gold-white glitter, long trails"),
    TITANIUM("titanium_sponge", "Titanium Sponge", 0xF2F6FF, 1.15F, 3000, Role.BRIGHTENER,
            "Ti", "silver-white spark fountain"),
    IRON_FILINGS("iron_filings", "Iron Filings", 0xFFC061, 0.88F, 2200, Role.BRIGHTENER,
            "Fe", "sparkler-type orange spray, heavy smoke"),

    // ---- energetic / structural ---------------------------------------------------------------
    POTASSIUM_PERCHLORATE("potassium_perchlorate", "Potassium Perchlorate", 0xFFE8B0, 0.55F, 400, Role.OXIDISER,
            "KClO4", "clean, hot oxidiser"),
    SULFUR("sulfur", "Sulfur", 0x8A7CFF, 0.45F, 444, Role.FUEL,
            "S", "lowers ignition temperature"),
    CHARCOAL("charcoal_dust", "Charcoal Dust", 0xFF8A33, 0.60F, 900, Role.FUEL,
            "C", "orange embers, heavy smoke"),
    DEXTRIN("dextrin_binder", "Dextrin Binder", 0xF0D8A8, 0.30F, 250, Role.BINDER,
            "(C6H10O5)n", "binds the grain so the shell survives the lift charge");

    public enum Role {
        COLOUR, BRIGHTENER, OXIDISER, FUEL, BINDER;

        public boolean isColour() {
            return this == COLOUR;
        }
    }

    private final String itemName;
    private final String displayName;
    private final int rgb;
    private final float intensity;
    private final int burnTempC;
    private final Role role;
    private final String formula;
    private final String note;

    Chemical(String itemName, String displayName, int rgb, float intensity, int burnTempC, Role role,
             String formula, String note) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.rgb = rgb;
        this.intensity = intensity;
        this.burnTempC = burnTempC;
        this.role = role;
        this.formula = formula;
        this.note = note;
    }

    public String itemName() {
        return this.itemName;
    }

    public String displayName() {
        return this.displayName;
    }

    public int rgb() {
        return this.rgb;
    }

    /** 0.3 (binder) .. 1.35 (magnesium). Drives gain, trail density and how "hot" a colour looks. */
    public float intensity() {
        return this.intensity;
    }

    public int burnTempC() {
        return this.burnTempC;
    }

    public Role role() {
        return this.role;
    }

    public String formula() {
        return this.formula;
    }

    public String note() {
        return this.note;
    }

    /** A hotter burn is a whiter, less saturated colour; that is real blackbody behaviour. */
    public int effectiveRgb() {
        float heat = (this.burnTempC - 1000) / 2200.0F;
        return PyroMath.lerpColor(this.rgb, 0xFFFFFF, PyroMath.clamp01(heat) * 0.22F);
    }

    /** {@code item.fireworkshaders.strontium_nitrate} */
    public Component nameComponent() {
        return Component.translatable("item." + FireworkShadersId.NS + "." + this.itemName);
    }

    /** The chemistry tooltip: formula, burn temperature, emission line. */
    public Component description() {
        return Component.translatable("chemical." + FireworkShadersId.NS + "." + this.itemName + ".desc");
    }

    @Nullable
    public ItemStack asStack(int count) {
        Item item = FireworkShadersId.itemFor(this);
        return item == null ? null : new ItemStack(item, count);
    }

    public static Chemical byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Chemical c : values()) {
            if (c.itemName.equals(n) || c.name().toLowerCase(Locale.ROOT).equals(n)) {
                return c;
            }
        }
        return null;
    }

    public static Chemical byOrdinal(int ordinal) {
        Chemical[] v = values();
        return v[PyroMath.clampInt(ordinal, 0, v.length - 1)];
    }

    /** The eight that can sit in a shell's colour slots, in a sensible GUI order. */
    public static Chemical[] colourPalette() {
        return new Chemical[]{
                STRONTIUM_SALT, BARIUM_SALT, COPPER_HALIDE, SODIUM_SALT,
                CALCIUM_SALT, POTASSIUM_SALT, MAGNESIUM, ALUMINIUM
        };
    }
}
