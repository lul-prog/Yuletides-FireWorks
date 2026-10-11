package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.LaunchPadBlock;
import com.fireworkshaders.common.block.PyrotechnicWorkstationBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FSBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FireworkShaders.MOD_ID);

    public static final RegistryObject<Block> PYROTECHNIC_WORKSTATION = BLOCKS.register(
            "pyrotechnic_workstation",
            () -> new PyrotechnicWorkstationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // The shell being assembled glows through the hatch. A light level of 8 is
                    // enough to read as "busy" in a dark workshop without lighting up a room.
                    .lightLevel(state -> state.getValue(PyrotechnicWorkstationBlock.LIT) ? 8 : 0)));

    public static final RegistryObject<Block> LAUNCH_PAD = BLOCKS.register(
            "launch_pad",
            () -> new LaunchPadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(LaunchPadBlock.ARMED) ? 4 : 0)));

    private FSBlocks() {}
}
