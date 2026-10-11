package com.fireworkshaders.common.entity;

import com.fireworkshaders.common.burst.BurstExecution;
import com.fireworkshaders.common.particle.PyroParticleOptions;
import com.fireworkshaders.pyro.EffectModule;
import com.fireworkshaders.pyro.PyroFlags;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The lift stage: a shell under thrust.
 *
 * <p>Flight model, and why it is not "vanilla rocket but faster":
 * <ul>
 *   <li>The motor accelerates <em>along the launch axis</em>, not along the current velocity, so a
 *       shell launched at an angle flies a straight-ish bore and does not curve like a bottle rocket.</li>
 *   <li>Speed is clamped to the design's lift velocity instead of being allowed to run away — a real
 *       lift charge delivers one impulse and then the shell coasts.</li>
 *   <li>A small constant gravity term (0.012 b/t) plus drag (0.5%/tick) gives the gentle arc that
 *       makes a high break look like ballistics rather than an elevator.</li>
 * </ul>
 *
 * <p>The muzzle flash, whistle and trail are all client-side and derived from the design, so a
 * rocket costs the server exactly one entity and one burst packet.
 */
public class PyroRocketEntity extends AbstractPyroShellEntity {

    private static final double DRAG = 0.995;
    private static final double GRAVITY = 0.012;

    private boolean igniteSoundPlayed;

    public PyroRocketEntity(EntityType<? extends PyroRocketEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public PyroRocketEntity(Level level, RocketDesign design, double x, double y, double z,
                            float yaw, float elevationDegrees, @Nullable LivingEntity owner) {
        super(FSEntities.PYRO_ROCKET.get(), level, design, yaw);
        this.setPos(x, y, z);
        this.setOwner(owner);
        // Minecraft's pitch convention is inverted (positive pitch looks down), and vanilla stores
        // the *elevation* as the negative of it. Converting once here keeps the rest of the class
        // free of sign bugs.
        float pitch = -elevationDegrees;
        this.setYRot(yaw);
        this.setXRot(pitch);
        Vec3 axis = Vec3.directionFromRotation(pitch, yaw);
        this.setDeltaMovement(axis.scale(design.liftSpeed() * 0.35).add(0.0, 0.08, 0.0));
    }

    // ------------------------------------------------------------------ spawning

    /** Server-side factory used by the item, the launch pad and the commands. */
    public static PyroRocketEntity launch(ServerLevel level, Vec3 pos, RocketDesign design, float yaw,
                                          float elevationDegrees, @Nullable Entity owner) {
        PyroRocketEntity rocket = new PyroRocketEntity(level, design, pos.x, pos.y, pos.z, yaw,
                elevationDegrees, owner instanceof LivingEntity living ? living : null);
        level.addFreshEntity(rocket);
        return rocket;
    }

    // ------------------------------------------------------------------ flight

    @Override
    protected void serverTick() {
        RocketDesign design = getDesign();
        if (design == null) {
            this.discard();
            return;
        }
        if (this.life >= design.fuse()) {
            detonate();
            return;
        }

        Vec3 axis = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
        Vec3 velocity = this.getDeltaMovement();
        double target = design.liftSpeed();

        // Thrust: a shrieking shell gets there faster and with a harder kick.
        double accel = 0.052 + 0.010 * design.caliber()
                + (design.has(EffectModule.SHRIEK) ? 0.030 : 0.0);
        Vec3 next = velocity.add(axis.scale(accel));

        // Clamp the axial component only; the lateral component is free to be damped by drag.
        double axial = next.dot(axis);
        if (axial > target) {
            next = next.subtract(axis.scale(axial - target));
        }
        next = next.add(0.0, -GRAVITY, 0.0).scale(DRAG);
        this.setDeltaMovement(next);
        this.setPos(this.getX() + next.x, this.getY() + next.y, this.getZ() + next.z);
    }

    @Override
    protected void clientTick() {
        RocketDesign design = getDesign();
        if (design == null) {
            return;
        }
        // The burning grain: a bright core with the chemical colour around it, denser and longer
        // for shells with trails, plus the two side effects that read as "this thing is dangerous".
        float density = design.has(EffectModule.TRAIL) ? 1.0F : 0.72F;
        emitLaunchTrail(density, 0.95F + design.caliber() * 0.12F, design.has(EffectModule.SHRIEK)
                ? PyroFlags.PRIORITY : 0);
        if (this.life % 2 == 0) {
            emitThrustSmoke(0.55F + design.caliber() * 0.1F);
        }
        if (design.has(EffectModule.SHRIEK) && this.life % 3 == 0) {
            // Mach-diamond sparkle: short white flecks shed sideways off the shrieking shell.
            this.level().addParticle(new PyroParticleOptions(PyroParticleOptions.Kind.GLITTER,
                            0xEAF6FF, 1.6F, 0.5F, 6 + this.random.nextInt(5),
                            PyroFlags.FULLBRIGHT | PyroFlags.PRIORITY),
                    this.getX(), this.getY(), this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.35,
                    (this.random.nextDouble() - 0.5) * 0.35,
                    (this.random.nextDouble() - 0.5) * 0.35);
        }
        if (!this.igniteSoundPlayed) {
            this.igniteSoundPlayed = true;
            com.fireworkshaders.common.fx.FxBus.audio().onShellLaunch(this, design);
        }
    }

    @Override
    protected void onDetonateServer() {
        RocketDesign design = getDesign();
        if (design == null || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        BurstExecution.detonate(server, this.position(), design, audienceFacing(), burstSeed(), this,
                1.0F, true);
    }
}
