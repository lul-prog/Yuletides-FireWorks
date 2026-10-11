package com.fireworkshaders.common.entity;

import com.fireworkshaders.common.burst.BurstExecution;
import com.fireworkshaders.pyro.PyroFlags;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A child shell: what a multi-break shell turns into when it breaks.
 *
 * <p>Ballistic, not powered. It keeps whatever velocity its parent gave it (the planner aims those
 * downwards), is pulled in by gravity and breaks low. That arc is the entire visual signature of
 * the Meteor Shower — eight burning trails falling out of the break, each blooming into its own
 * small shell just above the treeline.
 */
public class PyroShellEntity extends AbstractPyroShellEntity {

    private static final double DRAG = 0.988;
    private static final double GRAVITY = 0.032;

    /** Sub-shells get a fraction of the main break's budget; eight full breaks would be absurd. */
    private static final float BUDGET_SCALE = 0.42F;

    private int fuse = 26;

    public PyroShellEntity(EntityType<? extends PyroShellEntity> type, Level level) {
        super(type, level);
    }

    public PyroShellEntity(Level level, RocketDesign design, double x, double y, double z,
                           Vec3 velocity, int fuse, float facingYaw) {
        super(FSEntities.PYRO_SHELL.get(), level, design, facingYaw);
        this.setPos(x, y, z);
        this.setDeltaMovement(velocity);
        this.fuse = Math.max(1, fuse);
        this.setYRot(facingYaw);
        this.setXRot((float) (-Math.toDegrees(Math.atan2(velocity.y,
                Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)))));
    }

    @Override
    protected void serverTick() {
        if (this.life >= this.fuse) {
            detonate();
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        Vec3 next = velocity.add(0.0, -GRAVITY, 0.0).scale(DRAG);
        this.setDeltaMovement(next);
        this.setPos(this.getX() + next.x, this.getY() + next.y, this.getZ() + next.z);

        // Cheap ground test: eight entities are ticking, so we do not run a full sweep, we just ask
        // the movement flags the position update already produced.
        if (this.onGround() || this.verticalCollision) {
            this.setPos(this.getX(), this.getY() + 1.4, this.getZ());
            detonate();
        }
    }

    @Override
    protected void clientTick() {
        RocketDesign design = getDesign();
        if (design == null) {
            return;
        }
        // Sub-shells trail hard: they are the meteor part of the meteor shower.
        emitLaunchTrail(1.0F, 0.8F, PyroFlags.PRIORITY);
        if (this.life % 2 == 0) {
            emitThrustSmoke(0.4F);
        }
    }

    @Override
    protected void onDetonateServer() {
        RocketDesign design = getDesign();
        if (design == null || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        BurstExecution.detonate(server, this.position(), design, audienceFacing(), burstSeed(), this,
                BUDGET_SCALE, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", this.fuse);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fuse = Math.max(1, tag.getInt("Fuse"));
    }
}
