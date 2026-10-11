package com.fireworkshaders.common.entity;

import com.fireworkshaders.common.particle.PyroParticleOptions;
import com.fireworkshaders.pyro.PyroFlags;
import com.fireworkshaders.pyro.PyroMath;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Shared shell behaviour: it carries a {@link RocketDesign}, burns its fuse, and detonates.
 *
 * <p>The design is synced the way vanilla syncs a firework rocket's item — an {@link ItemStack} in
 * a synched entity datum. That gives us NBT-correct, version-safe synchronisation for free (no
 * custom "compound tag" serializer, which does not exist in 1.20.1 anyway), and it means a
 * designed rocket dropped in the world and a designed rocket in flight are literally the same
 * serialised object.
 *
 * <p>Determinism: the burst seed is derived from the entity UUID, which every client receives with
 * the spawn packet. Server and clients therefore agree on the exact star layout without ever
 * sending it.
 */
public abstract class AbstractPyroShellEntity extends Projectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_STACK =
            SynchedEntityData.defineId(AbstractPyroShellEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_FACING_YAW =
            SynchedEntityData.defineId(AbstractPyroShellEntity.class, EntityDataSerializers.FLOAT);

    /** Cached decode of the synced stack; invalidated whenever the datum changes. */
    @Nullable
    private RocketDesign cachedDesign;
    private ItemStack cachedFrom = ItemStack.EMPTY;

    protected int life;
    protected boolean detonated;

    protected AbstractPyroShellEntity(EntityType<? extends AbstractPyroShellEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    protected AbstractPyroShellEntity(EntityType<? extends AbstractPyroShellEntity> type, Level level,
                                      RocketDesign design, float facingYaw) {
        this(type, level);
        setDesign(design);
        this.entityData.set(DATA_FACING_YAW, facingYaw);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STACK, ItemStack.EMPTY);
        this.entityData.define(DATA_FACING_YAW, 0.0F);
    }

    // ------------------------------------------------------------------ design plumbing

    public void setDesign(RocketDesign design) {
        this.entityData.set(DATA_STACK, design.createStack(FSItems.CUSTOM_FIREWORK.get(), 1));
        this.cachedDesign = design;
        this.cachedFrom = this.entityData.get(DATA_STACK);
    }

    @Nullable
    public RocketDesign getDesign() {
        ItemStack stack = this.entityData.get(DATA_STACK);
        if (this.cachedDesign == null || stack != this.cachedFrom) {
            this.cachedFrom = stack;
            this.cachedDesign = RocketDesign.fromStack(stack);
        }
        return this.cachedDesign;
    }

    /** Entity-facing yaw at launch; the horizontal direction the audience is on is its negative. */
    public float facingYaw() {
        return this.entityData.get(DATA_FACING_YAW);
    }

    public Vec3 audienceFacing() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, facingYaw());
        Vec3 facing = new Vec3(-forward.x, 0.0, -forward.z);
        return facing.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : facing.normalize();
    }

    public long burstSeed() {
        return PyroMath.mix(this.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(this.getUUID().getLeastSignificantBits(), 17));
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(DATA_STACK);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    protected abstract void serverTick();

    protected abstract void clientTick();

    /** Called on both sides when the fuse runs out; the server side is where the burst is sent. */
    protected void detonate() {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        if (!this.level().isClientSide) {
            onDetonateServer();
        }
        this.discard();
    }

    protected abstract void onDetonateServer();

    // ------------------------------------------------------------------ client effects

    /**
     * The burning trail. Streak colours are taken from the shell's own chemistry so a strontium
     * shell leaves a red-orange trail and a copper one leaves blue.
     */
    protected void emitLaunchTrail(float density, float scale, int extraFlags) {
        RocketDesign design = getDesign();
        if (design == null || this.random.nextFloat() > density) {
            return;
        }
        int rgb = design.colorAt(this.random.nextFloat());
        // Additives burn white-hot at the nozzle; the tail cools into the chemical's colour.
        int hot = this.random.nextFloat() < 0.45F ? 0xFFF4DC : rgb;
        int life = 10 + this.random.nextInt(12);
        this.level().addParticle(new PyroParticleOptions(
                        PyroParticleOptions.Kind.SPARK, hot, 1.25F, scale, life,
                        PyroFlags.FULLBRIGHT | PyroFlags.WIND | extraFlags
                                | (design.has(com.fireworkshaders.pyro.EffectModule.TRAIL)
                                ? PyroFlags.TRAIL : 0)),
                this.getX() + (this.random.nextDouble() - 0.5) * 0.28,
                this.getY() + 0.1,
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.28,
                (this.random.nextDouble() - 0.5) * 0.05,
                -0.02 - this.random.nextDouble() * 0.03,
                (this.random.nextDouble() - 0.5) * 0.05);
    }

    protected void emitThrustSmoke(float density) {
        if (this.random.nextFloat() > density) {
            return;
        }
        this.level().addParticle(new PyroParticleOptions(
                        PyroParticleOptions.Kind.SMOKE, 0x9AA0A6, 0.4F,
                        0.55F + this.random.nextFloat() * 0.3F, 40 + this.random.nextInt(40),
                        PyroFlags.WIND),
                this.getX(), this.getY() - 0.05, this.getZ(),
                (this.random.nextDouble() - 0.5) * 0.02, -0.012, (this.random.nextDouble() - 0.5) * 0.02);
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Rocket", this.entityData.get(DATA_STACK).save(new CompoundTag()));
        tag.putFloat("Facing", facingYaw());
        tag.putInt("Life", this.life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Rocket")) {
            this.entityData.set(DATA_STACK, ItemStack.of(tag.getCompound("Rocket")));
        }
        this.entityData.set(DATA_FACING_YAW, tag.getFloat("Facing"));
        this.life = tag.getInt("Life");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;   // 256 blocks: a shell is visible (as a streak of light) from far away
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Shells are not stopped by rain or pushed by entities; they are projectiles with a motor. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        Entity owner = super.getOwner();
        return owner instanceof LivingEntity living ? living : null;
    }
}
