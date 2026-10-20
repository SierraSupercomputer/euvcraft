package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Real server-level tests for the shared support-multiblock foundation (T3 foundation, no jobs).
 *
 * <p>The twelve positions are recomputed here from the core definition with a literal basis
 * vector table, the same way {@code MultiblockGameTests} treats {@code StructureMatcher}, so a
 * regression in {@code SupportStructureMatcher} fails these tests instead of being agreed with by
 * both sides.</p>
 *
 * <p>No client GUI class is loaded: the screens are client-only, and the menu is exercised
 * server-side over a real block entity, which is the supported game-test path.</p>
 *
 * <p>Loot/state-containing drops are intentionally not asserted beyond the menu inventory: block
 * states, models, crafting and loot tables are the next resource phase.</p>
 */
public final class SupportMultiblockGameTests {

    private static final String ENVIRONMENT_ID = "support_multiblock";

    private static final String FORMS_ALL_FACINGS_BOTH_COLUMNS = "support_forms_all_facings_both_columns";
    private static final String REMOVAL_REFORM_AND_ALWAYS_REASON = "support_removal_reform_with_reason";
    private static final String RELOADED_MEMBER_FLAGS_REPAIRED = "support_reloaded_member_flags_repaired";
    private static final String REPLACED_CORE_NOT_RECLAIMED = "support_replaced_core_not_reclaimed";
    private static final String NEIGHBOR_CELLS_NO_OVERLAP = "support_neighbor_cells_no_overlap";
    private static final String FE_ONLY_FORMED_OWNED_PORT = "support_fe_only_formed_owned_port";
    private static final String PURIFIER_WATER_FOUNDATION = "support_purifier_water_foundation";
    private static final String MENU_INDICES_COORDS_VALIDITY = "support_menu_indices_coords_validity";
    private static final String NO_AUTOMATIC_RECIPE_RUNNER = "support_definition_numbers_no_automatic_runner";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");

    static {
        FUNCTIONS.register(FORMS_ALL_FACINGS_BOTH_COLUMNS, () -> SupportMultiblockGameTests::formsAllFacingsBothColumns);
        FUNCTIONS.register(REMOVAL_REFORM_AND_ALWAYS_REASON, () -> SupportMultiblockGameTests::removalReformWithReason);
        FUNCTIONS.register(RELOADED_MEMBER_FLAGS_REPAIRED, () -> SupportMultiblockGameTests::reloadedMemberFlagsRepaired);
        FUNCTIONS.register(REPLACED_CORE_NOT_RECLAIMED, () -> SupportMultiblockGameTests::replacedCoreNotReclaimed);
        FUNCTIONS.register(NEIGHBOR_CELLS_NO_OVERLAP, () -> SupportMultiblockGameTests::neighborCellsNoOverlap);
        FUNCTIONS.register(FE_ONLY_FORMED_OWNED_PORT, () -> SupportMultiblockGameTests::feOnlyFormedOwnedPort);
        FUNCTIONS.register(PURIFIER_WATER_FOUNDATION, () -> SupportMultiblockGameTests::purifierWaterFoundation);
        FUNCTIONS.register(MENU_INDICES_COORDS_VALIDITY, () -> SupportMultiblockGameTests::menuIndicesCoordsValidity);
        FUNCTIONS.register(NO_AUTOMATIC_RECIPE_RUNNER, () -> SupportMultiblockGameTests::definitionNumbersNoAutomaticRunner);
    }

