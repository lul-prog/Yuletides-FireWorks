package com.fireworkshaders;

import com.fireworkshaders.common.block.LaunchPadBlockEntity;
import com.fireworkshaders.common.entity.PyroRocketEntity;
import com.fireworkshaders.registry.FSItems;
import com.fireworkshaders.pyro.RocketDesign;
import com.fireworkshaders.pyro.ShellPresets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@code /fireworkshaders test stardust_willow} launches a shell slightly above the caller, which
 * is how you tune a show without building one. All show control is permission level 2 — a command
 * that can fire a player's display should not be available to everyone on a server.
 */
public final class FSCommands {

    private FSCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fireworkshaders")
                .then(Commands.literal("test")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ShellPresets.all().stream().map(ShellPresets.Preset::id), builder))
                                .executes(ctx -> test(ctx, 26))
                                .then(Commands.argument("height", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> test(ctx,
                                                IntegerArgumentType.getInteger(ctx, "height"))))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ShellPresets.all().stream().map(ShellPresets.Preset::id), builder))
                                .executes(FSCommands::give)))
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("arm").executes(ctx -> show(ctx, "arm")))
                        .then(Commands.literal("go").executes(ctx -> show(ctx, "go")))
                        .then(Commands.literal("safe").executes(ctx -> show(ctx, "safe")))
                        .then(Commands.literal("status").executes(FSCommands::status)))
                .then(Commands.literal("info")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ShellPresets.all().stream().map(ShellPresets.Preset::id), builder))
                                .executes(FSCommands::info)))
                .then(Commands.literal("modes")
                        .executes(ctx -> {
                            ShellPresets.Preset preset = ShellPresets.STARDUST_WILLOW;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    preset.design().toString()), false);
                            return 1;
                        })));
    }

    private static int test(CommandContext<CommandSourceStack> ctx, int height) {
        String id = StringArgumentType.getString(ctx, "preset");
        ShellPresets.Preset preset = ShellPresets.byId(id);
        if (preset == null) {
            preset = ShellPresets.byIndex(0);
        }
        ShellPresets.Preset resolved = preset;
        // 1.20.1: getLevel() already returns ServerLevel, no instanceof check needed.
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 origin = ctx.getSource().getPosition().add(0.0, height, 0.0);
        ServerPlayer player = ctx.getSource().getPlayer();
        PyroRocketEntity.launch(level, origin, resolved.design(),
                player == null ? 0.0F : player.getYRot(), 88.0F, player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message." + FireworkShaders.MOD_ID
                + ".command_test", resolved.name()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "preset");
        ShellPresets.Preset preset = ShellPresets.byId(id);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown preset: " + id));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = preset.design().createStack(FSItems.CUSTOM_FIREWORK.get(), 8);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message." + FireworkShaders.MOD_ID
                + ".command_give", preset.name()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, String action) {
        LaunchPadBlockEntity pad = nearestPad(ctx);
        if (pad == null) {
            ctx.getSource().sendFailure(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".command_no_pad"));
            return 0;
        }
        switch (action) {
            case "arm" -> pad.arm();
            case "go" -> com.fireworkshaders.common.menu.LaunchPadController.start(pad);
            case "safe" -> pad.safe();
            default -> {
                return 0;
            }
        }
        com.fireworkshaders.common.menu.LaunchPadController.syncNearby(pad);
        ctx.getSource().sendSuccess(() -> Component.translatable("message." + FireworkShaders.MOD_ID
                + ".command_show", action, pad.getProgram().enabledCount(),
                pad.loadedCount()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        LaunchPadBlockEntity pad = nearestPad(ctx);
        if (pad == null) {
            ctx.getSource().sendFailure(Component.translatable("message." + FireworkShaders.MOD_ID
                    + ".command_no_pad"));
            return 0;
        }
        List<String> lines = List.of(
                "state: " + pad.getPadState(),
                "loaded: " + pad.loadedCount() + "/9",
                "cues: " + pad.getProgram().enabledCount() + " of " + pad.getProgram().cues().size(),
                "duration: " + pad.getProgram().durationTicks() + "t",
                "lead-in: " + pad.getProgram().leadInTicks() + "t",
                "t-minus: " + pad.ticksToStart() + "t");
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "preset");
        ShellPresets.Preset preset = ShellPresets.byId(id);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown preset: " + id));
            return 0;
        }
        RocketDesign design = preset.design();
        ctx.getSource().sendSuccess(() -> Component.literal(preset.name().getString())
                .withStyle(ChatFormatting.GOLD), false);
        for (Component line : design.tooltip()) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "stars=%d radius=%.1f speed=%.2f lift=%.2f power=%.2f grains=%d",
                design.starCount(), design.burstRadius(), design.burstSpeed(), design.liftSpeed(),
                design.power(), design.grainCost())).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    @Nullable
    private static LaunchPadBlockEntity nearestPad(CommandContext<CommandSourceStack> ctx) {
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
        ServerLevel level = ctx.getSource().getLevel();
        LaunchPadBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-24, -12, -24), origin.offset(24, 12, 24))) {
            if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pad;
                }
            }
        }
        return best;
    }
}
