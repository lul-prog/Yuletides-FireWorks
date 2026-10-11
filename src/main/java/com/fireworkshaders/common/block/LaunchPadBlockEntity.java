package com.fireworkshaders.common.block;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.entity.PyroRocketEntity;
import com.fireworkshaders.common.menu.LaunchPadController;
import com.fireworkshaders.common.menu.LaunchPadMenu;
import com.fireworkshaders.common.show.ShowProgram;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Nine mortar tubes and a show clock.
 *
 * <p>The clock is the interesting part. Cues store an <em>absolute</em> offset from T-0, and a pad
 * computes its T-0 as {@code serverGameTime + leadIn} at the instant it is told to go. Every pad in
 * a show therefore shares one wall clock: fire three pads from one igniter press and their cues
 * line up within one server tick, which is the difference between a display and a mess. Triggering
 * a pad is idempotent per press, and a second press during a run is ignored so a nervous player
 * cannot double-fire a finale.
 *
 * <p>Tubes fan outwards (a few degrees per slot) so a salvo from one pad spreads into a fan instead
 * of stacking nine shells into one column. Each tube also has its own launch offset for the same
 * reason.
 */
public class LaunchPadBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements Container, MenuProvider {

    public static final int SLOTS = 9;

    public enum PadState {
        /** Nothing loaded or nothing armed. */
        IDLE,
        /** Countdown running, waiting for T-0. */
        ARMED,
        /** Show in progress. */
        RUNNING,
        /** Aborted; needs an explicit arm before it will fire again. */
        SAFE
    }

    private final NonNullList<ItemStack> rockets = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private ShowProgram program = new ShowProgram();
    private PadState state = PadState.IDLE;
    private long startTick;
    private int lastElapsed = -1;
    private int selectedCue;
    private int syncTimer = 10;
    private int countdownStage = -1;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(com.fireworkshaders.registry.FSBlockEntities.LAUNCH_PAD.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    public ShowProgram getProgram() {
        return this.program;
    }

    public PadState getPadState() {
        return this.state;
    }

    public int getSelectedCue() {
        return this.selectedCue;
    }

    public void setSelectedCue(int index) {
        this.selectedCue = Math.max(0, Math.min(ShowProgram.MAX_CUES - 1, index));
    }

    /** Ticks until T-0, or a negative value when no countdown is running. */
    public int ticksToStart() {
        if (this.state != PadState.ARMED && this.state != PadState.RUNNING) {
            return -1;
        }
        if (this.level == null) {
            return -1;
        }
        return (int) (this.startTick - this.level.getGameTime());
    }

    public int elapsedTicks() {
        if (this.state != PadState.RUNNING || this.level == null) {
            return -1;
        }
        return (int) (this.level.getGameTime() - this.startTick);
    }

    public boolean isArmed() {
        return this.state == PadState.ARMED || this.state == PadState.RUNNING;
    }

    public int loadedCount() {
        int loaded = 0;
        for (ItemStack stack : this.rockets) {
            if (!stack.isEmpty()) {
                loaded++;
            }
        }
        return loaded;
    }

    // ------------------------------------------------------------------ loading

    public boolean loadDesign(RocketDesign design) {
        for (int i = 0; i < SLOTS; i++) {
            if (this.rockets.get(i).isEmpty()) {
                this.rockets.set(i, design.createStack(FSItems.CUSTOM_FIREWORK.get(), 1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    @Nullable
    public RocketDesign designAt(int slot) {
        return slot >= 0 && slot < SLOTS ? RocketDesign.fromStack(this.rockets.get(slot)) : null;
    }

    public CompoundTag designTags() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < SLOTS; i++) {
            RocketDesign design = designAt(i);
            tag.put("s" + i, design == null ? new CompoundTag() : design.toTag());
        }
        return tag;
    }

    // ------------------------------------------------------------------ show control

    public void arm() {
        if (this.state == PadState.RUNNING) {
            return;
        }
        this.state = PadState.ARMED;
        this.countdownStage = -1;
        updateArmedState();
        setChanged();
    }

    public void safe() {
        this.state = PadState.SAFE;
        this.lastElapsed = -1;
        this.countdownStage = -1;
        updateArmedState();
        setChanged();
    }

    /** Starts the show. Returns the absolute server tick of T-0 so pads can be compared. */
    public long go() {
        if (this.level == null || this.state == PadState.RUNNING) {
            return -1L;
        }
        this.startTick = this.level.getGameTime() + this.program.leadInTicks();
        this.state = PadState.RUNNING;
        this.lastElapsed = -1;
        this.countdownStage = -1;
        updateArmedState();
        setChanged();
        return this.startTick;
    }

    /** Rehearsal: fires one tube right now, ignoring the clock. */
    public void testFire(int slot) {
        fire(slot);
    }

    private void updateArmedState() {
        if (this.level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(LaunchPadBlock.ARMED)
                && state.getValue(LaunchPadBlock.ARMED) != isArmed()) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(LaunchPadBlock.ARMED, isArmed()), 3);
        }
    }

    // ------------------------------------------------------------------ tick

    public void serverTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        switch (this.state) {
            case ARMED -> {
                int ticks = ticksToStart();
                if (ticks <= 0) {
                    // T-0 arrives while still ARMED only if the pad was armed but never told to go.
                    this.state = PadState.RUNNING;
                    this.lastElapsed = -1;
                } else {
                    playCountdownTick(ticks);
                }
            }
            case RUNNING -> {
                int elapsed = elapsedTicks();
                if (elapsed >= 0) {
                    if (this.lastElapsed < 0) {
                        this.lastElapsed = -1;
                    }
                    if (elapsed != this.lastElapsed) {
                        for (ShowProgram.Cue cue : this.program.due(elapsed, this.lastElapsed)) {
                            fire(cue.slot());
                        }
                        this.lastElapsed = elapsed;
                    }
                    if (elapsed > this.program.durationTicks() + 80) {
                        this.state = PadState.IDLE;
                        this.lastElapsed = -1;
                        updateArmedState();
                        setChanged();
                    }
                }
            }
            default -> {
            }
        }

        if (--this.syncTimer <= 0) {
            this.syncTimer = 10;
            if (this.state != PadState.IDLE || this.level.getGameTime() % 40 == 0) {
                LaunchPadController.syncNearby(this);
            }
        }
    }

    /** A countdown you can hear across the field: three pings, then the show. */
    private void playCountdownTick(int ticks) {
        int stage = ticks > 60 ? 3 : ticks > 40 ? 2 : ticks > 20 ? 1 : 0;
        if (stage == this.countdownStage) {
            return;
        }
        this.countdownStage = stage;
        float pitch = switch (stage) {
            case 3 -> 0.7F;
            case 2 -> 0.9F;
            case 1 -> 1.15F;
            default -> 1.5F;
        };
        this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.BLOCKS, 1.2F, pitch);
    }

