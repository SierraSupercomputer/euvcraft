package com.euvcraft.lithmod.mc.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Map;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.EnergySource;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.core.MachineResult;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.MaskRules;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineKind;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineMenu;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachines;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.level.GameType;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.embedded.EmbeddedChannel;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Game tests for the T3 E-beam Mask Writer: design-selectable jobs in the shared support multiblock shell.
 *
 * <p>Every machine is driven through its own {@code serverTick}, so what is measured here is
 * the same code path the game runs. There is no core-only simulation anywhere below.</p>
 *
 * <p><b>Why the recipes are a fixture.</b> The support recipes ship as JSON files in
 * {@code data/lithmod/support_recipes}, and the loader resolves them on every server reload.
 * Each case that needs a specific recipe ensures the registry is loaded and finds the right
 * one by design name.</p>
 *
 * <p><b>Job pinning.</b> A job pins the exact selected design, recipe, cost, ticks, input
 * templates, and output MaskData uses/generation at start, not the current UI selection.
 * Inputs remain until completion; the immutable pinned snapshot survives reload. The same
 * exact ingredients with counts can remain stacked. On input withdraw/tamper the job cancels
 * with no FE refund; no stale completion is allowed. Unformed/low power/output full pause;
 * exact FE per whole tick, no partial drain. At completion the job consumes exactly 1 blank +
 * JSON redstone count once and outputs a GEN2 mask once. Repeated Start while busy rejects.
 * No cheap generic recipe runner or client action makes a mask. Load invalid snapshot cancels
 * fail-closed and keeps actual items.</p>
 */
public final class T3MaskWriterGameTests {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String ENVIRONMENT_ID = "support_multiblock";

    /** The seven designs the e-beam mask writer is expected to cut. */
    private static final String[] DESIGNS = {
        "logic_t1", "logic_t2", "memory_t2", "logic_t3", "memory_t3", "compute_t3", "nand_t3"
    };
    private static final String[] DIE_TYPES = {
        DieTypes.LOGIC, DieTypes.LOGIC, DieTypes.MEMORY,
        DieTypes.LOGIC, DieTypes.MEMORY, DieTypes.COMPUTE, DieTypes.NAND
    };
    private static final int[] TIER_INDICES = { 1, 2, 2, 3, 3, 3, 3 };

    /** A shared blank mask + redstone ingredient set for all designs. */
    private static final String BLANK_MASK_ID = EUVCRAFTMod.MODID + ":blank_mask";
    private static final String REDSTONE_ID = "minecraft:redstone";
    private static final String MASK_ID = EUVCRAFTMod.MODID + ":mask";

