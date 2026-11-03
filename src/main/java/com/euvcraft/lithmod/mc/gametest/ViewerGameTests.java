package com.euvcraft.lithmod.mc.gametest;

import static com.euvcraft.lithmod.mc.gametest.T4RuntimeFixtures.*;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.viewer.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ViewerGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,"lithmod");
    private static final Map<String,Consumer<GameTestHelper>> CASES=new LinkedHashMap<>();
    static {add("viewer_catalog_registry",ViewerGameTests::catalog);add("viewer_catalog_authoritative_reload",ViewerGameTests::reload);add("viewer_catalog_wire_and_disconnect",ViewerGameTests::wire);add("viewer_linear_route_costs",ViewerGameTests::routes);add("viewer_zero_resist",ViewerGameTests::zeroResist);}
    private static void add(String name,Consumer<GameTestHelper> fn){CASES.put(name,fn);FUNCTIONS.register(name,()->fn);}
    public static void register(IEventBus bus){FUNCTIONS.register(bus);bus.addListener((Consumer<RegisterGameTestsEvent>)event->{for(var name:CASES.keySet()){var id=Identifier.fromNamespaceAndPath("lithmod",name);var env=event.registerEnvironment(id,new TestEnvironmentDefinition[0]);var data=new TestData<>(env,Level.OVERWORLD,Identifier.withDefaultNamespace("empty"),400,0,true,Rotation.NONE,false,1,1,false,0);event.registerTest(id,new FunctionGameTestInstance(ResourceKey.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,id),data));}});}
    private static ViewerPage find(List<ViewerPage> rows,String id){return rows.stream().filter(row->row.id().equals("lithmod:process/"+id)).findFirst().orElseThrow();}
    private static void catalog(GameTestHelper h){
        var pages=ViewerCatalog.build();check(h,pages.size()>80,"All process families projected");check(h,pages.equals(ViewerCatalogCodec.decode(ViewerCatalogCodec.encode(pages))),"Public catalog codec roundtrip");
        for(var entry:ViewerMachines.CONTROLLERS.entrySet()){var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.getValue()));check(h,item instanceof BlockItem,"Crafting station is a real controller block: "+entry);check(h,pages.stream().anyMatch(p->p.machine().equals(entry.getKey())),"Station has process pages: "+entry);}
        for(var row:pages){check(h,!row.machine().equals("test_machine")&&!row.id().contains("/t8"),"No fixtures or unimplemented T8 recipes");for(var i:java.util.stream.Stream.of(row.inputs(),row.outputs(),row.catalysts()).flatMap(List::stream).toList()){
            switch(i.kind()){
                case ITEM->check(h,BuiltInRegistries.ITEM.getValue(Identifier.parse(i.id()))!=Items.AIR,"Registered display item: "+i.id());
                case TAG->check(h,BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,Identifier.parse(i.id()))).iterator().hasNext(),"Live tag resolves");
                case FLUID,GAS->check(h,BuiltInRegistries.FLUID.getValue(Identifier.parse(i.id()))!=Fluids.EMPTY,"Gas/fluid uses its real registered fluid: "+i.id());
            }
        }}
        EUVCRAFTMod.LOGGER.info("Recipe viewer: {} public pages, {} controller stations",pages.size(),ViewerMachines.CONTROLLERS.size());h.succeed();
    }
    private static void reload(GameTestHelper h){
        try{
            var current=MachineDefinitionLoader.class.getDeclaredField("CURRENT");current.setAccessible(true);var loader=current.get(null);var field=JsonResourceDataLoader.class.getDeclaredField("current");field.setAccessible(true);var original=field.get(loader);
            try{
                var all=new ArrayList<>(MachineDefinitionLoader.getRegistry().all());for(int i=0;i<all.size();i++){var d=all.get(i);if(d.id().equals("hydrogen_purifier")){var params=new HashMap<>(d.params());params.put("raw_hydrogen_per_batch",8333);params.put("fe_per_tick",607);all.set(i,new MachineDefinition(d.id(),d.energyCapacity(),d.maxReceive(),d.energyPerTick(),d.perTierEnergyPerTick(),params));}}
                field.set(loader,MachineDefinitionLoader.build(all,"viewer native override"));var page=find(ViewerCatalog.build(),"hydrogen_purifier");eq(h,8333,page.inputs().getFirst().amount(),"Catalog uses current server override, not bundled JSON");eq(h,Math.max(1,(int)Math.round(607*EUVCRAFTMod.settings().fePowerMultiplier())),page.fePerTick(),"Recipe-specific power uses live settings");
            }finally{field.set(loader,original);}
            eq(h,8000,find(ViewerCatalog.build(),"hydrogen_purifier").inputs().getFirst().amount(),"Definition restored");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}h.succeed();
    }
    private static void wire(GameTestHelper h){
        var pages=ViewerCatalog.build();String json=ViewerCatalogCodec.encode(pages);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{ViewerPayload.CODEC.encode(buffer,new ViewerPayload(json));var decoded=ViewerPayload.CODEC.decode(buffer);check(h,json.equals(decoded.json()),"Bounded actual NeoForge payload roundtrip");}finally{buffer.release();}
        ViewerClientStore.clear();var calls=new AtomicInteger();ViewerClientStore.listen(calls::incrementAndGet);
        try{ViewerClientStore.accept(json);ViewerClientStore.accept(json);eq(h,1,calls.get(),"Identical sync is idempotent");check(h,pages.equals(ViewerClientStore.pages()),"Full immutable client replacement");ViewerClientStore.accept("[]");eq(h,2,calls.get(),"Changed server snapshot removes old rows");ViewerClientStore.accept(json);ViewerClientStore.clear();check(h,ViewerClientStore.pages().isEmpty(),"Disconnect clears previous server's recipes");eq(h,4,calls.get(),"Listener notified on each real transition");}finally{ViewerClientStore.listen(()->{});ViewerClientStore.clear();}h.succeed();
    }
    private static void routes(GameTestHelper h){
        var pages=ViewerCatalog.build();for(int tier=5;tier<=7;tier++){
            var coat=find(pages,"coat/t"+tier);eq(h,TierDataLoader.getRegistry().getByIndex(tier).layers()*EUVCRAFTMod.settings().resistPerLayer(),coat.inputs().get(1).amount(),"Whole-lot resist in coating");
            final int t=tier;for(var scan:pages.stream().filter(p->p.id().contains("/expose/t"+t+"/")).toList()){check(h,scan.inputs().stream().noneMatch(i->i.id().contains("resist_t")),"Later scanner has no duplicate resist input");check(h,scan.catalysts().size()==1&&scan.catalysts().getFirst().id().equals("lithmod:mask"),"Reusable mask is separate from consumed inputs");}
            check(h,find(pages,"clean/t"+tier).inputs().stream().anyMatch(i->i.id().equals("lithmod:ultrapure_water")),"Productive cleaning is discoverable from real UPW");
        }
        check(h,pages.stream().anyMatch(p->p.outputs().stream().anyMatch(i->i.id().equals("lithmod:fab_hydrogen"))),"Fab hydrogen has a searchable production output");h.succeed();
    }
    private static void zeroResist(GameTestHelper h){
        int original=FabConfig.RESIST_PER_LAYER.get();try{FabConfig.RESIST_PER_LAYER.set(0);EUVCRAFTMod.config().onConfigReload();var pages=ViewerCatalog.build();ViewerCatalogCodec.encode(pages);for(int tier=5;tier<=7;tier++){var coat=find(pages,"coat/t"+tier);eq(h,1,coat.inputs().size(),"Zero-cost resist is not a consumed zero-count stack");check(h,coat.catalysts().size()==1&&coat.catalysts().getFirst().id().equals("lithmod:resist_t"+tier)&&coat.catalysts().getFirst().amount()==1,"Tier-selecting resist remains visible as reusable");}}finally{FabConfig.RESIST_PER_LAYER.set(original);EUVCRAFTMod.config().onConfigReload();}h.succeed();
    }
    private ViewerGameTests(){}
}
