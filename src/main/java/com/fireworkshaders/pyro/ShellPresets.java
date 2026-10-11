package com.fireworkshaders.pyro;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The catalogue. Five premium shells with hand-tuned designs, plus a few honest starters.
 *
 * <p>Each premium shell is really just a {@link RocketDesign} — the exact same object a player can
 * build by hand in the Pyrotechnic Workstation. There is no special-cased code path for them
 * anywhere in the renderer or the burst generator; they are "premium" because the design is
 * carefully tuned (star counts, burn times, module combination), not because they cheat.
 */
public final class ShellPresets {

    public record Preset(String id, Component name, Component lore, RocketDesign design,
                         boolean premium, String... notes) {

        public String translationKey() {
            return "preset." + FireworkShadersId.NS + "." + this.id;
        }

        public Component loreKey() {
            return Component.translatable(this.translationKey() + ".lore");
        }

        public Component description() {
            return Component.translatable(this.translationKey() + ".desc");
        }
    }

    private static final List<Preset> ALL = new ArrayList<>();

    // ------------------------------------------------------------------ the five

    /**
     * Stardust Willow — a canopy that does not fall, it <em>weeps</em>.
     *
     * <p>Aluminium and magnesium burn hot and white, the drape module kills gravity for the first
     * third of the burn and then ramps it up, and the burn time is inflated by the willow shape, so
     * the stars hang at the top of the break for a full second before the tails start to sag.
     */
    public static final Preset STARDUST_WILLOW = register(new Preset(
            "stardust_willow",
            Component.translatable("preset." + FireworkShadersId.NS + ".stardust_willow"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.ALUMINIUM, Chemical.MAGNESIUM))
                    .shape(ShellShape.WILLOW)
                    .modules(java.util.EnumSet.of(EffectModule.TRAIL, EffectModule.DRAPE,
                            EffectModule.SMOKE, EffectModule.LINGER))
                    .caliber(2.2F)
                    .build(),
            true,
            "A golden canopy that hangs, then weeps slowly towards the ground."));

    /**
     * Meteor Shower — a high break that turns into eight independent falling shells.
     *
     * <p>The lift burns long (a high break is the point), the main break is deliberately sparse
     * because its payload is the children, and each child arcs downwards and breaks low with heavy
     * trails, so what you see is a meteor storm rather than one firework.
     */
    public static final Preset METEOR_SHOWER = register(new Preset(
            "meteor_shower",
            Component.translatable("preset." + FireworkShadersId.NS + ".meteor_shower"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.IRON_FILINGS, Chemical.CALCIUM_SALT))
                    .shape(ShellShape.PEONY)
                    .modules(java.util.EnumSet.of(EffectModule.SHELL_OF_SHELLS, EffectModule.TRAIL,
                            EffectModule.CRACKLE, EffectModule.WHISTLE))
                    .caliber(1.7F)
                    .fuse(74)
                    .payload(RocketDesign.builder()
                            .palette(List.of(Chemical.IRON_FILINGS, Chemical.CALCIUM_SALT))
                            .shape(ShellShape.SPHERE)
                            .modules(java.util.EnumSet.of(EffectModule.TRAIL, EffectModule.SMOKE,
                                    EffectModule.LINGER))
                            .caliber(1.0F)
                            .build())
                    .build(),
            true,
            "Breaks high, then splits into eight shells that arc down and break again."));

    /**
     * Sonic Boom — supersonic lift, then a flash you feel.
     *
     * <p>Shriek multiplies lift speed by 2.45 and the fuse is shortened to match, so the shell
     * climbs ~100 blocks in under two seconds. At break: the white core, a 64-point pressure
     * shell, a 128-point ground ring, and a camera shake scaled by caliber — the shockwave module
     * is what makes the shake three times as heavy as a normal shell of the same size.
     */
    public static final Preset SONIC_BOOM = register(new Preset(
            "sonic_boom",
            Component.translatable("preset." + FireworkShadersId.NS + ".sonic_boom"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.MAGNESIUM, Chemical.TITANIUM))
                    .shape(ShellShape.SPHERE)
                    .modules(java.util.EnumSet.of(EffectModule.SHRIEK, EffectModule.FLASH,
                            EffectModule.SHOCKWAVE, EffectModule.TRAIL, EffectModule.SMOKE))
                    .caliber(2.6F)
                    .fuse(36)
                    .build(),
            true,
            "Supersonic climb, blinding flash, expanding shockwave, heavy screen shake."));

    /**
     * Dragon's Breath — the one meant to be seen from the ground.
     *
     * <p>A near-flat ring left open low, calcium and sodium for the fire, charcoal for the smoke
     * and iron for the sparks. Ground glow places real light blocks and projects additive light
     * pools on the terrain under the embers, so the show lights up the paddock instead of merely
     * floating above it.
     */
    public static final Preset DRAGONS_BREATH = register(new Preset(
            "dragons_breath",
            Component.translatable("preset." + FireworkShadersId.NS + ".dragons_breath"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.CALCIUM_SALT, Chemical.SODIUM_SALT, Chemical.CHARCOAL))
                    .shape(ShellShape.RING)
                    .modules(java.util.EnumSet.of(EffectModule.GROUND_GLOW, EffectModule.LINGER,
                            EffectModule.SMOKE, EffectModule.TRAIL))
                    .caliber(2.4F)
                    .fuse(46)
                    .build(),
            true,
            "A ring of fire that washes outwards and sets the ground alight with embers."));

    /**
     * Prismatic Cascade — a waterfall of light that walks the spectrum on the way down.
     *
     * <p>Spectrum mode makes every particle's hue a function of its own age, and the cascade shape
     * biases the whole break downwards, so the colour bands are horizontal: a red-to-violet
     * waterfall that keeps changing as it falls.
     */
    public static final Preset PRISMATIC_CASCADE = register(new Preset(
            "prismatic_cascade",
            Component.translatable("preset." + FireworkShadersId.NS + ".prismatic_cascade"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.STRONTIUM_SALT, Chemical.CALCIUM_SALT,
                            Chemical.COPPER_HALIDE, Chemical.POTASSIUM_SALT))
                    .shape(ShellShape.CASCADE)
                    .modules(java.util.EnumSet.of(EffectModule.SPECTRUM, EffectModule.TRAIL,
                            EffectModule.SMOKE, EffectModule.LINGER))
                    .caliber(2.8F)
                    .build(),
            true,
            "A waterfall of stars that shifts through the entire RGB spectrum as it falls."));

    // ------------------------------------------------------------------ starters

    public static final Preset RED_PEONY = register(new Preset(
            "red_peony",
            Component.translatable("preset." + FireworkShadersId.NS + ".red_peony"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.STRONTIUM_SALT))
                    .shape(ShellShape.PEONY)
                    .modules(java.util.EnumSet.of(EffectModule.TRAIL))
                    .caliber(1.0F)
                    .build(),
            false,
            "The classic: one colour, one sphere, one clean break."));

    public static final Preset GOLDEN_CHRYSANTHEMUM = register(new Preset(
            "golden_chrysanthemum",
            Component.translatable("preset." + FireworkShadersId.NS + ".golden_chrysanthemum"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.ALUMINIUM))
                    .shape(ShellShape.CHRYSANTHEMUM)
                    .modules(java.util.EnumSet.of(EffectModule.TRAIL, EffectModule.CRACKLE))
                    .caliber(1.4F)
                    .build(),
            false,
            "Long golden trails with a crackling finish."));

    public static final Preset EMERALD_RING = register(new Preset(
            "emerald_ring",
            Component.translatable("preset." + FireworkShadersId.NS + ".emerald_ring"),
            null,
            RocketDesign.builder()
                    .palette(List.of(Chemical.BARIUM_SALT, Chemical.MAGNESIUM))
                    .shape(ShellShape.RING)
                    .modules(java.util.EnumSet.of(EffectModule.TRAIL, EffectModule.STROBE))
                    .caliber(1.6F)
                    .build(),
            false,
            "A green ring that strobes as it opens."));

    // ------------------------------------------------------------------ lookup

    private static Preset register(Preset preset) {
        ALL.add(preset);
        return preset;
    }

    public static List<Preset> all() {
        return List.copyOf(ALL);
    }

    public static List<Preset> premium() {
        List<Preset> out = new ArrayList<>();
        for (Preset p : ALL) {
            if (p.premium()) {
                out.add(p);
            }
        }
        return out;
    }

    @Nullable
    public static Preset byId(String id) {
        for (Preset p : ALL) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static Preset byIndex(int index) {
        List<Preset> all = all();
        if (all.isEmpty()) {
            throw new IllegalStateException("No presets registered");
        }
        return all.get(Math.floorMod(index, all.size()));
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private ShellPresets() {}
}
