package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.entity.PyroRocketEntity;
import com.fireworkshaders.common.entity.PyroShellEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FSEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FireworkShaders.MOD_ID);

    /**
     * {@code clientTrackingRange(20)} (320 blocks) and {@code updateInterval(1)}: a shell is fast
     * and its trail is its silhouette, so it gets a high refresh rate. Both entity types are tiny
     * (0.4 blocks) and are never loaded by the chunk loader — they only exist in response to a
     * launch.
     */
    public static final RegistryObject<EntityType<PyroRocketEntity>> PYRO_ROCKET = ENTITY_TYPES.register(
            "pyro_rocket", () -> EntityType.Builder.<PyroRocketEntity>of(PyroRocketEntity::new,
                            MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(20)
                    .updateInterval(1)
                    .noSave()
                    .build(FireworkShaders.id("pyro_rocket").toString()));

    public static final RegistryObject<EntityType<PyroShellEntity>> PYRO_SHELL = ENTITY_TYPES.register(
            "pyro_shell", () -> EntityType.Builder.<PyroShellEntity>of(PyroShellEntity::new,
                            MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(20)
                    .updateInterval(1)
                    .noSave()
                    .build(FireworkShaders.id("pyro_shell").toString()));

    private FSEntities() {}
}
