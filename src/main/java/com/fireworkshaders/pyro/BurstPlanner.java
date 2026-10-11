package com.fireworkshaders.pyro;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link RocketDesign} plus a seed into a {@link BurstPlan}.
 *
 * <p>This is where the physics of a real break lives:
 * <ul>
 *   <li>Stars get the shell's break velocity, scaled per star by the pattern's speed multiplier,
 *       with a few percent of jitter so the surface of the break is not mathematically perfect.</li>
 *   <li>Heavy elements (embers, smoke, shells) leave <em>slower</em> than the stars because they
 *       are heavier — that mass separation is why a real willow's smoke hangs below its stars.</li>
 *   <li>Smoke and embers are the only things that get a downward bias at spawn; a star that starts
 *       falling has already lost, and it looks wrong.</li>
 * </ul>
 *
 * <p>Determinism contract: for a fixed (design, seed, origin, facing, budget) this method returns
 * the same plan. Trimming for the budget removes entries from a fixed order, so a client with a
 * smaller budget sees the same explosion with fewer stars rather than a different explosion.
 */
public final class BurstPlanner {

    /** Hard cap on glitter micro-sparks per shell; they are cheap individually but add up fast. */
    private static final int MAX_GLITTER = 220;

    private BurstPlanner() {}

    public static BurstPlan plan(RocketDesign design, Vec3 origin, long seed, Vec3 facing,
                                 int budget) {
        List<BurstPlan.Spawn> core = new ArrayList<>();
        List<BurstPlan.Spawn> stars = new ArrayList<>();
        List<BurstPlan.Spawn> soft = new ArrayList<>();
        List<BurstPlan.SubShell> children = new ArrayList<>();

        Vec3 face = facing.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0)
                : new Vec3(facing.x, 0.0, facing.z).normalize();

        float power = design.power();
        double breakSpeed = design.burstSpeed();
        float paletteIntensity = design.intensity();
        int flags = design.flags();
        double radius = design.burstRadius();

        // ---- 1. the flash core -------------------------------------------------------------
        boolean flash = design.has(EffectModule.FLASH) || design.caliber() >= 2.2F;
        if (flash) {
            core.add(new BurstPlan.Spawn(origin.x, origin.y, origin.z, 0.0, 0.0, 0.0,
                    BurstPlan.Kind.FLASH, 0xFFFFFF, 1.45F * paletteIntensity,
                    1.6F + design.caliber() * 1.7F, 7, flags | PyroFlags.PRIORITY
                    | PyroFlags.FLASH_CORE | PyroFlags.FULLBRIGHT));
            core.add(new BurstPlan.Spawn(origin.x, origin.y, origin.z, 0.0, 0.02, 0.0,
                    BurstPlan.Kind.FLASH, design.colorAt(0.0F), 1.2F, 1.05F + design.caliber(),
                    11, flags | PyroFlags.PRIORITY | PyroFlags.FLASH_CORE));
        }

        // ---- 2. the pressure shell ----------------------------------------------------------
        if (design.has(EffectModule.SHOCKWAVE)) {
            int rings = design.caliber() >= 2.5F ? 96 : 64;
            List<Vec3> shell = BurstPatterns.sphere(rings, 0.0, PyroMath.derive(seed, 91));
            for (int i = 0; i < shell.size(); i++) {
                Vec3 dir = shell.get(i);
                double speed = 0.72 + PyroMath.rand(seed, 700 + i) * 0.10;
                core.add(new BurstPlan.Spawn(
                        origin.x + dir.x * 0.6, origin.y + dir.y * 0.6, origin.z + dir.z * 0.6,
                        dir.x * speed, dir.y * speed, dir.z * speed,
                        BurstPlan.Kind.SHOCKWAVE, 0xEAF6FF, 1.35F, 1.35F + design.caliber() * 0.5F,
                        26 + (int) (design.caliber() * 6.0F),
                        PyroFlags.PRIORITY | PyroFlags.FULLBRIGHT));
            }
            // The ground ring: a horizontal disc that races outwards across the terrain. This is
            // the single most "big budget film" element in the mod.
            List<Vec3> disc = ring(128);
            for (int i = 0; i < disc.size(); i++) {
                Vec3 dir = disc.get(i);
                double speed = 1.55 + PyroMath.rand(seed, 950 + i) * 0.25;
                core.add(new BurstPlan.Spawn(origin.x, origin.y + 0.35, origin.z,
                        dir.x * speed, -0.012, dir.z * speed,
                        BurstPlan.Kind.SHOCKWAVE, 0xFFFFFF, 0.95F, 1.9F,
                        30, PyroFlags.PRIORITY | PyroFlags.FULLBRIGHT));
            }
        }

