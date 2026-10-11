package com.fireworkshaders.client.particle;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.common.particle.PyroParticleOptions;
import com.fireworkshaders.client.render.FSRenderTypes;
import com.fireworkshaders.pyro.PyroFlags;
import com.fireworkshaders.pyro.PyroMath;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Every particle this mod draws, in one place (vanilla does the same thing in
 * {@code FireworkParticles}), plus the providers that build them from
 * {@link PyroParticleOptions}.
 *
 * <p>Physics is hand-rolled rather than inherited from {@code Particle#tick}: the vanilla tick
 * applies one drag, one gravity and one collision sweep for every particle in the game, and a
 * firework star is exactly the particle that needs something else. Specifically:
 *
 * <ul>
 *   <li><b>Willow drape.</b> Gravity is a function of the star's age: near-zero for the first third
 *       of the burn (the star is still riding the break) and then ramping to full, which is what
 *       makes a willow hang and then <em>weep</em> instead of falling immediately.</li>
 *   <li><b>Wind.</b> Sampled from the level's own weather, so a storm visibly blows a canopy apart
 *       and a still night does not.</li>
 *   <li><b>No collision.</b> Firework stars are practically massless; they pass through leaves and
 *       fences. Running a 0.2-block AABB sweep for 900 of them would cost more than the rest of the
 *       mod put together, and it would look wrong.</li>
 *   <li><b>Ember raycast.</b> Embers are the exception: they <em>do</em> query the ground, because
 *       the query result is what the ground light pool is drawn from.</li>
 * </ul>
 */
public final class PyroParticles {

    private PyroParticles() {}

    // ================================================================== budget

    /** Soft cap so a finale degrades instead of freezing the client. */
    public static final class Budget {
        private static int live;

        private Budget() {}

        public static boolean allows(int flags) {
            if (PyroFlags.has(flags, PyroFlags.PRIORITY)) {
                return true;
            }
            return live < FSConfig.CLIENT.particleBudget.get();
        }

        static void acquire() {
            live++;
        }

        static void release() {
            if (live > 0) {
                live--;
            }
        }

        public static int live() {
            return live;
        }
    }

    // ================================================================== spark

    /**
     * The star: a hot, fast, bright particle that traces a ballistic arc, twinkles, sheds a trail
     * and pops with crackle at the end of its life.
     */
    public static class SparkParticle extends TextureSheetParticle {

        private final float exposure;
        private final float baseSize;
        private final double drag;
        private final double gravityScale;
        private final double windFactor;
        private final int flags;
        private final boolean spectrum;
        private final float spectrumPhase;
        private final float twinklePhase;
        private final float strobeRate;
        private final long seed;

        protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                                double vz, PyroParticleOptions options) {
            super(level, x, y, z);
            this.setParticleSpeed(vx, vy, vz);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;

            this.flags = options.flags();
            this.exposure = options.exposure();
            this.lifetime = Math.max(2, options.life());
            this.baseSize = 0.115F * options.scale();
            this.seed = new Random((long) (x * 341873128712L) ^ (long) (z * 132897987541L) ^ this.lifetime)
                    .nextLong();
            this.twinklePhase = (float) (this.seed % 1000L) / 1000.0F;
            this.strobeRate = 0.9F + (float) PyroMath.unitFloat(PyroMath.derive(this.seed, 3)) * 1.9F;
            this.spectrum = PyroFlags.has(this.flags, PyroFlags.SPECTRUM);
            this.spectrumPhase = (options.rgb() & 0xFFFF) / 65535.0F;

            // Light elements decelerate faster than heavy ones: this is what separates a
            // magnesium spark (stops and dies) from an ember (keeps going).
            this.drag = PyroFlags.has(this.flags, PyroFlags.DRAPE) ? 0.972 : 0.981;
            this.gravityScale = PyroFlags.has(this.flags, PyroFlags.DRAPE) ? 1.0 : 0.92;
            this.windFactor = PyroFlags.has(this.flags, PyroFlags.WIND) && FSConfig.CLIENT.windDrift.get()
                    ? 0.75 : 0.0;

            boolean shimmer = !this.spectrum;
            this.quadSize = this.baseSize;
            if (shimmer) {
                setColourFrom(options.rgb(), options.exposure());
            } else {
                setColourFrom(PyroMath.spectrum(this.spectrumPhase, 0.35F, 1.0F), options.exposure());
            }
            this.hasPhysics = false;
            this.gravity = 0.0F;
        }

        /** The behaviour bitmask this particle was spawned with (see {@code PyroFlags}). */
        protected int getFlags() {
            return this.flags;
        }

        protected void setColourFrom(int rgb, float exposure) {
            float gain = FSConfig.CLIENT.emissiveGain.get().floatValue() * exposure;
            int gained = PyroMath.gain(rgb, gain);
            this.rCol = PyroMath.red(gained);
            this.gCol = PyroMath.green(gained);
            this.bCol = PyroMath.blue(gained);
            // The very centre of a fresh star is white-hot regardless of its chemistry: the flame
            // front is hotter than the salt's emission line.
            float white = Mth.clamp(1.0F - this.age / 3.0F, 0.0F, 1.0F) * 0.55F;
            this.rCol = Mth.lerp(white, this.rCol, 1.0F);
            this.gCol = Mth.lerp(white, this.gCol, 1.0F);
            this.bCol = Mth.lerp(white, this.bCol, 1.0F);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;

            if (this.age++ >= this.lifetime) {
                crackleOut();
                remove();
                return;
            }

            float t = (float) this.age / this.lifetime;

            // ---- gravity, with the willow drape curve -------------------------------------
            double gravity = 0.045 * this.gravityScale;
            if (PyroFlags.has(this.flags, PyroFlags.DRAPE)) {
                // 0.18x gravity for the first 35% of the burn, ramping to 1.35x: the star hangs at
                // the top of the break and then starts sagging, exactly like a real willow.
                double ramp = t < 0.35 ? 0.18 : 0.18 + (t - 0.35) / 0.65 * 1.17;
                gravity *= ramp;
            }
            gravity *= 1.0 - 0.45 * PyroMath.smoothstep(t);

            double drag = Math.pow(this.drag * (1.0 - 0.06 * t), 1.0);

            double vy = (this.yd - gravity) * drag;
            double vx = this.xd * drag;
            double vz = this.zd * drag;

            // ---- wind ------------------------------------------------------------------------
            if (this.windFactor > 0.0) {
                double[] wind = PyroMath.windAt(this.level.getGameTime(), this.level.getRainLevel(0.0F),
                        this.level.getThunderLevel(0.0F), this.x, this.z);
                vx += wind[0] * this.windFactor * (0.4 + t);
                vz += wind[1] * this.windFactor * (0.4 + t);
                vy -= 0.0022 * this.windFactor * t;
            }

            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            setPos(this.x + vx, this.y + vy, this.z + vz);

            // ---- colour ----------------------------------------------------------------------
            if (this.spectrum) {
                // Prismatic Cascade: hue is a function of this particle's own age, offset by its
                // spawn phase, so the falling stream is banded rather than uniform.
                float hue = (this.spectrumPhase + t * 1.15F) % 1.0F;
                setColourFrom(PyroMath.spectrum(hue, 0.30F, 1.0F), this.exposure);
            } else if (t > 0.72F) {
                // Cooling: emission drops and the spark reads as a duller, redder ember.
                float cool = (t - 0.72F) / 0.28F;
                int cooled = PyroMath.lerpColor(0xFFFFFF, 0xFF6A18, cool * 0.85F);
                setColourFrom(PyroMath.multiply(cooled, 1.0F - cool * 0.35F), this.exposure);
            }

            // ---- alpha: twinkle, strobe, lifetime curve ---------------------------------------
            float alpha = PyroMath.lifeCurve(t, 0.02F, 0.55F);
            float twinkle = 0.78F + 0.22F * (float) Math.sin((this.age + this.twinklePhase * 20.0F) * 1.7F);
            alpha *= twinkle;
            if (PyroFlags.has(this.flags, PyroFlags.STROBE)) {
                // Square wave: a strobe shell is on/off, not a sine fade.
                float wave = (float) Math.sin((this.age * this.strobeRate + this.twinklePhase * 10.0F) * 1.9F);
                alpha *= wave > 0.0F ? 1.0F : 0.12F;
            }
            setAlpha(Mth.clamp(alpha, 0.0F, 1.0F));
            this.quadSize = this.baseSize * (0.75F + 0.55F * alpha);

            // ---- secondary emission ------------------------------------------------------------
            if (PyroFlags.has(this.flags, PyroFlags.TRAIL) && FSConfig.CLIENT.sparkTrails.get()
                    && this.random.nextFloat() < 0.42F) {
                emitTrailParticle();
            }
            if (PyroFlags.has(this.flags, PyroFlags.SMOKY) && this.age % 5 == 0
                    && this.random.nextFloat() < 0.5F) {
                emitTrailSmoke();
            }
        }

        private void emitTrailParticle() {
            int colour = PyroMath.multiply(PyroMath.rgb(this.rCol, this.gCol, this.bCol), 0.85F);
            this.level.addParticle(new PyroParticleOptions(PyroParticleOptions.Kind.GLITTER, colour,
                            1.05F, 0.55F + this.random.nextFloat() * 0.3F,
                            5 + this.random.nextInt(6), this.flags & ~PyroFlags.TRAIL),
                    this.x, this.y, this.z,
                    this.xd * 0.15 + (this.random.nextDouble() - 0.5) * 0.012,
                    this.yd * 0.15 + (this.random.nextDouble() - 0.5) * 0.012,
                    this.zd * 0.15 + (this.random.nextDouble() - 0.5) * 0.012);
        }

        private void emitTrailSmoke() {
            this.level.addParticle(new PyroParticleOptions(PyroParticleOptions.Kind.SMOKE,
                            PyroMath.multiply(PyroMath.rgb(this.rCol, this.gCol, this.bCol), 0.5F),
                            0.4F, 0.45F + this.random.nextFloat() * 0.25F, 60 + this.random.nextInt(60),
                            PyroFlags.WIND),
                    this.x, this.y, this.z, 0.0, -0.004, 0.0);
        }

        /** Titanium/antimony glitter popping at the end of the burn. */
        private void crackleOut() {
            if (!PyroFlags.has(this.flags, PyroFlags.CRACKLE)) {
                return;
            }
            int bursts = 1 + this.random.nextInt(2);
            for (int i = 0; i < bursts; i++) {
                this.level.addParticle(new PyroParticleOptions(PyroParticleOptions.Kind.GLITTER,
                                0xFFF6D8, 1.7F, 0.5F, 4 + this.random.nextInt(4),
                                PyroFlags.FULLBRIGHT | PyroFlags.PRIORITY | PyroFlags.STROBE),
                        this.x, this.y, this.z,
                        (this.random.nextDouble() - 0.5) * 0.16,
                        (this.random.nextDouble() - 0.5) * 0.16,
                        (this.random.nextDouble() - 0.5) * 0.16);
            }
        }

        @Override
        public float getQuadSize(float partialTick) {
            return this.quadSize;
        }

        @Override
        protected int getLightColor(float partialTick) {
            // Fully bright: this is the whole "emissive" trick. A shaderpack multiplies the
            // lightmap in; feeding it the maximum of both channels means the particle receives
            // 100% light and its texture brightness is all that matters.
            return PyroFlags.has(this.flags, PyroFlags.FULLBRIGHT)
                    ? 0xF000F0
                    : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.EMISSIVE;
        }

        @Override
        public void remove() {
            if (!this.removed) {
                Budget.release();
            }
            super.remove();
        }
    }

    // ================================================================== ember

    /**
     * A heavy ember: the part of a shell that outlives the sparks and paints the ground.
     *
     * <p>It raycasts downwards every few ticks; if it finds ground it draws an additive light pool
     * on it, scaled by how close the ember is. That is a fake, but it is the honest kind of fake:
     * it goes through the same shader the world does, it is occluded by geometry correctly, and it
     * costs one clip query per ember per four ticks.
     */
    public static class EmberParticle extends SparkParticle {

        private Vec3 groundHit;
        private float groundIntensity;
        private final float decalSize;

        protected EmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                                double vz, PyroParticleOptions options) {
            super(level, x, y, z, vx, vy, vz, options);
            this.tuneEmber();
            this.decalSize = 2.2F + options.scale() * 1.4F;
            this.gravity = 0.0F;
        }

        private void tuneEmber() {
            // Embers are the only element with a genuinely heavy ballistic arc.
            this.quadSize *= 0.85F;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) {
                return;
            }
            if (PyroFlags.has(getFlags(), PyroFlags.GROUND_GLOW) && FSConfig.CLIENT.groundGlowDecals.get()
                    && this.age % 4 == 0) {
                updateGround();
            }
            // Embers flicker and gutter rather than fading cleanly.
            float flicker = 0.55F + 0.45F * (float) Math.abs(Math.sin(this.age * 0.55F + this.x));
            setAlpha(Mth.clamp(getAlpha() * flicker, 0.0F, 1.0F));
        }

        private float getAlpha() {
            float t = (float) this.age / this.lifetime;
            return PyroMath.lifeCurve(t, 0.03F, 0.5F);
        }

        private void updateGround() {
            Vec3 from = new Vec3(this.x, this.y, this.z);
            Vec3 to = from.add(0.0, -18.0, 0.0);
            BlockHitResult hit = this.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, null));
            if (hit.getType() == HitResult.Type.BLOCK) {
                this.groundHit = hit.getLocation();
                float height = (float) (this.y - hit.getLocation().y);
                this.groundIntensity = Mth.clamp(1.0F - height / 18.0F, 0.0F, 1.0F);
            } else {
                this.groundHit = null;
                this.groundIntensity = 0.0F;
            }
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, Camera camera,
                           float partialTick) {
            super.render(buffer, camera, partialTick);
            if (this.groundHit == null || this.groundIntensity <= 0.01F) {
                return;
            }
            Vec3 camPos = camera.getPosition();
            float x = (float) (this.groundHit.x - camPos.x());
            float y = (float) (this.groundHit.y + 0.035 - camPos.y());
            float z = (float) (this.groundHit.z - camPos.z());
            float size = this.decalSize * (0.55F + this.groundIntensity * 0.75F);
            float alpha = this.groundIntensity * 0.42F
                    * PyroMath.lifeCurve((float) this.age / this.lifetime, 0.05F, 0.7F);
            float r = this.rCol;
            float g = this.gCol;
            float b = this.bCol;
            float u0 = 0.0F;
            float u1 = 1.0F;
            int light = 0xF000F0;
            buffer.vertex(x - size, y, z - size).uv(u0, u0).color(r, g, b, alpha).uv2(light).endVertex();
            buffer.vertex(x - size, y, z + size).uv(u0, u1).color(r, g, b, alpha).uv2(light).endVertex();
            buffer.vertex(x + size, y, z + size).uv(u1, u1).color(r, g, b, alpha).uv2(light).endVertex();
            buffer.vertex(x + size, y, z - size).uv(u1, u0).color(r, g, b, alpha).uv2(light).endVertex();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.EMBER;
        }
    }

    // ================================================================== smoke

    public static class SmokeParticle extends TextureSheetParticle {

        private final float growth;
        private final double windFactor;
        private final boolean dark;

        protected SmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                                double vz, PyroParticleOptions options) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.lifetime = Math.max(20, options.life());
            this.quadSize = 0.55F * options.scale();
            this.growth = 0.55F * options.scale() / this.lifetime;
            this.windFactor = PyroFlags.has(options.flags(), PyroFlags.WIND)
                    && FSConfig.CLIENT.windDrift.get() ? 1.0 : 0.0;
            this.dark = options.rgb() == 0x9AA0A6 || options.rgb() == 0x000000;

            float tint = 0.55F;
            int rgb = options.rgb();
            this.rCol = Mth.lerp(tint, PyroMath.red(rgb), 0.30F);
            this.gCol = Mth.lerp(tint, PyroMath.green(rgb), 0.30F);
            this.bCol = Mth.lerp(tint, PyroMath.blue(rgb), 0.32F);
            if (this.dark) {
                this.rCol = this.gCol = this.bCol = 0.22F;
            }
            this.hasPhysics = false;
            this.roll = this.random.nextFloat() * PyroMath.TAU;
            this.oRoll = this.roll;
            this.gravity = 0.0F;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                remove();
                return;
            }
            float t = (float) this.age / this.lifetime;

            // Smoke is buoyant while it is hot and then settles: a burst leaves a cloud that keeps
            // rising for a second or two before the drag of the air wins.
            double buoyancy = 0.0125 * (1.0F - PyroMath.smoothstep(t)) - 0.0016 * t;
            double drag = 0.986;
            this.xd *= drag;
            this.zd *= drag;
            this.yd = this.yd * drag + buoyancy;

            if (this.windFactor > 0.0) {
                double[] wind = PyroMath.windAt(this.level.getGameTime(), this.level.getRainLevel(0.0F),
                        this.level.getThunderLevel(0.0F), this.x, this.z);
                this.xd += wind[0] * this.windFactor * 0.5;
                this.zd += wind[1] * this.windFactor * 0.5;
            }

            setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);

            this.quadSize += this.growth * (1.0F - t * 0.5F);
            setAlpha(Mth.clamp(PyroMath.lifeCurve(t, 0.12F, 0.75F) * 0.42F, 0.0F, 1.0F));
            this.roll += 0.004F + this.random.nextFloat() * 0.003F;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LevelRenderer.getLightColor(this.level, BlockPos.containing(this.x, this.y, this.z));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.SMOKE;
        }

        @Override
        public void remove() {
            if (!this.removed) {
                Budget.release();
            }
            super.remove();
        }
    }

    // ================================================================== flash

    /** The core flash: white, huge, and gone in a quarter of a second. */
    public static class FlashParticle extends TextureSheetParticle {

        protected FlashParticle(ClientLevel level, double x, double y, double z, PyroParticleOptions options) {
            super(level, x, y, z);
            this.lifetime = Math.max(2, options.life());
            this.quadSize = 1.4F * options.scale();
            float exposure = options.exposure() * FSConfig.CLIENT.emissiveGain.get().floatValue() * 0.55F;
            this.rCol = Mth.lerp(0.35F, PyroMath.red(options.rgb()), 1.0F) * Mth.clamp(exposure, 0.5F, 2.0F);
            this.gCol = Mth.lerp(0.35F, PyroMath.green(options.rgb()), 1.0F) * Mth.clamp(exposure, 0.5F, 2.0F);
            this.bCol = Mth.lerp(0.35F, PyroMath.blue(options.rgb()), 1.0F) * Mth.clamp(exposure, 0.5F, 2.0F);
            this.hasPhysics = false;
            this.gravity = 0.0F;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                remove();
                return;
            }
            float t = (float) this.age / this.lifetime;
            // The flash expands fast and decelerates: it is an expanding ball of plasma, not a
            // growing window.
            this.quadSize *= 1.32F + 0.20F * (1.0F - t);
            setAlpha(Mth.clamp((1.0F - t) * (1.0F - t), 0.0F, 1.0F));
        }

        @Override
        public float getQuadSize(float partialTick) {
            return this.quadSize;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.EMISSIVE;
        }

        @Override
        public void remove() {
            if (!this.removed) {
                Budget.release();
            }
            super.remove();
        }
    }

    // ================================================================== shockwave

    /**
     * One billboard of the pressure shell. A shockwave is rendered as a shell of these expanding
     * outwards, which reads correctly from any viewing angle — a single sprite cannot.
     */
    public static class ShockwaveParticle extends TextureSheetParticle {

        private final float growth;

        protected ShockwaveParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                                    double vz, PyroParticleOptions options) {
            super(level, x, y, z);
            this.setParticleSpeed(vx, vy, vz);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.lifetime = Math.max(4, options.life());
            this.quadSize = 0.6F * options.scale();
            this.growth = (1.9F * options.scale()) / this.lifetime;
            this.rCol = PyroMath.red(options.rgb());
            this.gCol = PyroMath.green(options.rgb());
            this.bCol = PyroMath.blue(options.rgb());
            this.hasPhysics = false;
            this.roll = this.random.nextFloat() * PyroMath.TAU;
            this.oRoll = this.roll;
            this.friction = 0.94F;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                remove();
                return;
            }
            float t = (float) this.age / this.lifetime;
            // The shell decelerates hard: a real blast wave loses most of its speed almost at once.
            this.xd *= this.friction;
            this.yd *= this.friction;
            this.zd *= this.friction;
            setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
            this.quadSize += this.growth * (1.0F - t * 0.7F);
            setAlpha(Mth.clamp((1.0F - t) * 0.85F, 0.0F, 1.0F));
        }

        @Override
        public float getQuadSize(float partialTick) {
            return this.quadSize;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.EMISSIVE;
        }

        @Override
        public void remove() {
            if (!this.removed) {
                Budget.release();
            }
            super.remove();
        }
    }

    // ================================================================== glitter

    /** Crackle/strobe fleck: tiny, extremely bright, extremely short lived. */
    public static class GlitterParticle extends SparkParticle {

        protected GlitterParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                                  double vz, PyroParticleOptions options) {
            super(level, x, y, z, vx, vy, vz, options);
            this.quadSize *= 0.5F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return FSRenderTypes.EMISSIVE;
        }
    }

    // ================================================================== dust

    /** Fine metallic dust: a soft, dim, wide break rather than discrete stars. */
    public static class DustParticle extends SparkParticle {

        protected DustParticle(ClientLevel level, double x, double y, double z, double vx, double vy,
                               double vz, PyroParticleOptions options) {
            super(level, x, y, z, vx, vy, vz, options);
            this.quadSize *= 1.6F;
            setAlpha(0.5F);
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.removed) {
                setAlpha(Mth.clamp(getAlpha() * 0.55F, 0.0F, 1.0F));
            }
        }

        private float getAlpha() {
            return PyroMath.lifeCurve((float) this.age / this.lifetime, 0.05F, 0.6F);
        }
    }

    // ================================================================== providers

    public static final class Providers {

        private Providers() {}

        private static <T extends Particle> T acquire(T particle) {
            Budget.acquire();
            return particle;
        }

        public static final class Spark implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Spark(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                if (!Budget.allows(options.flags())) {
                    return null;
                }
                SparkParticle particle = new SparkParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Ember implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Ember(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                if (!Budget.allows(options.flags())) {
                    return null;
                }
                EmberParticle particle = new EmberParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Smoke implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Smoke(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                double density = FSConfig.CLIENT.smokeDensity.get();
                if (density <= 0.0D || level.random.nextDouble() > density) {
                    return null;
                }
                if (!Budget.allows(options.flags())) {
                    return null;
                }
                SmokeParticle particle = new SmokeParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Flash implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Flash(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                if (FSConfig.CLIENT.flashIntensity.get() <= 0.0D) {
                    return null;
                }
                FlashParticle particle = new FlashParticle(level, x, y, z, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Shockwave implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Shockwave(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                ShockwaveParticle particle = new ShockwaveParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Glitter implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Glitter(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                if (!Budget.allows(options.flags())) {
                    return null;
                }
                GlitterParticle particle = new GlitterParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }

        public static final class Dust implements ParticleProvider<PyroParticleOptions> {
            private final SpriteSet sprites;

            public Dust(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(PyroParticleOptions options, ClientLevel level, double x,
                                           double y, double z, double vx, double vy, double vz) {
                if (!Budget.allows(options.flags())) {
                    return null;
                }
                DustParticle particle = new DustParticle(level, x, y, z, vx, vy, vz, options);
                particle.pickSprite(this.sprites);
                return acquire(particle);
            }
        }
    }
}
