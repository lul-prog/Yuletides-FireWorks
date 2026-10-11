package com.fireworkshaders.common.item;

import com.fireworkshaders.common.entity.PyroRocketEntity;
import com.fireworkshaders.common.menu.WorkstationEditor;
import com.fireworkshaders.pyro.RocketDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A designed shell, in item form. One item, infinite designs — the {@link RocketDesign} lives in
 * the stack's NBT, so a "shell" is a document, not an item type.
 *
 * <p>Two ways to use it, both of them real:
 * <ul>
 *   <li><b>Right-click in the air</b> — fires it like a shoulder tube, along your aim. Elevation is
 *       clamped to at least 35° so nobody can machine-gun their own feet.</li>
 *   <li><b>Right-click a block</b> — sets it up as a ground mortar: it launches straight up out of
 *       that block face after ignition, which is how you build a display line by hand.</li>
 * </ul>
 */
public class CustomFireworkItem extends Item {

    public CustomFireworkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RocketDesign design = RocketDesign.fromStack(stack);
        if (design == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && level instanceof ServerLevel server) {
            float yaw = player.getYRot();
            float elevation = Mth.clamp(-player.getXRot(), 35.0F, 90.0F);
            Vec3 muzzle = player.getEyePosition()
                    .add(Vec3.directionFromRotation(-elevation, yaw).scale(0.7));
            PyroRocketEntity.launch(server, muzzle, design, yaw, elevation, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F,
                    0.8F + level.random.nextFloat() * 0.2F);
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        RocketDesign design = RocketDesign.fromStack(stack);
        if (design == null || player == null) {
            return InteractionResult.PASS;
        }
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(target).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level instanceof ServerLevel server) {
            Vec3 origin = Vec3.atBottomCenterOf(target);
            PyroRocketEntity.launch(server, origin, design, 0.0F, 90.0F, player);
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.BLOCKS, 0.9F, 0.9F + level.random.nextFloat() * 0.2F);
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        RocketDesign design = RocketDesign.fromStack(stack);
        if (design == null) {
            tooltip.add(Component.translatable("tooltip." + com.fireworkshaders.pyro.FireworkShadersId.NS
                    + ".unbuilt").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.addAll(design.tooltip());
        tooltip.add(Component.translatable("tooltip." + com.fireworkshaders.pyro.FireworkShadersId.NS
                + ".burst_radius", String.format("%.0f", design.burstRadius()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        RocketDesign design = RocketDesign.fromStack(stack);
        return design != null && design.modules().size() >= 3;
    }

    /** Convenience used by the workstation to preview what a design costs. */
    public static Component costLine(RocketDesign design) {
        return WorkstationEditor.costComponent(design);
    }
}
