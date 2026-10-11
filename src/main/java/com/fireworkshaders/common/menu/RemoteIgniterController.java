package com.fireworkshaders.common.menu;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.item.RemoteIgniterItem;
import com.fireworkshaders.common.net.FSAction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-side handling of the Remote Igniter.
 *
 * <p>Two security properties worth stating explicitly, because both are easy to get wrong:
 * <ol>
 *   <li>the pads that fire are read from the <em>server's</em> copy of the held stack, so the client
 *       cannot nominate a target,</li>
 *   <li>the pad must be within simulation distance of the igniter's owner and must have been linked
 *       from within link range, which is enforced at link time in the block's interaction.</li>
 * </ol>
 */
public final class RemoteIgniterController {

    private RemoteIgniterController() {}

    public static void apply(ServerPlayer player, int action, int handIndex) {
        InteractionHand hand = handIndex == 0 ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RemoteIgniterItem)) {
            // The client claimed to be holding the igniter in a hand that does not have one.
            return;
        }

        if (action == FSAction.IGNITER_CYCLE_MODE) {
            RemoteIgniterItem.Mode next = RemoteIgniterItem.cycleMode(stack);
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                            + ".igniter_mode", Component.translatable("mode." + FireworkShaders.MOD_ID
                            + "." + next.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.AQUA), true);
            player.containerMenu.broadcastChanges();
            return;
        }

        List<BlockPos> links = RemoteIgniterItem.links(stack);
        if (links.isEmpty()) {
            player.displayClientMessage(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".igniter_no_links").withStyle(ChatFormatting.RED), true);
            return;
        }

        int fired = 0;
        for (BlockPos pos : links) {
            if (!(player.level().getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
                continue;
            }
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 512.0 * 512.0) {
                continue;
            }
            switch (action) {
                case FSAction.IGNITER_ARM -> {
                    pad.arm();
                    fired++;
                }
                case FSAction.IGNITER_GO -> {
                    LaunchPadController.start(pad);
                    fired++;
                }
                case FSAction.IGNITER_SAFE -> {
                    pad.safe();
                    fired++;
                }
                case FSAction.IGNITER_TEST -> {
                    int cue = pad.getSelectedCue();
                    var cues = pad.getProgram().cues();
                    int slot = cue >= 0 && cue < cues.size() ? cues.get(cue).slot() : 0;
                    pad.testFire(slot);
                    fired++;
                }
                default -> {
                }
            }
            LaunchPadController.syncNearby(pad);
        }

        Component result = switch (action) {
            case FSAction.IGNITER_ARM -> Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".igniter_armed", fired).withStyle(ChatFormatting.GOLD);
            case FSAction.IGNITER_GO -> Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".igniter_go", fired).withStyle(ChatFormatting.GREEN);
            case FSAction.IGNITER_SAFE -> Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".igniter_safe", fired).withStyle(ChatFormatting.RED);
            default -> Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".igniter_test", fired).withStyle(ChatFormatting.AQUA);
        };
        player.displayClientMessage(result, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
