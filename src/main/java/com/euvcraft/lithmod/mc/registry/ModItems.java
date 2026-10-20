package com.euvcraft.lithmod.mc.registry;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FoupItem;
import com.euvcraft.lithmod.mc.GameTestFixtures;
import com.euvcraft.lithmod.mc.machine.test.ModMachines;
import com.euvcraft.lithmod.mc.LineComponentItem;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

/**
 * Items registered by EUVCRAFT.
 *
 * <p>Both FOUP variants carry the same {@link ModDataComponents#FOUP_CONTENTS}
 * component and are otherwise identical in this milestone. Mk2 exists so that the
 * later Encoded Logistics transit support has an item to hang off; nothing about it
 * differs yet.</p>
 *
 * <p>Everything from {@link #LOGIC_CHIP_T1} onwards is a plain item: no behaviour, no
 * hidden data and no machine. Chips are told apart by the design they stand for, which
 * {@link com.euvcraft.lithmod.mc.ChipCatalog} keeps next to them; resist items carry
 * their node tier in {@link ModDataComponents#RESIST_TIER}, which is how
 * {@link com.euvcraft.lithmod.mc.ResistCatalog} answers for a stack. The resist tier
 * number in an item definition is part of which resist that item is, not a tunable
 * gameplay number.</p>
 *
 * <p>{@link #MASK} is the one item here that carries data of its own: its
 * {@link ModDataComponents#MASK_DATA} component names the die type and node tier
 * the mask was cut for and counts down the exposures it has left. All of that is
 * public, so the client reads the same design the server keeps.</p>
 *
 * <p>Chips and consumables use the vanilla maximum of 64. Blank mask plates are a
 * consumable and stack to 64; FOUPs, photomasks and line components stack to one;
 * block items use their normal defaults.</p>
 */
public final class ModItems {

    /** Registry of EUVCRAFT items. */
    public static final Items ITEMS = DeferredRegister.createItems(EUVCRAFTMod.MODID);

    /** FOUP Mk1: the basic sealed lot carrier. */
    public static final DeferredItem<Item> FOUP_MK1 = ITEMS.registerItem("foup_mk1", FoupItem::new, properties -> properties.stacksTo(1));

    /** FOUP Mk2: reserved for Encoded Logistics transit support. */
    public static final DeferredItem<Item> FOUP_MK2 = ITEMS.registerItem("foup_mk2", FoupItem::new, properties -> properties.stacksTo(1));

    /** A logic die cut at node tier 1. */
    public static final DeferredItem<Item> LOGIC_CHIP_T1 = ITEMS.registerSimpleItem("logic_chip_t1");

    /** A logic die cut at node tier 2. */
    public static final DeferredItem<Item> LOGIC_CHIP_T2 = ITEMS.registerSimpleItem("logic_chip_t2");

    /** A memory die cut at node tier 2. */
    public static final DeferredItem<Item> MEMORY_CHIP_T2 = ITEMS.registerSimpleItem("memory_chip_t2");

    /** One failed chip, recycled back into a usable die. */
    public static final DeferredItem<Item> CHIP_SCRAP = ITEMS.registerSimpleItem("chip_scrap");

    /**
     * Smelted sand: the feedstock of the Crystal Puller.
     *
     * <p>Made from {@code minecraft:sand} by the vanilla smelting and blasting recipes in
     * {@code data/lithmod/recipe}, so it needs no behaviour of its own.</p>
     */
    public static final DeferredItem<Item> SILICON = ITEMS.registerSimpleItem("silicon");

    /**
     * A pulled single-crystal boule.
     *
     * <p>The product of the Crystal Puller and the only input the Wafer Slicer takes;
     * one boule becomes one FOUP of wafers.</p>
     */
    public static final DeferredItem<Item> SILICON_BOULE = ITEMS.registerSimpleItem("silicon_boule");

    /**
     * A purified single-crystal boule.
     *
     * <p>The product of the Boule Purifier: one ordinary boule plus the purifier's
     * data-driven water cost becomes one pure boule. It is a plain item with no
     * hidden data — the grade it stands for is the item itself, and only the Wafer
     * Slicer reads it, to stamp {@link com.euvcraft.lithmod.core.WaferSubstrate#PURE}
     * onto the lot it slices. No crafting recipe produces it; on purpose.</p>
     */
    public static final DeferredItem<Item> PURE_SILICON_BOULE = ITEMS.registerSimpleItem("pure_silicon_boule");

