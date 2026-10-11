package com.fireworkshaders.common.menu;

import com.fireworkshaders.common.block.PyrotechnicWorkstationBlockEntity;
import com.fireworkshaders.common.item.ChemicalItem;
import com.fireworkshaders.registry.FSItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * The workstation's container: nine machine slots plus the player's inventory.
 *
 * <p>Design edits are <em>not</em> container operations, and they deliberately do not go through
 * the slot system: the design is not an item, it is a document being edited, and it is kept
 * server-authoritative by {@link WorkstationEditor}. Discrete edits arrive here as
 * {@link #clickMenuButton} calls (the vanilla int-button channel, which comes with free debouncing
 * and works even while the player is dragging items), while free text arrives as its own small
 * packet.
 *
 * <p>Slot roles are enforced by filters rather than by convention — the machine will not accept
 * bread in a reagent slot, and the output slot will not accept anything at all.
 */
public class PyrotechnicWorkstationMenu extends AbstractContainerMenu {

    public static final int SEPARATOR_X = 178;

    // Slot coordinates — the single source of truth for both the menu and the drawn background.
    public static final int REAGENT_X = 24;
    public static final int REAGENT_Y = 26;
    public static final int REAGENT_STEP = 22;
    public static final int PROPELLANT_X = 60;
    public static final int PROPELLANT_Y = 26;
    public static final int PAPER_X = 60;
    public static final int PAPER_Y = 92;
    public static final int OUTPUT_X = 120;
    public static final int OUTPUT_Y = 59;

    public static final int INV_X = 8;
    public static final int INV_Y = 140;
    public static final int HOTBAR_Y = 198;

    private final BlockPos pos;
    @Nullable
    private final PyrotechnicWorkstationBlockEntity workstation;
    private final Container container;

    /** Client-side constructor used by the menu-type factory (position arrives in the packet). */
    public PyrotechnicWorkstationMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, resolve(inventory, pos));
    }

    /** Server-side constructor. */
    public PyrotechnicWorkstationMenu(int windowId, Inventory inventory,
                                      PyrotechnicWorkstationBlockEntity workstation) {
        this(windowId, inventory, (BlockEntity) workstation);
        if (workstation != null) {
            // Mirror the server's design to the client immediately so the preview pane is correct
            // on the very first frame the GUI is drawn.
            if (inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                WorkstationEditor.syncTo(serverPlayer, workstation);
            }
        }
    }

    @Nullable
    private static BlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos);
    }

    private PyrotechnicWorkstationMenu(int windowId, Inventory inventory, @Nullable BlockEntity entity) {
        super(com.fireworkshaders.registry.FSMenus.PYROTECHNIC_WORKSTATION.get(), windowId);
        this.pos = entity != null ? entity.getBlockPos() : BlockPos.ZERO;
        this.workstation = entity instanceof PyrotechnicWorkstationBlockEntity ws ? ws : null;
        this.container = this.workstation != null ? this.workstation
                : new SimpleContainer(PyrotechnicWorkstationBlockEntity.SIZE);

        // ---- machine: reagents ------------------------------------------------------------
        for (int i = 0; i < PyrotechnicWorkstationBlockEntity.REAGENT_SLOTS; i++) {
            addSlot(new Slot(this.container, PyrotechnicWorkstationBlockEntity.SLOT_REAGENT_START + i,
                    REAGENT_X, REAGENT_Y + i * REAGENT_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ChemicalItem;
                }
            });
        }
        // ---- machine: propellant ----------------------------------------------------------
        for (int i = 0; i < PyrotechnicWorkstationBlockEntity.PROPELLANT_SLOTS; i++) {
            addSlot(new Slot(this.container, PyrotechnicWorkstationBlockEntity.SLOT_PROPELLANT_START + i,
                    PROPELLANT_X, PROPELLANT_Y + i * REAGENT_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(net.minecraft.world.item.Items.GUNPOWDER);
                }
            });
        }
        // ---- machine: paper and output -----------------------------------------------------
        addSlot(new Slot(this.container, PyrotechnicWorkstationBlockEntity.SLOT_PAPER, PAPER_X, PAPER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.PAPER);
            }
        });
        addSlot(new Slot(this.container, PyrotechnicWorkstationBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // ---- player -------------------------------------------------------------------------
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Nullable
    public PyrotechnicWorkstationBlockEntity getWorkstation() {
        return this.workstation;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType,
                        Player player) {
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Every discrete design edit from the GUI lands here. The server owns the design; the client
     * only ever sends the intent.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WorkstationEditor.apply(serverPlayer, this.pos, id, null);
            return true;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = PyrotechnicWorkstationBlockEntity.SIZE;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (stack.is(net.minecraft.world.item.Items.GUNPOWDER)) {
                moved = moveItemStackTo(stack,
                        PyrotechnicWorkstationBlockEntity.SLOT_PROPELLANT_START,
                        PyrotechnicWorkstationBlockEntity.SLOT_PROPELLANT_START
                                + PyrotechnicWorkstationBlockEntity.PROPELLANT_SLOTS, false);
            } else if (stack.is(net.minecraft.world.item.Items.PAPER)) {
                moved = moveItemStackTo(stack, PyrotechnicWorkstationBlockEntity.SLOT_PAPER,
                        PyrotechnicWorkstationBlockEntity.SLOT_PAPER + 1, false);
            } else if (stack.getItem() instanceof ChemicalItem) {
                moved = moveItemStackTo(stack, PyrotechnicWorkstationBlockEntity.SLOT_REAGENT_START,
                        PyrotechnicWorkstationBlockEntity.SLOT_REAGENT_START
                                + PyrotechnicWorkstationBlockEntity.REAGENT_SLOTS, false);
            }
            if (!moved && !moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        // On the client the block entity may not exist yet for a frame or two; the server is the
        // side that owns validity, so a null entity here is not a reason to close the screen.
        if (this.workstation == null) {
            return true;
        }
        return Container.stillValidBlockEntity(this.workstation, player);
    }

    public ItemStack outputPreview() {
        return this.container.getItem(PyrotechnicWorkstationBlockEntity.SLOT_OUTPUT);
    }

    /** True when the machine can pay for the current design (server side only). */
    public boolean canAssemble() {
        return this.workstation != null && this.workstation.canAfford();
    }

    public ItemStack iconStack() {
        return new ItemStack(FSItems.CUSTOM_FIREWORK.get());
    }
}