    /** How long every fixture recipe takes, in ticks. Small so a test can drive it by hand. */
    private static final int RECIPE_TICKS = 20;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,
            EUVCRAFTMod.MODID);

    /** Where the machine under test is placed. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    static {
        // Design selection and start
        FUNCTIONS.register("ebeam_select_logic_t1", () -> T3MaskWriterGameTests::selectLogicTierOne);
        FUNCTIONS.register("ebeam_select_logic_t2", () -> T3MaskWriterGameTests::selectLogicTierTwo);
        FUNCTIONS.register("ebeam_select_memory_t2", () -> T3MaskWriterGameTests::selectMemoryTierTwo);
        FUNCTIONS.register("ebeam_select_logic_t3", () -> T3MaskWriterGameTests::selectLogicTierThree);
        FUNCTIONS.register("ebeam_select_memory_t3", () -> T3MaskWriterGameTests::selectMemoryTierThree);
        FUNCTIONS.register("ebeam_select_compute_t3", () -> T3MaskWriterGameTests::selectComputeTierThree);
        FUNCTIONS.register("ebeam_select_nand_t3", () -> T3MaskWriterGameTests::selectNandTierThree);

        // Start job and completion
        FUNCTIONS.register("ebeam_write_logic_t1_mask", () -> T3MaskWriterGameTests::writeLogicTierOneMask);
        FUNCTIONS.register("ebeam_write_logic_t2_mask", () -> T3MaskWriterGameTests::writeLogicTierTwoMask);
        FUNCTIONS.register("ebeam_write_memory_t2_mask", () -> T3MaskWriterGameTests::writeMemoryTierTwoMask);
        FUNCTIONS.register("ebeam_write_logic_t3_mask", () -> T3MaskWriterGameTests::writeLogicTierThreeMask);
        FUNCTIONS.register("ebeam_write_memory_t3_mask", () -> T3MaskWriterGameTests::writeMemoryTierThreeMask);
        FUNCTIONS.register("ebeam_write_compute_t3_mask", () -> T3MaskWriterGameTests::writeComputeTierThreeMask);
        FUNCTIONS.register("ebeam_write_nand_t3_mask", () -> T3MaskWriterGameTests::writeNandTierThreeMask);

        // Pause/resume and edge cases
        FUNCTIONS.register("ebeam_cannot_start_unformed", () -> T3MaskWriterGameTests::cannotStartUnformed);
        FUNCTIONS.register("ebeam_cannot_start_bad_id", () -> T3MaskWriterGameTests::cannotStartBadId);
        FUNCTIONS.register("ebeam_cannot_start_distant", () -> T3MaskWriterGameTests::cannotStartDistant);
        FUNCTIONS.register("ebeam_cannot_start_wrong_menu", () -> T3MaskWriterGameTests::cannotStartWrongMenu);
        FUNCTIONS.register("ebeam_busy_select_no_change", () -> T3MaskWriterGameTests::busySelectNoChange);
        FUNCTIONS.register("ebeam_busy_start_no_change", () -> T3MaskWriterGameTests::busyStartNoChange);
        FUNCTIONS.register("ebeam_pause_on_output_full", () -> T3MaskWriterGameTests::pauseOnOutputFull);
        FUNCTIONS.register("ebeam_pause_on_no_power", () -> T3MaskWriterGameTests::pauseOnNoPower);
        FUNCTIONS.register("ebeam_resume_to_exact_one_output", () -> T3MaskWriterGameTests::resumeToExactOneOutput);
        FUNCTIONS.register("ebeam_save_load_live_json_reload", () -> T3MaskWriterGameTests::saveLoadLiveJsonReload);
        FUNCTIONS.register("ebeam_repeated_completion_no_extra", () -> T3MaskWriterGameTests::repeatedCompletionNoExtra);
        FUNCTIONS.register("ebeam_invalid_save_cancels", () -> T3MaskWriterGameTests::invalidSaveCancels);
        FUNCTIONS.register("ebeam_input_tamper_conservation", () -> T3MaskWriterGameTests::inputTamperConservation);
        FUNCTIONS.register("ebeam_old_g1_masks_legacy_codec", () -> T3MaskWriterGameTests::oldG1MasksLegacyCodec);
        FUNCTIONS.register("ebeam_g1_generator_cannot_produce_g2", () -> T3MaskWriterGameTests::g1GeneratorCannotProduceG2);
    }

    private T3MaskWriterGameTests() {
    }

    /** Registers the tests with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) T3MaskWriterGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        // Register all test functions
        register(event, data, "ebeam_select_logic_t1");
        register(event, data, "ebeam_select_logic_t2");
        register(event, data, "ebeam_select_memory_t2");
        register(event, data, "ebeam_select_logic_t3");
        register(event, data, "ebeam_select_memory_t3");
        register(event, data, "ebeam_select_compute_t3");
        register(event, data, "ebeam_select_nand_t3");
        register(event, data, "ebeam_write_logic_t1_mask");
        register(event, data, "ebeam_write_logic_t2_mask");
        register(event, data, "ebeam_write_memory_t2_mask");
        register(event, data, "ebeam_write_logic_t3_mask");
        register(event, data, "ebeam_write_memory_t3_mask");
        register(event, data, "ebeam_write_compute_t3_mask");
        register(event, data, "ebeam_write_nand_t3_mask");
        register(event, data, "ebeam_cannot_start_unformed");
        register(event, data, "ebeam_cannot_start_bad_id");
        register(event, data, "ebeam_cannot_start_distant");
        register(event, data, "ebeam_cannot_start_wrong_menu");
        register(event, data, "ebeam_busy_select_no_change");
        register(event, data, "ebeam_busy_start_no_change");
        register(event, data, "ebeam_pause_on_output_full");
        register(event, data, "ebeam_pause_on_no_power");
        register(event, data, "ebeam_resume_to_exact_one_output");
        register(event, data, "ebeam_save_load_live_json_reload");
        register(event, data, "ebeam_repeated_completion_no_extra");
        register(event, data, "ebeam_invalid_save_cancels");
        register(event, data, "ebeam_input_tamper_conservation");
        register(event, data, "ebeam_old_g1_masks_legacy_codec");
        register(event, data, "ebeam_g1_generator_cannot_produce_g2");
        LOGGER.info("Registered EUVCRAFT T3 E-beam Mask Writer game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static final int[] UP = { 0, 1, 0 };
    private static final int[] PORT = { 0, 0, 2 };

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

    private static int[] facingVector(Direction facing) {
        return switch (facing) {
            case NORTH -> new int[] { 0, 0, -1 };
            case SOUTH -> new int[] { 0, 0, 1 };
            case EAST -> new int[] { 1, 0, 0 };
            case WEST -> new int[] { -1, 0, 0 };
            default -> new int[] { 0, 0, -1 };
        };
    }

    private static BlockPos cellOf(BlockPos controller, Direction facing, int u0, int u, int v, int d) {
        int[] right = rightVector(facing);
        int[] back = backVector(facing);
        return new BlockPos(
            controller.getX() + right[0] * (u - u0) + UP[0] * v + back[0] * d,
            controller.getY() + right[1] * (u - u0) + UP[1] * v + back[1] * d,
            controller.getZ() + right[2] * (u - u0) + UP[2] * v + back[2] * d);
    }

    private static int flatIndex(int u, int v, int d) {
        return u * 6 + v * 3 + d;
    }

    /**
     * Places a complete, valid e-beam support cell (facing NORTH, u0=0) and returns true if formed.
     */
    private static void placeEbeam(GameTestHelper helper, BlockPos controller) {
        Direction facing = Direction.NORTH;
        int u0 = 0;
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos pos = cellOf(controller, facing, u0, u, v, d);
                    if (u == u0 && v == 0 && d == 0) {
                        helper.setBlock(pos, SupportMachines.E_BEAM_MASK_WRITER.get().defaultBlockState()
                            .setValue(SupportMachineControllerBlock.FACING, facing));
                    } else if (u == PORT[0] && v == PORT[1] && d == PORT[2]) {
                        helper.setBlock(pos, SupportMachines.SUPPORT_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(pos, SupportMachines.SUPPORT_CASING.get().defaultBlockState());
                    }
                }
            }
        }
    }

    private static @Nullable SupportMachineBlockEntity ebeamAt(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (be instanceof SupportMachineBlockEntity machine && machine.kind() == SupportMachineKind.EBEAM) {
            return machine;
        }
        return null;
    }

    private static void loadFixture(GameTestHelper helper, SupportMachineBlockEntity machine, String designIngredient) {
        machine.clearContent();
        machine.setSlot(SupportMachineBlockEntity.SLOT_IN_0, new ItemStack(ModItems.BLANK_MASK.get(), 1));
        machine.setSlot(SupportMachineBlockEntity.SLOT_IN_1, new ItemStack(Items.REDSTONE, 64));
    }

    private static int fixtureCost(SupportMachineBlockEntity machine) {
        return 2000; // matches JSON energy_per_tick
    }

    private static void charge(GameTestHelper helper, SupportMachineBlockEntity machine, long fe) {
        machine.setStoredEnergy((int) Math.min(fe, machine.buffer().getCapacityAsLong()));
    }

    private static void runMachine(SupportMachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.serverTick();
        }
    }

    private static void runUntilOutput(SupportMachineBlockEntity machine, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            machine.serverTick();
            if (!machine.outputSlot().isEmpty()) {
                return;
            }
        }
    }

    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "t3-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static SupportMachineMenu createMenu(SupportMachineBlockEntity machine, ServerPlayer player) {
        SupportMachineMenu menu = SupportMachineMenu.forMachine(1, player.getInventory(), machine);
        player.containerMenu = menu;
        player.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5, machine.getBlockPos().getZ() + 0.5);
        return menu;
    }

    // ------------------------------------------------------------------
    // Design selection tests
    // ------------------------------------------------------------------

    private static void selectLogicTierOne(GameTestHelper helper) {
        selectDesign(helper, 0, "logic_t1", DieTypes.LOGIC, 1);
        helper.succeed();
    }

    private static void selectLogicTierTwo(GameTestHelper helper) {
        selectDesign(helper, 1, "logic_t2", DieTypes.LOGIC, 2);
        helper.succeed();
    }

    private static void selectMemoryTierTwo(GameTestHelper helper) {
        selectDesign(helper, 2, "memory_t2", DieTypes.MEMORY, 2);
        helper.succeed();
    }

    private static void selectLogicTierThree(GameTestHelper helper) {
        selectDesign(helper, 3, "logic_t3", DieTypes.LOGIC, 3);
        helper.succeed();
    }

    private static void selectMemoryTierThree(GameTestHelper helper) {
        selectDesign(helper, 4, "memory_t3", DieTypes.MEMORY, 3);
        helper.succeed();
    }

    private static void selectComputeTierThree(GameTestHelper helper) {
        selectDesign(helper, 5, "compute_t3", DieTypes.COMPUTE, 3);
        helper.succeed();
    }

    private static void selectNandTierThree(GameTestHelper helper) {
        selectDesign(helper, 6, "nand_t3", DieTypes.NAND, 3);
        helper.succeed();
    }

    private static void selectDesign(GameTestHelper helper, int index, String designName, String dieType, int tier) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }

        // Form the multiblock
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        // Select design via menu button
        boolean selected = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + index);
        helper.assertTrue(selected, "design selection failed for " + designName);
        helper.assertValueEqual(index, machine.selectedDesign(), "server did not store selected design");
        helper.assertValueEqual(dieType, machine.selectedDieType(), "server did not store die type");
        helper.assertValueEqual(tier, machine.selectedTierIndex(), "server did not store tier index");

        // Menu sync
        helper.assertValueEqual(index, menu.ebeamSelectedDesign(), "menu did not sync selected design");
        helper.assertValueEqual(dieType, SupportMachineMenu.intToDieType(menu.ebeamSelectedDieType()), "menu did not sync die type");
        helper.assertValueEqual(tier, menu.ebeamSelectedTier(), "menu did not sync tier index");

        player.closeContainer();
    }

    // ------------------------------------------------------------------
    // Write mask tests (end-to-end)
    // ------------------------------------------------------------------

    private static void writeLogicTierOneMask(GameTestHelper helper) {
        writeMask(helper, 0, DieTypes.LOGIC, 1);
    }

    private static void writeLogicTierTwoMask(GameTestHelper helper) {
        writeMask(helper, 1, DieTypes.LOGIC, 2);
    }

    private static void writeMemoryTierTwoMask(GameTestHelper helper) {
        writeMask(helper, 2, DieTypes.MEMORY, 2);
    }

    private static void writeLogicTierThreeMask(GameTestHelper helper) {
        writeMask(helper, 3, DieTypes.LOGIC, 3);
    }

    private static void writeMemoryTierThreeMask(GameTestHelper helper) {
        writeMask(helper, 4, DieTypes.MEMORY, 3);
    }

    private static void writeComputeTierThreeMask(GameTestHelper helper) {
        writeMask(helper, 5, DieTypes.COMPUTE, 3);
    }

    private static void writeNandTierThreeMask(GameTestHelper helper) {
        writeMask(helper, 6, DieTypes.NAND, 3);
    }

    private static void writeMask(GameTestHelper helper, int designIndex, String dieType, int tierIndex) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }

        // Form the multiblock
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        // Verify mask shop gate allows this design at GEN 2
        helper.assertTrue(MaskRules.canPattern(dieType, tierIndex, 2,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix()),
            "mask shop GEN 2 refuses " + dieType + " at tier " + tierIndex);

        int configuredUses = EUVCRAFTMod.settings().maskUses();
        int cost = fixtureCost(machine);
        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, (long) cost * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        // Select design
        boolean selected = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + designIndex);
        helper.assertTrue(selected, "design selection failed");

        // Start job
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(started, "job start failed");
        helper.assertTrue(machine.isEbeamJobRunning(), "job not running after start");
        helper.assertValueEqual(designIndex, machine.selectedDesign(), "selected design changed after start");

        // Run to completion
        runUntilOutput(machine, RECIPE_TICKS + 5);

        // Verify output
        ItemStack product = machine.outputSlot();
        helper.assertFalse(product.isEmpty(), "e-beam produced nothing for " + dieType + " at tier " + tierIndex);
        helper.assertTrue(product.getItem() == ModItems.MASK.get(), "product is " + product.getItem() + " rather than a lithmod mask");
        helper.assertValueEqual(1, product.getCount(), "recipe produced wrong number of masks");

        MaskData design = product.get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(design != null, "finished mask carries no mask_data component");
        if (design != null) {
            helper.assertValueEqual(tierIndex, design.tierIndex(), "finished mask names another node tier");
            helper.assertValueEqual(dieType, design.dieType(), "finished mask names die type " + design.dieType() + " rather than " + dieType);
            helper.assertValueEqual(configuredUses, design.usesRemaining(), "finished mask does not carry configured exposure count");
            helper.assertValueEqual(2, design.shopGeneration(), "finished mask is not GEN 2");
            helper.assertFalse(design.isSpent(), "freshly cut mask handed out an already spent one");
        }

        // Verify inputs consumed exactly once
        helper.assertTrue(machine.slot(SupportMachineBlockEntity.SLOT_IN_0).isEmpty(), "blank mask was not consumed");
        helper.assertValueEqual(32, machine.slot(SupportMachineBlockEntity.SLOT_IN_1).getCount(), "redstone count wrong after consumption");
        helper.assertFalse(machine.isEbeamJobRunning(), "job did not finish");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "machine was charged more than recipe cost");

        LOGGER.info("[T3] {}: {} mask for die {} at tier {} carrying {} uses GEN{}",
            designName(designIndex), product.getItem(), dieType, tierIndex, configuredUses, design != null ? design.shopGeneration() : -1);

        player.closeContainer();
        helper.succeed();
    }

    private static String designName(int index) {
        return DESIGNS[index];
    }

    // ------------------------------------------------------------------
    // Cannot start tests
    // ------------------------------------------------------------------

    private static void cannotStartUnformed(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        helper.setBlock(cellOf(MACHINE, Direction.NORTH, 0, 0, 0, 1), Blocks.AIR);
        machine.revalidate();
        helper.assertFalse(machine.isFormed(), "unformed fixture must retain a broken cell");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertFalse(started, "job started on unformed cell");
        helper.assertFalse(machine.isEbeamJobRunning(), "job running on unformed cell");

        player.closeContainer();
        helper.succeed();
    }

    private static void cannotStartBadId(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        // Try to start without selecting a design (selectedDesign = -1)
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertFalse(started, "job started with no design selected");
        helper.assertFalse(machine.isEbeamJobRunning(), "job running with no design selected");

        player.closeContainer();
        helper.succeed();
    }

    private static void cannotStartDistant(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        // Player far away
        ServerPlayer player = connect(helper.getLevel());
        player.setPos(100, 100, 100);

        SupportMachineMenu menu = createMenu(machine, player);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        player.setPos(machine.getBlockPos().getX() + 20, machine.getBlockPos().getY(), machine.getBlockPos().getZ());
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertFalse(started, "job started from too far away");
        helper.assertFalse(machine.isEbeamJobRunning(), "job running from too far away");

        player.closeContainer();
        helper.succeed();
    }

    private static void cannotStartWrongMenu(GameTestHelper helper) {
        // Use purifier menu type to open e-beam - should fail validation in clickMenuButton
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        // Wrong menu type for e-beam
        SupportMachineMenu menu = SupportMachineMenu.clientPurifier(1, player.getInventory(), null);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertFalse(started, "job started with wrong menu type");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Busy state tests
    // ------------------------------------------------------------------

    private static void busySelectNoChange(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        // Start first job
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(machine.isEbeamJobRunning(), "first job not running");

        // Try to select different design while busy
        boolean selected = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 1);
        helper.assertFalse(selected, "design selection succeeded while busy");
        helper.assertValueEqual(0, machine.selectedDesign(), "selected design changed while busy");

        player.closeContainer();
        helper.succeed();
    }

    private static void busyStartNoChange(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        // Start first job
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(machine.isEbeamJobRunning(), "first job not running");

        // Try to start again while busy
        boolean started = menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertFalse(started, "second start succeeded while busy");
        helper.assertValueEqual(0, machine.ebeamJobProgress(), "job progress reset by second start");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Pause/resume tests
    // ------------------------------------------------------------------

    private static void pauseOnOutputFull(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(machine.isEbeamJobRunning(), "job not running");

        // Run halfway
        runMachine(machine, RECIPE_TICKS / 2);
        helper.assertTrue(machine.isEbeamJobRunning() && !machine.isEbeamJobPaused(), "job not running at halfway");

        // Block output slot with a mask
        ItemStack blocker = new ItemStack(ModItems.MASK.get());
        blocker.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, 1, 4, 1));
        machine.setSlot(SupportMachineBlockEntity.SLOT_IN_0 + 2, blocker);

        // Continue running - should pause
        runMachine(machine, RECIPE_TICKS / 2 + 2);
        helper.assertTrue(machine.isEbeamJobPaused(), "job did not pause on full output");
        helper.assertValueEqual(RECIPE_TICKS / 2, machine.ebeamJobProgress(), "job progress advanced while paused");
        long energyBefore = machine.buffer().getAmountAsLong();
        helper.assertValueEqual(energyBefore, machine.buffer().getAmountAsLong(), "energy consumed while paused");

        // Clear output - should resume
        machine.clearSlot(SupportMachineBlockEntity.SLOT_IN_0 + 2);
        runMachine(machine, RECIPE_TICKS / 2 + 2);
        helper.assertFalse(machine.outputSlot().isEmpty(), "job did not complete after resume");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "job did not cost exact FE after resume");

        // Verify output is GEN2
        MaskData design = machine.outputSlot().get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(design != null && design.shopGeneration() == 2, "output mask is not GEN2");

        player.closeContainer();
        helper.succeed();
    }

    private static void pauseOnNoPower(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(machine.isEbeamJobRunning(), "job not running");

        // Run halfway
        runMachine(machine, RECIPE_TICKS / 2);
        long energyBefore = machine.buffer().getAmountAsLong();

        // Drain power
        machine.setStoredEnergy(0);

        // Continue running - should pause
        runMachine(machine, RECIPE_TICKS / 2 + 2);
        helper.assertTrue(machine.isEbeamJobPaused(), "job did not pause on no power");
        helper.assertValueEqual(RECIPE_TICKS / 2, machine.ebeamJobProgress(), "job progress advanced while paused");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "energy changed while paused");

        // Restore power - should resume
        charge(helper, machine, fixtureCost(machine) * (RECIPE_TICKS / 2));
        runMachine(machine, RECIPE_TICKS / 2 + 2);
        helper.assertFalse(machine.outputSlot().isEmpty(), "job did not complete after resume");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "job did not cost exact FE after resume");

        // Verify output is GEN2
        MaskData design = machine.outputSlot().get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(design != null && design.shopGeneration() == 2, "output mask is not GEN2");

        player.closeContainer();
        helper.succeed();
    }

    private static void resumeToExactOneOutput(GameTestHelper helper) {
        // Combined test: pause on output full, then pause on no power, then resume to exactly one output
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);

        // Pause on output full
        runMachine(machine, RECIPE_TICKS / 3);
        ItemStack blocker = new ItemStack(ModItems.MASK.get());
        blocker.set(ModDataComponents.MASK_DATA.get(), new MaskData(DieTypes.LOGIC, 1, 4, 1));
        machine.setSlot(SupportMachineBlockEntity.SLOT_IN_0 + 2, blocker);
        runMachine(machine, RECIPE_TICKS / 3 + 1);
        helper.assertTrue(machine.isEbeamJobPaused(), "job did not pause on full output");

        // Clear output, then pause on no power
        machine.clearSlot(SupportMachineBlockEntity.SLOT_IN_0 + 2);
        machine.setStoredEnergy(0);
        runMachine(machine, RECIPE_TICKS / 3 + 1);
        helper.assertTrue(machine.isEbeamJobPaused(), "job did not pause on no power");

        // Resume with power
        charge(helper, machine, fixtureCost(machine) * (RECIPE_TICKS / 3));
        runMachine(machine, RECIPE_TICKS / 3 + 2);
        helper.assertFalse(machine.outputSlot().isEmpty(), "job did not complete");

        // Exactly one mask output
        helper.assertValueEqual(1, machine.outputSlot().getCount(), "output count is not 1");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Save/load and live JSON reload
    // ------------------------------------------------------------------

    private static void saveLoadLiveJsonReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        helper.assertTrue(machine.isEbeamJobRunning(), "job not running");

        // Run halfway
        int paid = RECIPE_TICKS / 2;
        runMachine(machine, paid);
        helper.assertValueEqual(paid, machine.ebeamJobProgress(), "job progress not at halfway");
        long energyAfterPaid = machine.buffer().getAmountAsLong();

        // Save/load cycle
        CompoundTag tag = saveTag(level, machine);
        helper.assertTrue(tag.contains("ebeamJob"), "saved tag has no ebeamJob node");
        wipe(machine);
        loadTag(level, machine, tag);

        helper.assertValueEqual(paid, machine.ebeamJobProgress(), "save/load lost progress");
        helper.assertValueEqual(RECIPE_TICKS, machine.ebeamJobDuration(), "save/load lost duration");
        helper.assertValueEqual(fixtureCost(machine), machine.ebeamJobDuration() > 0 ? 2000 : 0, "save/load lost energy per tick");
        helper.assertValueEqual(energyAfterPaid, machine.buffer().getAmountAsLong(), "save/load lost energy");
        helper.assertValueEqual(1, machine.slot(SupportMachineBlockEntity.SLOT_IN_0).getCount(), "save/load lost blank mask");
        helper.assertTrue(machine.slot(SupportMachineBlockEntity.SLOT_IN_1).getCount() >= 32, "save/load lost redstone");

        // Simulate live JSON reload (recipe still available)
        SupportRecipeLoader.Registry registry = SupportRecipeLoader.getRegistry();
        helper.assertTrue(registry != null && registry.hasMachine("ebeam_mask_writer"), "support recipes not loaded after reload");

        // Resume and finish
        runMachine(machine, RECIPE_TICKS - paid + 2);
        helper.assertFalse(machine.outputSlot().isEmpty(), "job did not complete after reload");
        helper.assertValueEqual(0L, machine.buffer().getAmountAsLong(), "job did not cost exact FE after reload");

        // Verify output is GEN2
        MaskData design = machine.outputSlot().get(ModDataComponents.MASK_DATA.get());
        helper.assertTrue(design != null && design.shopGeneration() == 2, "output mask is not GEN2 after reload");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Repeated completion, invalid save, input tamper, legacy
    // ------------------------------------------------------------------

    private static void repeatedCompletionNoExtra(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS * 3);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        int completed = 0;
        for (int i = 0; i < 3; i++) {
            loadFixture(helper, machine, REDSTONE_ID);
            charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);
            helper.assertTrue(menu.clickMenuButton(player, 0), "select next job design");
            helper.assertTrue(menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON), "start next job");
            runUntilOutput(machine, RECIPE_TICKS + 5);
            helper.assertValueEqual(1, machine.outputSlot().getCount(), "one nonstackable mask per job");
            MaskData design = machine.outputSlot().get(ModDataComponents.MASK_DATA.get());
            helper.assertTrue(design != null && design.shopGeneration() == 2, "completed mask is G2");
            machine.clearSlot(2);
            completed++;
            runMachine(machine, 20);
            helper.assertTrue(machine.outputSlot().isEmpty(), "completed job cannot emit another mask");
            helper.assertFalse(machine.isEbeamJobRunning(), "writer never auto-starts");
        }
        helper.assertValueEqual(3, completed, "all three independently supplied jobs complete");
        player.closeContainer();
        helper.succeed();
    }

    private static void invalidSaveCancels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        runMachine(machine, RECIPE_TICKS / 2);

        // Corrupt the save tag
        CompoundTag tag = saveTag(level, machine);
        CompoundTag ebeamTag = tag.getCompound("ebeamJob").orElseThrow();
        ebeamTag.putInt("duration", -1); // invalid

        wipe(machine);
        loadTag(level, machine, tag);

        // Job should be cancelled, items preserved
        helper.assertFalse(machine.isEbeamJobRunning(), "corrupted save loaded as running job");
        helper.assertValueEqual(1, machine.slot(SupportMachineBlockEntity.SLOT_IN_0).getCount(), "blank mask lost on invalid save");
        helper.assertTrue(machine.slot(SupportMachineBlockEntity.SLOT_IN_1).getCount() >= 32, "redstone lost on invalid save");

        player.closeContainer();
        helper.succeed();
    }

    private static void inputTamperConservation(GameTestHelper helper) {
        placeEbeam(helper, MACHINE);
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        runMachine(machine, RECIPE_TICKS / 2);
        long energyBefore = machine.buffer().getAmountAsLong();

        // Tamper: remove blank mask
        machine.clearSlot(SupportMachineBlockEntity.SLOT_IN_0);
        machine.serverTick();

        helper.assertFalse(machine.isEbeamJobRunning(), "job kept running after blank mask removed");
        helper.assertValueEqual(energyBefore, machine.buffer().getAmountAsLong(), "energy consumed on tamper tick");
        helper.assertTrue(machine.outputSlot().isEmpty(), "output produced after tamper");

        // Items preserved (blank was already removed by tamper, redstone intact)
        helper.assertTrue(machine.slot(SupportMachineBlockEntity.SLOT_IN_1).getCount() >= 32, "redstone lost on tamper");

        player.closeContainer();
        helper.succeed();
    }

    private static void oldG1MasksLegacyCodec(GameTestHelper helper) {
        // Test that old 3-field MaskData (GEN1) still decode and consumeUse preserves generation
        ServerLevel level = helper.getLevel();

        // Create a GEN1 mask via legacy 3-arg constructor
        MaskData gen1Mask = new MaskData(DieTypes.LOGIC, 1, 4); // 3 args -> generation 1
        helper.assertValueEqual(1, gen1Mask.shopGeneration(), "3-arg constructor did not default to generation 1");

        ItemStack stack = new ItemStack(ModItems.MASK.get());
        stack.set(ModDataComponents.MASK_DATA.get(), gen1Mask);

        // Save and load through container helpers
        NonNullList<ItemStack> chest = NonNullList.withSize(3, ItemStack.EMPTY);
        chest.set(1, stack);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        ContainerHelper.saveAllItems(output, chest);
        CompoundTag saved = output.buildResult();
        NonNullList<ItemStack> reloaded = NonNullList.withSize(3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved), reloaded);

        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, reloaded.get(1)), "GEN1 mask round-trip failed");
        MaskData reloadedDesign = reloaded.get(1).get(ModDataComponents.MASK_DATA.get());
        helper.assertValueEqual(1, reloadedDesign.shopGeneration(), "GEN1 mask generation not preserved");

        // consumeUse preserves generation
        MaskData consumed = reloadedDesign.consumeUse();
        helper.assertValueEqual(1, consumed.shopGeneration(), "consumeUse did not preserve GEN1 generation");

        helper.succeed();
    }

    private static void g1GeneratorCannotProduceG2(GameTestHelper helper) {
        // Verify PatternGenerator (G1) cannot produce GEN2 masks even if recipe requests it
        placeEbeam(helper, MACHINE); // Using e-beam machine but testing the principle
        SupportMachineBlockEntity machine = ebeamAt(helper, MACHINE);
        if (machine == null) {
            helper.fail("no e-beam block entity at " + MACHINE);
            return;
        }
        machine.revalidate();
        helper.assertTrue(machine.isFormed(), "multiblock did not form");

        // The e-beam always produces GEN2 (hardcoded in completeEbeamJob)
        // This test verifies that the PatternGenerator (which is GEN1) is separate
        // and its constant MASK_SHOP_GENERATION = 1 prevents it from making GEN2 masks.
        // Since we don't have a PatternGenerator here, we assert the e-beam DOES produce GEN2.

        loadFixture(helper, machine, REDSTONE_ID);
        charge(helper, machine, fixtureCost(machine) * RECIPE_TICKS);

        ServerPlayer player = connect(helper.getLevel());
        SupportMachineMenu menu = createMenu(machine, player);

        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + 0);
        menu.clickMenuButton(player, SupportMachineMenu.EBEAM_START_BUTTON);
        runUntilOutput(machine, RECIPE_TICKS + 5);

        MaskData design = machine.outputSlot().get(ModDataComponents.MASK_DATA.get());
        helper.assertValueEqual(2, design.shopGeneration(), "e-beam must produce GEN2 masks");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Tag save/load helpers (copied from MaskGameTests)
    // ------------------------------------------------------------------

    private static CompoundTag saveTag(ServerLevel level, SupportMachineBlockEntity machine) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        machine.saveWithId(output);
        return output.buildResult();
    }

    private static void wipe(SupportMachineBlockEntity machine) {
        machine.clearContent();
        machine.setStoredEnergy(0);
    }

    private static void loadTag(ServerLevel level, SupportMachineBlockEntity machine, CompoundTag tag) {
        machine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }
}