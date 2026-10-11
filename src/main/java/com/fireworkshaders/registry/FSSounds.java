package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events for the layered audio.
 *
 * <p>These are <em>optional</em>. The mod ships without .ogg assets on purpose: encoding high
 * quality firework audio is a job for an audio engineer with a recorder and a licence, not for a
 * code repository. Instead:
 *
 * <ul>
 *   <li>if you drop your own files in {@code assets/fireworkshaders/sounds/fireworks/} and add a
 *       matching {@code sounds.json} (see {@code sounds.json.template}), the audio mixer uses them,</li>
 *   <li>if you do not, the mixer layers vanilla's own blast events with distance, air absorption
 *       and echo modelling — which is where most of the perceived quality comes from anyway.</li>
 * </ul>
 *
 * <p>The custom events are only referenced after a resource-existence probe, so a missing file can
 * never produce a "unable to play unknown sound event" log spam.
 */
public final class FSSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FireworkShaders.MOD_ID);

    public static final RegistryObject<SoundEvent> BOOM = register("fireworks.boom");
    public static final RegistryObject<SoundEvent> BOOM_FAR = register("fireworks.boom_far");
    public static final RegistryObject<SoundEvent> ECHO = register("fireworks.echo");
    public static final RegistryObject<SoundEvent> CRACKLE = register("fireworks.crackle");
    public static final RegistryObject<SoundEvent> WHISTLE = register("fireworks.whistle");
    public static final RegistryObject<SoundEvent> SHRIEK = register("fireworks.shriek");
    public static final RegistryObject<SoundEvent> LAUNCH = register("fireworks.launch");

    private static RegistryObject<SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(FireworkShaders.MOD_ID, path)));
    }

    private FSSounds() {}
}
