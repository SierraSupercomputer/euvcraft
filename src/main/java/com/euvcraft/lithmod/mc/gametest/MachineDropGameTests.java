package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.test.ModMachines;
import com.euvcraft.lithmod.mc.machine.test.TestMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game tests for dropping a machine's contents, on a real server level with the real
 * registries and the real loaded data pack.
 *
 * <p>The mechanism under test is 26.3's {@code BlockEntity.preRemoveSideEffects}: a block
 * entity that is a {@code net.minecraft.world.Container} has its contents dropped by
 * {@code LevelChunk.setBlockState} on any block change that removes it. Every test here goes
 * through a real removal path rather than calling a drop helper, which is what makes the
 * counts meaningful.</p>
 *
 * <p>Breaking uses {@code ServerPlayerGameMode.destroyBlock}, the method a player in
 * survival reaches by mining. {@code GameTestHelper.destroyBlock} is deliberately not used:
 * it calls {@code Level.destroyBlock(pos, false, null)} with {@code dropBlock = false}.</p>
 *
 * <p>Note on {@code assertValueEqual}: its signature is
 * {@code assertValueEqual(value, expected, name)}, so the actual reading goes first.</p>
 *
 * <p>Every positive assertion is paired with a negative control, marked "negative
 * control".</p>
 */
public final class MachineDropGameTests {

    private static final String ENVIRONMENT_ID = "machine_drop";

