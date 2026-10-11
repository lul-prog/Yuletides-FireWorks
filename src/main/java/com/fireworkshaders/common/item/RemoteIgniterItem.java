package com.fireworkshaders.common.item;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.net.C2SIgniterActionPacket;
import com.fireworkshaders.common.net.FSAction;
import com.fireworkshaders.common.net.FSNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The show director's baton.
 *
 * <p>Links are stored on the item, not on the pad. A pad can be linked to any number of igniters
 * (useful for handing a second pyro technician a copy), and "can this person fire my show?" is a
 * question answered entirely by whether they are holding a linked igniter — no permission systems,
 * no block-entity lists, and unlinking is instant.
 *
 * <p>Modes cycle with sneak-use so the item can do all four show actions without a keybind:
 * {@code GO} fires, {@code ARM} pre-arms pads with their countdown lead-in, {@code SAFE} aborts,
 * {@code TEST} rehearses the selected cue on every linked pad. Sneak-use <em>on</em> a pad unlinks
 * it, which is the one destructive action, and it is deliberately the one you cannot do by accident.
 */
public class RemoteIgniterItem extends Item {

    public enum Mode {
        GO, ARM, SAFE, TEST
    }

    private static final String TAG_LINKS = "Pads";
    private static final String TAG_MODE = "Mode";
    private static final int MAX_LINKS = 16;

    public RemoteIgniterItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ modes

    public static Mode mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODE)) {
            return Mode.GO;
        }
        try {
            return Mode.valueOf(tag.getString(TAG_MODE));
        } catch (IllegalArgumentException e) {
            return Mode.GO;
        }
    }

    public static Mode cycleMode(ItemStack stack) {
        Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
        stack.getOrCreateTag().putString(TAG_MODE, next.name());
        return next;
    }

    public static int actionFor(Mode mode) {
        return switch (mode) {
            case GO -> FSAction.IGNITER_GO;
            case ARM -> FSAction.IGNITER_ARM;
            case SAFE -> FSAction.IGNITER_SAFE;
            case TEST -> FSAction.IGNITER_TEST;
        };
    }

    // ------------------------------------------------------------------ links

    public static List<BlockPos> links(ItemStack stack) {
        List<BlockPos> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            out.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        return out;
    }

    private static void writeLinks(ItemStack stack, List<BlockPos> links) {
        ListTag list = new ListTag();
        for (BlockPos pos : links) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            list.add(entry);
        }
        stack.getOrCreateTag().put(TAG_LINKS, list);
    }

    public static void link(Player player, ItemStack stack, BlockPos pos, LaunchPadBlockEntity pad) {
        List<BlockPos> links = links(stack);
        if (links.contains(pos)) {
            player.displayClientMessage(Component.translatable(msgKey("already_linked")), true);
            return;
        }
        if (links.size() >= MAX_LINKS) {
            player.displayClientMessage(Component.translatable(msgKey("link_limit"), MAX_LINKS), true);
            return;
        }
        links.add(pos.immutable());
        writeLinks(stack, links);
        player.displayClientMessage(Component.translatable(msgKey("linked"), links.size(),
                pad.loadedCount()), true);
        playPing(player, 0.8F);
    }

    public static void unlink(Player player, ItemStack stack, BlockPos pos, LaunchPadBlockEntity pad) {
        List<BlockPos> links = links(stack);
        if (links.remove(pos)) {
            writeLinks(stack, links);
            player.displayClientMessage(Component.translatable(msgKey("unlinked"), links.size()), true);
            playPing(player, 1.4F);
        } else {
            player.displayClientMessage(Component.translatable(msgKey("not_linked")), true);
        }
    }

    private static void playPing(Player player, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, pitch);
    }

    private static String msgKey(String suffix) {
        return "message." + FireworkShaders.MOD_ID + ".igniter_" + suffix;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                Mode next = cycleMode(stack);
                player.displayClientMessage(Component.translatable(msgKey("mode"),
                        Component.translatable("mode." + FireworkShaders.MOD_ID
                                + "." + next.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.AQUA),
                        true);
            } else {
                // The server resolves the action from its own copy of the stack: the client only
                // ever says "run the current mode".
                FSNetwork.sendToServer(new C2SIgniterActionPacket(
                        actionFor(mode(stack)), hand.ordinal()));
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        List<BlockPos> links = links(stack);
        tooltip.add(Component.translatable("tooltip." + FireworkShaders.MOD_ID + ".igniter_mode",
                        Component.translatable("mode." + FireworkShaders.MOD_ID
                                + "." + mode(stack).name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip." + FireworkShaders.MOD_ID + ".igniter_links",
                links.size(), MAX_LINKS).withStyle(ChatFormatting.GRAY));
        for (BlockPos pos : links) {
            tooltip.add(Component.literal("  " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip." + FireworkShaders.MOD_ID + ".igniter_help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !links(stack).isEmpty();
    }
}