        // ---- 3. the stars -------------------------------------------------------------------
        int starCount = design.starCount();
        int perStarGlitter = flags(PyroFlags.CRACKLE) ? Math.min(MAX_GLITTER, starCount / 3) : 0;
        List<BurstPatterns.ShellStar> cloud = BurstPatterns.cast(design, starCount, face, seed);

        for (int i = 0; i < cloud.size(); i++) {
            BurstPatterns.ShellStar star = cloud.get(i);
            int colorIndex = star.colorIndex() < 0 ? 0 : star.colorIndex();
            Chemical chemical = design.palette().get(Math.floorMod(colorIndex, design.palette().size()));
            int rgb = chemical.effectiveRgb();

            double jitter = 0.94 + PyroMath.rand(seed, 20_000 + i) * 0.12;
            double speed = breakSpeed * star.speedMul() * jitter;
            // A shell that breaks low into still air opens slightly squashed; add a touch of
            // vertical variation so the canopy is not a perfect sphere.
            double vy = star.dir().y * speed * (0.96 + PyroMath.rand(seed, 40_000 + i) * 0.09);
            double vx = star.dir().x * speed;
            double vz = star.dir().z * speed;

            boolean spectrum = design.has(EffectModule.SPECTRUM);
            float lifeScale = 0.88F + PyroMath.rand(seed, 30_000 + i) * 0.28F;
            int life = Math.round(design.burnTicks() * lifeScale);

            stars.add(new BurstPlan.Spawn(origin.x, origin.y, origin.z, vx, vy, vz,
                    spectrum ? BurstPlan.Kind.SPECTRUM
                            : (design.has(EffectModule.DUST) ? BurstPlan.Kind.DUST : BurstPlan.Kind.SPARK),
                    rgb, chemical.intensity() * (flash ? 1.05F : 1.0F),
                    0.95F + PyroMath.rand(seed, 50_000 + i) * 0.35F, life, flags));

            // Comet heads: the first few stars of a comet tail burn much brighter and longer.
            if (design.shape() == ShellShape.COMET && i < 6) {
                stars.set(stars.size() - 1, new BurstPlan.Spawn(origin.x, origin.y, origin.z,
                        vx * 0.7, vy * 0.7, vz * 0.7, BurstPlan.Kind.SPARK, 0xFFFFFF,
                        1.4F, 1.7F, life, flags | PyroFlags.FLASH_CORE));
            }
        }

        // ---- 4. crackle glitter --------------------------------------------------------------
        // Deliberately NOT spread evenly: crackle in real shells comes from discrete grains that
        // pop at the very end of the burn, so the glitter clusters at the break shell.
        for (int i = 0; i < perStarGlitter; i++) {
            Vec3 dir = BurstPatterns.sphere(1, 0.0, PyroMath.derive(seed, 60_000 + i)).get(0);
            double speed = breakSpeed * (0.72 + PyroMath.rand(seed, 61_000 + i) * 0.55);
            int life = Math.round(design.burnTicks() * (0.30F + PyroMath.rand(seed, 62_000 + i) * 0.35F));
            stars.add(new BurstPlan.Spawn(origin.x, origin.y, origin.z,
                    dir.x * speed, dir.y * speed, dir.z * speed,
                    BurstPlan.Kind.GLITTER, 0xFFF6D8, 1.5F, 0.55F, life,
                    flags | PyroFlags.CRACKLE | PyroFlags.STROBE));
        }

