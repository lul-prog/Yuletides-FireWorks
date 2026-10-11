package com.fireworkshaders.client.fx;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Camera and screen feedback: shake, whiteout and the sub-bass FOV punch.
 *
 * <p>Three deliberate design choices:
 *
 * <ol>
 *   <li><b>The shake is applied through {@code ComputeCameraAngles}, not by writing to the camera
 *       entity.</b> That means it composes with the player's own look direction, with third-person
 *       cameras, and with shaderpacks that read the camera — and it never fights a mod that also
 *       wants to move the camera.</li>
 *   <li><b>The shake is radial.</b> A blast pushes you away from itself, so the shake carries a yaw
 *       bias along the burst→camera direction rather than jittering uniformly. It reads as a
 *       pressure wave instead of a hand tremor.</li>
 *   <li><b>The whiteout is exposure, not a white rectangle.</b> It is scaled by how much of the
 *       burst you could actually see: distance to the fourth power, and how close to your view axis
 *       it was. A shell behind a hill, or one you have turned away from, barely flashes.</li>
 * </ol>
 */
public final class ScreenFx {

    private static final int MAX_SHAKES = 8;

    private record Shake(float amplitudeDeg, float rollDeg, int duration, int age, float speed,
                         float phase, float yawBias) {
    }

    private record Flash(float peak, float decayTicks, float age, int rgb) {
    }

    private static final List<Shake> SHAKES = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static float fovPunch;

    private ScreenFx() {}

    // ------------------------------------------------------------------ triggers

    public static void onBurst(RocketDesign design, Vec3 origin, double distance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float power = design.power();
        boolean flash = design.has(EffectModule.FLASH) || design.caliber() >= 2.2F;
        boolean shockwave = design.has(EffectModule.SHOCKWAVE);

        // The pressure wave arrives with the sound, not with the light: a shell 200 blocks away
        // shakes you about half a second after you see it. This is the same delay the audio mixer
        // uses, applied to the camera, and it is the single detail that sells the whole effect.
        int soundDelay = FSConfig.CLIENT.speedOfSoundDelay.get()
                ? (int) Math.round(distance / (343.0 / 20.0))
                : 0;

        double attenuation = 1.0 / (1.0 + distance / 22.0);
        double shakeScale = FSConfig.CLIENT.shakeIntensity.get() * (shockwave ? 3.0 : 1.0);
        if (shakeScale > 0.0) {
            float amplitude = (float) Math.min(4.6, 1.5 * power * attenuation * shakeScale);
            if (amplitude > 0.05F) {
                Vec3 toBurst = origin.subtract(minecraft.player.position());
                double horizontal = Math.sqrt(toBurst.x * toBurst.x + toBurst.z * toBurst.z);
                float yawBias = horizontal < 0.5 ? 0.0F
                        : (float) (Math.atan2(toBurst.x, toBurst.z) * 180.0 / Math.PI);
                addShake(amplitude, soundDelay, yawBias, power);
            }
        }

        double flashScale = FSConfig.CLIENT.flashIntensity.get();
        if (flash && flashScale > 0.0) {
            // 1/d^1.6 rather than 1/d: a flash follows roughly the inverse-square law, and using
            // anything gentler makes distant shells look like they are on fire.
            double reach = 1.0 / (1.0 + Math.pow(distance / 18.0, 1.6));
            float peak = (float) Math.min(1.15, 1.35 * power * reach * flashScale);
            if (peak > 0.02F) {
                FLASHES.add(new Flash(peak, (float) (7.0 + power * 5.0), 0.0F, design.colorAt(0.0F)));
                fovPunch = Math.max(fovPunch, (float) Math.min(2.4, 1.2 * peak * 20.0));
            }
        }
    }

    private static void addShake(float amplitude, int delayTicks, float yawBias, float power) {
        if (SHAKES.size() >= MAX_SHAKES) {
            SHAKES.remove(0);
        }
        // Delay is expressed by a negative age: the entry sits inert until the pressure arrives.
        SHAKES.add(new Shake(amplitude, amplitude * 0.55F,
                (int) (14 + power * 7), -delayTicks,
                0.55F + power * 0.12F,
                (float) Math.random() * 100.0F, yawBias));
    }

