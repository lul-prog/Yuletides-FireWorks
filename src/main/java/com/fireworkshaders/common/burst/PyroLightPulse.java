package com.fireworkshaders.common.burst;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.pyro.BurstPlan;
import com.fireworkshaders.pyro.PyroMath;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Real light, not a faked glow.
 *
 * <p>There is no supported way to change a client's block light from a mod without a mixin into
 * the light engine, and a shaderpack is going to use the vanilla lightmap regardless — so the only
 * honest way to make a shell "illuminate the ground below" is to genuinely put light in the world.
 * Vanilla's {@code minecraft:light} block is exactly the right tool: it is invisible, emits light
 * level 15, does not collide, and does not survive being pushed.
 *
 * <p>Pulses are removed by a {@link TickTask} rather than a block entity ticker. Placement is
 * restricted to air so cleanup cannot erase a player's block or another pulse's light. Cleanup is
 * best-effort for loaded chunks; as with any temporary world block, an abrupt server stop before
 * the task runs can leave a vanilla light block behind.
 */
public final class PyroLightPulse {

    /** Purely for diagnostics: the number of live pulses, useful in a profiler dump. */
    private static int livePulses;

    private PyroLightPulse() {}

    public static void pulse(ServerLevel level, BurstPlan plan, RocketDesign design) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        int wanted = Math.min(plan.lightPulses(), FSConfig.COMMON.maxLightPulsesPerBurst.get());
        if (wanted <= 0) {
            return;
        }
        int lifetime = FSConfig.COMMON.lightPulseLifetime.get();
        double radius = Math.min(8.0, 1.5 + design.caliber() * 1.6);
        Vec3 origin = plan.origin();
        long seed = plan.seed();

        List<BlockPos> placed = new ArrayList<>(wanted);
        // The epicentre gets first claim — that is the light the audience sees reflected in the fog.
        BlockPos centre = BlockPos.containing(origin);
        if (placeLight(level, centre)) {
            placed.add(centre);
        }
        // Count the epicentre against the configured limit; it replaces rather than adds to one of
        // the random pulses.
        for (int i = 0; i < wanted - 1; i++) {
            double dx = PyroMath.randSigned(seed, 3000 + i) * radius;
            double dy = PyroMath.randSigned(seed, 4000 + i) * radius * 0.55;
            double dz = PyroMath.randSigned(seed, 5000 + i) * radius;
            BlockPos pos = BlockPos.containing(origin.x + dx, origin.y + dy, origin.z + dz);
            if (placeLight(level, pos)) {
                placed.add(pos);
            }
        }
        if (placed.isEmpty()) {
            return;
        }
        livePulses += placed.size();
        server.tell(new TickTask(lifetime, () -> {
            for (BlockPos pos : placed) {
                removeLight(level, pos);
            }
            livePulses -= placed.size();
        }));
    }

    private static boolean placeLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        // Only air is safe. Replaceable plants and existing light blocks can still belong to a
        // player or another pulse; replacing them and later setting the position to air would lose
        // the original block (or prematurely erase the other pulse's light).
        if (!existing.isAir()) {
            return false;
        }
        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
        return true;
    }

    private static void removeLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static int livePulses() {
        return livePulses;
    }
}
