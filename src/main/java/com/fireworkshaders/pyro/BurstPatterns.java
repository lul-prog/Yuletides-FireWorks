package com.fireworkshaders.pyro;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry engine. Given a shape and a seed, produce the point cloud of stars for one break.
 *
 * <p>Everything here is deterministic and side-agnostic, which is the whole reason the mod can
 * afford 900-particle shells: the server sends the design and the seed and every client runs this
 * same code to get the same explosion. No per-particle packets, and — more importantly — the shape
 * is <em>the same shape</em> for everybody watching, so a heart is a heart for the whole server.
 *
 * <p>Distribution notes that matter visually:
 * <ul>
 *   <li>Fibonacci (golden angle) spheres pack evenly with no visible seam. A {@code random}
 *       sphere clumps and reads as a fuzzy blob.</li>
 *   <li>Per-star jitter is applied to speed and burn time, never to direction: jittered
 *       directions destroy the silhouette, jittered burn times make it look alive.</li>
 *   <li>Radius is encoded in <em>speed</em>, not direction (directions are unit vectors), which is
 *       why {@link ShellStar#speedMul} exists — it is how a double ring gets two radii.</li>
 * </ul>
 */
public final class BurstPatterns {

    /** One star of a break: where it goes, how fast, and which palette slot paints it. */
    public record ShellStar(Vec3 dir, float speedMul, int colorIndex) {

        public static ShellStar of(Vec3 dir) {
            return new ShellStar(dir, 1.0F, -1);
        }

        public static ShellStar of(Vec3 dir, float speedMul) {
            return new ShellStar(dir, speedMul, -1);
        }

        public ShellStar withColour(int index) {
            return new ShellStar(this.dir, this.speedMul, index);
        }

        public ShellStar withSpeed(float mul) {
            return new ShellStar(this.dir, mul, this.colorIndex);
        }
    }

    private BurstPatterns() {}

    // ------------------------------------------------------------------ primitives

    public static List<Vec3> sphere(int count, double flatten, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        double golden = PyroMath.GOLDEN_ANGLE_RAD;
        for (int i = 0; i < count; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / count;
            y *= (1.0 - flatten);
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = golden * i;
            out.add(new Vec3(Math.cos(theta) * r, y, Math.sin(theta) * r).normalize());
        }
        return out;
    }

    public static List<Vec3> canopy(int count, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        double golden = PyroMath.GOLDEN_ANGLE_RAD;
        for (int i = 0; i < count; i++) {
            double y = 1.0 - (i + 0.5) / count;
            double flare = Math.pow(1.0 - y, 0.42);
            y = 0.14 + 0.86 * Math.pow(Math.max(0.0, y), 1.22) - 0.10 * flare;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = golden * i;
            out.add(new Vec3(Math.cos(theta) * r, y, Math.sin(theta) * r).normalize());
        }
        return out;
    }

    public static List<Vec3> ring(int count, double thickness, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double a = (i + 0.5) / count * Math.PI * 2.0 + PyroMath.randSigned(seed, 11 + i) * 0.012;
            double tilt = PyroMath.randSigned(seed, 5000 + i) * thickness;
            out.add(new Vec3(Math.cos(a), tilt, Math.sin(a)).normalize());
        }
        return out;
    }

    public static List<Vec3> ringStanding(int count, double thickness, long seed) {
        List<Vec3> flat = ring(count, thickness, seed);
        List<Vec3> out = new ArrayList<>(count);
        for (Vec3 v : flat) {
            out.add(new Vec3(v.x, v.z, -v.y));   // 90° about X
        }
        return out;
    }

    // ------------------------------------------------------------------ shaped breaks

    /** Weeping willow: an uneven canopy, so the drape reads as cloth rather than a lampshade. */
    public static List<Vec3> willow(int count, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        List<Vec3> base = canopy(count, seed);
        for (int i = 0; i < base.size(); i++) {
            Vec3 v = base.get(i);
            double droop = PyroMath.rand(seed, 6000 + i);
            double s = 0.92 + droop * 0.20;
            out.add(new Vec3(v.x * s, v.y * (0.86 + droop * 0.30), v.z * s).normalize());
        }
        return out;
    }

    /** Waterfall: everything pushed outwards and downwards before it hangs. */
    public static List<Vec3> cascade(int count, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        double golden = PyroMath.GOLDEN_ANGLE_RAD;
        for (int i = 0; i < count; i++) {
            double y = 0.55 - 1.25 * (i + 0.5) / count;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = golden * i;
            out.add(new Vec3(Math.cos(a) * r, y, Math.sin(a) * r).normalize());
        }
        return out;
    }

    /** Ten to fourteen thick rays. Needs trails to look like anything. */
    public static List<Vec3> palm(int count, long seed) {
        int rays = 10 + (int) (PyroMath.rand(seed, 42) * 5.0);
        List<Vec3> anchors = canopy(rays, seed);
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec3 anchor = anchors.get(i % rays);
            double jitter = 0.10;
            out.add(anchor.add(PyroMath.randSigned(seed, 300 + i) * jitter,
                    PyroMath.randSigned(seed, 1300 + i) * jitter * 0.6,
                    PyroMath.randSigned(seed, 2300 + i) * jitter).normalize());
        }
        return out;
    }

    /** Single heavy tail with a bright burning head. */
    public static List<Vec3> comet(int count, long seed) {
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double spread = 0.05 + t * 0.12;
            double a = PyroMath.rand(seed, 900 + i) * Math.PI * 2.0;
            double rad = spread * Math.sqrt(PyroMath.rand(seed, 1800 + i));
            out.add(new Vec3(Math.cos(a) * rad, 1.0, Math.sin(a) * rad).normalize());
        }
        return out;
    }

    /** Parametric heart, thrown as a flat face orthonormal to {@code facing}. */
    public static List<Vec3> heart(int count, Vec3 facing, long seed) {
        Vec3 normal = facing.normalize();
        Vec3 helper = Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = helper.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count * Math.PI * 2.0;
            double hx = 16.0 * Math.pow(Math.sin(t), 3.0);
            double hy = 13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t) - 2.0 * Math.cos(3 * t)
                    - Math.cos(4 * t);
            // Half the stars sit at 60-85% of the outline radius: a hollow heart reads as a loop,
            // not as a heart.
            double fill = (i % 2 == 0) ? 1.0 : 0.60 + PyroMath.rand(seed, i) * 0.25;
            Vec3 v = right.scale(hx * 0.055 * fill)
                    .add(up.scale(hy * 0.055 * fill))
                    .add(normal.scale(PyroMath.randSigned(seed, 77 + i) * 0.035));
            out.add(v.normalize());
        }
        return out;
    }

    /** Five-pointed star outline with a sparse interior fill. */
    public static List<Vec3> star(int count, Vec3 facing, long seed) {
        Vec3 normal = facing.normalize();
        Vec3 helper = Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = helper.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();
        List<Vec3> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i % 4 == 0) {
                double a = PyroMath.rand(seed, 400 + i) * PyroMath.TAU;
                double rad = Math.sqrt(PyroMath.rand(seed, 800 + i)) * 0.40;
                out.add(right.scale(Math.cos(a) * rad).add(up.scale(Math.sin(a) * rad)).normalize());
                continue;
            }
            double t = (i + 0.5) / count;
            double a = t * PyroMath.TAU - Math.PI / 2.0;
            double wobble = (Math.cos(5.0 * a) + 1.0) * 0.5;
            double rad = 0.42 + 0.58 * PyroMath.smoothstep((float) wobble);
            out.add(right.scale(Math.cos(a) * rad)
                    .add(up.scale(Math.sin(a) * rad))
                    .add(normal.scale(PyroMath.randSigned(seed, 55 + i) * 0.025))
                    .normalize());
        }
        return out;
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * Build the star cloud for a design.
     *
     * @param facing horizontal direction towards the audience; only the flat pattern shells
     *               (heart, star) use it, and the shell derives it from its launch bearing.
     */
    public static List<ShellStar> cast(RocketDesign design, int count, Vec3 facing, long seed) {
        ShellShape shape = design.shape();
        int palette = Math.max(1, design.palette().size());
        List<ShellStar> out = new ArrayList<>(count);

        switch (shape) {
            case PEONY, CHRYSANTHEMUM, SPHERE -> {
                for (Vec3 v : sphere(count, shape == ShellShape.SPHERE ? 0.06 : 0.0, seed)) {
                    out.add(ShellStar.of(v));
                }
            }
            case WILLOW, CASCADE -> {
                List<Vec3> dirs = shape == ShellShape.WILLOW ? willow(count, seed) : cascade(count, seed);
                for (int i = 0; i < dirs.size(); i++) {
                    // Willow stars leave slow: they must still be there when gravity wins.
                    float speed = shape == ShellShape.WILLOW ? 0.86F : 0.94F;
                    speed *= 0.94F + PyroMath.rand(seed, 12000 + i) * 0.12F;
                    out.add(ShellStar.of(dirs.get(i), speed));
                }
            }
            case RING -> {
                // A ring reads best slightly inclined; a perfectly flat one seen from above
                // collapses into a line. Half the shells come out tilted.
                double tilt = PyroMath.rand(seed, 3) < 0.5 ? 0.0 : 0.22;
                for (Vec3 v : ring(count, 0.05 + PyroMath.rand(seed, 4) * 0.05, seed)) {
                    out.add(ShellStar.of(tiltRing(v, tilt)));
                }
            }
            case DOUBLE_RING -> {
                int inner = Math.max(6, count / 3);
                int i = 0;
                for (Vec3 v : ring(count - inner, 0.045, seed)) {
                    out.add(ShellStar.of(v, 1.0F).withColour(0));
                    i++;
                }
                for (Vec3 v : ring(inner, 0.045, PyroMath.derive(seed, 5))) {
                    out.add(ShellStar.of(v, 0.52F + PyroMath.rand(seed, 100 + i) * 0.06F)
                            .withColour(palette > 1 ? 1 : 0));
                    i++;
                }
            }
            case CROSSETTE -> {
                int half = count / 2;
                int i = 0;
                for (Vec3 v : ring(half, 0.035, seed)) {
                    out.add(ShellStar.of(v, 1.0F).withColour(i % palette));
                    i++;
                }
                for (Vec3 v : ringStanding(count - half, 0.035, PyroMath.derive(seed, 3))) {
                    out.add(ShellStar.of(v, 0.82F).withColour((i + 1) % palette));
                    i++;
                }
            }
            case PALM -> {
                for (Vec3 v : palm(count, seed)) {
                    out.add(ShellStar.of(v, 0.90F + PyroMath.rand(seed, 77 + out.size()) * 0.22F));
                }
            }
            case SATURN -> {
                int ringCount = Math.max(8, count / 3);
                int i = 0;
                for (Vec3 v : sphere(count - ringCount, 0.0, seed)) {
                    out.add(ShellStar.of(v).withColour(0));
                    i++;
                }
                for (Vec3 v : ring(ringCount, 0.05, PyroMath.derive(seed, 7))) {
                    out.add(ShellStar.of(new Vec3(v.x, v.y * 0.14, v.z).normalize(),
                            0.76F + PyroMath.rand(seed, 200 + i) * 0.05F)
                            .withColour(palette > 1 ? 1 : 0));
                    i++;
                }
            }
            case HEART -> {
                for (Vec3 v : heart(count, facing, seed)) {
                    out.add(ShellStar.of(v, 0.94F + PyroMath.rand(seed, 300 + out.size()) * 0.10F));
                }
            }
            case STAR -> {
                for (Vec3 v : star(count, facing, seed)) {
                    out.add(ShellStar.of(v, 0.94F + PyroMath.rand(seed, 300 + out.size()) * 0.10F));
                }
            }
            case COMET -> {
                List<Vec3> dirs = comet(count, seed);
                for (int i = 0; i < dirs.size(); i++) {
                    out.add(ShellStar.of(dirs.get(i), 0.55F + 0.5F * (i / (float) dirs.size())));
                }
            }
        }

        // Multi-colour shells alternate their stars — that is how a real 2-colour shell works, and
        // it reads far better than blending, which just looks like a muddy third colour.
        if (palette > 1) {
            for (int i = 0; i < out.size(); i++) {
                ShellStar s = out.get(i);
                if (s.colorIndex() < 0) {
                    out.set(i, s.withColour(i % palette));
                }
            }
        }
        return out;
    }

    private static Vec3 tiltRing(Vec3 v, double tilt) {
        if (tilt <= 0.0) {
            return v;
        }
        // Rotate the ring's plane about the X axis: a purely flat ring hides itself when the
        // spectator is at eye level with the pad.
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos).normalize();
    }
}
