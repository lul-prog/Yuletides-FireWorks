package com.fireworkshaders;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Two specs, because the knobs that matter for "how does it look" are all client-side and must be
 * tweakable without kicking players, while the knobs that matter for "how does this behave on a
 * server" (light pulses, block damage, particle budget fairness) are authoritative.
 */
public final class FSConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private FSConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue dynamicLightPulses;
        public final ForgeConfigSpec.IntValue lightPulseLifetime;
        public final ForgeConfigSpec.IntValue maxLightPulsesPerBurst;
        public final ForgeConfigSpec.BooleanValue shellOfShells;
        public final ForgeConfigSpec.BooleanValue igniteFire;
        public final ForgeConfigSpec.BooleanValue broadcastBursts;
        public final ForgeConfigSpec.DoubleValue burstBroadcastRange;
        public final ForgeConfigSpec.IntValue maxParticlesPerBurst;
        public final ForgeConfigSpec.BooleanValue serverPlaysSound;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Server-authoritative behaviour. Everything here is synced to clients on login.")
                    .push("general");

            this.broadcastBursts = b
                    .comment("Bursts are generated once on the server as a seed + design and then executed",
                            "deterministically on every client (one packet per shell instead of ~900).",
                            "Disable only for debugging.")
                    .define("broadcastBursts", true);

            this.burstBroadcastRange = b
                    .comment("Shells are only streamed to players within this many blocks, like vanilla fireworks.")
                    .defineInRange("burstBroadcastRange", 320.0D, 32.0D, 1024.0D);

            this.maxParticlesPerBurst = b
                    .comment("Hard cap on the number of sparks a single shell may generate. Caliber scales",
                            "quadratically, so a 3.0-caliber shell at 100% would otherwise spawn ~2600 sparks.")
                    .defineInRange("maxParticlesPerBurst", 1200, 64, 6000);
            b.pop();

            b.comment("Real light where the firework is: temporary vanilla light blocks are placed and removed.")
                    .push("dynamicLight");
            this.dynamicLightPulses = b
                    .comment("Place short-lived light blocks at the burst epicentre and under heavy shells.",
                            "This is the only way to make shaderpacks actually light the terrain around a burst.")
                    .define("dynamicLightPulses", true);
            this.lightPulseLifetime = b
                    .comment("Ticks a light pulse stays in the world.")
                    .defineInRange("lightPulseLifetime", 26, 4, 200);
            this.maxLightPulsesPerBurst = b
                    .defineInRange("maxLightPulsesPerBurst", 12, 0, 64);
            b.pop();

            b.comment("Pyrotechnic payload behaviour.").push("payload");
            this.shellOfShells = b
                    .comment("Allow multi-break shells (Meteor Shower splits into 8 sub-shells).")
                    .define("shellOfShells", true);
            this.igniteFire = b
                    .comment("Dragon's Breath can set fire to the ground it lands on. Off by default so a",
                            "public server does not get griefed by firework shows.")
                    .define("igniteFire", false);
            this.serverPlaysSound = b
                    .comment("Let the server play the boom sound as well (vanilla packet). Usually redundant:",
                            "the mod computes distance, air absorption and echoes client side. Enable when you",
                            "want vanilla-accurate packets for players running a different mod version.")
                    .define("serverPlaysSound", false);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.DoubleValue emissiveGain;
        public final ForgeConfigSpec.DoubleValue smokeDensity;
        public final ForgeConfigSpec.DoubleValue shakeIntensity;
        public final ForgeConfigSpec.DoubleValue flashIntensity;
        public final ForgeConfigSpec.BooleanValue windDrift;
        public final ForgeConfigSpec.BooleanValue groundGlowDecals;
        public final ForgeConfigSpec.IntValue particleBudget;
        public final ForgeConfigSpec.BooleanValue layeredAudio;
        public final ForgeConfigSpec.BooleanValue speedOfSoundDelay;
        public final ForgeConfigSpec.IntValue echoCount;
        public final ForgeConfigSpec.DoubleValue masterVolume;
        public final ForgeConfigSpec.BooleanValue showPreviewInGui;
        public final ForgeConfigSpec.BooleanValue sparkTrails;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Visual tuning. These are what you change while watching a show from a distance.")
                    .push("visuals");

            this.emissiveGain = b
                    .comment("Vertex-colour multiplier for emissive sparks. Values above ~2.2 push the spark",
                            "cores past the bloom threshold of most shaderpacks (Complementary, BSL, Photon).",
                            "1.0 = vanilla-bright, 3.0 = retina damage.")
                    .defineInRange("emissiveGain", 1.85D, 0.5D, 4.0D);

            this.smokeDensity = b
                    .comment("Multiplier on how much lingering smoke a shell emits. 0 disables it.")
                    .defineInRange("smokeDensity", 1.0D, 0.0D, 3.0D);

            this.sparkTrails = b
                    .comment("Sparks shed micro-trail particles. Beautiful, and roughly 40% of the particle cost.")
                    .define("sparkTrails", true);

            this.windDrift = b
                    .comment("Sparks and smoke drift with the weather (thunder gusts blow a willow apart mid-air).")
                    .define("windDrift", true);

            this.groundGlowDecals = b
                    .comment("Heavy embers project an additive light pool onto the ground below them.")
                    .define("groundGlowDecals", true);
            b.pop();

            b.comment("Performance guards. A 6-pad finale can ask for 40k particles in one tick.").push("performance");
            this.particleBudget = b
                    .comment("Soft ceiling on live firework particles. Spawns are dropped (lowest priority first)",
                            "once this is exceeded, instead of letting the whole particle engine starve.")
                    .defineInRange("particleBudget", 14000, 1000, 60000);
            b.pop();

            b.comment("Audio. The mod synthesises its own acoustic layers from vanilla sound events.").push("audio");
            this.layeredAudio = b
                    .comment("Play the layered boom: crack, body, sub-bass thump and echo tails.")
                    .define("layeredAudio", true);
            this.speedOfSoundDelay = b
                    .comment("Delay the boom by distance/343 m/s, so a shell 200 blocks away thumps ~0.6 s late.",
                            "Genuinely the single most convincing realism tweak in the whole mod.")
                    .define("speedOfSoundDelay", true);
            this.echoCount = b
                    .comment("How many echo tails follow a large shell (0-4).")
                    .defineInRange("echoCount", 2, 0, 4);
            this.masterVolume = b
                    .defineInRange("masterVolume", 1.0D, 0.0D, 3.0D);
            b.pop();

            b.comment("Interface.").push("ui");
            this.showPreviewInGui = b
                    .comment("Draw the live 3D burst projection in the Pyrotechnic Workstation preview pane.",
                            "Turn off on very old machines; the preview is real geometry, not a texture.")
                    .define("showPreviewInGui", true);
            b.pop();

            b.comment("Camera feedback.").push("camera");
            this.shakeIntensity = b
                    .comment("Screen shake strength for large shells, 0 disables.")
                    .defineInRange("shakeIntensity", 1.0D, 0.0D, 3.0D);
            this.flashIntensity = b
                    .comment("How much a nearby burst whites out your screen.")
                    .defineInRange("flashIntensity", 1.0D, 0.0D, 2.0D);
            b.pop();
        }
    }
}
