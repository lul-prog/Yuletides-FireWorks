package com.fireworkshaders.common.block;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.menu.PyrotechnicWorkstationMenu;
import com.fireworkshaders.pyro.Chemical;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.registry.FSItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The workstation's brain: eight inventory slots, the design under construction, and nine memory
 * slots for designs the player wants to keep.
 *
 * <p>The ingredient model is deliberately physical rather than recipe-shaped: you put real chemicals
 * in the reagent slots, the machine checks the design's stoichiometry against what is actually
 * sitting in the hopper, and it consumes exactly what it used. Nothing here is a crafting recipe,
 * which is the point — a player can design a 900-star triple-colour willow and the machine will
 * happily take payment in proportion to how outrageous it is.
 */
public class PyrotechnicWorkstationBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements Container, MenuProvider {

    // Slot layout — the GUI is laid out around these constants, so they are the source of truth.
    public static final int SLOT_REAGENT_START = 0;
    public static final int REAGENT_SLOTS = 4;
    public static final int SLOT_PROPELLANT_START = 4;
    public static final int PROPELLANT_SLOTS = 2;
    public static final int SLOT_PAPER = 6;
    public static final int SLOT_OUTPUT = 7;
    public static final int SIZE = 8;

    public static final int SAVED_SLOTS = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final RocketDesign[] saved = new RocketDesign[SAVED_SLOTS];

    private RocketDesign design = RocketDesign.defaultDesign();
    private int presetIndex = -1;
    private int selectedMemory = 0;
    private int litTicks;

