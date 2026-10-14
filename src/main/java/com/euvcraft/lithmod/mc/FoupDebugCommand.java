package com.euvcraft.lithmod.mc;

import java.util.List;
import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only debug commands for FOUPs, behind the {@code debugCommands} config key.
 *
 * <p>Registered at permission level 2 (gamemasters) and only when
 * {@link com.euvcraft.lithmod.core.FabSettings#debugCommands()} is true, which is off
 * by default. This is a development tool: it hands out lots with chosen contents and
 * prints hidden values, so it must not exist on a pack that ships to players.</p>
 *
 * <ul>
 *   <li>{@code /debug foup fill <count> <layer>} - gives the caller a FOUP holding
 *       {@code count} wafers, all at {@code layer}, with cumulative rolls drawn from
 *       the server-side random source.</li>
 *   <li>{@code /debug foup inspect} - prints the held FOUP's wafers, layer and
 *       cumulative roll, to the operator who typed the command and to nobody else.</li>
 * </ul>
 */
public final class FoupDebugCommand {

    /** Literal the debug tree hangs off. */
    public static final String ROOT = "debug";

    private FoupDebugCommand() {
    }

    /**
     * Registers the debug tree when the config allows it.
     *
     * @param event the command registration event
     */
    public static void register(RegisterCommandsEvent event) {
        if (!EUVCRAFTMod.settings().debugCommands()) {
            return;
        }

        LiteralArgumentBuilder<CommandSourceStack> foup = Commands.literal("foup")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("fill")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
                    .then(Commands.argument("layer", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                        .executes(FoupDebugCommand::fill))))
            .then(Commands.literal("inspect")
                .executes(FoupDebugCommand::inspect));

        event.getDispatcher().register(
            Commands.literal(ROOT)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(foup));

        EUVCRAFTMod.LOGGER.info("EUVCRAFT debug commands enabled: /{} foup fill|inspect"
            + " (vanilla already owns /{}, so the effective gate is that of the merged node)",
            ROOT, ROOT);
    }

    /**
     * Gives the caller a FOUP holding the requested number of wafers.
     *
     * <p>The configured capacity is the only bound on {@code count}: the command takes
     * any positive number and rejects whatever does not fit in the current capacity,
     * so the argument never duplicates the capacity that config already decides.</p>
     *
     * @param context the command context
     * @return the command result count
     */
    private static int fill(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.lithmod.foup.needs_player"));
            return 0;
        }

        int requested = IntegerArgumentType.getInteger(context, "count");
        int layer = IntegerArgumentType.getInteger(context, "layer");
        int capacity = FoupItems.configuredCapacity();
        if (requested > capacity) {
            source.sendFailure(Component.translatable("commands.lithmod.foup.too_many", requested, capacity));
            return 0;
        }

        Item item = ModItems.FOUP_MK1.get();
        UUID lotId = UUID.randomUUID();
        FoupContents contents = fillContents(source.getLevel(), lotId, capacity, requested, layer);
        ItemStack stack = FoupItems.with(item, contents);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }

        source.sendSuccess(() -> Component.translatable("commands.lithmod.foup.filled", requested, layer, capacity), false);
        return 1;
    }

    /**
     * Prints the held FOUP's wafers to the calling operator.
     *
     * @param context the command context
     * @return the command result count
     */
    private static int inspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.lithmod.foup.needs_player"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        FoupContents contents = FoupItems.contentsOf(held);
        if (contents == null) {
            source.sendFailure(Component.translatable("commands.lithmod.foup.not_holding"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.lithmod.foup.inspect_header",
            contents.lotId().toString(), contents.capacity(), contents.size(), contents.lotLayer()), false);

        List<Wafer> wafers = contents.wafers();
        for (int i = 0; i < wafers.size(); i++) {
            Wafer wafer = wafers.get(i);
            int index = i;
            source.sendSuccess(() -> Component.translatable("commands.lithmod.foup.inspect_row",
                index, wafer.layerIndex(), wafer.cumulativeRoll()), false);
        }

        return 1;
    }

    /**
     * Builds a lot whose wafers all sit at the given layer, with one roll drawn per
     * wafer from the server-side random source.
     *
     * <p>The rolls come from {@link FabRandomService#rngFor}, so they are the same
     * deterministic, server-only values the yield engine draws, keyed by lot id,
     * wafer index and step index. This command does not implement the yield roll
     * model; it only produces a lot whose cumulative rolls are unpredictable.</p>
     *
     * @param level    the server level the lot is created on
     * @param lotId    lot id to draw the rolls for
     * @param capacity lot capacity
     * @param count    number of wafers
     * @param layer    layer every wafer sits at
     * @return the filled lot
     */
    public static FoupContents fillContents(ServerLevel level, UUID lotId, int capacity, int count, int layer) {
        FoupContents contents = FoupContents.empty(lotId, capacity);
        for (int i = 0; i < count; i++) {
            contents = contents.withWafer(new Wafer(layer, rollFor(level, lotId, i), Wafer.NO_FLAGS));
        }
        return contents;
    }

    /**
     * Draws one cumulative roll for one wafer.
     *
     * <p>{@code ContextualRng.nextDouble()} is uniform over {@code (0, 1]}, but
     * narrowing it to a float can round a value that is just above zero down to
     * {@code 0.0f}, which {@link Wafer} rejects. Such a value is replaced with
     * {@link Float#MIN_VALUE}.</p>
     *
     * @param level      the server level to draw from
     * @param lotId      lot the wafer belongs to
     * @param waferIndex position of the wafer inside the lot
     * @return a roll in {@code (0, 1]}
     */
    public static float rollFor(ServerLevel level, UUID lotId, int waferIndex) {
        float roll = (float) FabRandomService.rngFor(level, lotId, waferIndex, 0).nextDouble();
        return roll <= 0.0f ? Float.MIN_VALUE : roll;
    }
}