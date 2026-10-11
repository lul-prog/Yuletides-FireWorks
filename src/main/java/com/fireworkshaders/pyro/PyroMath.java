package com.fireworkshaders.pyro;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Deterministic maths used by every shell. Nothing in here touches the world, which is what lets
 * the server compute a burst once and have every client reproduce it exactly from (design, seed).
 */
public final class PyroMath {

    public static final float TAU = (float) (Math.PI * 2.0);
    public static final double GOLDEN_ANGLE_RAD = Math.PI * (3.0 - Math.sqrt(5.0));

    private PyroMath() {}

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Smooth 0..1 ramp. */
    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    /** Sparks fade on a curve; linear fades read as "flipping a switch" and look cheap. */
    public static float lifeCurve(float t, float fadeIn, float fadeOut) {
        t = clamp01(t);
        if (t < fadeIn) {
            return smoothstep(t / Math.max(1.0E-4F, fadeIn));
        }
        float out = (t - fadeIn) / Math.max(1.0E-4F, 1.0F - fadeIn);
        if (out > (1.0F - fadeOut)) {
            return 1.0F - smoothstep((out - (1.0F - fadeOut)) / Math.max(1.0E-4F, fadeOut));
        }
        return 1.0F;
    }

    // ------------------------------------------------------------------ colour

    public static int rgb(float r, float g, float b) {
        int ri = (int) (clamp01(r) * 255.0F) & 0xFF;
        int gi = (int) (clamp01(g) * 255.0F) & 0xFF;
        int bi = (int) (clamp01(b) * 255.0F) & 0xFF;
        return (ri << 16) | (gi << 8) | bi;
    }

    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    /**
     * HDR-style gain. Emissive particles write vertex colours, which the vertex format stores as
     * unsigned bytes and therefore clamps to 1.0 — so instead of pushing the colour above white we
     * saturate the hue towards its own maximum and let additive blending stack the brightness of
     * overlapping sparks. That is what makes a burst read as "blooming" under a shaderpack.
     */
    public static int gain(int rgb, float gain) {
        if (gain <= 1.0F) {
            return rgb;
        }
        float r = red(rgb);
        float g = green(rgb);
        float b = blue(rgb);
        float m = Math.max(r, Math.max(g, b));
        if (m <= 1.0E-4F) {
            return rgb;
        }
        float k = Mth.clamp(1.0F + (gain - 1.0F) * 0.85F, 1.0F, 3.0F);
        return rgb(r + (1.0F - r) * (k - 1.0F) * 0.55F,
                g + (1.0F - g) * (k - 1.0F) * 0.55F,
                b + (1.0F - b) * (k - 1.0F) * 0.55F);
    }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        return rgb(Mth.lerp(t, red(a), red(b)),
                Mth.lerp(t, green(a), green(b)),
                Mth.lerp(t, blue(a), blue(b)));
    }

    public static int multiply(int rgb, float f) {
        return rgb(red(rgb) * f, green(rgb) * f, blue(rgb) * f);
    }

    public static int hsv(float hue, float sat, float val) {
        return Mth.hsvToRgb(hue - (float) Math.floor(hue), clamp01(sat), clamp01(val));
    }

    /**
     * The Prismatic Cascade ramp: a reciprocal ("RAINBOW") Hue sweep that spends equal perceptual
     * time in every band instead of the compressed magenta-green sprint a naive HSV sweep gives.
     */
    public static int spectrum(float t, float saturation, float value) {
        float h = t - (float) Math.floor(t);
        // Four anchors: red, green, blue, red — interpolated in linear-ish steps so the hue walk
        // looks like a prism rather than a hexagon.
        int c;
        float x = h * 3.0F;
        int band = (int) x;
        float f = x - band;
        switch (band) {
            case 0 -> c = lerpColor(0xFF2A18, 0x3BFF5A, smoothstep(f));
            case 1 -> c = lerpColor(0x3BFF5A, 0x2A6CFF, smoothstep(f));
            default -> c = lerpColor(0x2A6CFF, 0xFF2A18, smoothstep(f));
        }
        float v = 0.72F + 0.28F * value;
        return rgb(Mth.lerp(saturation, 1.0F, red(c)) * v,
                Mth.lerp(saturation, 1.0F, green(c)) * v,
                Mth.lerp(saturation, 1.0F, blue(c)) * v);
    }

    // ------------------------------------------------------------------ random

    /** Cheap splitmix64 step; deterministic across the client/server boundary. */
    public static long mix(long z) {
        z = (z + 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long derive(long seed, int salt) {
        return mix(seed ^ (0x632BE59BD9B4E019L * (salt + 1L)));
    }

    public static float unitFloat(long hashed) {
        return (float) ((hashed >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    public static float rand(long seed, int salt) {
        return unitFloat(derive(seed, salt));
    }

    public static float rand(long seed, int salt, float lo, float hi) {
        return lo + rand(seed, salt) * (hi - lo);
    }

    public static double randSigned(long seed, int salt) {
        return rand(seed, salt) * 2.0 - 1.0;
    }

    /** Roughly normal, three samples is plenty and much cheaper than a real Box-Muller. */
    public static double gaussian(RandomSource rng) {
        return (rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 1.5) / 0.75;
    }

    public static double[] spherical(double radius, long seed, int salt) {
        double u = rand(seed, salt) * 2.0 - 1.0;
        double theta = rand(seed, salt + 977) * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - u * u));
        return new double[]{r * Math.cos(theta) * radius, u * radius, r * Math.sin(theta) * radius};
    }

    // ------------------------------------------------------------------ atmosphere

    /**
     * Wind is sampled from the level's weather, not from a random walk: two low-frequency sine
     * terms plus a gust envelope that ramps with the rain level, so a show during a thunderstorm
     * visibly blows sideways and a clear night is calm.
     */
    public static double[] windAt(long gameTime, float rainLevel, float thunderLevel, double x, double z) {
        double t = gameTime * 0.0055;
        double gust = 0.35 + rainLevel * 1.6 + thunderLevel * 1.1;
        double wx = Math.sin(t + x * 0.0131) * 0.6 + Math.sin(t * 2.7 + z * 0.0077) * 0.25;
        double wz = Math.cos(t * 0.87 + z * 0.0129) * 0.6 + Math.cos(t * 3.1 + x * 0.0061) * 0.25;
        return new double[]{wx * gust * 0.021, wz * gust * 0.021};
    }
}
