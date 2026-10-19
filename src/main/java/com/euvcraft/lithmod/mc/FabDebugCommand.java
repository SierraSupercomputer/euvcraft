package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only debug command for filling energy of a fab machine the player is looking at,
 * behind the {@code debugCommands} config key.
 *
 * <p>Registered at permission level 3 (admins) and only when
 * {@link com.euvcraft.lithmod.core.FabSettings#debugCommands()} is true, which is off
 * by default. This is a development tool: it sets a machine's energy buffer to full capacity,
 * so it must not exist on a pack that ships to players.</p>
 *
 * <ul>
 *   <li>{@code /debug fab energy} - fills the energy buffer of the {@link FabMachineBlockEntity}
 *       (including a formed {@link ProcessCellControllerBlockEntity}) the player is looking at,
 *       up to a normal block interaction distance (6 blocks).</li>
 * </ul>
 */
public final class FabDebugCommand {

    /** Literal the debug tree hangs off. */
    public static final String ROOT = "debug";

    /** The fab sub-literal. */
    private static final String FAB = "fab";

    private FabDebugCommand() {
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

        LiteralArgumentBuilder<CommandSourceStack> energy = Commands.literal("energy")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .executes(FabDebugCommand::fillEnergy);

        LiteralArgumentBuilder<CommandSourceStack> fab = Commands.literal(FAB)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(energy);

        event.getDispatcher().register(
            Commands.literal(ROOT)
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(fab));

        EUVCRAFTMod.LOGGER.info("EUVCRAFT debug commands enabled: /{} {} energy"
            + " (vanilla already owns /{}, so the effective gate is that of the merged node)",
            ROOT, FAB, ROOT);
    }

    /**
     * Fills the energy buffer of the looked-at fab machine to full capacity.
     *
     * @param context the command context
     * @return the command result count
     */
    private static int fillEnergy(CommandContext<CommandSourceStack> context) {
        // Runtime config check: disabling after registration prevents use
        if (!EUVCRAFTMod.settings().debugCommands()) {
            CommandSourceStack source = context.getSource();
            source.sendFailure(Component.translatable("commands.lithmod.fab.energy.disabled"));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.lithmod.fab.energy.needs_player"));
            return 0;
        }

        // Raycast from player's eyes up to normal block interaction distance (6 blocks)
        HitResult picked = player.pick(6.0, 1.0f, false);
        if (!(picked instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.translatable("commands.lithmod.fab.energy.no_target"));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        ServerLevel level = source.getLevel();
        var blockEntity = level.getBlockEntity(pos);
        FabMachineBlockEntity machine = blockEntity instanceof FabMachineBlockEntity be ? be : null;

        if (machine == null) {
            source.sendFailure(Component.translatable("commands.lithmod.fab.energy.not_machine"));
            return 0;
        }

        // Fill the energy buffer to capacity
        int capacity = machine.buffer().getCapacityAsInt();
        if (capacity <= 0) {
            source.sendFailure(Component.translatable("commands.lithmod.fab.energy.no_capacity"));
            return 0;
        }

        machine.setStoredEnergy(capacity);
        int stored = machine.buffer().getAmountAsInt();

        source.sendSuccess(() -> Component.translatable(
            "commands.lithmod.fab.energy.filled", stored, capacity), false);
        return 1;
    }
}
