package com.fireworkshaders.common.fx;

import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The seam between the two sides.
 *
 * <p>Entities are common code, but a shell launching needs to start a layered audio performance
 * that only exists on the client (sound manager, distance, echo scheduler). Rather than reference a
 * client class from common code — which works until the day something verifies the class eagerly on
 * a dedicated server — the client installs a handler here during its setup. When no handler is
 * installed (dedicated server, unit tests) every call is a no-op.
 */
public final class FxBus {

    /** Client implementation of the sound design. */
    public interface AudioHook {
        void onShellLaunch(Entity shell, RocketDesign design);

        void onShellDepart(Entity shell, RocketDesign design);
    }

    /** Client implementation of the screen-space feedback (flash, shake, FOV punch). */
    public interface CameraHook {
        void onBurst(Vec3 origin, float power, boolean flash, boolean shockwave,
                     double distanceToPlayer);
    }

    private static AudioHook audio = new NoopAudio();
    private static CameraHook camera = new NoopCamera();

    private FxBus() {}

    public static void installAudio(AudioHook hook) {
        audio = hook == null ? new NoopAudio() : hook;
    }

    public static void installCamera(CameraHook hook) {
        camera = hook == null ? new NoopCamera() : hook;
    }

    public static AudioHook audio() {
        return audio;
    }

    public static CameraHook camera() {
        return camera;
    }

    private static final class NoopAudio implements AudioHook {
        @Override
        public void onShellLaunch(Entity shell, RocketDesign design) {
        }

        @Override
        public void onShellDepart(Entity shell, RocketDesign design) {
        }
    }

    private static final class NoopCamera implements CameraHook {
        @Override
        public void onBurst(Vec3 origin, float power, boolean flash, boolean shockwave,
                            double distanceToPlayer) {
        }
    }
}
