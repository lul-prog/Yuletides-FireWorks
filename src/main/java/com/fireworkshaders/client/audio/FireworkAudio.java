package com.fireworkshaders.client.audio;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.fx.FxBus;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The sound design.
 *
 * <p>Fireworks are the one effect in the game where <em>delay is the realism</em>. Light reaches you
 * instantly, sound does not: a shell breaking 200 blocks away is heard almost two thirds of a second
 * late, and by the time it arrives the high frequencies have been eaten by the air, so it is a
 * thump rather than a crack. Vanilla plays a firework sound at the moment of the break with a
 * distance-based volume and nothing else, which is why fireworks in modded Minecraft have always
 * felt "small" regardless of how many particles they throw.
 *
 * <p>This mixer builds each break out of layers rather than playing one sample:
 *
 * <ol>
 *   <li><b>crack</b> — the high transient, delayed by distance/343 m·s⁻¹,</li>
 *   <li><b>body</b> — the main report, two ticks behind the crack (the shell casing arriving),</li>
 *   <li><b>sub</b> — a sub-bass thump that only large shells get, which is what you feel in a car
 *       park,</li>
 *   <li><b>tails</b> — two to four echoes at increasing spacing and decreasing pitch, standing in
 *       for the reflections off terrain and buildings that a real display has and a single sample
 *       cannot,</li>
 *   <li><b>crackle</b> — a scatter of tight pops over the following two seconds for shells with the
 *       crackling module.</li>
 * </ol>
 *
 * <p>If you provide your own recordings (see {@code sounds.json.template} and {@link
 * com.fireworkshaders.registry.FSSounds}) the mixer uses them in place of the vanilla stand-ins. The
 * probe is done once, on the client, and the result cached, so a missing asset can never spam the
 * log or leave you with silent fireworks.
 */
public final class FireworkAudio implements FxBus.AudioHook {

    /** Real speed of sound, in blocks (metres) per tick at 20 TPS. */
    private static final double SOUND_BLOCKS_PER_TICK = 343.0 / 20.0;

    /**
     * One scheduled layer. {@code at} is the position the sound comes <em>from</em>: echoes are
     * resolved at the original break, not at the listener, which is what makes a distant shell sound
     * like it is over there rather than inside your head.
     */
    private record Queued(SoundEvent event, float volume, float pitch, int delay,
                          @Nullable Vec3 at) {
    }

    private static final Deque<Queued> QUEUE = new ArrayDeque<>();

    @Nullable
    private static Boolean customAudioAvailable;

    // ------------------------------------------------------------------ FxBus hooks

    @Override
    public void onShellLaunch(Entity shell, RocketDesign design) {
        if (!FSConfig.CLIENT.layeredAudio.get()) {
            return;
        }
        double volume = FSConfig.CLIENT.masterVolume.get();
        Vec3 at = shell.position();
        // The lift: a whoosh that rises as the shell accelerates away. Two layers, six ticks apart,
        // so the pop of ignition and the rush of the lift charge read as one event.
        enqueue(pick(SoundEvents.FIREWORK_ROCKET_LAUNCH, FSSounds.LAUNCH), (float) (0.85 * volume),
                0.85F, 0, at);
        enqueue(pick(SoundEvents.FIREWORK_ROCKET_LAUNCH, FSSounds.LAUNCH), (float) (0.55 * volume),
                1.15F, 6, at);
        if (design.has(EffectModule.WHISTLE)) {
            enqueue(pick(SoundEvents.FIREWORK_ROCKET_SHOOT, FSSounds.WHISTLE),
                    (float) (0.7 * volume), 1.4F, 10, at);
            enqueue(pick(SoundEvents.FIREWORK_ROCKET_SHOOT, FSSounds.WHISTLE),
                    (float) (0.6 * volume), 1.7F, 18, at);
            enqueue(pick(SoundEvents.FIREWORK_ROCKET_SHOOT, FSSounds.WHISTLE),
                    (float) (0.45 * volume), 1.95F, 28, at);
        }
        if (design.has(EffectModule.SHRIEK)) {
            // A shriek is a sawtooth, not a boom: the phantom swoop is the closest vanilla stand-in
            // for the Doppler-edged howl of a shell going transonic.
            enqueue(pick(SoundEvents.PHANTOM_SWOOP, FSSounds.SHRIEK), (float) (0.9 * volume), 1.75F,
                    4, at);
            enqueue(pick(SoundEvents.PHANTOM_SWOOP, FSSounds.SHRIEK), (float) (0.65 * volume),
                    1.45F, 16, at);
        }
    }

    @Override
    public void onShellDepart(Entity shell, RocketDesign design) {
        onShellLaunch(shell, design);
    }

    // ------------------------------------------------------------------ bursts