    public PyrotechnicWorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(com.fireworkshaders.registry.FSBlockEntities.PYROTECHNIC_WORKSTATION.get(), pos, state);
    }

    // ------------------------------------------------------------------ design state

    public RocketDesign getDesign() {
        return this.design;
    }

    public void setDesign(RocketDesign design) {
        this.design = design;
        setChanged();
    }

    @Nullable
    public RocketDesign getSaved(int index) {
        return index >= 0 && index < SAVED_SLOTS ? this.saved[index] : null;
    }

    public void setSaved(int index, @Nullable RocketDesign design) {
        if (index >= 0 && index < SAVED_SLOTS) {
            this.saved[index] = design;
            setChanged();
        }
    }

    public int getSelectedMemory() {
        return this.selectedMemory;
    }

    public void setSelectedMemory(int index) {
        this.selectedMemory = Math.floorMod(index, SAVED_SLOTS);
    }

    public int getPresetIndex() {
        return this.presetIndex;
    }

    public void setPresetIndex(int index) {
        this.presetIndex = index;
    }

    /** Non-null list of the memory slots, for syncing. */
    public List<RocketDesign> savedDesigns() {
        return Arrays.asList(this.saved);
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick() {
        if (this.litTicks > 0) {
            this.litTicks--;
            if (this.litTicks == 0 && this.level != null) {
                BlockState state = getBlockState();
                if (state.hasProperty(PyrotechnicWorkstationBlock.LIT)
                        && state.getValue(PyrotechnicWorkstationBlock.LIT)) {
                    this.level.setBlock(this.worldPosition,
                            state.setValue(PyrotechnicWorkstationBlock.LIT, Boolean.FALSE), 3);
                }
            }
        }
    }

    private void flashLit(int ticks) {
        this.litTicks = ticks;
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(PyrotechnicWorkstationBlock.LIT)
                    && !state.getValue(PyrotechnicWorkstationBlock.LIT)) {
                this.level.setBlock(this.worldPosition,
                        state.setValue(PyrotechnicWorkstationBlock.LIT, Boolean.TRUE), 3);
            }
        }
    }

    // ------------------------------------------------------------------ assembling

    /**
     * Checks the design's stoichiometry against the hopper. Returns the list of what is missing,
     * which the GUI shows verbatim — a crafting failure you cannot diagnose is a bad GUI.
     */
    public List<Component> missingIngredients() {
        List<Component> missing = new ArrayList<>();
        for (Chemical chemical : this.design.palette()) {
            Item item = com.fireworkshaders.pyro.FireworkShadersId.itemFor(chemical);
            int needed = Math.max(1, Math.round(2.0F * this.design.caliber()));
            int have = count(item, SLOT_REAGENT_START, SLOT_REAGENT_START + REAGENT_SLOTS);
            if (have < needed) {
                missing.add(Component.translatable("gui." + FireworkShaders.MOD_ID + ".missing_count",
                        needed - have, chemical.nameComponent()));
            }
        }
        int binderNeeded = 1 + this.design.modules().size() / 3;
        int binderHave = count(com.fireworkshaders.pyro.FireworkShadersId.itemFor(Chemical.DEXTRIN),
                SLOT_REAGENT_START, SLOT_REAGENT_START + REAGENT_SLOTS);
        if (binderHave < binderNeeded) {
            missing.add(Component.translatable("gui." + FireworkShaders.MOD_ID + ".missing_count",
                    binderNeeded - binderHave, Chemical.DEXTRIN.nameComponent()));
        }
        int grainsNeeded = this.design.grainCost() / 2 + 1;
        int grainsHave = count(Items.GUNPOWDER, SLOT_PROPELLANT_START,
                SLOT_PROPELLANT_START + PROPELLANT_SLOTS);
        if (grainsHave < grainsNeeded) {
            missing.add(Component.translatable("gui." + FireworkShaders.MOD_ID + ".missing_count",
                    grainsNeeded - grainsHave, Component.translatable("item.minecraft.gunpowder")));
        }
        int paperHave = count(Items.PAPER, SLOT_PAPER, SLOT_PAPER + 1);
        if (paperHave < 1) {
            missing.add(Component.translatable("gui." + FireworkShaders.MOD_ID + ".missing_count",
                    1, Component.translatable("item.minecraft.paper")));
        }
        if (!this.items.get(SLOT_OUTPUT).isEmpty()) {
            missing.add(Component.translatable("gui." + FireworkShaders.MOD_ID + ".output_occupied"));
        }
        return missing;
    }

    public boolean canAfford() {
        return missingIngredients().isEmpty();
    }

    /** Consumes the ingredients and puts the assembled rocket in the output slot. */
    public boolean assemble(Player player) {
        if (!canAfford()) {
            return false;
        }
        for (Chemical chemical : this.design.palette()) {
            int needed = Math.max(1, Math.round(2.0F * this.design.caliber()));
            consume(com.fireworkshaders.pyro.FireworkShadersId.itemFor(chemical), needed,
                    SLOT_REAGENT_START, SLOT_REAGENT_START + REAGENT_SLOTS);
        }
        consume(com.fireworkshaders.pyro.FireworkShadersId.itemFor(Chemical.DEXTRIN),
                1 + this.design.modules().size() / 3, SLOT_REAGENT_START,
                SLOT_REAGENT_START + REAGENT_SLOTS);
        consume(Items.GUNPOWDER, this.design.grainCost() / 2 + 1, SLOT_PROPELLANT_START,
                SLOT_PROPELLANT_START + PROPELLANT_SLOTS);
        consume(Items.PAPER, 1, SLOT_PAPER, SLOT_PAPER + 1);

        this.items.set(SLOT_OUTPUT, this.design.createStack(FSItems.CUSTOM_FIREWORK.get(), 1));
        flashLit(30);
        setChanged();
        return true;
    }

    private int count(@Nullable Item item, int from, int to) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int i = from; i < Math.min(to, SIZE); i++) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void consume(@Nullable Item item, int amount, int from, int to) {
        if (item == null) {
            return;
        }
        int remaining = amount;
        for (int i = from; i < Math.min(to, SIZE) && remaining > 0; i++) {
            ItemStack stack = this.items.get(i);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    public void dropContents() {
        if (this.level != null) {
            Containers.dropContents(this.level, this.worldPosition, (Container) this);
        }
    }

    // ------------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + FireworkShaders.MOD_ID + ".pyrotechnic_workstation");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new PyrotechnicWorkstationMenu(windowId, inventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains("Design")) {
            this.design = RocketDesign.fromTag(tag.getCompound("Design"));
        }
        CompoundTag memory = tag.getCompound("Saved");
        for (int i = 0; i < SAVED_SLOTS; i++) {
            String key = "s" + i;
            this.saved[i] = memory.contains(key) ? RocketDesign.fromTag(memory.getCompound(key)) : null;
        }
        this.presetIndex = tag.getInt("Preset");
        this.selectedMemory = tag.getInt("Memory");
        this.litTicks = tag.getInt("Lit");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
        tag.put("Design", this.design.toTag());
        CompoundTag memory = new CompoundTag();
        for (int i = 0; i < SAVED_SLOTS; i++) {
            if (this.saved[i] != null) {
                memory.put("s" + i, this.saved[i].toTag());
            }
        }
        tag.put("Saved", memory);
        tag.putInt("Preset", this.presetIndex);
        tag.putInt("Memory", this.selectedMemory);
        tag.putInt("Lit", this.litTicks);
    }
}