    /** Photoresist for node tier 1 lithography. */
    public static final DeferredItem<Item> RESIST_T1 =
        ITEMS.registerSimpleItem("resist_t1", properties -> properties.component(ModDataComponents.RESIST_TIER, 1));

    /** Photoresist for node tier 2 lithography. */
    public static final DeferredItem<Item> RESIST_T2 =
        ITEMS.registerSimpleItem("resist_t2", properties -> properties.component(ModDataComponents.RESIST_TIER, 2));

    /** The packaging consumable the Test and Package machine feeds on. */
    public static final DeferredItem<Item> CHIP_PACKAGE = ITEMS.registerSimpleItem("chip_package");

    /**
     * An unexposed photomask plate.
     *
     * <p>The raw input a Mask Writer exposes: a plate with no design on it at all,
     * which is why it carries no {@link ModDataComponents#MASK_DATA} either. It is
     * a bulk consumable, so it uses the vanilla stack size of 64.</p>
     */
    public static final DeferredItem<Item> BLANK_MASK = ITEMS.registerSimpleItem("blank_mask");

    /**
     * A photomask carrying one design.
     *
     * <p>The design is public stack data rather than part of the item, so a stack
     * that does carry one can name any die type at any tier. Deliberately no
     * default component: a stack with no {@link ModDataComponents#MASK_DATA} has
     * no die type, and the Mask Writer has to keep rejecting it instead of
     * guessing one. Masks do not stack, so the stack size is one.</p>
     */
    public static final DeferredItem<Item> MASK = ITEMS.registerSimpleItem("mask", properties -> properties.stacksTo(1));

    /** Line component: mercury lamp, light_source, node tier 1. */
    public static final DeferredItem<LineComponentItem> MERCURY_LAMP =
        ITEMS.registerItem("mercury_lamp", properties -> new LineComponentItem(properties, "light_source", 1), properties -> properties.stacksTo(1));

    /** Line component: i-line lamp, light_source, node tier 2. */
    public static final DeferredItem<LineComponentItem> I_LINE_LAMP =
        ITEMS.registerItem("i_line_lamp", properties -> new LineComponentItem(properties, "light_source", 2), properties -> properties.stacksTo(1));

    /** Line component: contact plate, optics, node tier 1. */
    public static final DeferredItem<LineComponentItem> CONTACT_PLATE =
        ITEMS.registerItem("contact_plate", properties -> new LineComponentItem(properties, "optics", 1), properties -> properties.stacksTo(1));

    /** Line component: reduction lens, optics, node tier 2. */
    public static final DeferredItem<LineComponentItem> REDUCTION_LENS =
        ITEMS.registerItem("reduction_lens", properties -> new LineComponentItem(properties, "optics", 2), properties -> properties.stacksTo(1));

    /** Line component: manual aligner, stage, node tier 1. */
    public static final DeferredItem<LineComponentItem> MANUAL_ALIGNER =
        ITEMS.registerItem("manual_aligner", properties -> new LineComponentItem(properties, "stage", 1), properties -> properties.stacksTo(1));

    /** Line component: stepper stage, stage, node tier 2. */
    public static final DeferredItem<LineComponentItem> STEPPER_STAGE =
        ITEMS.registerItem("stepper_stage", properties -> new LineComponentItem(properties, "stage", 2), properties -> properties.stacksTo(1));

    /** Line component: basic air filter, cleanroom, node tier 1. */
    public static final DeferredItem<LineComponentItem> BASIC_AIR_FILTER =
        ITEMS.registerItem("basic_air_filter", properties -> new LineComponentItem(properties, "cleanroom", 1), properties -> properties.stacksTo(1));

    /** Line component: fine air filter, cleanroom, node tier 2. */
    public static final DeferredItem<LineComponentItem> FINE_AIR_FILTER =
        ITEMS.registerItem("fine_air_filter", properties -> new LineComponentItem(properties, "cleanroom", 2), properties -> properties.stacksTo(1));