    private SupportMultiblockGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) SupportMultiblockGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, FORMS_ALL_FACINGS_BOTH_COLUMNS);
        register(event, data, REMOVAL_REFORM_AND_ALWAYS_REASON);
        register(event, data, RELOADED_MEMBER_FLAGS_REPAIRED);
        register(event, data, REPLACED_CORE_NOT_RECLAIMED);
        register(event, data, NEIGHBOR_CELLS_NO_OVERLAP);
        register(event, data, FE_ONLY_FORMED_OWNED_PORT);
        register(event, data, PURIFIER_WATER_FOUNDATION);
        register(event, data, MENU_INDICES_COORDS_VALIDITY);
        register(event, data, NO_AUTOMATIC_RECIPE_RUNNER);
        EUVCRAFTMod.LOGGER.info("Registered EUVCRAFT support multiblock game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // geometry, recomputed independently of SupportStructureMatcher
    // ------------------------------------------------------------------

    private static final StructureShape SHAPE = SupportStructureMatcher.SUPPORT_SHAPE;

    /** Unit vector of each facing, written out from the core definition. */
    private static int[] facingVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 0, 0, -1 };
            case SOUTH -> new int[] { 0, 0, 1 };
            case EAST -> new int[] { 1, 0, 0 };
            case WEST -> new int[] { -1, 0, 0 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    private static int[] backVector(Direction facing) {
        int[] v = facingVector(facing);
        return new int[] { -v[0], -v[1], -v[2] };
    }

    private static int[] rightVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 1, 0, 0 };
            case SOUTH -> new int[] { -1, 0, 0 };
            case EAST -> new int[] { 0, 0, 1 };
            case WEST -> new int[] { 0, 0, -1 };
            default -> new int[] { 1, 0, 0 };
        };
    }

    private static final int[] UP = { 0, 1, 0 };

    /** Canonical order of the shape: u*6 + v*3 + d, matching SupportStructureMatcher. */
    private static BlockPos cellOf(BlockPos controller, Direction facing, int u0, int u, int v, int d) {
        int[] right = rightVector(facing);
        int[] back = backVector(facing);
        return new BlockPos(
            controller.getX() + right[0] * (u - u0) + UP[0] * (v - v0) + back[0] * d,
            controller.getY() + right[1] * (u - u0) + UP[1] * (v - v0) + back[1] * d,
            controller.getZ() + right[2] * (u - u0) + UP[2] * (v - v0) + back[2] * d);
    }

    /** Back-left lower cell; never the anchor, because the anchor is always d == 0. */
    private static final int[] PORT = { 0, 0, 2 };

    /** The twelve cells in canonical (u*6+v*3+d) order. */
    private static List<BlockPos> cellsOf(BlockPos controller, Direction facing, int u0) {
        List<BlockPos> cells = new ArrayList<>(SHAPE.volume());
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    cells.add(cellOf(controller, facing, u0, u, v, d));
                }
            }
        }
        return cells;
    }

    /** Canonical flat index of a (u, v, d) cell. */
    private static int flatIndex(int u, int v, int d) {
        return u * 6 + v * 3 + d;
    }

    private static BlockPos portOf(BlockPos controller, Direction facing, int u0) {
        return cellsOf(controller, facing, u0).get(flatIndex(PORT[0], PORT[1], PORT[2]));
    }

    // ------------------------------------------------------------------
    // world building
    // ------------------------------------------------------------------

    /**
     * Places a complete, valid support cell and returns its twelve relative positions in canonical
     * order: the controller (with facing), one power port, and the rest casing.
     */
    private static List<BlockPos> placeSupportCell(GameTestHelper helper, SupportMachineKind kind,
            BlockPos controller, Direction facing, int u0) {
        List<BlockPos> placed = new ArrayList<>();
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos pos = cellOf(controller, facing, u0, u, v, d);
                    if (u == u0 && v == 0 && d == 0) {
                        helper.setBlock(pos, blockFor(kind).defaultBlockState()
                            .setValue(SupportMachineControllerBlock.FACING, facing));
                    } else if (u == PORT[0] && v == PORT[1] && d == PORT[2]) {
                        helper.setBlock(pos, SupportMachines.SUPPORT_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(pos, SupportMachines.SUPPORT_CASING.get().defaultBlockState());
                    }
                    placed.add(pos);
                }
            }
        }
        return placed;
    }

    private static Block blockFor(SupportMachineKind kind) {
        return kind == SupportMachineKind.PURIFIER
            ? SupportMachines.BOULE_PURIFIER.get()
            : SupportMachines.E_BEAM_MASK_WRITER.get();
    }

    private static void clear(GameTestHelper helper, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static @Nullable SupportMachineBlockEntity controllerAt(GameTestHelper helper, BlockPos rel) {
        return controllerAt(helper.getLevel(), helper.absolutePos(rel));
    }

    private static @Nullable SupportMachineBlockEntity controllerAt(ServerLevel level, BlockPos abs) {
        return level.getBlockEntity(abs) instanceof SupportMachineBlockEntity controller ? controller : null;
    }

    private static boolean formedFlag(ServerLevel level, BlockPos abs) {
        BlockState state = level.getBlockState(abs);
        return state.hasProperty(SupportMachineControllerBlock.FORMED)
            && state.getValue(SupportMachineControllerBlock.FORMED);
    }

    private static void assertFormed(GameTestHelper helper, SupportMachineBlockEntity controller,
            BlockPos controllerRel, Direction facing, int u0, String what) {
        helper.assertTrue(controller.isFormed(),
            what + ": not formed (reason: " + controller.failureReason() + ")");
        if (!controller.isFormed()) {
            return;
        }
        List<BlockPos> expected = cellsOf(controllerRel, facing, u0);
        List<BlockPos> actual = controller.members();
        if (actual.size() != expected.size()) {
            helper.fail(what + ": expected 12 members, got " + actual.size());
            return;
        }
        for (int i = 0; i < expected.size(); i++) {
            BlockPos absExpected = helper.absolutePos(expected.get(i));
            helper.assertTrue(actual.get(i).equals(absExpected),
                what + ": canonical order broken at " + i + ": expected " + absExpected
                    + " got " + actual.get(i));
            helper.assertTrue(formedFlag(helper.getLevel(), actual.get(i)),
                what + ": " + actual.get(i) + " does not carry the formed flag");
        }
    }

    // ------------------------------------------------------------------
    // game tests
    // ------------------------------------------------------------------

    /**
     * Both kinds and every facing, anchoring the controller in either front column. Each round
     * rebuilds on the same spot and clears the previous cell, so the structure stays inside the
     * test bounds. The canonical ordered part index is pinned by member list equality against the
     * independently recomputed u*6+v*3+d order.
     */
    private static void formsAllFacingsBothColumns(GameTestHelper h) {
        Direction[] facings = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
        SupportMachineKind[] kinds = { SupportMachineKind.PURIFIER, SupportMachineKind.EBEAM };
        for (SupportMachineKind kind : kinds) {
            for (Direction facing : facings) {
                for (int u0 = 0; u0 <= 1; u0++) {
                    BlockPos controller = new BlockPos(1, 1, 1);
                    List<BlockPos> cells = placeSupportCell(h, kind, controller, facing, u0);
                    SupportMachineBlockEntity machine = controllerAt(h, controller);
                    h.assertTrue(machine != null, kind + ": no controller BE built");
                    if (machine != null) {
                        machine.revalidate();
                        assertFormed(h, machine, controller, facing, u0, kind + "@" + facing + "#u" + u0);
                        h.assertTrue(machine.kind() == kind, "kind must come from the block state");
                    }
                    clear(h, cells);
                }
            }
        }
        h.succeed();
    }

    /**
     * Removing any member unforms with a reason and counts a transition; a foreign block is
     * refused with a reason; restoring the block reforms.
     */
    private static void removalReformWithReason(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        List<BlockPos> cells = placeSupportCell(h, SupportMachineKind.PURIFIER, controller, facing, 0);
        SupportMachineBlockEntity machine = controllerAt(h, controller);
        if (machine == null) {
            h.fail("no controller BE");
            return;
        }
        machine.revalidate();
        h.assertTrue(machine.isFormed(), "initial form failed");
        h.assertValueEqual(1, machine.formationChanges(), "first transition");

        BlockPos broken = null;
        for (BlockPos cell : cells) {
            if (!cell.equals(portOf(controller, facing, 0))) {
                broken = cell;
                break;
            }
        }
        if (broken == null) {
            h.fail("no breakable casing cell found");
            return;
        }
        h.setBlock(broken, Blocks.AIR.defaultBlockState());
        machine.revalidate();
        h.assertFalse(machine.isFormed(), "machine must unform after losing a member block");
        h.assertTrue(!machine.failureReason().isEmpty(), "failure reason must explain the broken machine");
        h.assertValueEqual(2, machine.formationChanges(), "second transition");

        h.setBlock(broken, Blocks.STONE.defaultBlockState());
        machine.revalidate();
        h.assertFalse(machine.isFormed(), "stone must never satisfy a support structure");

        h.setBlock(broken, SupportMachines.SUPPORT_CASING.get().defaultBlockState());
        machine.revalidate();
        h.assertTrue(machine.isFormed(), "restoring the casing must reform");
        h.assertTrue(machine.failureReason().isEmpty(), "reason must clear when formed again");
        h.assertValueEqual(4, machine.formationChanges(), "fourth transition");
        h.succeed();
    }

    /**
     * Replacing a formed member with a fresh block resets its visual state; the next controller
     * pass repairs the flags in the canonical part order without a formation transition.
     */
    private static void reloadedMemberFlagsRepaired(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        Direction facing = Direction.EAST;
        placeSupportCell(h, SupportMachineKind.EBEAM, controller, facing, 0);
        SupportMachineBlockEntity machine = controllerAt(h, controller);
        if (machine == null) {
            h.fail("no controller BE");
            return;
        }
        machine.revalidate();
        h.assertTrue(machine.isFormed(), "initial form failed");

        int targetPart = 7;
        BlockPos target = machine.members().get(targetPart);
        h.getLevel().setBlock(target, SupportMachines.SUPPORT_CASING.get().defaultBlockState(), 3);
        BlockState resetState = h.getLevel().getBlockState(target);
        h.assertTrue(resetState.getBlock() instanceof SupportCasingBlock, "replacement is a support casing");
        h.assertValueEqual(0, resetState.getValue(SupportMachineControllerBlock.PART).intValue(),
            "fresh block must start with part 0");

        machine.revalidate();
        BlockState repaired = h.getLevel().getBlockState(target);
        h.assertTrue(formedFlag(h.getLevel(), target), "reload must repair the formed flag");
        h.assertValueEqual(targetPart, repaired.getValue(SupportMachineControllerBlock.PART).intValue(),
            "reload must repair the ordered part");
        h.assertValueEqual(1, machine.formationChanges(), "flag repair is not a transition");
        h.assertTrue(machine.members().get(targetPart).equals(target), "membership identity stable");
        h.succeed();
    }

    /**
     * Replacing the controller block discards its block entity: the departed owner is removed,
     * cannot push anything new, the members reset their visual state through their own backstop,
     * and the unowned port exposes no FE. A fresh re-validation of the new instance reforms.
     */
    private static void replacedCoreNotReclaimed(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        Direction facing = Direction.WEST;
        List<BlockPos> cells = placeSupportCell(h, SupportMachineKind.PURIFIER, controller, facing, 0);
        SupportMachineBlockEntity old = controllerAt(h, controller);
        if (old == null) {
            h.fail("no controller BE");
            return;
        }
        old.revalidate();
        h.assertTrue(old.isFormed(), "initial form failed");
        BlockPos portAbs = h.absolutePos(portOf(controller, facing, 0));

        h.getLevel().setBlock(h.absolutePos(controller), blockFor(SupportMachineKind.PURIFIER)
            .defaultBlockState().setValue(SupportMachineControllerBlock.FACING, facing), 3);
        SupportMachineBlockEntity fresh = controllerAt(h, controller);
        h.assertTrue(fresh != null && fresh != old, "replacement builds a new controller BE");
        if (fresh == null) {
            return;
        }
        h.assertTrue(old.isRemoved(), "departed owner is removed");
        h.assertFalse(fresh.isFormed(), "fresh core starts unformed");
        h.assertTrue(fresh.members().isEmpty(), "fresh core must not inherit the old member list");

        // The member backstop: the port's own tick resets the visual state because the owner left.
        BlockEntity portEntity = h.getLevel().getBlockEntity(portAbs);
        h.assertTrue(portEntity instanceof SupportMemberBlockEntity, "port keeps a member BE");
        if (portEntity instanceof SupportMemberBlockEntity member) {
            member.serverTick();
            h.assertFalse(formedFlag(h.getLevel(), portAbs),
                "stale member must not keep the formed flag after the owner left");
            h.assertTrue(member.controller() == null, "stale member must not keep an owner link");
        }
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, portAbs, Direction.NORTH) == null,
            "unowned port must not expose FE");

        // A fresh owner reforms the same structure and re-lends the buffer.
        fresh.revalidate();
        h.assertTrue(fresh.isFormed(), "fresh owner reforms the same structure");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, portAbs, Direction.NORTH) != null,
            "re-owned formed port exposes FE again");
        h.succeed();
    }

    /**
     * Two machines cannot claim each other's blocks: an overlapping candidate fails on the foreign
     * formed member and the first stays formed. A genuinely adjacent machine forms, and their
     * member lists are disjoint.
     */
    private static void neighborCellsNoOverlap(GameTestHelper h) {
        Direction north = Direction.NORTH;
        BlockPos first = new BlockPos(1, 1, 1);
        List<BlockPos> firstCells = placeSupportCell(h, SupportMachineKind.PURIFIER, first, north, 0);
        SupportMachineBlockEntity machine = controllerAt(h, first);
        if (machine == null) {
            h.fail("no controller BE");
            return;
        }
        machine.revalidate();
        h.assertTrue(machine.isFormed(), "first machine must form");

        // Overlap candidate: facing SOUTH at (3,1,3), whose u=1 column lands inside the formed
        // machine. Only non-overlapping cells of the candidate are placed; the overlapping cells
        // keep the formed machine's own blocks.
        Direction south = Direction.SOUTH;
        SupportMachineKind kind = SupportMachineKind.EBEAM;
        BlockPos overlapController = new BlockPos(3, 1, 3);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos pos = cellOf(overlapController, south, 0, u, v, d);
                    boolean overlapsFirst = firstCells.contains(pos);
                    if (overlapsFirst) {
                        continue;
                    }
                    if (u == 0 && v == 0 && d == 0) {
                        h.setBlock(pos, blockFor(kind).defaultBlockState()
                            .setValue(SupportMachineControllerBlock.FACING, south));
                    } else if (u == PORT[0] && v == PORT[1] && d == PORT[2]) {
                        h.setBlock(pos, SupportMachines.SUPPORT_POWER_PORT.get().defaultBlockState());
                    } else {
                        h.setBlock(pos, SupportMachines.SUPPORT_CASING.get().defaultBlockState());
                    }
                }
            }
        }
        SupportMachineBlockEntity candidate = controllerAt(h, overlapController);
        if (candidate == null) {
            h.fail("no candidate controller BE");
            return;
        }
        candidate.revalidate();
        h.assertFalse(candidate.isFormed(), "overlapping candidate must not form");
        h.assertTrue(!candidate.failureReason().isEmpty(), "overlap must be explained");
        h.assertTrue(machine.isFormed(), "formed machine must keep its members");
        h.assertValueEqual(12, machine.members().size(), "formed machine keeps twelve members");

        BlockPos adjacent = new BlockPos(8, 1, 8);
        placeSupportCell(h, SupportMachineKind.EBEAM, adjacent, north, 1);
        SupportMachineBlockEntity second = controllerAt(h, adjacent);
        if (second == null) {
            h.fail("no adjacent controller BE");
            return;
        }
        second.revalidate();
        h.assertTrue(second.isFormed(), "adjacent machine must form");
        for (BlockPos member : machine.members()) {
            h.assertFalse(second.members().contains(member), "no member may be shared");
        }
        h.succeed();
    }

    /**
     * FE exists only on a formed, owned power port; the controller and casing never expose FE, and
     * no support block exposes item or fluid capabilities on any side. Losing the only port
     * unforms the machine and the capability answer is gone.
     */
    private static void feOnlyFormedOwnedPort(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        Direction facing = Direction.NORTH;
        placeSupportCell(h, SupportMachineKind.EBEAM, controller, facing, 0);
        SupportMachineBlockEntity machine = controllerAt(h, controller);
        if (machine == null) {
            h.fail("no controller BE");
            return;
        }
        machine.revalidate();
        h.assertTrue(machine.isFormed(), "machine must form");

        BlockPos portAbs = h.absolutePos(portOf(controller, facing, 0));
        BlockState portState = h.getLevel().getBlockState(portAbs);
        h.assertTrue(portState.getBlock() instanceof SupportPowerPortBlock, "expected the power port");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, portAbs, Direction.NORTH) != null,
            "formed owned port must expose FE");

        BlockPos controllerAbs = h.absolutePos(controller);
        BlockPos casingAbs = h.absolutePos(cellsOf(controller, facing, 0).get(flatIndex(1, 0, 0)));
        for (Direction side : Direction.values()) {
            h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, controllerAbs, side) == null,
                "controller must never expose FE, side " + side);
            h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, casingAbs, side) == null,
                "casing must never expose FE, side " + side);
            h.assertTrue(h.getLevel().getCapability(Capabilities.Item.BLOCK, portAbs, side) == null,
                "port must never expose an item capability, side " + side);
            h.assertTrue(h.getLevel().getCapability(Capabilities.Fluid.BLOCK, portAbs, side) == null,
                "port must never expose a fluid capability, side " + side);
        }

        h.setBlock(portAbs, Blocks.AIR.defaultBlockState());
        machine.revalidate();
        h.assertFalse(machine.isFormed(), "a machine without any port must unform");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, portAbs, Direction.NORTH) == null,
            "removed port has no FE");
        h.succeed();
    }

    /**
     * The purifier owns a persistent water tank: initial 16000 mB, capacity from the definition
     * param, a save/load round trip and strict rejection of an out-of-bounds saved amount; the
     * e-beam owns no functional water. Also pins both machine definitions' JSON numbers.
     */
    private static void purifierWaterFoundation(GameTestHelper h) {
        var registry = MachineDefinitionLoader.getRegistry();
        h.assertTrue(registry != null, "machine definitions must be loaded");
        var purifierDef = registry == null ? null : registry.byId("boule_purifier");
        var ebeamDef = registry == null ? null : registry.byId("ebeam_mask_writer");
        if (purifierDef == null || ebeamDef == null) {
            h.fail("both support definitions must exist");
            return;
        }
        h.assertValueEqual(100000, purifierDef.energyCapacity(), "purifier capacity");
        h.assertValueEqual(10000, purifierDef.maxReceive(), "purifier max receive");
        h.assertValueEqual(80, purifierDef.energyPerTick(), "purifier base FE per tick");
        h.assertValueEqual(16000, purifierDef.paramOrDefault("water_capacity", -1), "purifier water param");
        h.assertValueEqual(4000000, ebeamDef.energyCapacity(), "ebeam capacity");
        h.assertValueEqual(32000, ebeamDef.maxReceive(), "ebeam max receive");
        h.assertValueEqual(2000, ebeamDef.energyPerTick(), "ebeam base FE per tick");

        BlockPos controller = new BlockPos(1, 1, 1);
        placeSupportCell(h, SupportMachineKind.PURIFIER, controller, Direction.NORTH, 0);
        SupportMachineBlockEntity purifier = controllerAt(h, controller);
        if (purifier == null) {
            h.fail("no purifier BE");
            return;
        }
        purifier.serverTick();
        h.assertTrue(purifier.waterTank() != null, "purifier owns a tank");
        h.assertValueEqual(0, purifier.waterAmount(), "new purifier starts empty; capacity is not free water");
        h.assertValueEqual(16000, purifier.waterCapacity(), "capacity from definition param");
        h.assertValueEqual(100000, purifier.buffer().getCapacityAsInt(), "energy capacity applied");

        // Save / load round trip keeps the amount under strict bounds.
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
            h.getLevel().registryAccess());
        purifier.saveWithId(out);
        var tag = out.buildResult();
        tag.putInt("supportWaterAmount", 12345);
        purifier.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
            h.getLevel().registryAccess(), tag));
        h.assertValueEqual(12345, purifier.waterAmount(), "persisted amount restored");

        tag.putInt("supportWaterAmount", -5);
        purifier.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
            h.getLevel().registryAccess(), tag));
        h.assertValueEqual(12345, purifier.waterAmount(), "negative saved amount rejected, amount kept");

        tag.putInt("supportWaterAmount", WaterTank.MAX_CAPACITY + 1);
        purifier.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
            h.getLevel().registryAccess(), tag));
        h.assertValueEqual(12345, purifier.waterAmount(), "over-bound saved amount rejected, amount kept");

        BlockPos machinePos = new BlockPos(6, 1, 6);
        placeSupportCell(h, SupportMachineKind.EBEAM, machinePos, Direction.EAST, 1);
        SupportMachineBlockEntity ebeam = controllerAt(h, machinePos);
        if (ebeam == null) {
            h.fail("no ebeam BE");
            return;
        }
        ebeam.serverTick();
        h.assertTrue(ebeam.waterTank() == null, "ebeam owns no functional water");
        h.assertValueEqual(0, ebeam.waterAmount(), "ebeam reports zero water");
        h.assertValueEqual(0, ebeam.waterCapacity(), "ebeam reports zero water capacity");
        h.assertValueEqual(4000000, ebeam.buffer().getCapacityAsInt(), "ebeam energy capacity applied");
        h.succeed();
    }

    /**
     * The shell menus keep their fixed underlying indices and coordinates, validate inputs by
     * known items only, never accept input on outputs, keep the same client-side coordinates, and
     * the measured wire values carry formed status and the purifier's water at full int precision.
     */
    private static void menuIndicesCoordsValidity(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        placeSupportCell(h, SupportMachineKind.PURIFIER, controller, Direction.NORTH, 0);
        SupportMachineBlockEntity purifier = controllerAt(h, controller);
        if (purifier == null) {
            h.fail("no purifier BE");
            return;
        }
        serverTickN(purifier, 1);
        h.assertTrue(purifier.isFormed(), "purifier must form");

        ServerPlayer player = connect(h);
        SupportMachineMenu menu = SupportMachineMenu.forMachine(1, new Inventory(player), purifier);
        h.assertValueEqual(4 + 36, menu.slots.size(), "purifier shell: 4 machine + 36 player slots");
        assertSlot(h, menu, 0, 0, 62, 35, "purifier boule in");
        assertSlot(h, menu, 1, 1, 80, 35, "purifier water bucket in");
        assertSlot(h, menu, 2, 2, 104, 35, "purifier empty bucket out");
        assertSlot(h, menu, 3, 3, 122, 35, "purifier pure boule out");
        h.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(
            com.euvcraft.lithmod.mc.registry.ModItems.SILICON_BOULE.get())), "boule slot takes boules");
        h.assertFalse(menu.slots.get(0).mayPlace(new ItemStack(Items.STONE)),
            "boule slot refuses foreign items");
        h.assertFalse(menu.slots.get(2).mayPlace(new ItemStack(Items.BUCKET)),
            "outputs never accept input");
        h.assertTrue(menu.isServerSide(), "server menu binds the real machine");

        // Measured wire values: base data occupy 0..3, the menu's own values start at 4.
        List<DataSlot> wire = syncedData(menu);
        h.assertTrue(wire.size() >= 5, "menu registers its three extra wire values");
        h.assertValueEqual(1, wire.get(4).get(), "formed wire is live");
        h.assertValueEqual(16000, wire.get(5).get(), "water wire is live");
        h.assertValueEqual(16000, wire.get(6).get(), "water capacity wire is live");

        // The client shells build the same coordinates over the same kind, with no block entity.
        SupportMachineMenu client = SupportMachineMenu.clientPurifier(9, new Inventory(player), null);
        h.assertTrue(client.kind() == SupportMachineKind.PURIFIER, "client shell kind from its type");
        for (int i = 0; i < 4; i++) {
            Slot serverSlot = menu.slots.get(i);
            Slot clientSlot = client.slots.get(i);
            h.assertValueEqual(serverSlot.x, clientSlot.x, "client x " + i);
            h.assertValueEqual(serverSlot.y, clientSlot.y, "client y " + i);
        }

        // E-beam shell, output-only slot 2.
        BlockPos ebeamPos = new BlockPos(6, 1, 6);
        placeSupportCell(h, SupportMachineKind.EBEAM, ebeamPos, Direction.EAST, 1);
        SupportMachineBlockEntity ebeam = controllerAt(h, ebeamPos);
        if (ebeam == null) {
            h.fail("no ebeam BE");
            return;
        }
        serverTickN(ebeam, 1);
        SupportMachineMenu ebeamMenu = SupportMachineMenu.forMachine(2, new Inventory(player), ebeam);
        h.assertValueEqual(3 + 36, ebeamMenu.slots.size(), "ebeam shell: 3 machine + 36 player slots");
        assertSlot(h, ebeamMenu, 0, 0, 62, 35, "ebeam blank in");
        assertSlot(h, ebeamMenu, 1, 1, 80, 35, "ebeam redstone in");
        assertSlot(h, ebeamMenu, 2, 2, 122, 35, "ebeam mask out");
        h.assertFalse(ebeamMenu.slots.get(2).mayPlace(new ItemStack(
            com.euvcraft.lithmod.mc.registry.ModItems.MASK.get())), "mask out never accepts input");
        h.assertTrue(ebeamMenu.kind() == SupportMachineKind.EBEAM, "ebeam menu kind from its type");

        // Owner GUI reachable in range: move the fake player next to the e-beam and let the base
        // validity answer (world match, BE identity, distance).
        BlockPos today = h.absolutePos(ebeamPos);
        player.setPos(today.getX() + 0.5, today.getY() + 0.5, today.getZ() + 0.5);
        h.assertTrue(ebeamMenu.stillValid(player), "menu stays valid within range");
        h.succeed();
    }

    /**
     * The foundation never runs the inherited recipe runner: no job, no run, no FE spent, and the
     * definition numbers drive the buffer capacities; water costs nothing yet.
     */
    private static void definitionNumbersNoAutomaticRunner(GameTestHelper h) {
        BlockPos controller = new BlockPos(1, 1, 1);
        placeSupportCell(h, SupportMachineKind.PURIFIER, controller, Direction.NORTH, 0);
        SupportMachineBlockEntity purifier = controllerAt(h, controller);
        if (purifier == null) {
            h.fail("no purifier BE");
            return;
        }
        purifier.setStoredEnergy(50_000);
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0,
            new ItemStack(com.euvcraft.lithmod.mc.registry.ModItems.SILICON_BOULE.get()));
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.WATER_BUCKET));
        serverTickN(purifier, 40);
        h.assertFalse(purifier.runner().isRunning(), "no recipe must start without the job phase");
        h.assertValueEqual(50_000L, purifier.buffer().getAmountAsLong(), "no FE spent by the foundation");
        h.assertValueEqual(0, purifier.waterAmount(), "foundation does not exchange buckets or create water");
        h.assertTrue(purifier.isFormed(), "machine still forms with inputs filled");
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_0, ItemStack.EMPTY);
        purifier.setSlot(SupportMachineBlockEntity.SLOT_IN_1, ItemStack.EMPTY);
        h.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void serverTickN(SupportMachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.serverTick();
        }
    }

    private static void assertSlot(GameTestHelper h, SupportMachineMenu menu, int slotIndex,
            int containerIndex, int x, int y, String what) {
        Slot slot = menu.slots.get(slotIndex);
        h.assertValueEqual(containerIndex, slot.getContainerSlot(), what + " underlying index");
        h.assertValueEqual(x, slot.x, what + " x");
        h.assertValueEqual(y, slot.y, what + " y");
    }

    @SuppressWarnings("unchecked")
    private static List<DataSlot> syncedData(AbstractContainerMenu menu) {
        try {
            Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
            field.setAccessible(true);
            return new ArrayList<>((List<DataSlot>) field.get(menu));
        } catch (ReflectiveOperationException invalid) {
            throw new AssertionError("Cannot inspect registered menu wire values", invalid);
        }
    }

    /** Connects a real ServerPlayer over a mock channel, per the Process Cell menu tests. */
    private static ServerPlayer connect(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "support-multiblock-test"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
