package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.FoupDebugCommand;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.mojang.logging.LogUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorBlockEntity;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.FabYieldService;

import com.mojang.authlib.GameProfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.GameType;

/**
 * M1-12 boundary game tests: wire privacy, tooltip/log privacy, and hand-fed capability enumeration.
 */
public final class M1BoundaryGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ENVIRONMENT_ID = "m1_boundary";

    private static final String PRIVACY_WIRE = "m1_gate_privacy_wire";
    private static final String PRIVACY_TOOLTIP_LOGS = "m1_gate_privacy_tooltip_logs";
    private static final String ALL_HAND_FED = "m1_gate_all_hand_fed";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    static {
        FUNCTIONS.register(PRIVACY_WIRE, () -> M1BoundaryGameTests::privacyWire);
        FUNCTIONS.register(PRIVACY_TOOLTIP_LOGS, () -> M1BoundaryGameTests::privacyTooltipLogs);
        FUNCTIONS.register(ALL_HAND_FED, () -> M1BoundaryGameTests::allHandFed);
    }

    private M1BoundaryGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) M1BoundaryGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            200, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, PRIVACY_WIRE);
        register(event, data, PRIVACY_TOOLTIP_LOGS);
        register(event, data, ALL_HAND_FED);
        LOGGER.info("Registered EUVCRAFT M1 boundary game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    // ------------------------------------------------------------------
    // 1. Wire privacy invariance test
    // ------------------------------------------------------------------

    /**
     * Creates a client-view placeholder lot via STREAM_CODEC roundtrip.
     * This is the only way to get a client view from outside the mc package,
     * since FoupContentsCodec.fromPublicView is package-private.
     */
    private static FoupContents createClientView(int capacity, int waferCount, int layer) {
        // Build a sealed lot with the desired public view
        UUID dummyId = UUID.randomUUID();
        List<Wafer> wafers = new ArrayList<>(waferCount);
        for (int i = 0; i < waferCount; i++) {
            wafers.add(Wafer.unprocessed());
        }
        FoupContents sealed = new FoupContents(dummyId, capacity, wafers, null);
        // Encode via STREAM_CODEC (network form) and decode back to get client view
        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, sealed);
        return FoupContentsCodec.STREAM_CODEC.decode(buffer);
    }

    /**
     * Case m1_gate_privacy_wire:
     * Construct two SERVER-TRUSTED sealed FOUP stacks with identical PUBLIC cap/count/layer
     * but DIFFERENT private rolls/flags/run-spec internals.
     * Use actual FoupContentsCodec STREAM_CODEC and registered ItemStack stream with
     * registry-friendly buffers; bytes must be identical despite private differences.
     * Decoded placeholder contains no run-spec, no private rolls/flags.
     * trustedContentsOf still rejects fabricated filled placeholder.
     * Includes boundary validations for acceptedFoup/acceptsFoupStack.
     */
    private static void privacyWire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Build two SERVER-TRUSTED lots with SAME public view but DIFFERENT private internals
        UUID lotId1 = UUID.randomUUID();
        UUID lotId2 = UUID.randomUUID();
        int capacity = 25;
        int waferCount = 10;
        int layer = 3;

        // Lot 1: rolls around 0.1, no flags, no run spec
        List<Wafer> wafers1 = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers1.add(new Wafer(layer, 0.1f + i * 0.01f, Wafer.NO_FLAGS));
        }
        FoupContents lot1 = new FoupContents(lotId1, capacity, wafers1, null);

        // Lot 2: rolls around 0.9, WITH flags, WITH run spec
        List<Wafer> wafers2 = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers2.add(new Wafer(layer, 0.9f - i * 0.01f, 1)); // flag bit 1
        }
        RunSpec spec = new RunSpec(DieTypes.LOGIC, 1, 0, 0.6, 1); // dieType, tierIndex, cores, effectiveYield, rollCount
        FoupContents lot2 = new FoupContents(lotId2, capacity, wafers2, spec);

        // Verify they have identical public view
        FoupPublicView view1 = lot1.publicView();
        FoupPublicView view2 = lot2.publicView();
        helper.assertValueEqual(view1.capacity(), view2.capacity(), "capacity must match");
        helper.assertValueEqual(view1.waferCount(), view2.waferCount(), "wafer count must match");
        helper.assertValueEqual(view1.lotLayer(), view2.lotLayer(), "lot layer must match");

        // Verify they have different private internals
        helper.assertTrue(!lot1.lotId().equals(lot2.lotId()), "lot ids must differ");
        for (int i = 0; i < waferCount; i++) {
            helper.assertTrue(lot1.wafers().get(i).cumulativeRoll() != lot2.wafers().get(i).cumulativeRoll(),
                "rolls must differ at wafer " + i);
            helper.assertTrue(lot1.wafers().get(i).flags() != lot2.wafers().get(i).flags(),
                "flags must differ at wafer " + i);
        }
        helper.assertTrue(lot1.run() == null && lot2.run() != null, "run spec must differ");

        // --- Test 1: FoupContentsCodec.STREAM_CODEC (network form) ---
        ByteBuf buffer1 = Unpooled.buffer();
        ByteBuf buffer2 = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer1, lot1);
        FoupContentsCodec.STREAM_CODEC.encode(buffer2, lot2);

        int bytes1 = buffer1.readableBytes();
        int bytes2 = buffer2.readableBytes();

        // Bytes on the wire MUST be identical despite private differences
        helper.assertValueEqual(bytes1, bytes2, "network bytes must be identical for same public view");
        byte[] bytesArray1 = new byte[bytes1];
        byte[] bytesArray2 = new byte[bytes2];
        buffer1.readBytes(bytesArray1);
        buffer2.readBytes(bytesArray2);
        helper.assertTrue(java.util.Arrays.equals(bytesArray1, bytesArray2),
            "network byte arrays must be identical for same public view");

        // Decode back - must produce client-view placeholders with NO private data
        FoupContents decoded1 = FoupContentsCodec.STREAM_CODEC.decode(Unpooled.wrappedBuffer(bytesArray1));
        FoupContents decoded2 = FoupContentsCodec.STREAM_CODEC.decode(Unpooled.wrappedBuffer(bytesArray2));

        helper.assertTrue(FoupContentsCodec.isClientView(decoded1), "decoded1 must be client view");
        helper.assertTrue(FoupContentsCodec.isClientView(decoded2), "decoded2 must be client view");
        helper.assertValueEqual(view1.capacity(), decoded1.capacity(), "decoded capacity matches");
        helper.assertValueEqual(view1.waferCount(), decoded1.size(), "decoded wafer count matches");
        helper.assertValueEqual(view1.lotLayer(), decoded1.lotLayer(), "decoded lot layer matches");

        // Decoded placeholders contain NO run spec and NO private rolls/flags
        helper.assertTrue(decoded1.run() == null, "decoded placeholder must have no run spec");
        helper.assertTrue(decoded2.run() == null, "decoded placeholder must have no run spec");
        for (Wafer w : decoded1.wafers()) {
            helper.assertValueEqual(FoupContentsCodec.CLIENT_VIEW_ROLL, w.cumulativeRoll(), "decoded roll must be placeholder");
            helper.assertValueEqual(Wafer.NO_FLAGS, w.flags(), "decoded flags must be placeholder");
        }
        for (Wafer w : decoded2.wafers()) {
            helper.assertValueEqual(FoupContentsCodec.CLIENT_VIEW_ROLL, w.cumulativeRoll(), "decoded roll must be placeholder");
            helper.assertValueEqual(Wafer.NO_FLAGS, w.flags(), "decoded flags must be placeholder");
        }

        // --- Test 2: Registered ItemStack stream with registry-friendly buffers ---
        ItemStack stack1 = FoupItems.with(ModItems.FOUP_MK1.get(), lot1);
        ItemStack stack2 = FoupItems.with(ModItems.FOUP_MK1.get(), lot2);

        // Encode via ItemStack.STREAM_CODEC (registry-friendly)
        RegistryFriendlyByteBuf regBuf1 = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        RegistryFriendlyByteBuf regBuf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ItemStack.STREAM_CODEC.encode(regBuf1, stack1);
        ItemStack.STREAM_CODEC.encode(regBuf2, stack2);

        int regBytes1 = regBuf1.readableBytes();
        int regBytes2 = regBuf2.readableBytes();
        helper.assertValueEqual(regBytes1, regBytes2, "registry-friendly bytes must be identical for same public view");

        byte[] regBytesArray1 = new byte[regBytes1];
        byte[] regBytesArray2 = new byte[regBytes2];
        regBuf1.readBytes(regBytesArray1);
        regBuf2.readBytes(regBytesArray2);
        helper.assertTrue(java.util.Arrays.equals(regBytesArray1, regBytesArray2),
            "registry-friendly byte arrays must be identical for same public view");

        // Decode back
        ItemStack decodedStack1 = ItemStack.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(regBytesArray1), level.registryAccess()));
        ItemStack decodedStack2 = ItemStack.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(regBytesArray2), level.registryAccess()));

        FoupContents decodedStackLot1 = FoupItems.contentsOf(decodedStack1);
        FoupContents decodedStackLot2 = FoupItems.contentsOf(decodedStack2);
        helper.assertTrue(FoupContentsCodec.isClientView(decodedStackLot1), "decoded stack lot must be client view");
        helper.assertTrue(FoupContentsCodec.isClientView(decodedStackLot2), "decoded stack lot must be client view");
        helper.assertTrue(decodedStackLot1.run() == null, "decoded stack lot must have no run spec");
        helper.assertTrue(decodedStackLot2.run() == null, "decoded stack lot must have no run spec");

        // --- Test 3: trustedContentsOf rejects fabricated filled placeholder ---
        // Construct a client-view placeholder that CLAIMS to have wafers but carries a real run spec
        FoupContents fakeFilled = new FoupContents(
            FoupContentsCodec.CLIENT_VIEW_LOT_ID,
            capacity,
            decoded1.wafers(), // placeholder wafers
            lot2.run() // real run spec from lot2
        );
        helper.assertTrue(FoupContentsCodec.isClientView(fakeFilled), "fake must keep client view marker");
        helper.assertTrue(fakeFilled.run() != null, "fake must carry the run spec");

        ItemStack fakeStack = FoupItems.with(ModItems.FOUP_MK1.get(), fakeFilled);
        FoupContents trusted = FoupItems.trustedContentsOf(fakeStack);
        helper.assertTrue(trusted.isEmpty(), "trustedContentsOf must discard client view even with run spec");
        helper.assertTrue(trusted.run() == null, "trusted result must have no run spec");
        helper.assertTrue(!FoupContentsCodec.isClientView(trusted), "trusted result must be sealed lot with fresh id");

        // --- Test 4: Boundary validations for acceptedFoup / acceptsFoupStack ---
        // Absent lot refused without initializing/mutating/logspam
        ItemStack noLot = new ItemStack(ModItems.FOUP_MK1.get());
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(noLot) == null, "absent lot refused by acceptedFoup");
        helper.assertTrue(!WaferSlicerBlockEntity.acceptsFoupStack(noLot), "absent lot refused by acceptsFoupStack");

        helper.assertTrue(FoupItems.contentsOf(noLot) == null, "Absent component remains absent after admission checks");

        // Client view ALWAYS refused by acceptedFoup even empty
        ItemStack emptyClientView = FoupItems.with(ModItems.FOUP_MK1.get(),
            createClientView(capacity, 0, layer));
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(emptyClientView) == null,
            "empty client view refused by acceptedFoup");
        // acceptsFoupStack predicts empty client view acceptable (public wafer count = 0)
        helper.assertTrue(WaferSlicerBlockEntity.acceptsFoupStack(emptyClientView),
            "empty client view predicted acceptable by acceptsFoupStack");

        // Client view with wafers: refused by both
        ItemStack fullClientView = FoupItems.with(ModItems.FOUP_MK1.get(),
            createClientView(capacity, waferCount, layer));
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(fullClientView) == null,
            "full client view refused by acceptedFoup");
        helper.assertTrue(!WaferSlicerBlockEntity.acceptsFoupStack(fullClientView),
            "full client view refused by acceptsFoupStack");

        // Sealed filled / run started refused by both
        ItemStack sealedFilled = FoupItems.with(ModItems.FOUP_MK1.get(), lot1);
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(sealedFilled) == null,
            "sealed filled refused by acceptedFoup");
        helper.assertTrue(!WaferSlicerBlockEntity.acceptsFoupStack(sealedFilled),
            "sealed filled refused by acceptsFoupStack");

        ItemStack sealedWithRun = FoupItems.with(ModItems.FOUP_MK1.get(), lot2);
        helper.assertTrue(WaferSlicerBlockEntity.acceptedFoup(sealedWithRun) == null,
            "sealed with run refused by acceptedFoup");
        helper.assertTrue(!WaferSlicerBlockEntity.acceptsFoupStack(sealedWithRun),
            "sealed with run refused by acceptsFoupStack");

        // Sealed empty accepted with original capacity/id
        FoupContents sealedEmpty = FoupContents.empty(lotId1, capacity);
        ItemStack sealedEmptyStack = FoupItems.with(ModItems.FOUP_MK1.get(), sealedEmpty);
        FoupContents accepted = WaferSlicerBlockEntity.acceptedFoup(sealedEmptyStack);
        helper.assertTrue(accepted != null, "sealed empty accepted by acceptedFoup");
        helper.assertValueEqual(capacity, accepted.capacity(), "accepted keeps original capacity");
        helper.assertValueEqual(lotId1, accepted.lotId(), "accepted keeps original lot id");
        helper.assertTrue(WaferSlicerBlockEntity.acceptsFoupStack(sealedEmptyStack),
            "sealed empty accepted by acceptsFoupStack");

        // Existing inventory/craft initialization remains ONLY absent component
        ItemStack freshFoup = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
        FoupItems.initializeIfMissing(freshFoup);
        FoupContents afterInit = FoupItems.contentsOf(freshFoup);
        helper.assertTrue(afterInit != null && !FoupContentsCodec.isClientView(afterInit),
            "initializeIfMissing produces sealed lot");
        helper.assertValueEqual(FoupItems.configuredCapacity(), afterInit.capacity(), "initialized lot has configured capacity");

        // trustedContentsOf unchanged security semantics
        FoupContents fromTrusted = FoupItems.trustedContentsOf(sealedEmptyStack);
        helper.assertTrue(!FoupContentsCodec.isClientView(fromTrusted), "trustedContentsOf returns sealed lot");
        helper.assertValueEqual(lotId1, fromTrusted.lotId(), "trustedContentsOf preserves lot id");

        auditMenuValues(helper, stack1, stack2);
        LOGGER.info("[M1-BOUNDARY] privacyWire: wire invariance verified, boundary checks passed");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. Tooltip/log privacy test
    // ------------------------------------------------------------------

    /**
     * Case m1_gate_privacy_tooltip_logs:
     * Compare actual public tooltip text produced from two identical public lot views
     * with different private fields. FoupTooltip is package-private, so use actual
     * registered item tooltip/appender pipeline. Capture actual mod logging during
     * trusted read rejection/normal validation and bounded real FOUP/machine lifecycle.
     * Assert no private wafer roll values/serialized Wafer/FoupContents/run-spec content.
     * Diagnostic privileged /debug foup inspect explicitly exempt: test must not call it
     * and declare leak. Pair runtime capture with JUnit source audit.
     */
    private static void privacyTooltipLogs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Build two SERVER-TRUSTED lots with SAME public view but DIFFERENT private internals
        UUID lotId1 = UUID.randomUUID();
        UUID lotId2 = UUID.randomUUID();
        int capacity = 25;
        int waferCount = 10;
        int layer = 3;

        List<Wafer> wafers1 = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers1.add(new Wafer(layer, 0.1f + i * 0.01f, Wafer.NO_FLAGS));
        }
        FoupContents lot1 = new FoupContents(lotId1, capacity, wafers1, null);

        List<Wafer> wafers2 = new ArrayList<>();
        for (int i = 0; i < waferCount; i++) {
            wafers2.add(new Wafer(layer, 0.9f - i * 0.01f, 1)); // flag bit 1
        }
        RunSpec spec = new RunSpec(DieTypes.LOGIC, 1, 0, 0.6, 1); // dieType, tierIndex, cores, effectiveYield, rollCount
        FoupContents lot2 = new FoupContents(lotId2, capacity, wafers2, spec);

        ItemStack stack1 = FoupItems.with(ModItems.FOUP_MK1.get(), lot1);
        ItemStack stack2 = FoupItems.with(ModItems.FOUP_MK1.get(), lot2);

        // --- Capture tooltip via actual registered pipeline ---
        // FoupTooltipAppenders registers a TooltipAppender for FOUP_CONTENTS component
        // The tooltip is built from publicView() only
        List<net.minecraft.network.chat.Component> tooltip1 = new ArrayList<>();
        List<net.minecraft.network.chat.Component> tooltip2 = new ArrayList<>();

        // Use the actual registered item tooltip pipeline (as in MaskGameTests/ChipGameTests)
        tooltip1.addAll(stack1.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));
        tooltip2.addAll(stack2.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));

        // Tooltips must be IDENTICAL because they come from identical public view
        helper.assertTrue(!tooltip1.isEmpty(), "Actual tooltip pipeline must produce text");
        helper.assertValueEqual(tooltip1.size(), tooltip2.size(), "tooltip line count must match");
        for (int i = 0; i < tooltip1.size(); i++) {
            String t1 = tooltip1.get(i).getString();
            String t2 = tooltip2.get(i).getString();
            helper.assertValueEqual(t1, t2, "tooltip line " + i + " must match without exposing private fields");
        }

        // Verify tooltip contains ONLY public values (capacity, wafer count, layer)
        String combinedTooltip = tooltip1.stream().map(c -> c.getString()).collect(Collectors.joining(" "));
        helper.assertTrue(combinedTooltip.contains(String.valueOf(capacity)), "tooltip mentions capacity");
        helper.assertTrue(combinedTooltip.contains(String.valueOf(waferCount)), "tooltip mentions wafer count");
        helper.assertTrue(combinedTooltip.contains(String.valueOf(layer)), "tooltip mentions layer");
        // Must NOT contain private values
        for (Wafer w : wafers1) {
            String rollStr = String.valueOf(w.cumulativeRoll());
            helper.assertTrue(!combinedTooltip.contains(rollStr), "tooltip must not contain a private roll value");
        }
        for (Wafer w : wafers2) {
            String rollStr = String.valueOf(w.cumulativeRoll());
            helper.assertTrue(!combinedTooltip.contains(rollStr), "tooltip must not contain a private roll value");
        }
        helper.assertTrue(!combinedTooltip.contains("FLAG_PROCESSED"), "tooltip must not contain flags");
        helper.assertTrue(!combinedTooltip.contains("RunSpec"), "tooltip must not contain run spec");
        helper.assertTrue(!combinedTooltip.contains(lotId1.toString()), "tooltip must not contain lot id");
        helper.assertTrue(!combinedTooltip.contains(lotId2.toString()), "tooltip must not contain lot id");

        // --- Capture mod logging during trusted read rejection/normal validation ---
        // We'll use a custom appender to capture log output
        org.apache.logging.log4j.Logger log4jLogger = org.apache.logging.log4j.LogManager.getLogger(EUVCRAFTMod.LOGGER.getName());
        List<String> capturedLogs = new ArrayList<>();
        org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) log4jLogger;
        org.apache.logging.log4j.core.Appender capturingAppender = new org.apache.logging.log4j.core.appender.AbstractAppender("M1BoundaryCapture", null, null, true, null) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                String msg = event.getMessage().getFormattedMessage();
                capturedLogs.add(msg);
            }
        };
        capturingAppender.start();
        coreLogger.addAppender(capturingAppender);

        try {
            // Trigger trusted read rejection (client view with wafers)
            ItemStack clientViewWithWafers = FoupItems.with(ModItems.FOUP_MK1.get(),
                createClientView(capacity, waferCount, layer));
            FoupItems.trustedContentsOf(clientViewWithWafers);

            // Trigger trusted read acceptance (sealed empty)
            ItemStack sealedEmpty = FoupItems.with(ModItems.FOUP_MK1.get(), FoupContents.empty(UUID.randomUUID(), capacity));
            FoupItems.trustedContentsOf(sealedEmpty);

            // Trigger normal validation through WaferSlicer
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, SiliconMachines.WAFER_SLICER.get());
            WaferSlicerBlockEntity slicer = (WaferSlicerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(slicer != null, "slicer must exist");

            // Accept empty sealed FOUP - should NOT log warning
            slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, sealedEmpty);
            slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
            slicer.setStoredEnergy(10000);
            slicer.serverTick(); // This calls acceptedFoup

            // Try to feed client view - should be refused without extra logging beyond trustedContentsOf
            slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, clientViewWithWafers);
            slicer.serverTick();

            // Run slicer with valid inputs to exercise full lifecycle
            ItemStack freshFoup = FoupItems.emptyFoup(ModItems.FOUP_MK1.get());
            slicer.setSlot(WaferSlicerBlockEntity.FOUP_SLOT, freshFoup);
            slicer.setSlot(WaferSlicerBlockEntity.BOULE_SLOT, new ItemStack(ModItems.SILICON_BOULE.get()));
            // Run a few ticks
            for (int i = 0; i < 5; i++) {
                slicer.serverTick();
            }

            // --- Assert no private data in captured logs ---
            helper.assertTrue(capturedLogs.stream().anyMatch(line -> line.contains("client view holding")),
                    "Log capture must observe the actual rejected-client-view warning");
            String allLogs = String.join("\n", capturedLogs);
            // Forbid private roll values/structures
            for (Wafer w : wafers1) {
                String rollStr = String.valueOf(w.cumulativeRoll());
                helper.assertTrue(!allLogs.contains(rollStr), "logs must not contain a private roll value");
            }
            for (Wafer w : wafers2) {
                String rollStr = String.valueOf(w.cumulativeRoll());
                helper.assertTrue(!allLogs.contains(rollStr), "logs must not contain a private roll value");
            }
            // Forbid serialized Wafer/FoupContents/run-spec content
            helper.assertTrue(!allLogs.contains("FLAG_PROCESSED"), "logs must not contain flags");
            helper.assertTrue(!allLogs.contains("RunSpec"), "logs must not contain run spec class name");
            helper.assertTrue(!allLogs.contains("cumulativeRoll"), "logs must not contain roll field name");
            helper.assertTrue(!allLogs.contains("flags"), "logs must not contain flags field name");

            // Lot IDs may appear in WARNING lines (that's the diagnostic for empty FOUP)
            // but not the private roll/flag values
            // The word "rolls" generically is allowed, but not values/structures
            // Verify no wafer-by-wafer dump
            helper.assertTrue(!allLogs.matches("(?s).*wafer.*0\\.1.*0\\.9.*"), "logs must not dump multiple roll values");

        } finally {
            capturingAppender.stop();
            coreLogger.removeAppender(capturingAppender);
        }

        // --- Explicit declaration: /debug foup inspect is EXEMPT (diagnostic privileged) ---
        // Test must not call it and must declare this leak
        // The debug command prints wafer-by-wafer roll values to the OPERATOR only
        // This is explicitly permitted as a diagnostic tool behind debugCommands config
        // Our test does NOT invoke /debug foup inspect

        LOGGER.info("[M1-BOUNDARY] privacyTooltipLogs: tooltip identical, logs clean, debug command exempt declared");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. All hand-fed capability enumeration test
    // ------------------------------------------------------------------

    /**
     * Case m1_gate_all_hand_fed:
     * Enumerate ACTUAL registered lithmod blocks from BuiltInRegistries (nonempty coverage).
     * Place each with actual BE, select registered FabMachineBlockEntity and all ProcessCell member types.
     * Assert exact coverage of actual eligible block IDs (independent explicit expected 7 types plus fixture if registered).
     * For EACH Direction.values six and null, query actual Capabilities.Item.BLOCK and Capabilities.Fluid.BLOCK
     * and assert null. Include formed controller/casing/power ports if necessary to avoid only inactive coverage.
     * FE capability allowed; no hopper/item/fluid capabilities.
     * Positive coverage assertions report checked types/sides, no vacuous pass.
     * Keep new tests bounded/config restore finally before h.succeed.
     */
    private static void allHandFed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> expected = new HashSet<>(Set.of("crystal_puller", "wafer_slicer", "pattern_generator",
                "test_package", "process_cell_controller", "process_cell_casing", "process_cell_power_port"));
        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        Identifier fixture = Identifier.parse("lithmod:test_machine");
        if (registry.containsKey(fixture)) expected.add("test_machine");
        Set<String> registered = new HashSet<>();
        Set<String> checked = new HashSet<>();
        BlockPos pos = new BlockPos(1, 1, 1);
        for (var block : registry) {
            Identifier id = registry.getKey(block);
            if (!id.getNamespace().equals("lithmod")) continue;
            registered.add(id.getPath());
            helper.setBlock(pos, Blocks.AIR);
            helper.setBlock(pos, block);
            BlockEntity be = level.getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(be != null, "Every registered M1 block must have a block entity: " + id);
            helper.assertTrue(be instanceof FabMachineBlockEntity
                    || be instanceof com.euvcraft.lithmod.mc.multiblock.ProcessCellCasingBlockEntity
                    || be instanceof com.euvcraft.lithmod.mc.multiblock.ProcessCellPowerPortBlockEntity,
                    "Unexpected registered block type requires explicit audit: " + id);
            for (Direction side : Direction.values()) testCapabilities(helper, be, side);
            testCapabilities(helper, be, null);
            checked.add(id.getPath());
        }
        helper.assertTrue(!registered.isEmpty(), "Registry audit cannot be empty");
        helper.assertTrue(expected.equals(registered), "Actual lithmod blocks must match the independent M1 block inventory");
        helper.assertTrue(registered.equals(checked), "Every actual registered block must be checked");
        helper.setBlock(pos, Blocks.AIR);
        buildFormedProcessCell(helper, pos);
        ProcessCellControllerBlockEntity controller = (ProcessCellControllerBlockEntity)
                level.getBlockEntity(helper.absolutePos(pos));
        controller.revalidate();
        helper.assertTrue(controller.isFormed(), "Capability audit requires a genuinely formed cell");
        Set<String> formedTypes = new HashSet<>();
        int members = 0;
        for (int x=0; x<2; x++) for (int y=0; y<2; y++) for (int z=0; z<3; z++) {
            BlockPos absolute = helper.absolutePos(pos.offset(x,y,z));
            BlockEntity be = level.getBlockEntity(absolute);
            helper.assertTrue(be != null, "Every formed cell member has a block entity");
            for (Direction side : Direction.values()) testCapabilities(helper, be, side);
            testCapabilities(helper, be, null);
            formedTypes.add(registry.getKey(level.getBlockState(absolute).getBlock()).getPath());
            members++;
        }
        helper.assertValueEqual(12, members, "All twelve formed members checked");
        helper.assertTrue(formedTypes.equals(Set.of("process_cell_controller", "process_cell_casing", "process_cell_power_port")),
                "All three formed member types checked");
        LOGGER.info("[M1-BOUNDARY] hand-fed: {} registered blocks on seven sides, plus {} formed members", checked.size(), members);
        helper.succeed();
    }

    private static void buildFormedProcessCell(GameTestHelper helper, BlockPos controllerPos) {
        // Independent explicit 2 wide x 2 tall x 3 long layout, north-facing, 2 back-face ports
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos p = controllerPos.offset(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CONTROLLER.get()
                            .defaultBlockState().setValue(ProcessCellControllerBlock.FACING, Direction.NORTH));
                    } else if (z == 2 && (x == 0 && y == 0 || x == 1 && y == 1)) {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_POWER_PORT.get().defaultBlockState());
                    } else {
                        helper.setBlock(p, ModMultiblocks.PROCESS_CELL_CASING.get().defaultBlockState());
                    }
                }
            }
        }
    }

    private static void testCapabilities(GameTestHelper helper, BlockEntity be, Direction dir) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = be.getBlockPos();
        // Query Item capability using level.getCapability as in ProcessCellGameTests
        var itemCap = level.getCapability(Capabilities.Item.BLOCK, pos, dir);
        helper.assertTrue(itemCap == null,
            be.getClass().getSimpleName() + " side " + dir + " must NOT expose Item capability (hand-fed only)");

        // Query Fluid capability
        var fluidCap = level.getCapability(Capabilities.Fluid.BLOCK, pos, dir);
        helper.assertTrue(fluidCap == null,
            be.getClass().getSimpleName() + " side " + dir + " must NOT expose Fluid capability (hand-fed only)");

        // FE capability is ALLOWED (energy input)
        var energyCap = level.getCapability(Capabilities.Energy.BLOCK, pos, dir);
        // We don't assert null - FE is allowed. Just verify it doesn't crash.
        // The assertion is that item/fluid are NULL, energy MAY be non-null.
    }

    /** Actual registered menus: private input changes cannot add or alter their public data slots. */
    private static void auditMenuValues(GameTestHelper h, ItemStack first, ItemStack second) {
        ServerPlayer player = connect(h.getLevel());
        try {
            BlockPos cellPos = new BlockPos(1, 1, 1);
            h.setBlock(cellPos, ModMultiblocks.PROCESS_CELL_CONTROLLER.get());
            ProcessCellControllerBlockEntity cell = (ProcessCellControllerBlockEntity)
                    h.getLevel().getBlockEntity(h.absolutePos(cellPos));
            cell.setSlot(0, first.copy());
            var cellMenu = new com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu(19, player.getInventory(), cell);
            List<Integer> before = menuValues(cellMenu);
            h.assertValueEqual(12, before.size(), "Process Cell has exactly twelve public data slots");
            List<Integer> expected = new ArrayList<>();
            for (int i=0; i<FabMachineBlockEntity.DATA_COUNT; i++) expected.add(cell.data().get(i));
            expected.add(cell.processState().ordinal());
            expected.add(cell.lastRefusal()==null ? 0 : cell.lastRefusal().code().ordinal()+1);
            expected.add(cell.currentLayer()); expected.add(cell.totalLayers());
            expected.add(cell.isFormed()?1:0); expected.add(cell.tierIndex());
            expected.add(cell.dieType()==null ? 0 : DieTypes.ALL.indexOf(cell.dieType())+1);
            expected.add(cell.inputsLocked()?1:0);
            h.assertTrue(expected.equals(before), "Process menu transmits only the defined public tuple");
            cell.setSlot(0, second.copy());
            h.assertTrue(before.equals(menuValues(cellMenu)), "Private FOUP changes cannot alter Process Cell data slots");

            BlockPos packagePos = new BlockPos(5, 1, 1);
            h.setBlock(packagePos, PackagingMachines.TEST_PACKAGE.get());
            TestPackageBlockEntity packaging = (TestPackageBlockEntity)
                    h.getLevel().getBlockEntity(h.absolutePos(packagePos));
            packaging.setSlot(0, first.copy());
            var packageMenu = new com.euvcraft.lithmod.mc.machine.packaging.TestPackageMenu(20, player.getInventory(), packaging);
            before = menuValues(packageMenu);
            h.assertValueEqual(7, before.size(), "Test and Package has exactly seven public data slots");
            expected.clear();
            for (int i=0; i<FabMachineBlockEntity.DATA_COUNT; i++) expected.add(packaging.data().get(i));
            for (int i=0; i<TestPackageBlockEntity.QUEUE_DATA_COUNT; i++) expected.add(packaging.queueData().get(i));
            h.assertTrue(expected.equals(before), "Packaging menu transmits only the defined public tuple");
            packaging.setSlot(0, second.copy());
            h.assertTrue(before.equals(menuValues(packageMenu)), "Private FOUP changes cannot alter packaging data slots");
        } finally {
            player.closeContainer();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> menuValues(AbstractContainerMenu menu) {
        try {
            Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
            field.setAccessible(true);
            return ((List<DataSlot>)field.get(menu)).stream().map(DataSlot::get).toList();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inspect actual registered menu data slots", e);
        }
    }

    // Connected test player, using the same lifecycle as the existing server menu tests.
    private static ServerPlayer connect(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "boundary-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
            cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getAbilities().instabuild = true;
        return player;
    }
}
