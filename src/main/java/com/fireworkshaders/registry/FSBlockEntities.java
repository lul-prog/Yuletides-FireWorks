package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.block.PyrotechnicWorkstationBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FSBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FireworkShaders.MOD_ID);

    public static final RegistryObject<BlockEntityType<PyrotechnicWorkstationBlockEntity>>
            PYROTECHNIC_WORKSTATION = BLOCK_ENTITIES.register("pyrotechnic_workstation",
            () -> BlockEntityType.Builder.of(PyrotechnicWorkstationBlockEntity::new,
                    FSBlocks.PYROTECHNIC_WORKSTATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD =
            BLOCK_ENTITIES.register("launch_pad",
                    () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new,
                            FSBlocks.LAUNCH_PAD.get()).build(null));

    private FSBlockEntities() {}
}
