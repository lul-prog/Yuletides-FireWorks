package com.fireworkshaders.client;

import com.fireworkshaders.client.audio.FireworkAudio;
import com.fireworkshaders.client.fx.ScreenFx;
import com.fireworkshaders.client.gui.LaunchPadScreen;
import com.fireworkshaders.client.gui.PyrotechnicWorkstationScreen;
import com.fireworkshaders.client.gui.ShowHudOverlay;
import com.fireworkshaders.client.particle.PyroParticles;
import com.fireworkshaders.client.render.PyroRocketRenderer;
import com.fireworkshaders.client.state.ClientState;
import com.fireworkshaders.common.fx.FxBus;
import com.fireworkshaders.registry.FSEntities;
import com.fireworkshaders.registry.FSMenus;
import com.fireworkshaders.registry.FSParticles;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * All client wiring lives here, and this class is only ever loaded from a
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} block in the mod constructor — so a
 * dedicated server never touches a client class through this mod.
 *
 * <p>Note what is <em>not</em> here: no mixins, no coremods, no shader overrides. Everything the mod
 * draws goes through vanilla's particle and entity pipelines on purpose, because that is the only
 * way a shaderpack can apply its bloom and tonemapping to it.
 */
public final class FSClientSetup {

    private FSClientSetup() {}

    public static void bootstrap(IEventBus modBus) {
        modBus.addListener(FSClientSetup::onRegisterParticleProviders);
        modBus.addListener(FSClientSetup::onRegisterRenderers);
        modBus.addListener(FSClientSetup::onRegisterGuiOverlays);
        modBus.addListener(FSClientSetup::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(FSClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ScreenFx::applyCameraAngles);
        MinecraftForge.EVENT_BUS.addListener(ScreenFx::applyFov);
        MinecraftForge.EVENT_BUS.addListener(FSClientSetup::onLoggingOut);

        // Install the client implementations into the common-side seams.
        FxBus.installAudio(new FireworkAudio());
        // Common code can ask for camera feedback without knowing a client exists. Breaks that do
        // carry a design (all of ours do) go through ClientBurst → ScreenFx.onBurst; this hook is
        // the design-free path, so it stays wired to the same maths rather than being a no-op.
        FxBus.installCamera(ScreenFx::onBurstFromHook);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(FSMenus.PYROTECHNIC_WORKSTATION.get(), PyrotechnicWorkstationScreen::new);
            MenuScreens.register(FSMenus.LAUNCH_PAD.get(), LaunchPadScreen::new);
        });
    }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FSParticles.SPARK.get(), PyroParticles.Providers.Spark::new);
        event.registerSpriteSet(FSParticles.GLITTER.get(), PyroParticles.Providers.Glitter::new);
        event.registerSpriteSet(FSParticles.EMBER.get(), PyroParticles.Providers.Ember::new);
        event.registerSpriteSet(FSParticles.DUST.get(), PyroParticles.Providers.Dust::new);
        event.registerSpriteSet(FSParticles.SMOKE.get(), PyroParticles.Providers.Smoke::new);
        event.registerSpriteSet(FSParticles.FLASH.get(), PyroParticles.Providers.Flash::new);
        event.registerSpriteSet(FSParticles.SHOCKWAVE.get(), PyroParticles.Providers.Shockwave::new);
        event.registerSpriteSet(FSParticles.SPECTRUM.get(), PyroParticles.Providers.Spark::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FSEntities.PYRO_ROCKET.get(), PyroRocketRenderer::new);
        event.registerEntityRenderer(FSEntities.PYRO_SHELL.get(), PyroRocketRenderer::new);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // Above everything: a shell breaking next to your face should white out the HUD too.
        event.registerAboveAll("burst_flash",
                (gui, graphics, partialTick, width, height)
                        -> ScreenFx.renderOverlay(graphics, partialTick, width, height));
        event.registerAboveAll("show_clock", ShowHudOverlay::render);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        FireworkAudio.tick();
        ScreenFx.advance();
        ScreenFx.tick();
    }

    private static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clearAll();
        FireworkAudio.clear();
        FireworkAudio.invalidateAssetProbe();
        ScreenFx.clear();
    }
}
