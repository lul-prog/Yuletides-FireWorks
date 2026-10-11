package com.fireworkshaders;

import com.fireworkshaders.client.FSClientSetup;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.registry.FSBlockEntities;
import com.fireworkshaders.registry.FSBlocks;
import com.fireworkshaders.registry.FSCreativeTabs;
import com.fireworkshaders.registry.FSEntities;
import com.fireworkshaders.registry.FSItems;
import com.fireworkshaders.registry.FSMenus;
import com.fireworkshaders.registry.FSParticles;
import com.fireworkshaders.registry.FSSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Yuletide Fireworks.
 *
 * <p>Load order matters and is deliberate:
 * <ol>
 *   <li>blocks, then block entities (a block entity type references its blocks),</li>
 *   <li>items (block items reference blocks),</li>
 *   <li>entities, particles, sounds, menus, then the creative tab last (it references items).</li>
 * </ol>
 *
 * <p>Nothing here touches a client-only class: every renderer, particle provider, GUI overlay and
 * camera hook lives behind {@link DistExecutor#unsafeRunWhenOn} in {@code client.FSClientSetup},
 * so the same jar boots cleanly on a dedicated server.
 */
@Mod(FireworkShaders.MOD_ID)
public final class FireworkShaders {

    /** Registry namespace. Kept stable: renaming it invalidates every saved design in every world. */
    public static final String MOD_ID = "fireworkshaders";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FireworkShaders() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        FSBlocks.BLOCKS.register(modBus);
        FSBlockEntities.BLOCK_ENTITIES.register(modBus);
        FSItems.ITEMS.register(modBus);
        FSEntities.ENTITY_TYPES.register(modBus);
        FSParticles.PARTICLE_TYPES.register(modBus);
        FSSounds.SOUNDS.register(modBus);
        FSMenus.MENUS.register(modBus);
        FSCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FSConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FSConfig.CLIENT_SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FSClientSetup.bootstrap(modBus));
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FSNetwork::register);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FSCommands.register(event.getDispatcher());
    }

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return new net.minecraft.resources.ResourceLocation(MOD_ID, path);
    }
}
