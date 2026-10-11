package com.fireworkshaders.common.block;

import com.fireworkshaders.common.menu.LaunchPadController;
import com.fireworkshaders.common.item.CustomFireworkItem;
import com.fireworkshaders.common.item.RemoteIgniterItem;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * The Launch Pad. Nine mortar tubes in a 3x3, fanned so a salvo from one pad spreads instead of
 * stacking into a single column.
 *
 * <p>Interaction model (no keybinds, everything discoverable by holding the right item):
 * <ul>
 *   <li>hold a designed rocket and right-click — load it into the first free tube,</li>
 *   <li>hold the Remote Igniter and right-click — link the pad to that igniter,</li>
 *   <li>hold the Remote Igniter and sneak-right-click — unlink it,</li>
 *   <li>empty hand — open the show programmer.</li>
 * </ul>
 */
public class LaunchPadBlock extends Block implements EntityBlock {

    /** Lit while at least one tube is loaded and the pad is armed — visible from the ground. */
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    public LaunchPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ARMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof LaunchPadBlockEntity pad) {
                pad.serverTick();
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            ItemStack held = player.getItemInHand(hand);
            boolean igniter = held.getItem() instanceof RemoteIgniterItem;
            boolean sneaking = player.isShiftKeyDown();

            if (igniter) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (sneaking) {
                    RemoteIgniterItem.unlink(player, held, pos, pad);
                } else {
                    RemoteIgniterItem.link(player, held, pos, pad);
                }
                return InteractionResult.CONSUME;
            }

            if (held.getItem() instanceof CustomFireworkItem) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                RocketDesign design = RocketDesign.fromStack(held);
                if (design == null) {
                    return InteractionResult.CONSUME;
                }
                if (pad.loadDesign(design)) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS,
                            0.7F, 1.3F);
                    player.displayClientMessage(Component.translatable("message." + com.fireworkshaders.FireworkShaders.MOD_ID
                            + ".pad_loaded", design.displayName()), true);
                } else {
                    player.displayClientMessage(Component.translatable("message."
                            + com.fireworkshaders.FireworkShaders.MOD_ID + ".pad_full"), true);
                }
                return InteractionResult.CONSUME;
            }

            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                LaunchPadController.syncTo(serverPlayer, pad);
                NetworkHooks.openScreen(serverPlayer, pad, pos);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            pad.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