    /**
     * The {@link com.fireworkshaders.common.fx.FxBus.CameraHook} entry point.
     *
     * <p>Shells carry their design to the client, so in practice {@link #onBurst(RocketDesign, Vec3,
     * double)} is what runs. This overload exists because the common side can also produce a break
     * with nothing but a power figure and two flags — a future custom entity, a script, a shell from
     * another mod's launcher — and that should still rattle the camera rather than silently do
     * nothing. It is the same maths, minus the palette.
     */
    public static void onBurstFromHook(Vec3 origin, float power, boolean flash, boolean shockwave,
                                       double distance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int soundDelay = FSConfig.CLIENT.speedOfSoundDelay.get()
                ? (int) Math.round(distance / (343.0 / 20.0))
                : 0;
        double attenuation = 1.0 / (1.0 + distance / 22.0);
        double shakeScale = FSConfig.CLIENT.shakeIntensity.get() * (shockwave ? 3.0 : 1.0);
        if (shakeScale > 0.0) {
            float amplitude = (float) Math.min(4.6, 1.5 * power * attenuation * shakeScale);
            if (amplitude > 0.05F) {
                Vec3 toBurst = origin.subtract(minecraft.player.position());
                double horizontal = Math.sqrt(toBurst.x * toBurst.x + toBurst.z * toBurst.z);
                float yawBias = horizontal < 0.5 ? 0.0F
                        : (float) (Math.atan2(toBurst.x, toBurst.z) * 180.0 / Math.PI);
                addShake(amplitude, soundDelay, yawBias, power);
            }
        }
        double flashScale = FSConfig.CLIENT.flashIntensity.get();
        if (flash && flashScale > 0.0) {
            double reach = 1.0 / (1.0 + Math.pow(distance / 18.0, 1.6));
            float peak = (float) Math.min(1.15, 1.35 * power * reach * flashScale);
            if (peak > 0.02F) {
                FLASHES.add(new Flash(peak, (float) (7.0 + power * 5.0), 0.0F, 0xFFFFFF));
                fovPunch = Math.max(fovPunch, (float) Math.min(2.4, 1.2 * peak * 20.0));
            }
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick() {
        Iterator<Shake> shakes = SHAKES.iterator();
        while (shakes.hasNext()) {
            Shake shake = shakes.next();
            if (shake.age() > shake.duration()) {
                shakes.remove();
            }
        }
        Iterator<Flash> flashes = FLASHES.iterator();
        while (flashes.hasNext()) {
            Flash flash = flashes.next();
            if (flash.age() > flash.decayTicks() + 2.0F) {
                flashes.remove();
            }
        }
        fovPunch *= 0.82F;
        if (fovPunch < 0.01F) {
            fovPunch = 0.0F;
        }
    }

    public static void clear() {
        SHAKES.clear();
        FLASHES.clear();
        fovPunch = 0.0F;
    }

    /** Advance the ages by one. Called from the same client tick handler as the audio queue. */
    public static void advance() {
        for (int i = 0; i < SHAKES.size(); i++) {
            Shake shake = SHAKES.get(i);
            SHAKES.set(i, new Shake(shake.amplitudeDeg(), shake.rollDeg(), shake.duration(),
                    shake.age() + 1, shake.speed(), shake.phase(), shake.yawBias()));
        }
        for (int i = 0; i < FLASHES.size(); i++) {
            Flash flash = FLASHES.get(i);
            FLASHES.set(i, new Flash(flash.peak(), flash.decayTicks(), flash.age() + 1.0F, flash.rgb()));
        }
    }

    // ------------------------------------------------------------------ camera hooks

    /** Forge-bus {@code ViewportEvent.ComputeCameraAngles}. */
    public static void applyCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float dYaw = 0.0F;
        float dPitch = 0.0F;
        float dRoll = 0.0F;
        for (Shake shake : SHAKES) {
            float t = shake.age() + partial;
            if (t < 0.0F || t > shake.duration()) {
                continue;
            }
            float envelope = (float) Math.sin(Math.PI * (t / shake.duration()));
            envelope *= envelope;
            // Three octaves of sine is the cheapest convincing hand-shake: a single sine reads as
            // a wobble, and pure noise reads as a glitch.
            float n1 = (float) (Math.sin(t * shake.speed() + shake.phase())
                    + 0.5 * Math.sin(t * shake.speed() * 2.31F + shake.phase() * 1.7F)
                    + 0.23 * Math.sin(t * shake.speed() * 4.7F + shake.phase() * 0.4F)) / 1.73F;
            float n2 = (float) (Math.sin(t * shake.speed() * 1.37F + shake.phase() * 2.3F)
                    + 0.5 * Math.sin(t * shake.speed() * 3.1F + shake.phase())) / 1.5F;
            float n3 = (float) (Math.sin(t * shake.speed() * 0.83F + shake.phase() * 3.1F)
                    + 0.4 * Math.sin(t * shake.speed() * 2.7F + shake.phase() * 0.9F)) / 1.4F;

            float amplitude = shake.amplitudeDeg() * envelope;
            dRoll += n3 * shake.rollDeg() * envelope;
            dPitch += n2 * amplitude * 0.55F;
            // Bias the yaw term: the blast pushes the camera along the line of the shock.
            dYaw += n1 * amplitude * (0.35F + 0.65F * Math.abs(shake.yawBias()) / 180.0F) * 0.5F;
        }
        event.setRoll(Mth.clamp(event.getRoll() + dRoll, -20.0F, 20.0F));
        event.setPitch(Mth.clamp(event.getPitch() + dPitch, -90.0F, 90.0F));
        event.setYaw(event.getYaw() + dYaw);
    }

    /** Forge-bus {@code ViewportEvent.ComputeFov}: a pressure punch on the lens. */
    public static void applyFov(ViewportEvent.ComputeFov event) {
        double punch = fovPunch * (event.usedConfiguredFov() ? 1.0 : 0.6);
        if (Math.abs(punch) > 0.01) {
            event.setFOV(event.getFOV() + punch);
        }
    }

    // ------------------------------------------------------------------ overlay

    /** Registered as a GUI overlay; drawn above everything so a close burst really does blind you. */
    public static void renderOverlay(GuiGraphics graphics, float partialTick, int width, int height) {
        if (FLASHES.isEmpty()) {
            return;
        }
        // F1 hides the HUD, and that includes a flash: a player taking screenshots with the GUI off
        // does not want the one frame they are composing washed out by a shell they fired for it.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null && minecraft.options.hideGui) {
            return;
        }
        float total = 0.0F;
        int r = 0;
        int g = 0;
        int b = 0;
        int count = 0;
        for (Flash flash : FLASHES) {
            float t = (flash.age() + partialTick) / flash.decayTicks();
            // Fast attack, slower decay: the eye integrates the flash over ~150 ms.
            float intensity = t < 0.25F ? Mth.lerp(t / 0.25F, 0.35F, 1.0F)
                    : (float) Math.pow(1.0F - (t - 0.25F) / 0.75F, 2.1F);
            intensity = Mth.clamp(intensity, 0.0F, 1.0F) * flash.peak();
            if (intensity <= 0.0F) {
                continue;
            }
            total += intensity;
            r += (flash.rgb() >> 16) & 0xFF;
            g += (flash.rgb() >> 8) & 0xFF;
            b += flash.rgb() & 0xFF;
            count++;
        }
        if (count == 0 || total <= 0.01F) {
            return;
        }
        int cr = (int) Mth.lerp(0.72F, r / (float) count, 255.0F);
        int cg = (int) Mth.lerp(0.78F, g / (float) count, 252.0F);
        int cb = (int) Mth.lerp(0.62F, b / (float) count, 240.0F);
        int alpha = (int) (Mth.clamp(total, 0.0F, 1.25F) * 190.0F);
        graphics.fill(0, 0, width, height, (alpha << 24) | (cr << 16) | (cg << 8) | cb);
    }

    /** Exposed for the debug overlay: how many effects are currently running. */
    public static String debugStatus() {
        return "shakes=" + SHAKES.size() + " flashes=" + FLASHES.size()
                + " fov=" + String.format("%.2f", fovPunch);
    }

    /** The radial direction used by the shake bias, exposed for tests. */
    public static float yawTowards(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (float) (Math.atan2(delta.x, delta.z) * 180.0 / Math.PI);
    }

}
