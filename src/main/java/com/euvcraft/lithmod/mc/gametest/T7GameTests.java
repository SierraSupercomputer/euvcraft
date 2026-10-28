package com.euvcraft.lithmod.mc.gametest;

import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.T7Content;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.cleaner.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native registry and resource checks. Full production transitions have separate cases. */
public final class T7GameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
    private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
    static {
        add("block_entity_bindings",T7GameTests::blockEntities);
        add("large_visual_state_space",T7GameTests::visualStates);
        add("product_and_reticle_registry",T7GameTests::products);
        add("contaminated_cleaner_reload",T7GameTests::contaminatedCleaner);
    }
    private T7GameTests() {}
    private static void add(String suffix,Consumer<GameTestHelper> test) {
        String id="t7_"+suffix;CASES.put(id,test);FUNCTIONS.register(id,()->test);
    }
    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((Consumer<RegisterGameTestsEvent>)event->{
            Holder<TestEnvironmentDefinition<?>> environment=event.registerEnvironment(Identifier.parse("lithmod:t7_runtime"),new TestEnvironmentDefinition[0]);
            var data=new TestData<>(environment,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),400,0,true,Rotation.NONE,false,1,1,false,0);
            for(String name:CASES.keySet()){
                var id=Identifier.fromNamespaceAndPath("lithmod",name);
                event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));
            }
        });
    }
    private static void blockEntities(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));
        check(h,T7Content.blockItems().size()==45,"Every T7 block has a registered item");
        for(var item:T7Content.blockItems()) {
            var id=BuiltInRegistries.ITEM.getKey(item);
            var block=BuiltInRegistries.BLOCK.getValue(id);
            check(h,block!=Blocks.AIR,"Physical block registered: "+id);
            h.getLevel().setBlock(pos,block.defaultBlockState(),3);
            var entity=h.getLevel().getBlockEntity(pos);
            check(h,entity!=null,"Actual block entity factory: "+id);
            check(h,entity.getType().isValid(block.defaultBlockState()),"Factory is bound to exact member block: "+id);
            h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    private static void visualStates(GameTestHelper h) {
        for(String id:List.of("g3_deposition_etcher","g3_casing","g3_power_port","euv_scanner_controller","euv_scanner_casing","euv_scanner_exposure_module")) {
            var block=BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("lithmod",id));
            var property=block.getStateDefinition().getProperty("part");
            check(h,property instanceof IntegerProperty,"Large machine part property: "+id);
            var part=(IntegerProperty)property;
            check(h,part.getPossibleValues().contains(174),"175th cell can form without throwing: "+id);
            check(h,block.defaultBlockState().setValue(part,174).getValue(part)==174,"Native property accepts last cell: "+id);
        }
        h.succeed();
    }
    private static void products(GameTestHelper h) {
        for(var die:List.of(DieTypes.COMPUTE,DieTypes.MEMORY,DieTypes.NAND))check(h,ChipCatalog.chipFor(die,7).isPresent(),"T7 output catalog: "+die);
        check(h,ChipCatalog.chipFor(DieTypes.LOGIC,7).isEmpty(),"Logic remains capped at T5");
        var registry=SupportRecipeLoader.getRegistry();
        check(h,registry!=null,"Support recipe reload completed");
        for(String family:List.of("compute","memory","nand"))check(h,registry.byId(Identifier.parse("lithmod:g3_"+family+"_t7")).isPresent(),"G3 reticle recipe loaded: "+family);
        h.succeed();
    }
    private static void contaminatedCleaner(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlock(pos,FoupCleanerMachines.BLOCK.get().defaultBlockState(),3);
        var cleaner=(FoupCleanerBlockEntity)level.getBlockEntity(pos);
        try {
            var lot=FoupContents.of(UUID.randomUUID(),25,List.of(Wafer.unprocessed(),Wafer.unprocessed(),Wafer.unprocessed()),WaferSubstrate.ELECTRONIC_GRADE).preparedG3(800,new int[]{1000,800,600}).contaminated();
            var carrier=FoupItems.with(ModItems.FOUP_MK2.get(),lot);
            carrier.set(DataComponents.CUSTOM_NAME,Component.literal("Contaminated Mk2 carrier"));
            cleaner.serverTick();cleaner.setStoredEnergy(40000);cleaner.water.fill(16000,false);
            cleaner.setSlot(0,carrier);check(h,cleaner.start(),"Contaminated G3 lot accepts ordinary-water disposal");
            for(int i=0;i<80;i++)cleaner.serverTick();
            var tag=save(level,cleaner);level.removeBlockEntity(pos);
            cleaner=new FoupCleanerBlockEntity(pos,level.getBlockState(pos));level.setBlockEntity(cleaner);load(level,cleaner,tag);
            check(h,FoupItems.trustedContentsOf(cleaner.slot(0)).isContaminated(),"Contamination survives active cleaner reload");
            for(int i=0;i<120;i++)cleaner.serverTick();
            var result=cleaner.slot(2);var empty=FoupItems.trustedContentsOf(result);
            check(h,result.is(ModItems.FOUP_MK2.get())&&empty.isEmpty()&&!empty.isContaminated(),"Clean Mk2 carrier returned");
            check(h,Component.literal("Contaminated Mk2 carrier").equals(result.get(DataComponents.CUSTOM_NAME)),"Custom carrier metadata preserved");
            check(h,cleaner.slot(3).is(FoupCleanerMachines.WAFER_SCRAP.get())&&cleaner.slot(3).getCount()==3,"Exactly one scrap per contaminated wafer");
            check(h,cleaner.water.amount()==15000&&cleaner.buffer().getAmountAsLong()==36000,"One normal water batch and 200 paid FE ticks");
        } finally {
            cleaner.clearContent();level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
}