    /**
     * Builds and schedules the acoustic event for one break.
     *
     * @param distance blocks between the listener and the break at the moment it happened
     */
    public static void playBurst(RocketDesign design, Vec3 origin, double distance) {
        if (!FSConfig.CLIENT.layeredAudio.get()) {
            return;
        }
        float master = FSConfig.CLIENT.masterVolume.get().floatValue();
        if (master <= 0.0F) {
            return;
        }

        // Geometric attenuation. Real sound falls off at 6 dB per doubling of distance; the world
        // is also loud, so we keep the falloff gentle enough that a show 150 blocks away still
        // reads as loud.
        double attenuation = 1.0 / (1.0 + distance / 26.0);
        double airAbsorption = Math.max(0.62, 1.0 - distance / 900.0);
        float power = design.power();

        int delay = FSConfig.CLIENT.speedOfSoundDelay.get()
                ? (int) Math.round(distance / SOUND_BLOCKS_PER_TICK)
                : 0;

        boolean far = distance > 70.0;

        // 1. crack
        enqueue(far ? pick(SoundEvents.FIREWORK_ROCKET_BLAST_FAR, FSSounds.BOOM_FAR)
                        : pick(SoundEvents.FIREWORK_ROCKET_BLAST, FSSounds.BOOM),
                (float) (clampVolume(1.4 * attenuation * master)), (float) (1.25 * airAbsorption),
                delay, origin);
        // 2. body
        enqueue(far ? pick(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, FSSounds.BOOM_FAR)
                        : pick(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, FSSounds.BOOM),
                (float) (clampVolume(1.6 * attenuation * master * Math.min(1.4, power))),
                (float) (0.92 * airAbsorption), delay + 2, origin);
        // 3. sub-bass, only for shells that deserve it
        if (power >= 1.2F) {
            enqueue(SoundEvents.GENERIC_EXPLODE,
                    (float) (clampVolume(1.1 * attenuation * master * power * 0.5)),
                    (float) (0.52 * airAbsorption), delay + 3, origin);
        }
        // 4. echoes
        int echoes = FSConfig.CLIENT.echoCount.get();
        for (int i = 0; i < echoes; i++) {
            int spread = 16 + i * 26 + (int) (distance * 0.35);
            float volume = (float) clampVolume(0.75 * attenuation * master / (1.4 + i * 0.8));
            float pitch = (float) (0.62 - i * 0.07) * (float) airAbsorption;
            enqueue(pick(SoundEvents.LIGHTNING_BOLT_THUNDER, FSSounds.ECHO), volume, pitch,
                    delay + spread, origin);
        }
        // 5. crackle tail
        if (design.has(EffectModule.CRACKLE)) {
            int pops = 4 + (int) (design.caliber() * 3.0F);
            for (int i = 0; i < pops; i++) {
                int when = delay + 8 + (int) (Math.random() * 34);
                enqueue(i % 3 == 0 ? pick(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, FSSounds.CRACKLE)
                                : pick(SoundEvents.FIREWORK_ROCKET_TWINKLE, FSSounds.CRACKLE),
                        (float) clampVolume(0.5 * attenuation * master),
                        (float) (1.0 + Math.random() * 0.6), when, origin);
            }
        }
    }

    private static double clampVolume(double volume) {
        return Math.max(0.0, Math.min(3.0, volume));
    }

    // ------------------------------------------------------------------ scheduling

    /**
     * Chooses between the shipped vanilla stand-in and a pack author's recording. The {@code
     * fireworks.*} events are only referenced once the resource probe has confirmed the files exist,
     * so a player without them never sees a missing-sound warning.
     */
    private static SoundEvent pick(SoundEvent vanilla, java.util.function.Supplier<SoundEvent> custom) {
        if (hasCustomAudio()) {
            SoundEvent event = custom.get();
            if (event != null) {
                return event;
            }
        }
        return vanilla;
    }

    private static void enqueue(SoundEvent event, float volume, float pitch, int delay,
                               @Nullable Vec3 at) {
        if (volume <= 0.01F) {
            return;
        }
        if (QUEUE.size() > 256) {
            // A pathological finale must not turn into a memory leak; the oldest layers are the
            // ones already playing anyway.
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Queued(event, Math.min(volume, 3.0F), Math.max(0.4F, Math.min(pitch, 2.2F)),
                Math.max(0, delay), at));
    }

    /** Called from the client tick event: this is the echo scheduler running down its delays. */
    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        int size = QUEUE.size();
        for (int i = 0; i < size; i++) {
            Queued queued = QUEUE.pollFirst();
            if (queued == null) {
                break;
            }
            if (queued.delay() > 0) {
                QUEUE.addLast(new Queued(queued.event(), queued.volume(), queued.pitch(),
                        queued.delay() - 1, queued.at()));
            } else {
                play(queued.event(), queued.pitch(), queued.volume(), queued.at());
            }
        }
    }

    /**
     * Plays one layer. Positional layers use {@link SoundInstance.Attenuation#NONE} with real
     * coordinates: the vanilla attenuator would apply a second, harsher distance falloff on top of
     * the mixer's own air-absorption model, but dropping the coordinates entirely would collapse the
     * stereo image and make a show 200 blocks away sound like it is happening inside the player's
     * head. Unattenuated-but-positioned is what a real firework sounds like from a lawn chair.
     */
    private static void play(SoundEvent event, float pitch, float volume, @Nullable Vec3 at) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        if (at == null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),
                SoundSource.AMBIENT, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    public static void clear() {
        QUEUE.clear();
    }

    // ------------------------------------------------------------------ optional custom assets

    /**
     * True when the pack/mod provides real recordings. The probe is a single resource lookup and the
     * result is cached for the session, so adding .ogg files and a sounds.json is all a pack author
     * has to do to replace the vanilla stand-ins.
     */
    public static boolean hasCustomAudio() {
        if (customAudioAvailable == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return false;
            }
            // 1.20.1: getResource returns Optional<Resource> and a Resource holds no open stream,
            // so existence is the probe — there is nothing to close.
            customAudioAvailable = minecraft.getResourceManager().getResource(
                    new ResourceLocation(FireworkShaders.MOD_ID, "sounds/fireworks/boom.ogg")).isPresent();
        }
        return customAudioAvailable;
    }

    /** Drops the cached probe so a resource reload can pick up newly added audio. */
    public static void invalidateAssetProbe() {
        customAudioAvailable = null;
    }
}