    /** Line component: optical microscope, metrology, node tier 1. */
    public static final DeferredItem<LineComponentItem> OPTICAL_MICROSCOPE =
        ITEMS.registerItem("optical_microscope", properties -> new LineComponentItem(properties, "metrology", 1), properties -> properties.stacksTo(1));

    /** Line component: overlay scope, metrology, node tier 2. */
    public static final DeferredItem<LineComponentItem> OVERLAY_SCOPE =
        ITEMS.registerItem("overlay_scope", properties -> new LineComponentItem(properties, "metrology", 2), properties -> properties.stacksTo(1));

    /**
     * The four Process Cell multiblock block items.
     *
     * <p>Declared here so the whole item registry stays in one place, but the blocks come from
     * {@code ModMultiblocks} because a block item cannot be built before its block is registered.
     * The {@code .get()} calls sit inside lambdas, so neither class touches the other during static
     * initialisation.</p>
     *
     * <p>{@code registerSimpleBlockItem}, not {@code register} with a hand-built
     * {@code new BlockItem(block, new Item.Properties())}: only the helper stamps the item id onto
     * the properties, and without it the constructor throws {@code NullPointerException: Item id
     * not set}. That is the item-side twin of the block-side {@code registerBlock} rule the M1-02s
     * spike found.</p>
     */
    public static final DeferredItem<BlockItem> PROCESS_CELL_CONTROLLER =
        ITEMS.registerSimpleBlockItem("process_cell_controller",
            () -> ModMultiblocks.PROCESS_CELL_CONTROLLER.get());

    public static final DeferredItem<BlockItem> PROCESS_CELL_CASING =
        ITEMS.registerSimpleBlockItem("process_cell_casing",
            () -> ModMultiblocks.PROCESS_CELL_CASING.get());

    public static final DeferredItem<BlockItem> PROCESS_CELL_POWER_PORT =
        ITEMS.registerSimpleBlockItem("process_cell_power_port",
            () -> ModMultiblocks.PROCESS_CELL_POWER_PORT.get());

    public static final DeferredItem<BlockItem> PROCESS_CELL_OPERATING_PANEL =
        ITEMS.registerSimpleBlockItem("process_cell_operating_panel",
            () -> ModMultiblocks.PROCESS_CELL_OPERATING_PANEL.get());

    /**
     * The four T3 support-multiblock block items.
     *
     * <p>Same rule as every block item above: the {@code .get()} call sits inside a lambda and the
     * blocks come from {@link com.euvcraft.lithmod.mc.multiblock.support.SupportMachines}, which is
     * the support foundation's own registry class. The two controller items name the machine kinds;
     * the casing and port items name the shared member blocks that never alias Process Cell blocks.</p>
     */
    public static final DeferredItem<BlockItem> BOULE_PURIFIER =
        ITEMS.registerSimpleBlockItem("boule_purifier",
            () -> com.euvcraft.lithmod.mc.multiblock.support.SupportMachines.BOULE_PURIFIER.get());

    public static final DeferredItem<BlockItem> E_BEAM_MASK_WRITER =
        ITEMS.registerSimpleBlockItem("ebeam_mask_writer",
            () -> com.euvcraft.lithmod.mc.multiblock.support.SupportMachines.E_BEAM_MASK_WRITER.get());

    public static final DeferredItem<BlockItem> SUPPORT_CASING =
        ITEMS.registerSimpleBlockItem("support_casing",
            () -> com.euvcraft.lithmod.mc.multiblock.support.SupportMachines.SUPPORT_CASING.get());

    public static final DeferredItem<BlockItem> SUPPORT_POWER_PORT =
        ITEMS.registerSimpleBlockItem("support_power_port",
            () -> com.euvcraft.lithmod.mc.multiblock.support.SupportMachines.SUPPORT_POWER_PORT.get());

    /**
     * The test-only machine's block item.
     *
     * <p>Added by M1-02c. Without a block item the machine cannot drop itself at all, so a
     * player who breaks one gets their FOUP back and no block. Same
     * {@code registerSimpleBlockItem} rule as the Process Cell items above.</p>
     *
     * <p>Conditionally registered only when fixtures are enabled. When fixtures are off,
     * the holder is left null and nothing is registered at all: there is no plain Item
     * fallback, so the id {@code lithmod:test_machine} is genuinely absent from the item
     * registry. Every use of this holder is inside fixture-gated game tests.</p>
     */
    public static final DeferredItem<BlockItem> TEST_MACHINE =
        GameTestFixtures.enabled()
            ? ITEMS.registerSimpleBlockItem("test_machine",
                () -> ModMachines.TEST_MACHINE.get())
            : null;

