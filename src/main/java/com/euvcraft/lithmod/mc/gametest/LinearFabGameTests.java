package com.euvcraft.lithmod.mc.gametest;

import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.FabProgress;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.multiblock.linear.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.routing.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;

/** Native evidence for stage ownership, frozen jobs and the actual Arcforge item network. */
public final class LinearFabGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
    private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
    static {
        add("linear_t4_etch_reload",LinearFabGameTests::etchReload);
        add("linear_t4_etch_refusals",LinearFabGameTests::etchRefusals);
        add("linear_t4_item_port_transactions",LinearFabGameTests::transactions);
        add("linear_t4_arcforge_items",LinearFabGameTests::arcforge);
    }
    private static void add(String name,Consumer<GameTestHelper> body){CASES.put(name,body);FUNCTIONS.register(name,()->body);}
    public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{
        for(var name:CASES.keySet()){
            var id=Identifier.fromNamespaceAndPath("lithmod",name);
            var env=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);
            var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),500,0,true,Rotation.NONE,false,1,1,false,0);
            event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));
        }
    });}
    private static ItemStack exposed(){
        var stack=complete(4,DieTypes.LOGIC,0);
        stack.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(new FabProgress(FabProgress.Stage.EXPOSED,4,1),DieTypes.LOGIC,0,false));
        FoupRouting.refresh(stack);return stack;
    }
    private static void etchReload(GameTestHelper h){
        try(var c=build(h,Kind.ETCH)){
            var m=(EtchCellBlockEntity)c.machine();var input=exposed();var body=FoupItems.trustedContentsOf(input);
            m.setSlot(0,input);m.setSlot(1,new ItemStack(ModItems.ETCHANT_CHARGE.get(),2));replenish(m);m.serverTick();
            check(h,m.hasJob()&&m.slot(0).isEmpty(),"Accepted FOUP is owned by the frozen job");eq(h,1,m.slot(1).getCount(),"Exactly one charge paid");
            m.setStoredEnergy(0);int before=m.jobProgress();for(int i=0;i<30;i++)m.serverTick();eq(h,before,m.jobProgress(),"No power pauses etching");
            replenish(m);for(int i=0;i<20;i++)m.serverTick();int paid=m.jobProgress();var saved=save(c.level(),m);
            c.level().removeBlockEntity(c.anchor());var reloaded=new EtchCellBlockEntity(c.anchor(),c.level().getBlockState(c.anchor()));c.level().setBlockEntity(reloaded);load(c.level(),reloaded,saved);reloaded.revalidate();
            eq(h,paid,reloaded.jobProgress(),"Progress survives actual block-entity reload");runPreparation(h,reloaded);
            check(h,body.equals(FoupItems.trustedContentsOf(reloaded.slot(2))),"Etch preserves all private rolls, identity and frozen exposure evidence");
            check(h,reloaded.slot(2).get(ModDataComponents.FOUP_ROUTING.get()).progress().stage()==FabProgress.Stage.ETCHED,"Only completion advances stage");
            eq(h,1,reloaded.slot(1).getCount(),"Reload never pays twice");check(h,FoupRouting.packagingReady(reloaded.slot(2)),"Etched T4 is eligible for packaging");
        }h.succeed();
    }
    private static void etchRefusals(GameTestHelper h){
        try(var c=build(h,Kind.ETCH)){
            var m=(EtchCellBlockEntity)c.machine();var withoutProof=exposed();withoutProof.remove(ModDataComponents.FOUP_ROUTING.get());
            for(var input:List.of(fresh(2,WaferSubstrate.PURE),complete(4,DieTypes.LOGIC,0),withoutProof,complete(3,DieTypes.LOGIC,0))){
                m.setSlot(0,input.copy());m.setSlot(1,new ItemStack(ModItems.ETCHANT_CHARGE.get(),2));replenish(m);long energy=m.buffer().getAmountAsLong();
                for(int i=0;i<5;i++)m.serverTick();check(h,!m.hasJob()&&m.slot(2).isEmpty(),"Wrong stage or missing proof cannot etch");eq(h,2,m.slot(1).getCount(),"Refusal retains charge");eq(h,energy,m.buffer().getAmountAsLong(),"Refusal retains FE");
            }
            m.setSlot(0,exposed());m.setSlot(1,new ItemStack(ModItems.SILICON.get(),16));m.serverTick();check(h,!m.hasJob(),"Raw silicon is not etchant");
        }h.succeed();
    }
    private static BlockPos port(Cell c,int offset){
        var pos=c.anchor().east(offset);c.level().setBlock(pos,LinearMachines.ITEM_PORT.get().defaultBlockState(),3);((AdvancedOwner)c.machine()).revalidate();return pos;
    }
    private static void transactions(GameTestHelper h){
        try(var c=build(h,Kind.ETCH)){
            var m=(EtchCellBlockEntity)c.machine();var p=port(c,1);var q=port(c,2);
            var a=c.level().getCapability(Capabilities.Item.BLOCK,p,Direction.NORTH);var b=c.level().getCapability(Capabilities.Item.BLOCK,q,(Direction)null);
            check(h,a!=null&&b!=null,"All faces and unsided lookup expose formed ports");var carrier=ItemResource.of(exposed());var charge=ItemResource.of(ModItems.ETCHANT_CHARGE.get());
            try(var tx=Transaction.openRoot()){eq(h,1,a.insert(0,carrier,1,tx),"FOUP insertion");eq(h,1,b.getAmountAsLong(0),"Multiple ports share the same transaction journal");eq(h,0,b.extract(0,carrier,1,tx),"Input FOUP cannot be stolen");eq(h,0,a.insert(1,ItemResource.of(Items.DIAMOND),1,tx),"Wrong reagent rejected");}
            check(h,m.slot(0).isEmpty(),"Uncommitted insertion rolls back");
            try(var tx=Transaction.openRoot()){a.insert(0,carrier,1,tx);b.insert(1,charge,1,tx);tx.commit();}
            runPreparation(h,m);var output=ItemResource.of(m.slot(2));
            try(var tx=Transaction.openRoot()){eq(h,1,b.extract(2,output,1,tx),"Finished output can be extracted");}eq(h,1,a.getAmountAsLong(2),"Uncommitted output extraction rolls back");
            c.level().setBlock(p,Blocks.AIR.defaultBlockState(),3);eq(h,0,a.size(),"Removed port invalidates retained handler");((AdvancedOwner)m).revalidate();eq(h,0,b.size(),"Broken shell invalidates other retained handler");
        }h.succeed();
    }
    private static void configure(Cell c,BlockPos pipe,boolean reverse){
        var conduit=(ConduitBlockEntity)c.level().getBlockEntity(pipe);for(var side:Direction.values())conduit.setSetting(side,SideSetting.DISABLED);
        conduit.setSetting(Direction.NORTH,reverse?SideSetting.INPUT:SideSetting.OUTPUT);conduit.setSetting(Direction.SOUTH,reverse?SideSetting.OUTPUT:SideSetting.INPUT);
        net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(c.level(),pipe);ConduitNetworkManager.get(c.level()).markDirty(pipe);
    }
    private static void arcforge(GameTestHelper h){
        var c=build(h,Kind.ETCH);var m=(EtchCellBlockEntity)c.machine();var p=port(c,1);var pipe=p.north();var chest=p.north(2);
        var conduit=BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_item_conduit"));check(h,conduit!=Blocks.AIR,"Real Arcforge conduit registered");
        c.level().setBlock(chest,Blocks.CHEST.defaultBlockState(),3);c.level().setBlock(pipe,conduit.defaultBlockState(),3);
        var source=(Container)c.level().getBlockEntity(chest);var first=exposed();var second=exposed();var ids=Set.of(FoupItems.trustedContentsOf(first).lotId(),FoupItems.trustedContentsOf(second).lotId());
        source.setItem(0,first);source.setItem(1,second);source.setItem(2,new ItemStack(ModItems.ETCHANT_CHARGE.get(),2));configure(c,pipe,false);
        h.runAfterDelay(100,()->{
            check(h,!m.slot(0).isEmpty()||m.hasJob(),"Actual Arcforge network delivers FOUP");runPreparation(h,m);configure(c,pipe,true);
        });
        h.runAfterDelay(200,()->{
            check(h,m.slot(2).isEmpty(),"Actual network retrieves completed output even with a queued input");
            // Resume pulling remaining feedstock if the second carrier was not queued during the first transfer.
            configure(c,pipe,false);
        });
        h.runAfterDelay(300,()->{runPreparation(h,m);configure(c,pipe,true);});
        h.runAfterDelay(400,()->{
            try{
                var found=new HashSet<UUID>();int carriers=0;for(int i=0;i<source.getContainerSize();i++)if(FoupItems.isFoup(source.getItem(i))){
                    var stack=source.getItem(i);carriers++;found.add(FoupItems.trustedContentsOf(stack).lotId());check(h,stack.get(ModDataComponents.FOUP_ROUTING.get()).progress().stage()==FabProgress.Stage.ETCHED,"Every returned lot completed etching");
                }
                eq(h,2,carriers,"Two sequential lots returned without duplication");check(h,found.equals(ids),"Both original private lot identities survive actual transport");check(h,m.slot(0).isEmpty()&&m.slot(1).isEmpty()&&m.slot(2).isEmpty()&&!m.hasJob(),"Exactly two charges consumed and no stranded machine items");
                var pipeEntity=(ConduitBlockEntity)c.level().getBlockEntity(pipe);eq(h,0,pipeEntity.getStoredItems().size()+pipeEntity.getPackets().size(),"No in-flight leftovers");
            }finally{source.clearContent();c.level().setBlock(chest,Blocks.AIR.defaultBlockState(),3);c.level().setBlock(pipe,Blocks.AIR.defaultBlockState(),3);c.close();}h.succeed();
        });
    }
    private LinearFabGameTests(){}
}
