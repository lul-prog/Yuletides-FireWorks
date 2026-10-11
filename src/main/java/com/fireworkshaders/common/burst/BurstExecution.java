package com.fireworkshaders.common.burst;

import com.fireworkshaders.FSConfig;
import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.entity.PyroShellEntity;
import com.fireworkshaders.common.net.FSNetwork;
import com.fireworkshaders.common.net.S2CBurstPacket;
import com.fireworkshaders.pyro.BurstPlan;
import com.fireworkshaders.pyro.BurstPlanner;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The single place where a shell actually breaks, on either side.
 *
 * <p>Flow for a break:
 * <ol>
 *   <li>the server builds the plan (pure maths, no world access),</li>
 *   <li>the server sends {@code (design, seed, origin, facing)} to every player in range — one
 *       packet, not nine hundred,</li>
 *   <li>the server performs the things that must be authoritative: child shells, light pulses,
 *       optional fire,</li>
 *   <li>each client rebuilds the identical plan and spawns the particles, then runs its own audio,
 *       camera shake and screen flash scaled by its distance to the break.</li>
 * </ol>
 *
 * <p>Note what is <em>not</em> here: no per-particle network traffic, no entity-per-spark, and no
 * server-side particle spam. A ten-shell finale costs ten packets.
 */
public final class BurstExecution {

    private BurstExecution() {}

    public static int budget() {
        return FSConfig.COMMON.maxParticlesPerBurst.get();
    }

    // ------------------------------------------------------------------ server

    public static void detonate(ServerLevel level, Vec3 pos, RocketDesign design, Vec3 facing,
                                long seed, @Nullable Entity source, float budgetScale,
                                boolean primary) {
        int budget = Math.max(24, Math.round(budget() * budgetScale));
        BurstPlan plan = BurstPlanner.plan(design, pos, seed, facing, budget);

        broadcast(level, plan, design, facing, budgetScale);

        if (FSConfig.COMMON.dynamicLightPulses.get()) {
            PyroLightPulse.pulse(level, plan, design);
        }

        if (FSConfig.COMMON.shellOfShells.get() && design.hasPayload()) {
            spawnChildren(level, design, plan, source, facing);
        }

        if (FSConfig.COMMON.igniteFire.get() && design.has(EffectModule.GROUND_GLOW)) {
            igniteGround(level, pos, design);
        }

        if (FSConfig.COMMON.serverPlaysSound.get()) {
            level.playSound(null, pos.x, pos.y, pos.z,
                    design.power() > 1.6F ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.WEATHER, 8.0F, 0.85F + level.random.nextFloat() * 0.2F);
        }
    }

    private static void broadcast(ServerLevel level, BurstPlan plan, RocketDesign design, Vec3 facing,
                                  float budgetScale) {
        if (!FSConfig.COMMON.broadcastBursts.get()) {
            return;
        }
        double range = FSConfig.COMMON.burstBroadcastRange.get();
        double rangeSq = range * range;
        S2CBurstPacket packet = new S2CBurstPacket(design.toTag(), plan.origin().x, plan.origin().y,
                plan.origin().z, plan.seed(), facing.x, facing.y, facing.z, budgetScale);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(plan.origin()) <= rangeSq) {
                FSNetwork.sendTo(packet, player);
            }
        }
    }

    private static void spawnChildren(ServerLevel level, RocketDesign design, BurstPlan plan,
                                      @Nullable Entity source, Vec3 facing) {
        RocketDesign payload = design.payload();
        if (payload == null || plan.subShells().isEmpty()) {
            return;
        }
        float yaw = facingYawFrom(facing);
        for (BurstPlan.SubShell child : plan.subShells()) {
            PyroShellEntity shell = new PyroShellEntity(level, payload,
                    plan.origin().x, plan.origin().y, plan.origin().z,
                    new Vec3(child.vx(), child.vy(), child.vz()), child.fuse(), yaw);
            if (source != null) {
                shell.setOwner(source instanceof net.minecraft.world.entity.LivingEntity living
                        ? living : null);
            }
            level.addFreshEntity(shell);
        }
    }

    private static float facingYawFrom(Vec3 facing) {
        Vec3 back = new Vec3(-facing.x, 0.0, -facing.z);
        if (back.lengthSqr() < 1.0E-6) {
            return 0.0F;
        }
        return (float) (Math.toDegrees(Math.atan2(-back.x, back.z)));
    }

    /**
     * Dragon's Breath lights the paddock. Deliberately conservative: a small dice roll per attempt
     * so the config option does not turn a display into a wildfire.
     */
    private static void igniteGround(ServerLevel level, Vec3 pos, RocketDesign design) {
        int radius = (int) Math.min(9, 3 + design.caliber());
        int attempts = (int) (design.caliber() * 2.5F);
        for (int i = 0; i < attempts; i++) {
            BlockPos target = BlockPos.containing(
                    pos.x + (level.random.nextDouble() - 0.5) * radius * 2.0,
                    pos.y - 2.0,
                    pos.z + (level.random.nextDouble() - 0.5) * radius * 2.0);
            BlockPos ground = findGround(level, target);
            if (ground == null) {
                continue;
            }
            BlockPos above = ground.above();
            if (level.getBlockState(above).isAir() && level.isEmptyBlock(above)
                    && level.random.nextFloat() < 0.35F) {
                level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    @Nullable
    private static BlockPos findGround(ServerLevel level, BlockPos from) {
        for (int i = 0; i < 6; i++) {
            BlockPos p = from.below(i);
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && state.isSolidRender(level, p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Client-side execution lives in {@code client.fx.ClientBurst}: it needs {@code ClientLevel},
     * the client config and the audio/camera hooks, and none of those belong in a class that a
     * dedicated server loads.
     */
}
