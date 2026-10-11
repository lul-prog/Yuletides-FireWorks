package com.fireworkshaders.common.menu;

import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.item.CustomFireworkItem;
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

/** Nine tubes on the left, the show programmer on the right. */
public class LaunchPadMenu extends AbstractContainerMenu {

    public static final int PAD_X = 26;
    public static final int PAD_Y = 22;
    public static final int INV_X = 8;
    public static final int INV_Y = 118;
    public static final int HOTBAR_Y = 176;

    private final BlockPos pos;
    @Nullable
    private final LaunchPadBlockEntity pad;
    private final Container container;

    public LaunchPadMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, inventory.player.level().getBlockEntity(pos));
    }

    public LaunchPadMenu(int windowId, Inventory inventory, LaunchPadBlockEntity pad) {
        this(windowId, inventory, (BlockEntity) pad);
        if (pad != null && inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            LaunchPadController.syncTo(serverPlayer, pad);
        }
    }

    private LaunchPadMenu(int windowId, Inventory inventory, @Nullable BlockEntity entity) {
        super(com.fireworkshaders.registry.FSMenus.LAUNCH_PAD.get(), windowId);
        this.pos = entity != null ? entity.getBlockPos() : BlockPos.ZERO;
        this.pad = entity instanceof LaunchPadBlockEntity launchPad ? launchPad : null;
        this.container = this.pad != null ? this.pad : new SimpleContainer(LaunchPadBlockEntity.SLOTS);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                addSlot(new Slot(this.container, index, PAD_X + col * 18, PAD_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof CustomFireworkItem;
                    }
                });
            }
        }

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
    public LaunchPadBlockEntity getPad() {
        return this.pad;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // Cue-programming buttons reuse the container-button channel; only the action id and a
            // small payload travel, and the server decides what it means.
            int action = id & 0xFF;
            int value = (id >> 8) - 512;
            LaunchPadController.apply(serverPlayer, this.pos, action, value);
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
        if (index < LaunchPadBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, LaunchPadBlockEntity.SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, LaunchPadBlockEntity.SLOTS, false)) {
            return ItemStack.EMPTY;
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
        if (this.pad == null) {
            return true;
        }
        return Container.stillValidBlockEntity(this.pad, player);
    }
}
