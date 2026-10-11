package com.fireworkshaders.client.gui;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.pyro.BurstPatterns;
import com.fireworkshaders.pyro.PyroMath;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The live burst preview in the workstation.
 *
 * <p>This is real geometry, not a picture: it calls the same {@link BurstPatterns#cast} the
 * explosion does, projects the star directions with a slowly rotating camera, and animates the break
 * from a point to full radius on a loop. If you design a heart, you get a heart; if you change the
 * caliber, the canopy grows; if you add a second colour, alternating stars change colour. A static
 * icon could not do any of that, and a player who can see their shell before spending gunpowder on
 * it designs far more interesting shells.
 *
 * <p>Drawn with {@code GuiGraphics#fill} rather than textured quads so per-star tinting works
 * without touching the global shader colour — the glow is approximated with three nested squares.
 */
public final class BurstPreview {

    private static final int PREVIEW_STARS = 132;

    private static int cachedHash = Integer.MIN_VALUE;
    private static List<BurstPatterns.ShellStar> cachedStars = List.of();

    private BurstPreview() {}

    public static void render(GuiGraphics graphics, RocketDesign design, int x, int y, int size,
                              float time) {
        if (!FSConfig.CLIENT.showPreviewInGui.get()) {
            return;
        }
        List<BurstPatterns.ShellStar> stars = starsFor(design);
        if (stars.isEmpty()) {
            return;
        }

        int centreX = x + size / 2;
        int centreY = y + size / 2;
        float cycle = (time * 0.30F) % 1.0F;
        // Expand over the first 60% of the cycle, hold, then fade: the pause matters, because that
        // is when the shell is at its most beautiful.
        float expand = PyroMath.easeOutCubic(Math.min(1.0F, cycle / 0.6F));
        float fade = cycle < 0.72F ? 1.0F : 1.0F - (cycle - 0.72F) / 0.28F;
        float maxRadius = size * 0.44F;

        // Slow turntable plus a fixed 32 degree tilt: a flat ring reads as a ring, not a line.
        double rotation = time * 0.45F;
        double cosY = Math.cos(rotation);
        double sinY = Math.sin(rotation);
        double tilt = 0.58;
        double cosT = Math.cos(tilt);
        double sinT = Math.sin(tilt);

        for (BurstPatterns.ShellStar star : stars) {
            Vec3 dir = star.dir();
            double radius = maxRadius * star.speedMul() * expand;
            double dx = dir.x * radius;
            double dy = dir.y * radius;
            double dz = dir.z * radius;
            double rx = dx * cosY - dz * sinY;
            double rz = dx * sinY + dz * cosY;
            double ry = dy * cosT - rz * sinT;
            double depth = dy * sinT + rz * cosT;

            int screenX = centreX + (int) Math.round(rx);
            int screenY = centreY - (int) Math.round(ry);
            // Depth cueing: nearer stars are bigger and brighter. Cheap, and it turns a flat
            // scatter into something with volume.
            float depthFactor = (float) PyroMath.clamp(0.5 + depth / (maxRadius * 2.2), 0.15, 1.0);
            float alpha = fade * depthFactor;
            if (alpha <= 0.02F) {
                continue;
            }
            int colour = design.colorRgb(star.colorIndex() < 0 ? 0 : star.colorIndex());
            colour = PyroMath.gain(colour, 1.5F);
            int core = 1 + (int) (depthFactor * 1.6F);
            int outerAlpha = (int) (alpha * 70.0F) << 24;
            int midAlpha = (int) (alpha * 140.0F) << 24;
            int coreAlpha = (int) (alpha * 235.0F) << 24;
            graphics.fill(screenX - core - 1, screenY - core - 1, screenX + core + 2,
                    screenY + core + 2, outerAlpha | (colour & 0xFFFFFF));
            graphics.fill(screenX - core, screenY - core, screenX + core + 1, screenY + core + 1,
                    midAlpha | (colour & 0xFFFFFF));
            graphics.fill(screenX, screenY, screenX + 1, screenY + 1,
                    coreAlpha | 0xFFFFFF);
        }

        // The break point itself, brightest at the start of the cycle.
        if (cycle < 0.35F) {
            float core = 1.0F - cycle / 0.35F;
            int glow = (int) (core * 200.0F) << 24;
            graphics.fill(centreX - 2, centreY - 2, centreX + 3, centreY + 3, glow | 0xFFFFFF);
        }
    }

    private static List<BurstPatterns.ShellStar> starsFor(RocketDesign design) {
        int hash = design.hashCode();
        if (hash != cachedHash) {
            cachedHash = hash;
            // A fixed seed: the preview must be stable while the player tweaks an unrelated field,
            // otherwise the shape jitters on every click and comparisons become impossible.
            cachedStars = BurstPatterns.cast(design, PREVIEW_STARS, new Vec3(0.0, 0.0, 1.0), 0x5EEDL);
        }
        return cachedStars;
    }

    public static void invalidate() {
        cachedHash = Integer.MIN_VALUE;
    }
}