    private static final String BREAK_IN_SURVIVAL = "machine_drops_contents_on_break_in_survival";
    private static final String REPLACE_DROPS = "machine_drops_contents_when_block_replaced";
    private static final String NO_DUPLICATION = "machine_contents_do_not_duplicate";
    private static final String UNLOAD_DROPS_NOTHING = "machine_unloading_drops_nothing";
    private static final String EXTENSION_POINT = "machine_extra_inventory_drops_on_break";
    private static final String PROCESS_CELL_DROPS_SELF = "process_cell_blocks_drop_themselves";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, "lithmod");

    /** The machine under test. */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    /** A second position, used for side-by-side controls. */
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    /**
     * A third position, far enough from the first that its drops cannot be confused with the
     * first's. The 3x3 test structure is only 3 blocks across, so anything more than 2
     * blocks from {@link #MACHINE} is out of reach of both drop windows.
     */
    private static final BlockPos THIRD = new BlockPos(7, 1, 1);
    /**
 * Radius used for per-machine counts.
 *
 * <p>Deliberately small. {@code Containers.dropItemStack} scatters within roughly one block,
 * and game test structures sit only a few blocks apart in the same world, so a generous
 * radius would count a neighbouring test's drops as this test's. That is not hypothetical:
 * an 8-block radius made every test see its neighbours' items.</p>
 */
    private static final double DROP_RADIUS = 2.0;
    /** Radius covering both machines used by the duplication test, 2 blocks apart. */
    private static final double VOLUME_RADIUS = 3.0;

    static {
        FUNCTIONS.register(BREAK_IN_SURVIVAL, () -> MachineDropGameTests::breakInSurvival);
        FUNCTIONS.register(REPLACE_DROPS, () -> MachineDropGameTests::replaceDrops);
        FUNCTIONS.register(NO_DUPLICATION, () -> MachineDropGameTests::noDuplication);
        FUNCTIONS.register(UNLOAD_DROPS_NOTHING, () -> MachineDropGameTests::unloadDropsNothing);
        FUNCTIONS.register(EXTENSION_POINT, () -> MachineDropGameTests::extensionPoint);
        FUNCTIONS.register(PROCESS_CELL_DROPS_SELF, () -> MachineDropGameTests::processCellDropsSelf);
    }

    private MachineDropGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) MachineDropGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath("lithmod", ENVIRONMENT_ID), new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, BREAK_IN_SURVIVAL);
        register(event, data, REPLACE_DROPS);
        register(event, data, NO_DUPLICATION);
        register(event, data, UNLOAD_DROPS_NOTHING);
        register(event, data, EXTENSION_POINT);
        register(event, data, PROCESS_CELL_DROPS_SELF);
        EUVCRAFTMod.LOGGER.info("Registered EUVCRAFT machine contents drop game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath("lithmod", name);
        ResourceKey<Consumer<GameTestHelper>> function =
            ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. Breaking in survival drops every item exactly once, plus the block
    // ------------------------------------------------------------------

    private static void breakInSurvival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        BlockPos pos = helper.absolutePos(MACHINE);
        FabMachineBlockEntity machine = placeMachine(helper, MACHINE);

        // Three distinct stacks in three distinct slots: the FOUP in the recipe input, the
        // machine's own product in the output, and a plain item in storage.
        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, aFilledFoup(level));
        machine.giveOutput(new ItemStack(Items.GOLD_INGOT, 3));
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 5));

        helper.assertValueEqual(3, countNonEmptySlots(machine),
            "the machine should hold three stacks before the break (value, expected)");

        // A survival player with a stone pickaxe: the same path as mining by hand.
        ServerPlayer player = survivalPlayerWithPickaxe(level, pos);
        helper.assertValueEqual(GameType.SURVIVAL, player.gameMode.getGameModeForPlayer(),
            "the player is not in survival, so this is not the survival drop path");
        helper.assertTrue(level.getBlockState(pos).canHarvestBlock(level, pos, player),
            "a stone pickaxe cannot harvest the machine, so the block item would not drop "
                + "(check the mineable/pickaxe and needs_stone_tool tags)");

        // Nothing may already be lying around, or the counts prove nothing.
        helper.assertValueEqual(0, sumOf(countItems(helper, pos, DROP_RADIUS)),
            "the test area already held item entities before the break");

        boolean destroyed = player.gameMode.destroyBlock(pos);
        helper.assertTrue(destroyed, "the survival break did not remove the block");
        helper.assertTrue(level.getBlockState(pos).isAir(),
            "the block is still there after the survival break");

        Map<Item, Integer> after = countItems(helper, pos, DROP_RADIUS);
        EUVCRAFTMod.LOGGER.info("[M1-02c] break_in_survival: dropped {}", after);

        // Every stack comes back exactly once, at its original size.
        helper.assertValueEqual(5, stackSizeOf(after, Items.IRON_INGOT),
            "the stored iron ingots did not drop, or dropped the wrong size");
        helper.assertValueEqual(3, stackSizeOf(after, Items.GOLD_INGOT),
            "the output stack did not drop, or dropped the wrong size");
        helper.assertValueEqual(1, countOf(after, ModItems.FOUP_MK1.get()),
            "the FOUP did not drop exactly once");

        // The block itself drops too.
        helper.assertValueEqual(1, countOf(after, ModItems.TEST_MACHINE.get()),
            "the machine did not drop its own block item");

        // And nothing more than that: four stacks, and no stray extras.
        helper.assertValueEqual(4, countEntities(helper, pos, DROP_RADIUS),
            "expected exactly four dropped stacks (iron, gold, foup, block), got " + after);
        helper.assertValueEqual(10, sumOf(after),
            "expected 5 iron + 3 gold + 1 foup + 1 block = 10 items in total, got " + after);

        // The FOUP must come back with its lot intact, not as a bare item.
        ItemEntity droppedFoup = findItemEntity(helper, pos, DROP_RADIUS, ModItems.FOUP_MK1.get());
        helper.assertTrue(droppedFoup != null, "no FOUP item entity was found");
        if (droppedFoup != null) {
            FoupContents contents = FoupItems.contentsOf(droppedFoup.getItem());
            helper.assertTrue(contents != null && !contents.isEmpty(),
                "the dropped FOUP lost its lot contents");
            EUVCRAFTMod.LOGGER.info("[M1-02c] break: the FOUP came back carrying {} wafers",
                contents == null ? 0 : contents.size());
        }

        // NEGATIVE CONTROL: an empty machine drops only its block, never a phantom stack.
        // Placed at THIRD rather than SECOND so its drop window cannot see the first
        // machine's block item, which is only 2 blocks away at SECOND.
        FabMachineBlockEntity empty = placeMachine(helper, THIRD);
        empty.setStoredEnergy(4_000);
        player.gameMode.destroyBlock(helper.absolutePos(THIRD));
        Map<Item, Integer> emptyDrops = countItems(helper, helper.absolutePos(THIRD), DROP_RADIUS);
        helper.assertValueEqual(1, countOf(emptyDrops, ModItems.TEST_MACHINE.get()),
            "the empty machine did not drop just its block (negative control)");
        helper.assertValueEqual(1, sumOf(emptyDrops),
            "the empty machine dropped contents it never had (negative control): " + emptyDrops);
        helper.assertValueEqual(1, countEntities(helper, helper.absolutePos(THIRD), DROP_RADIUS),
            "the empty machine dropped more than one stack (negative control): " + emptyDrops);
        EUVCRAFTMod.LOGGER.info("[M1-02c] break_in_survival: empty machine dropped only {}", emptyDrops);

        // NEGATIVE CONTROL: stored energy is not an item, so it cannot be counted as a drop
        // of anything. Checked over the whole test volume.
        helper.assertValueEqual(0, countOf(countItems(helper, pos, VOLUME_RADIUS), Items.BUCKET),
            "a bucket appeared out of nowhere, so the item count is not trustworthy "
                + "(negative control)");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. Replacing the block drops the contents too
    // ------------------------------------------------------------------

    private static void replaceDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        FabMachineBlockEntity machine = placeMachine(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, aFilledFoup(level));
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 7));
        helper.assertValueEqual(2, countNonEmptySlots(machine),
            "the machine should hold two stacks before the replacement");

        helper.assertValueEqual(0, sumOf(countItems(helper, pos, DROP_RADIUS)),
            "the test area already held item entities: " + countItems(helper, pos, DROP_RADIUS));

        // Replace it, the way a build or another block placement would. setBlock goes through
        // the same LevelChunk.setBlockState path as a break.
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE),
            "the replacement block did not go in");
        helper.assertTrue(machine.isRemoved(),
            "the machine's block entity was not removed by the replacement");

        Map<Item, Integer> after = countItems(helper, pos, DROP_RADIUS);
        EUVCRAFTMod.LOGGER.info("[M1-02c] replace: dropped {}", after);
        helper.assertValueEqual(7, stackSizeOf(after, Items.IRON_INGOT),
            "replacing the block did not drop the stored iron ingots, got " + after);
        helper.assertValueEqual(1, countOf(after, ModItems.FOUP_MK1.get()),
            "replacing the block did not drop the FOUP, got " + after);

        // NEGATIVE CONTROL: replacing a block never yields the block that was there, nor the
        // block that replaced it. Both would be duplication if they appeared.
        helper.assertValueEqual(0, countOf(after, ModItems.TEST_MACHINE.get()),
            "replacing the block also dropped the machine block item (negative control)");
        helper.assertValueEqual(0, countOf(after, Blocks.STONE.asItem()),
            "replacing a block dropped the replacement block (negative control)");

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. No duplication: totals before and after
    // ------------------------------------------------------------------

    private static void noDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        FabMachineBlockEntity machine = placeMachine(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        // Three distinct items at three distinct sizes, so a double drop changes the totals
        // rather than merely the entity count.
        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, aFilledFoup(level));
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 11));
        machine.giveOutput(new ItemStack(Items.GOLD_INGOT, 4));

        int expectedIron = 11;
        int expectedGold = 4;
        int expectedFoup = 1;

        ServerPlayer player = survivalPlayerWithPickaxe(level, pos);
        Map<Item, Integer> before = totalItemCounts(helper);
        player.gameMode.destroyBlock(pos);
        Map<Item, Integer> after = totalItemCounts(helper);

        helper.assertValueEqual(expectedIron, stackSizeOf(after, Items.IRON_INGOT),
            "the iron count changed on break");
        helper.assertValueEqual(expectedGold, stackSizeOf(after, Items.GOLD_INGOT),
            "the gold count changed on break");
        helper.assertValueEqual(expectedFoup, countOf(after, ModItems.FOUP_MK1.get()),
            "the FOUP count changed on break");

        // The delta is exactly what went in, plus the block item. Anything else is
        // duplication or loss.
        int expectedDelta = expectedIron + expectedGold + expectedFoup + 1;
        int actualDelta = sumOf(after) - sumOf(before);
        helper.assertValueEqual(expectedDelta, actualDelta,
            "the world total changed by " + actualDelta + " instead of " + expectedDelta
                + ": duplication or loss");
        helper.assertValueEqual(1, countOf(after, ModItems.TEST_MACHINE.get()),
            "the machine did not drop exactly one block item");
        EUVCRAFTMod.LOGGER.info("[M1-02c] no_duplication: world total {} -> {}, delta {} (expected {})",
            sumOf(before), sumOf(after), actualDelta, expectedDelta);

        // The items were moved out, not merely copied: the machine is gone.
        helper.assertTrue(machine.isRemoved(), "the block entity survived the break");

        // NEGATIVE CONTROL: a second identical break adds exactly the same amount again.
        // Two breaks must mean two sets, not one set doubled and one set lost.
        FabMachineBlockEntity second = placeMachine(helper, SECOND);
        second.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 11));
        Map<Item, Integer> mid = totalItemCounts(helper);
        player.gameMode.destroyBlock(helper.absolutePos(SECOND));
        Map<Item, Integer> twice = totalItemCounts(helper);
        helper.assertValueEqual(11 + 1, sumOf(twice) - sumOf(mid),
            "the second break did not drop the same amount as the first (negative control)");
        helper.assertValueEqual(expectedIron * 2, stackSizeOf(twice, Items.IRON_INGOT),
            "two breaks did not produce two iron stacks (negative control)");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. Unloading the block entity drops nothing
    // ------------------------------------------------------------------

    private static void unloadDropsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        FabMachineBlockEntity machine = placeMachine(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        machine.setSlot(TestMachineBlockEntity.INPUT_SLOT, aFilledFoup(level));
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 13));

        helper.assertValueEqual(0, sumOf(countItems(helper, pos, DROP_RADIUS)),
            "the test area already held items");

        // Removing the block entity is what a chunk unload does to it. It must not drop:
        // preRemoveSideEffects is deliberately not part of this path, so an unloaded
        // machine's contents stay in the save and come back when the chunk does.
        level.removeBlockEntity(pos);

        // Level.getBlockEntity uses EntityCreationType.IMMEDIATE and recreates a block
        // entity for a block that still has one, so a non-null result here would prove
        // nothing. The removed instance is the real check.
        helper.assertTrue(machine.isRemoved(),
            "the block entity was not removed. Level.getBlockEntity cannot be used to check "
                + "this: it recreates one for a block that still has a block entity");
        helper.assertTrue(level.getBlockState(pos).is(ModMachines.TEST_MACHINE.get()),
            "unloading must not remove the block itself");

        Map<Item, Integer> after = countItems(helper, pos, DROP_RADIUS);
        helper.assertValueEqual(0, sumOf(after),
            "unloading the block entity dropped items (negative control): " + after);
        EUVCRAFTMod.LOGGER.info("[M1-02c] unload: removed the block entity, {} items dropped",
            sumOf(after));

        // The unload dropped nothing, and the contents are still on the removed instance:
        // a real chunk unload saves the block entity before dropping it, so nothing is lost.
        Map<Item, Integer> stillHeld = contentsOf(machine);
        helper.assertValueEqual(13, stackSizeOf(stillHeld, Items.IRON_INGOT),
            "the removed block entity lost its contents in memory, so a save would lose them");
        helper.assertTrue(FoupItems.contentsOf(
                machine.getItem(TestMachineBlockEntity.INPUT_SLOT)) != null,
            "the removed block entity lost the FOUP lot");

        // Prove it end to end: save the removed instance, place a fresh machine, load the
        // tag into it, and the contents come back. That is the chunk-reload path.
        CompoundTag saved = new CompoundTag();
        TagValueOutput out = TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        machine.saveWithId(out);
        saved = out.buildResult();
        helper.assertTrue(saved.contains(ContainerHelper.TAG_ITEMS),
            "the unloaded machine's save has no Items entry, so the contents were already lost");

        FabMachineBlockEntity reloaded = placeMachine(helper, THIRD);
        reloaded.loadWithComponents(TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertValueEqual(13, stackSizeOf(contentsOf(reloaded), Items.IRON_INGOT),
            "the reloaded machine did not get its iron back: the unload lost the contents");

        ServerPlayer player = survivalPlayerWithPickaxe(level, helper.absolutePos(THIRD));
        player.gameMode.destroyBlock(helper.absolutePos(THIRD));
        Map<Item, Integer> afterBreak = countItems(helper, helper.absolutePos(THIRD), DROP_RADIUS);
        helper.assertValueEqual(13, stackSizeOf(afterBreak, Items.IRON_INGOT),
            "the reloaded machine did not drop the iron that came back from the save");
        helper.assertValueEqual(1, countOf(afterBreak, ModItems.FOUP_MK1.get()),
            "the reloaded machine did not drop the FOUP that came back from the save");
        EUVCRAFTMod.LOGGER.info("[M1-02c] unload: save, reload and break produced {}", afterBreak);

        // NEGATIVE CONTROL: the other path gated on the same flag must also drop nothing.
        // That shows the gate is the flag rather than a coincidence.
        FabMachineBlockEntity flagged = placeMachine(helper, SECOND);
        flagged.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 17));
        Map<Item, Integer> preFlag = countItems(helper, helper.absolutePos(SECOND), DROP_RADIUS);
        level.setBlock(helper.absolutePos(SECOND), Blocks.STONE.defaultBlockState(),
            Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        Map<Item, Integer> postFlag = countItems(helper, helper.absolutePos(SECOND), DROP_RADIUS);
        helper.assertValueEqual(sumOf(preFlag), sumOf(postFlag),
            "setBlock with UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS dropped items (negative control): "
                + preFlag + " -> " + postFlag);
        EUVCRAFTMod.LOGGER.info("[M1-02c] unload: the flagged setBlock dropped {} items",
            sumOf(postFlag));

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. The documented extension point for a second inventory
    // ------------------------------------------------------------------

    private static void extensionPoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        FabMachineBlockEntity machine = placeMachine(helper, MACHINE);
        BlockPos pos = helper.absolutePos(MACHINE);

        // Stand in for the Process Cell controller: a machine that also owns a second
        // inventory and opts into the extension point.
        ((TestMachineBlockEntity) machine).setExtraInventoryForTest(new ItemStack(Items.EMERALD, 9), new ItemStack(Items.DIAMOND, 2));
        machine.setSlot(TestMachineBlockEntity.STORAGE_SLOT, new ItemStack(Items.IRON_INGOT, 4));

        helper.assertValueEqual(1, countNonEmptySlots(machine),
            "the machine should hold one stack before the break");

        ServerPlayer player = survivalPlayerWithPickaxe(level, pos);
        player.gameMode.destroyBlock(pos);

        Map<Item, Integer> after = countItems(helper, pos, DROP_RADIUS);
        EUVCRAFTMod.LOGGER.info("[M1-02c] extension_point: both inventories dropped {}", after);

        // NEGATIVE CONTROL: the machine's own storage slot still drops, so a failure in the
        // extension point cannot be mistaken for "nothing drops at all".
        helper.assertValueEqual(4, stackSizeOf(after, Items.IRON_INGOT),
            "the machine's own storage slot did not drop (negative control)");
        helper.assertValueEqual(9, stackSizeOf(after, Items.EMERALD),
            "the extra inventory did not drop, got " + after);
        helper.assertValueEqual(2, stackSizeOf(after, Items.DIAMOND),
            "the second extra stack did not drop, got " + after);
        helper.assertValueEqual(0, countOf(after, ModItems.FOUP_MK1.get()),
            "an empty slot produced a FOUP (negative control)");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. The Process Cell blocks drop themselves
    // ------------------------------------------------------------------

    private static void processCellDropsSelf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearItems(helper);
        ServerPlayer player = survivalPlayerWithPickaxe(level, helper.absolutePos(MACHINE));

        List<Block> blocks = List.of(
            ModMultiblocks.PROCESS_CELL_CONTROLLER.get(),
            ModMultiblocks.PROCESS_CELL_CASING.get(),
            ModMultiblocks.PROCESS_CELL_POWER_PORT.get(),
            ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get());
        List<Item> items = List.of(
            ModItems.PROCESS_CELL_CONTROLLER.get(),
            ModItems.PROCESS_CELL_CASING.get(),
            ModItems.PROCESS_CELL_POWER_PORT.get(),
            ModItems.PROCESS_CELL_OPERATING_PANEL.get());

        // All three are broken at the same position, one after another, so no block's drops
        // can fall inside another block's counting window. Spacing them across the structure
        // instead would put them 2 blocks apart, which overlaps.
        BlockPos pos = helper.absolutePos(MACHINE);
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(pos).is(block), "could not place " + id);

            clearItems(helper);
            player.gameMode.destroyBlock(pos);
            Map<Item, Integer> drops = countItems(helper, pos, DROP_RADIUS);
            helper.assertValueEqual(1, countOf(drops, items.get(index)),
                id + " did not drop itself, got " + drops);
            helper.assertValueEqual(1, sumOf(drops),
                id + " dropped something other than exactly itself: " + drops);
            helper.assertValueEqual(1, countEntities(helper, pos, DROP_RADIUS),
                id + " dropped more than one stack: " + drops);
            EUVCRAFTMod.LOGGER.info("[M1-02c] process_cell: {} dropped {}", id, drops);
        }

        // NEGATIVE CONTROL: a vanilla block whose loot table is untouched drops exactly one
        // of itself, so a zero here would mean the counting method cannot see a zero.
        BlockPos stonePos = helper.absolutePos(THIRD);
        helper.setBlock(THIRD, Blocks.STONE);
        helper.assertTrue(level.destroyBlock(stonePos, true, player, Block.UPDATE_ALL),
            "could not destroy the control stone block");
        Map<Item, Integer> stoneDrops = countItems(helper, stonePos, DROP_RADIUS);
        helper.assertValueEqual(1, sumOf(stoneDrops),
            "the control block should have dropped itself: " + stoneDrops);
        helper.assertValueEqual(0, countOf(stoneDrops, ModItems.PROCESS_CELL_CASING.get()),
            "the control block dropped a Process Cell casing (negative control)");

        player.closeContainer();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Places a machine and returns its block entity, failing the test if there is none. */
    private static FabMachineBlockEntity placeMachine(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModMachines.TEST_MACHINE.get());
        FabMachineBlockEntity machine = blockEntityAt(helper, relative);
        helper.assertTrue(machine != null, "no block entity at " + relative);
        return machine;
    }

    private static FabMachineBlockEntity blockEntityAt(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        return level.getBlockEntity(helper.absolutePos(relative)) instanceof FabMachineBlockEntity machine
            ? machine
            : null;
    }

    /** A FOUP with a real lot in it, so the test can check the lot survives the round trip. */
    private static ItemStack aFilledFoup(ServerLevel level) {
        return FoupItems.with(ModItems.FOUP_MK1.get(),
            FoupDebugCommand.fillContents(level, UUID.randomUUID(), 4, 4, 2));
    }

    /** How many of the machine's slots hold something. */
    private static int countNonEmptySlots(FabMachineBlockEntity machine) {
        int count = 0;
        for (int index = 0; index < machine.getContainerSize(); index++) {
            if (!machine.getItem(index).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * A real {@link ServerPlayer} in survival holding a stone pickaxe.
     *
     * <p>Placed through the player list rather than constructed bare, because the mining
     * path calls back into the connection (for latency when a block takes time to break) and
     * a player with a null connection throws.</p>
     *
     * <p>Survival rather than creative because {@code ServerPlayerGameMode.destroyBlock}
     * consults {@code canHarvestBlock} and {@code preventsBlockDrops}, which differ between
     * the two: a creative player drops nothing at all. The pickaxe is needed because every
     * machine block is registered {@code requiresCorrectToolForDrops()}.</p>
     */
    private static ServerPlayer survivalPlayerWithPickaxe(ServerLevel level, BlockPos pos) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "drop-tester"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) { };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.gameMode.setLevel(level);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
        return player;
    }

    /**
     * The item entity type.
     *
     * <p>26.3 moved the entity type constants off {@code EntityType} and onto a separate
     * {@code EntityTypes} holder; {@code EntityType.ITEM} no longer exists. Vanilla's own
     * game test helper uses {@code EntityTypes.ITEM}, so this does too.</p>
     */
    private static EntityType<ItemEntity> itemEntityType() {
        return net.minecraft.world.entity.EntityTypes.ITEM;
    }

    /**
     * Item entity counts inside a cube around {@code centre}, which is an absolute position.
     *
     * <p>Queries the level directly rather than through {@code helper.getEntities}, because
     * that helper takes a <em>structure-relative</em> position and calls
     * {@code absolutePos} on it. Passing an already-absolute position silently looks
     * somewhere else entirely: the drop really happens, but the query finds nothing. That
     * cost one confusing run before it was pinned down with a diagnostic.</p>
     */
    private static Map<Item, Integer> countItems(GameTestHelper helper, BlockPos centre, double radius) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemEntity entity : helper.getLevel().getEntities(itemEntityType(),
            new net.minecraft.world.phys.AABB(centre).inflate(radius), Entity::isAlive)) {
            add(counts, entity);
        }
        return counts;
    }

    /**
     * Item entity counts over the whole test volume, for the duplication test.
     *
     * <p>A radius of {@link #VOLUME_RADIUS} around the first machine comfortably covers a
     * 3x3 test structure plus everything its blocks can scatter.</p>
     */
    private static Map<Item, Integer> totalItemCounts(GameTestHelper helper) {
        return countItems(helper, helper.absolutePos(MACHINE), VOLUME_RADIUS);
    }

    /**
 * Clears every item entity in the test structure.
 *
     * <p>Game tests share one world, so an item dropped by one test is still lying on the
     * ground when the next test runs — and several of these tests place their block at the
     * same structure-relative position. Without this, a test's counts include the previous
     * test's leftovers, which is exactly the kind of false pass or false fail that makes a
     * drop test worthless. Scoped to the test structure, so it touches nothing outside.</p>
     */
    private static void clearItems(GameTestHelper helper) {
        helper.killAllEntitiesOfClass(ItemEntity.class);
    }