    private void fire(int slot) {
        if (this.level == null || slot < 0 || slot >= SLOTS) {
            return;
        }
        ItemStack stack = this.rockets.get(slot);
        RocketDesign design = RocketDesign.fromStack(stack);
        if (design == null) {
            return;
        }
        this.rockets.set(slot, ItemStack.EMPTY);
        setChanged();

        Vec3 origin = Vec3.atBottomCenterOf(this.worldPosition.above())
                .add(((slot % 3) - 1) * 0.32, 0.0, ((slot / 3) - 1) * 0.32);
        float yaw = (slot - 4) * 6.0F;
        float elevation = 88.0F - Math.abs(slot - 4) * 1.4F;

        if (this.level instanceof ServerLevel server) {
            PyroRocketEntity.launch(server, origin, design, yaw, elevation, null);
            this.level.playSound(null, this.worldPosition,
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.0F,
                    0.9F + this.level.random.nextFloat() * 0.2F);
        }
    }

    // ------------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.rockets) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.rockets.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.rockets, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.rockets, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.rockets.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.rockets.clear();
    }

    public void dropContents() {
        if (this.level != null) {
            Containers.dropContents(this.level, this.worldPosition, (Container) this);
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + FireworkShaders.MOD_ID + ".launch_pad");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new LaunchPadMenu(windowId, inventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, this.rockets);
        this.program = ShowProgram.fromTag(tag.getCompound("Program"));
        this.selectedCue = tag.getInt("Selected");
        try {
            this.state = PadState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            this.state = PadState.IDLE;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.rockets);
        tag.put("Program", this.program.toTag());
        tag.putInt("Selected", this.selectedCue);
        tag.putString("State", this.state.name());
    }

    /** For the sync packet: a flat list of the nine tubes' designs. */
    public List<CompoundTag> rocketTags() {
        List<CompoundTag> tags = new java.util.ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            RocketDesign design = designAt(i);
            tags.add(design == null ? new CompoundTag() : design.toTag());
        }
        return tags;
    }
}