    /**
     * The two silicon chain machines' block items.
     *
     * <p>Without a block item a machine cannot drop itself, so same rule as above: the
     * {@code .get()} call sits inside a lambda and the blocks come from
     * {@link com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines}.</p>
     */
    public static final DeferredItem<BlockItem> CRYSTAL_PULLER =
        ITEMS.registerSimpleBlockItem("crystal_puller",
            () -> com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines.CRYSTAL_PULLER.get());

    public static final DeferredItem<BlockItem> WAFER_SLICER =
        ITEMS.registerSimpleBlockItem("wafer_slicer",
            () -> com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines.WAFER_SLICER.get());

    /**
     * The Pattern Generator's block item.
     *
     * <p>Same rule as every machine item above: without a block item the machine cannot drop
     * itself, so the {@code .get()} call sits inside a lambda and the block comes from
     * {@link com.euvcraft.lithmod.mc.machine.mask.MaskMachines}, which is the mask chain's
     * own registry class. The machine has no recipes in this milestone, so it is placed last
     * in the photomask tab rather than given a tab of its own: every tab's entry count is
     * pinned by a passing game test, and appending keeps the existing entries in place.</p>
     */
    public static final DeferredItem<BlockItem> PATTERN_GENERATOR =
        ITEMS.registerSimpleBlockItem("pattern_generator",
            () -> com.euvcraft.lithmod.mc.machine.mask.MaskMachines.PATTERN_GENERATOR.get());

    /**
     * The Test and Package machine's block item.
     *
     * <p>Same rule as every machine item above: without a block item the machine cannot drop
     * itself, so the {@code .get()} call sits inside a lambda and the block comes from
     * {@link com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines}, which is the
     * packaging chain's own registry class.</p>
     */
    public static final DeferredItem<BlockItem> TEST_PACKAGE =
        ITEMS.registerSimpleBlockItem("test_package",
            () -> com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines.TEST_PACKAGE.get());

    // T3 products and line components. Recipes remain in editable JSON.
    public static final DeferredItem<Item> LOGIC_CHIP_T3 = ITEMS.registerSimpleItem("logic_chip_t3");
    public static final DeferredItem<Item> MEMORY_CHIP_T3 = ITEMS.registerSimpleItem("memory_chip_t3");
    public static final DeferredItem<Item> COMPUTE_CHIP_T3 = ITEMS.registerSimpleItem("compute_chip_t3");
    public static final DeferredItem<Item> NAND_CHIP_T3 = ITEMS.registerSimpleItem("nand_chip_t3");
    public static final DeferredItem<Item> RESIST_T3 = ITEMS.registerSimpleItem("resist_t3",
        properties -> properties.component(ModDataComponents.RESIST_TIER, 3));
    public static final DeferredItem<LineComponentItem> KRF_EXCIMER_LASER = ITEMS.registerItem("krf_excimer_laser",
        properties -> new LineComponentItem(properties, "light_source", 3), properties -> properties.stacksTo(1));
    public static final DeferredItem<LineComponentItem> DUV_PROJECTION_LENS = ITEMS.registerItem("duv_projection_lens",
        properties -> new LineComponentItem(properties, "optics", 3), properties -> properties.stacksTo(1));
    public static final DeferredItem<LineComponentItem> PRECISION_STEPPER_STAGE = ITEMS.registerItem("precision_stepper_stage",
        properties -> new LineComponentItem(properties, "stage", 3), properties -> properties.stacksTo(1));
    public static final DeferredItem<LineComponentItem> HEPA_FILTER = ITEMS.registerItem("hepa_filter",
        properties -> new LineComponentItem(properties, "cleanroom", 3), properties -> properties.stacksTo(1));
    public static final DeferredItem<LineComponentItem> LASER_INTERFEROMETER = ITEMS.registerItem("laser_interferometer",
        properties -> new LineComponentItem(properties, "metrology", 3), properties -> properties.stacksTo(1));

    private ModItems() {
    }
}
