package com.euvcraft.lithmod.mc.gametest;

import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.recycler.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Actual registered blocks, menus, saved runners and Arcforge networks. */
public final class RecyclerGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
    private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
    static {add("recipes",RecyclerGameTests::recipes);add("pause_reload",RecyclerGameTests::pauseReload);add("transfer",RecyclerGameTests::transfer);add("break",RecyclerGameTests::breaking);add("arcforge",RecyclerGameTests::arcforge);}
    private static void add(String name,Consumer<GameTestHelper> fn){name="recycler_"+name;CASES.put(name,fn);FUNCTIONS.register(name,()->fn);}
    public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{for(var name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);var env=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),1200,0,true,Rotation.NONE,false,1,1,false,0);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
    private static final AtomicInteger NEXT=new AtomicInteger();
    private static final class Arena extends T7RuntimeFixtures.Arena {
        final int offset=294+3*NEXT.getAndIncrement();
        Arena(GameTestHelper h){super(h);if(offset>312)throw new IllegalStateException("Recycler fixture capacity");}
        @Override BlockPos anchor(){return super.anchor().above(offset);}
        ScrapRecyclerBlockEntity machine(){put(anchor(),RecyclerMachines.BLOCK.get());return (ScrapRecyclerBlockEntity)level.getBlockEntity(anchor());}
    }
    private static void ticks(ScrapRecyclerBlockEntity m,int count){for(int i=0;i<count;i++)m.serverTick();}
    private static void recipes(GameTestHelper h){try(var a=new Arena(h)){
        var m=a.machine();for(var scrap:List.of(item("wafer_scrap"),ModItems.CHIP_SCRAP.get(),item("assembly_scrap"))){
            m.setSlot(0,new ItemStack(scrap,64));m.setStoredEnergy(40000);ticks(m,399);check(h,m.slot(1).isEmpty(),"No early recovery");ticks(m,1);
            check(h,m.slot(0).isEmpty()&&m.slot(1).is(ModItems.SILICON.get()),"Exact scrap consumed and Silicon recovered");eq(h,1,m.slot(1).getCount(),"Exactly one recovery");eq(h,24000,m.buffer().getAmountAsInt(),"400 paid ticks at40FE");m.clearSlot(1);
        }
    }h.succeed();}
    private static void pauseReload(GameTestHelper h){try(var a=new Arena(h)){
        var m=a.machine();m.setStoredEnergy(40000);m.setSlot(0,new ItemStack(item("wafer_scrap"),63));ticks(m,10);eq(h,40000,m.buffer().getAmountAsInt(),"Partial batch costs nothing");
        m.setSlot(0,new ItemStack(item("wafer_scrap"),64));ticks(m,120);var tag=save(a.level,m);a.level.removeBlockEntity(a.anchor());var restored=new ScrapRecyclerBlockEntity(a.anchor(),a.level.getBlockState(a.anchor()));a.level.setBlockEntity(restored);load(a.level,restored,tag);m=restored;
        eq(h,120,m.runner().progress(),"Paid progress restored");m.setStoredEnergy(0);ticks(m,20);eq(h,120,m.runner().progress(),"Power loss pauses");
        m.setStoredEnergy(35200);m.setSlot(1,new ItemStack(ModItems.SILICON.get(),64));ticks(m,20);eq(h,120,m.runner().progress(),"Full output pauses");eq(h,35200,m.buffer().getAmountAsInt(),"Blocked output costs no FE");m.clearSlot(1);ticks(m,280);eq(h,1,m.slot(1).getCount(),"Resumes exactly once");eq(h,24000,m.buffer().getAmountAsInt(),"No progress replay");
    }h.succeed();}
    private static void transfer(GameTestHelper h){try(var a=new Arena(h)){
        var m=a.machine();var cap=m.items();var resource=ItemResource.of(item("wafer_scrap"));
        for(var side:Direction.values())check(h,a.level.getCapability(Capabilities.Item.BLOCK,a.anchor(),side)==cap,"Same guarded handler on all faces");
        try(var tx=Transaction.openRoot()){eq(h,64,cap.insert(0,resource,64,tx),"Accept loaded recipe input");eq(h,0,cap.extract(0,resource,64,tx),"Queued inputs protected");}
        check(h,m.slot(0).isEmpty(),"Aborted insert rolls back");
        try(var tx=Transaction.openRoot()){eq(h,0,cap.insert(0,ItemResource.of(Items.DIRT),64,tx),"Reject nonrecipe");eq(h,64,cap.insert(0,resource,64,tx),"Commit batch");tx.commit();}
        m.setStoredEnergy(40000);ticks(m,1);check(h,!m.acceptsInput(new ItemStack(ModItems.CHIP_SCRAP.get())),"Active recipe locks input");var player=T7RuntimeFixtures.player(h,a.anchor());var menu=new ScrapRecyclerMenu(0,player.getInventory(),m);eq(h,38,menu.slots.size(),"Two machine and36 player slots");check(h,!menu.slots.get(0).mayPickup(player),"Active input cannot be picked up");check(h,!menu.slots.get(1).mayPlace(new ItemStack(ModItems.SILICON.get())),"Output cannot accept items");
        ticks(m,399);try(var tx=Transaction.openRoot()){eq(h,1,cap.extract(1,ItemResource.of(ModItems.SILICON.get()),1,tx),"Extract result");}eq(h,1,m.slot(1).getCount(),"Aborted extraction restores output");
        a.put(a.anchor(),Blocks.AIR);eq(h,0,cap.size(),"Cached capability rejects detached owner");
    }h.succeed();}
    private static void breaking(GameTestHelper h){try(var a=new Arena(h)){
        var m=a.machine();m.setSlot(0,new ItemStack(ModItems.CHIP_SCRAP.get(),64));m.setStoredEnergy(40000);ticks(m,200);a.level.destroyBlock(a.anchor(),true);int scrap=0,silicon=0;for(var entity:a.level.getEntitiesOfClass(ItemEntity.class,new AABB(a.anchor()).inflate(2))){if(entity.getItem().is(ModItems.CHIP_SCRAP.get()))scrap+=entity.getItem().getCount();if(entity.getItem().is(ModItems.SILICON.get()))silicon+=entity.getItem().getCount();entity.discard();}eq(h,64,scrap,"Unfinished batch drops original scrap");eq(h,0,silicon,"No unpaid recovery on break");
        m=a.machine();m.setSlot(0,new ItemStack(ModItems.CHIP_SCRAP.get(),64));m.setStoredEnergy(40000);ticks(m,400);a.level.destroyBlock(a.anchor(),true);scrap=0;silicon=0;for(var entity:a.level.getEntitiesOfClass(ItemEntity.class,new AABB(a.anchor()).inflate(2))){if(entity.getItem().is(ModItems.CHIP_SCRAP.get()))scrap+=entity.getItem().getCount();if(entity.getItem().is(ModItems.SILICON.get()))silicon+=entity.getItem().getCount();}eq(h,0,scrap,"Finished inputs consumed");eq(h,1,silicon,"Single finished recovery dropped");
    }h.succeed();}
    private static void arcforge(GameTestHelper h){var a=new Arena(h);var m=a.machine();m.setStoredEnergy(40000);var origin=a.anchor();var conduit=BuiltInRegistries.BLOCK.getValue(Identifier.parse("arcforge:wrought_item_conduit"));
        for(var pos:List.of(origin.west(),origin.east())){a.put(pos,conduit);var c=(net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity)a.level.getBlockEntity(pos);for(var side:Direction.values())c.setSetting(side,net.zagdrath.arcforge.conduit.SideSetting.DISABLED);c.setSetting(Direction.WEST,net.zagdrath.arcforge.conduit.SideSetting.OUTPUT);c.setSetting(Direction.EAST,net.zagdrath.arcforge.conduit.SideSetting.INPUT);net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(a.level,pos);net.zagdrath.arcforge.conduit.network.ConduitNetworkManager.get(a.level).markDirty(pos);}
        a.put(origin.west(2),Blocks.CHEST);a.put(origin.east(2),Blocks.CHEST);var source=(net.minecraft.world.Container)a.level.getBlockEntity(origin.west(2));var sink=(net.minecraft.world.Container)a.level.getBlockEntity(origin.east(2));source.setItem(0,new ItemStack(item("assembly_scrap"),64));
        h.runAfterDelay(700,()->{try{check(h,source.isEmpty(),"Real conduit admitted batch");int recovered=0;for(int i=0;i<sink.getContainerSize();i++)if(sink.getItem(i).is(ModItems.SILICON.get()))recovered+=sink.getItem(i).getCount();eq(h,1,recovered,"Real conduit extracts completed Silicon");eq(h,24000,m.buffer().getAmountAsInt(),"Automation pays exact FE");h.succeed();}finally{a.close();}});
    }
    private static Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.parse("lithmod:"+id));}
    private RecyclerGameTests(){}
}