        // ---- 5. smoke ------------------------------------------------------------------------
        float smoke = design.smokeAmount();
        int smokeCount = Math.round(smoke * 5.5F * design.caliber());
        for (int i = 0; i < smokeCount; i++) {
            Vec3 dir = BurstPatterns.sphere(1, 0.0, PyroMath.derive(seed, 70_000 + i)).get(0);
            double speed = breakSpeed * 0.16 * (0.5 + PyroMath.rand(seed, 71_000 + i));
            // Smoke rises: the air is still hot for a second or two after the break.
            double vy = dir.y * speed * 0.6 + 0.012 + PyroMath.rand(seed, 72_000 + i) * 0.02;
            soft.add(new BurstPlan.Spawn(
                    origin.x + dir.x * 1.1, origin.y + dir.y * 1.1, origin.z + dir.z * 1.1,
                    dir.x * speed, vy, dir.z * speed,
                    BurstPlan.Kind.SMOKE, design.colorAt(PyroMath.rand(seed, 73_000 + i)),
                    0.55F, 1.5F + smoke * 1.5F + design.caliber() * 0.6F,
                    Math.round(90 + PyroMath.rand(seed, 74_000 + i) * 70),
                    PyroFlags.WIND | PyroFlags.SMOKY));
        }

        // ---- 6. heavy embers -------------------------------------------------------------------
        int embers = design.emberCount();
        for (int i = 0; i < embers; i++) {
            Vec3 dir = BurstPatterns.sphere(1, 0.0, PyroMath.derive(seed, 80_000 + i)).get(0);
            // Embers are mass-separated: they leave the break at half the star speed and slightly
            // downwards, which is exactly why the real ones fall out of the bottom of a shell.
            double speed = breakSpeed * (0.28 + PyroMath.rand(seed, 81_000 + i) * 0.30);
            int color = design.colorAt(PyroMath.rand(seed, 82_000 + i));
            soft.add(new BurstPlan.Spawn(origin.x, origin.y - 0.4, origin.z,
                    dir.x * speed, dir.y * speed - 0.06, dir.z * speed,
                    BurstPlan.Kind.EMBER, PyroMath.lerpColor(color, 0xFF7A22, 0.35F),
                    0.95F, 1.0F + PyroMath.rand(seed, 83_000 + i) * 0.7F,
                    Math.round(140 + PyroMath.rand(seed, 84_000 + i) * 120),
                    flags | PyroFlags.SMOKY | PyroFlags.BOUNCE));
        }

        // ---- 7. multi-break children -----------------------------------------------------------
        boolean canSplit = design.has(EffectModule.SHELL_OF_SHELLS) && design.hasPayload();
        if (canSplit) {
            int shells = 8;
            for (int i = 0; i < shells; i++) {
                double angle = (i + 0.5) / shells * Math.PI * 2.0 + PyroMath.rand(seed, 90_000 + i) * 0.15;
                double speed = breakSpeed * 0.62 * (0.9 + PyroMath.rand(seed, 91_000 + i) * 0.2);
                double up = -0.16 - PyroMath.rand(seed, 92_000 + i) * 0.16;
                children.add(new BurstPlan.SubShell(
                        Math.cos(angle) * speed, speed * up, Math.sin(angle) * speed,
                        18 + (int) (PyroMath.rand(seed, 93_000 + i) * 16.0)));
            }
        }

        // ---- 8. budget -------------------------------------------------------------------------
        List<BurstPlan.Spawn> merged = new ArrayList<>(core);
        int softBudget = Math.max(0, budget - merged.size());
        // Smoke and embers are the first thing to go: they are the cheapest to lose visually
        // because the sparks and the flash are what the eye actually tracks.
        int softTake = Math.min(soft.size(), softBudget / 3);
        merged.addAll(soft.subList(0, softTake));
        int starBudget = Math.max(0, budget - merged.size());
        merged.addAll(stars.subList(0, Math.min(stars.size(), starBudget)));

        int lightPulses = design.has(EffectModule.GROUND_GLOW)
                ? Math.min(12, 3 + Math.round(design.caliber() * 3.0F))
                : (design.caliber() >= 2.0F ? 3 : 1);
        return new BurstPlan(seed, origin, power, lightPulses, merged, children);
    }

    private static boolean flags(int mask) {
        return mask != 0;
    }

    /** 128 points on a horizontal disc, evenly spaced with a little radial jitter. */
    private static List<Vec3> ring(int count) {
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double a = (i + 0.5) / count * Math.PI * 2.0;
            out.add(new Vec3(Math.cos(a), 0.0, Math.sin(a)));
        }
        return out;
    }
}
