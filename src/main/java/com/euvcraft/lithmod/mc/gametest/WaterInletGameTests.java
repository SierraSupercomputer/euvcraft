package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.mc.multiblock.support.*;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.ConduitCapabilities;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Real registered native-capability, transactional and Arcforge conduit interoperability tests. */
public final class WaterInletGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod");
    static {
        FUNCTIONS.register("water_inlet_geometry", () -> WaterInletGameTests::geometry);
        FUNCTIONS.register("water_inlet_transactions", () -> WaterInletGameTests::transactions);
        FUNCTIONS.register("water_inlet_lifecycle", () -> WaterInletGameTests::lifecycle);
        FUNCTIONS.register("water_inlet_process_reload", () -> WaterInletGameTests::processReload);
        FUNCTIONS.register("water_inlet_arcforge_pipe", () -> WaterInletGameTests::arcforgePipe);
    }
    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((Consumer<RegisterGameTestsEvent>) event -> {
            Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Identifier.parse("lithmod:water_inlet"), new TestEnvironmentDefinition[0]);
            var data = new TestData<>(env, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"), 300, 0, true, Rotation.NONE, false, 1, 1, false, 0);
            register(event, data, "water_inlet_geometry");
            register(event, data, "water_inlet_transactions");
            register(event, data, "water_inlet_lifecycle");
            register(event, data, "water_inlet_process_reload");
            register(event, data, "water_inlet_arcforge_pipe");
        });
    }
    private static void register(RegisterGameTestsEvent event, TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        var id = Identifier.fromNamespaceAndPath("lithmod", name);
        var key = ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(key, data));
    }
    private static List<BlockPos> cells(BlockPos controller, Direction facing, int column) {
        // Independent literal basis; no production matcher is used to generate the fixture.
        int rx = switch(facing) {case NORTH -> 1; case SOUTH -> -1; default -> 0;};
        int rz = switch(facing) {case EAST -> 1; case WEST -> -1; default -> 0;};
        int bx = switch(facing) {case EAST -> -1; case WEST -> 1; default -> 0;};
        int bz = switch(facing) {case NORTH -> 1; case SOUTH -> -1; default -> 0;};
        var result = new ArrayList<BlockPos>();
        for(int u=0;u<2;u++) for(int v=0;v<2;v++) for(int d=0;d<3;d++)
            result.add(controller.offset(rx*(u-column)+bx*d, v, rz*(u-column)+bz*d));
        return result;
    }
    private record Cell(ServerLevel level, List<BlockPos> positions, SupportMachineBlockEntity owner) implements AutoCloseable {
        BlockPos inlet() { return positions.get(4); }
        @Override public void close() {
            for(int i=0;i<owner.slotCount();i++) owner.clearSlot(i);
            for(var pos:positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static Cell build(GameTestHelper h, Direction facing, int column, boolean ebeam) {
        var level=h.getLevel(); var anchor=h.absolutePos(new BlockPos(3,2,3)); var positions=cells(anchor,facing,column);
        for(var pos:positions) level.setBlock(pos,SupportMachines.SUPPORT_CASING.get().defaultBlockState(),3);
        level.setBlock(positions.get(2),SupportMachines.SUPPORT_POWER_PORT.get().defaultBlockState(),3);
        var block=ebeam?SupportMachines.E_BEAM_MASK_WRITER.get():SupportMachines.BOULE_PURIFIER.get();
        level.setBlock(anchor,block.defaultBlockState().setValue(SupportMachineControllerBlock.FACING,facing),3);
        var owner=(SupportMachineBlockEntity)level.getBlockEntity(anchor); owner.revalidate();
        h.assertTrue(owner.isFormed(),"baseline bucket-only cell forms");
        return new Cell(level,positions,owner);
    }
    private static void inlet(Cell c, int index) { c.level.setBlock(c.positions.get(index),SupportMachines.SUPPORT_WATER_INLET.get().defaultBlockState(),3); c.owner.revalidate(); }
    private static ResourceHandler<FluidResource> cap(GameTestHelper h, Cell c, BlockPos pos, Direction side) {
        var result=c.level.getCapability(Capabilities.Fluid.BLOCK,pos,side); h.assertTrue(result!=null,"formed inlet exposes fluid capability on "+side);return result;
    }
    private static int insert(ResourceHandler<FluidResource> handler, int amount, boolean commit) {
        try(var tx=Transaction.openRoot()) { int accepted=handler.insert(0,FluidResource.of(Fluids.WATER),amount,tx);if(commit)tx.commit();return accepted; }
    }
    private static void geometry(GameTestHelper h) {
        int checked=0;
        for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) for(int column=0;column<2;column++) {
            try(var c=build(h,facing,column,false)) {
                for(int i=0;i<12;i++) {
                    if(i==column*6||i==2) continue;
                    inlet(c,i); h.assertTrue(c.owner.isFormed(),"inlet at casing "+i+" forms for "+facing+" column "+column);
                    for(var side:Direction.values()) {
                        var handler=cap(h,c,c.positions.get(i),side);
                        h.assertValueEqual(1,insert(handler,1,false),"all-face insertion simulates");
                        h.assertTrue(c.level.getCapability(Capabilities.Energy.BLOCK,c.positions.get(i),side)==null,"inlet never exposes FE");
                        h.assertTrue(c.level.getCapability(Capabilities.Item.BLOCK,c.positions.get(i),side)==null,"no item automation");
                    }
                    cap(h,c,c.positions.get(i),null); h.assertValueEqual(0,c.owner.waterAmount(),"simulation rolls back");checked++;
                    c.level.setBlock(c.positions.get(i),SupportMachines.SUPPORT_CASING.get().defaultBlockState(),3);c.owner.revalidate();
                }
            }
        }
        h.assertValueEqual(80,checked,"all ten casing positions across four facings/two columns");
        try(var c=build(h,Direction.NORTH,0,true)) {inlet(c,4);h.assertTrue(!c.owner.isFormed(),"E-beam rejects purifier inlet");h.assertTrue(c.level.getCapability(Capabilities.Fluid.BLOCK,c.inlet(),null)==null,"no fluid on invalid E-beam");}
        h.succeed();
    }
    private static void transactions(GameTestHelper h) {
        try(var c=build(h,Direction.NORTH,0,false)) {
            inlet(c,4);inlet(c,10);var a=cap(h,c,c.inlet(),Direction.UP);var b=cap(h,c,c.positions.get(10),Direction.DOWN);var water=FluidResource.of(Fluids.WATER);
            try(var outer=Transaction.openRoot()) {
                h.assertValueEqual(900,a.insert(0,water,900,outer),"outer insertion");
                try(var inner=Transaction.open(outer)) {h.assertValueEqual(600,b.insert(0,water,600,inner),"second inlet nested insertion");}
                h.assertValueEqual(900,c.owner.waterAmount(),"inner rollback shared journal");
                try(var inner=Transaction.open(outer)) {b.insert(0,water,700,inner);inner.commit();}
                h.assertValueEqual(1600,c.owner.waterAmount(),"inner commit visible to outer");
            }
            h.assertValueEqual(0,c.owner.waterAmount(),"parent abort restores all inlets");
            try(var tx=Transaction.openRoot()) {
                h.assertValueEqual(0,a.insert(0,FluidResource.of(Fluids.LAVA),500,tx),"lava rejected");
                h.assertValueEqual(0,a.insert(0,FluidResource.of(Fluids.FLOWING_WATER),500,tx),"flowing resource rejected");
                h.assertValueEqual(0,a.extract(0,water,500,tx),"extraction prohibited");tx.commit();
            }
            h.assertTrue(!a.isValid(0,FluidResource.of(Fluids.LAVA)),"lava invalid");
            h.assertValueEqual(0,a.getCapacityAsInt(0,FluidResource.of(Fluids.LAVA)),"lava capacity zero");
            int capacity=c.owner.waterCapacity(); h.assertValueEqual(capacity-123,insert(a,capacity-123,true),"partial initial fill");
            h.assertValueEqual(123,insert(b,500,true),"remaining capacity exact");h.assertValueEqual(0,insert(a,1,true),"full tank rejects");
            h.assertValueEqual(capacity,a.getAmountAsInt(0),"both lenses share tank");
            c.owner.waterTank().resize(100);h.assertValueEqual(capacity,c.owner.waterAmount(),"capacity shrink preserves water");h.assertValueEqual(0,insert(a,1,true),"overfull rejects");
        } h.succeed();
    }
    private static void lifecycle(GameTestHelper h) {
        try(var c=build(h,Direction.NORTH,0,false)) {
            // Query while unformed, then verify cache invalidation on attaching/reforming.
            c.level.setBlock(c.positions.get(5),Blocks.AIR.defaultBlockState(),3);inlet(c,4);
            var cache=BlockCapabilityCache.create(Capabilities.Fluid.BLOCK,c.level,c.inlet(),Direction.UP);
            h.assertTrue(cache.getCapability()==null,"cache starts without capability");
            c.level.setBlock(c.positions.get(5),SupportMachines.SUPPORT_CASING.get().defaultBlockState(),3);c.owner.revalidate();
            var retained=cache.getCapability();h.assertTrue(retained!=null,"formation invalidates missing capability cache");insert(retained,300,true);
            c.level.setBlock(c.positions.get(5),Blocks.AIR.defaultBlockState(),2); // no neighbour notification
            h.assertValueEqual(0,insert(retained,400,true),"retained lens rechecks actual broken structure");
            h.assertValueEqual(300,c.owner.waterAmount(),"broken structure cannot receive ghost water");h.assertTrue(cache.getCapability()==null,"unforming invalidates populated cache");
            c.level.setBlock(c.positions.get(5),SupportMachines.SUPPORT_CASING.get().defaultBlockState(),3);c.owner.revalidate();
            var beforeReplacement=cache.getCapability();h.assertTrue(beforeReplacement!=null,"cache reforms");
            c.level.setBlock(c.inlet(),Blocks.AIR.defaultBlockState(),3);
            h.assertValueEqual(0,insert(beforeReplacement,600,true),"removed member retained lens rejects");
            inlet(c,4);h.assertValueEqual(0,insert(beforeReplacement,600,true),"old member lens cannot follow replacement");
            h.assertValueEqual(200,insert(cap(h,c,c.inlet(),null),200,true),"new member lens works");
        } h.succeed();
    }
    private static void processReload(GameTestHelper h) {
        try(var c=build(h,Direction.NORTH,0,false)) {
            inlet(c,4);var handler=cap(h,c,c.inlet(),null);
            var recipe=SupportRecipeLoader.getRegistry().firstFor("boule_purifier").orElseThrow().recipe();
            insert(handler,recipe.waterMb(),true);c.owner.setSlot(1,new ItemStack(Items.WATER_BUCKET));c.owner.serverTick();
            h.assertValueEqual(recipe.waterMb()+1000,c.owner.waterAmount(),"pipe water and bucket share tank");
            h.assertTrue(c.owner.slot(2).is(Items.BUCKET),"bucket output preserved");
            c.owner.setSlot(0,new ItemStack(ModItems.SILICON_BOULE.get(),recipe.input().count()));
            c.owner.setStoredEnergy(recipe.energyPerTick()*(recipe.duration()+2));
            for(int i=0;i<recipe.duration()+2;i++)c.owner.serverTick();
            h.assertTrue(c.owner.slot(3).is(ModItems.PURE_SILICON_BOULE.get()),"pipe water produces pure boule");h.assertValueEqual(1000,c.owner.waterAmount(),"exact JSON water consumed");
            var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,c.level.registryAccess());c.owner.saveWithId(saved);
            var fresh=SupportMachines.SUPPORT_CONTROLLER_BE.get().create(c.owner.getBlockPos(),c.owner.getBlockState());
            c.level.removeBlockEntity(c.owner.getBlockPos());c.level.setBlockEntity(fresh);
            fresh.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,c.level.registryAccess(),saved.buildResult()));fresh.revalidate();
            h.assertValueEqual(1000,fresh.waterAmount(),"water survives BE reload");h.assertValueEqual(0,insert(handler,100,true),"old owner lens fails after replacement");
            h.assertValueEqual(500,insert(cap(h,c,c.inlet(),null),500,true),"reloaded owner receives water");h.assertValueEqual(1500,fresh.waterAmount(),"new shared tank amount");
            for(int i=0;i<fresh.slotCount();i++)fresh.clearSlot(i);
        } h.succeed();
    }
    private static void arcforgePipe(GameTestHelper h) {
        try(var c=build(h,Direction.NORTH,0,false)) {
            inlet(c,4);var pipePos=c.inlet().above();var tankPos=pipePos.above();
            var pipe=BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_fluid_conduit"));
            var tank=BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_fluid_tank"));
            h.assertTrue(pipe!=null&&pipe!=Blocks.AIR&&tank!=null&&tank!=Blocks.AIR,"real Arcforge tank/conduit registered");
            c.level.setBlock(tankPos,tank.defaultBlockState(),3);c.level.setBlock(pipePos,pipe.defaultBlockState(),3);
            try {
                var pipeBE=(ConduitBlockEntity)c.level.getBlockEntity(pipePos);
                for(var side:Direction.values())pipeBE.setSetting(side,SideSetting.DISABLED);
                pipeBE.setSetting(Direction.UP,SideSetting.OUTPUT);pipeBE.setSetting(Direction.DOWN,SideSetting.INPUT);
                net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(c.level,pipePos);
                var source=c.level.getCapability(Capabilities.Fluid.BLOCK,tankPos,Direction.UP);h.assertTrue(source!=null,"real tank input capability");
                h.assertValueEqual(1000,insert(source,1000,true),"fill actual Arcforge source tank");
                h.assertTrue(ConduitCapabilities.canConnect(c.level,pipePos,Direction.DOWN,ConduitType.FLUID),"Arcforge discovers inlet capability");
                var manager=ConduitNetworkManager.get(c.level);manager.markDirty(pipePos);
                for(int i=0;i<80;i++)manager.tick();
                h.assertValueEqual(1000,c.owner.waterAmount(),"actual Arcforge conduit pumps water into purifier");
                h.assertValueEqual(0,source.getAmountAsInt(0),"source debited exactly; no fluid duplication");
            } finally {c.level.setBlock(pipePos,Blocks.AIR.defaultBlockState(),3);c.level.setBlock(tankPos,Blocks.AIR.defaultBlockState(),3);}
        } h.succeed();
    }
}