/** What a container is holding, as item counts. Works on a removed block entity too. */
    private static Map<Item, Integer> contentsOf(net.minecraft.world.Container container) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** How many item entities are inside a cube around {@code centre}. */
    private static int countEntities(GameTestHelper helper, BlockPos centre, double radius) {
        return helper.getLevel().getEntities(itemEntityType(),
            new net.minecraft.world.phys.AABB(centre).inflate(radius), Entity::isAlive).size();
    }

    private static void add(Map<Item, Integer> counts, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!stack.isEmpty()) {
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }

    private static int countOf(Map<Item, Integer> counts, Item item) {
        return counts.getOrDefault(item, 0);
    }

    /** The size of the single dropped stack of {@code item}, or 0 if there is none. */
    private static int stackSizeOf(Map<Item, Integer> counts, Item item) {
        return counts.getOrDefault(item, 0);
    }

    private static int sumOf(Map<Item, Integer> counts) {
        int total = 0;
        for (int value : counts.values()) {
            total += value;
        }
        return total;
    }

    private static ItemEntity findItemEntity(GameTestHelper helper, BlockPos centre, double radius, Item item) {
        for (ItemEntity entity : helper.getLevel().getEntities(itemEntityType(),
            new net.minecraft.world.phys.AABB(centre).inflate(radius), Entity::isAlive)) {
            if (entity.getItem().getItem() == item) {
                return entity;
            }
        }
        return null;
    }
}