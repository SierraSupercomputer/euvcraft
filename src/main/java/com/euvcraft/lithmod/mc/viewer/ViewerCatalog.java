package com.euvcraft.lithmod.mc.viewer;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.LinearBudget;
import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.euvcraft.lithmod.core.viewer.ViewerPage.Ingredient;
import com.euvcraft.lithmod.core.viewer.ViewerPage.Kind;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Read-only projection of loaded server recipes, settings and process definitions. */
public final class ViewerCatalog {
    private static final List<String> FAMILIES=List.of("logic","compute","memory","nand");
    private static final String WATER="minecraft:water",UPW="lithmod:ultrapure_water";
    private static final Pattern DESIGN=Pattern.compile("(?:ebeam|g3)_(logic|compute|memory|nand)_t([1-7])");
    private final List<ViewerPage> pages=new ArrayList<>();
    private final FabSettings settings=EUVCRAFTMod.settings();
    public static List<ViewerPage> build(){
        var c=new ViewerCatalog();c.loadedRecipes();c.utilities();c.slicingAndPreparation();c.waferRoutes();c.binning();
        c.pages.sort(Comparator.comparing(ViewerPage::id));return List.copyOf(c.pages);
    }
    private MachineDefinition definition(String id){return MachineDefinitionLoader.getRegistry().byId(id).orElseThrow();}
    private int param(String machine,String key,int fallback){return definition(machine).paramOrDefault(key,fallback);}
    private int scaled(int raw){return Math.max(1,(int)Math.min(Integer.MAX_VALUE,Math.round(raw*settings.fePowerMultiplier())));}
    private int fe(String machine){return definition(machine).energyPerTick(null,settings);}
    private static String id(String raw){return raw.contains(":")?raw:"lithmod:"+raw;}
    private static Ingredient item(String raw,int amount){return Ingredient.item(id(raw),amount);}
    private static Ingredient fluid(String id,int amount){return new Ingredient(Kind.FLUID,id,amount,"","",0,0,0);}
    private static Ingredient foup(String label){return item("foup_mk1",1).labelled(label);}
    private Ingredient mask(String family,int tier){return new Ingredient(Kind.ITEM,"lithmod:mask",1,"One mask use per completed lot",family,tier,0,settings.maskUses());}
    private Ingredient ingredient(MachineIngredient source){return new Ingredient(source.isTag()?Kind.TAG:Kind.ITEM,source.id(),source.count(),"","",0,0,0);}
    private Ingredient product(ItemStack stack){
        var mask=stack.get(ModDataComponents.MASK_DATA.get());
        return new Ingredient(Kind.ITEM,BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),"",mask==null?"":mask.dieType(),mask==null?0:mask.tierIndex(),stack.getOrDefault(ModDataComponents.CORE_COUNT.get(),0),mask==null?0:settings.maskUses());
    }
    private void page(String key,String machine,List<Ingredient> inputs,List<Ingredient> outputs,List<Ingredient> catalysts,List<String> notes,long ticks,long cost){
        pages.add(new ViewerPage("lithmod:process/"+key,machine,inputs,outputs,catalysts,notes,ticks,cost));
    }
    private static String hash(Object spec){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new Gson().toJson(spec).getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private void loadedRecipes(){
        var recipes=MachineRecipeLoader.getRegistry();if(recipes!=null)for(var recipe:recipes.all()){
            String machine=recipe.machineId();if(!ViewerMachines.CONTROLLERS.containsKey(machine))continue;
            var inputs=new ArrayList<>(recipe.spec().ingredients().stream().map(this::ingredient).toList());var notes=new ArrayList<String>();
            if(machine.equals("zone_refiner"))inputs.add(fluid(UPW,param(machine,"water_per_batch_mb",4000)));
            if(machine.equals("reflow_oven")){notes.add("Requires accepted thermal calibration. Assembly yield varies; failed assemblies produce Assembly Scrap.");notes.add("CPU output retains the compute chip's core count.");}
            var outputs=new ArrayList<Ingredient>();outputs.add(product(recipe.assemble()));if(machine.equals("reflow_oven"))outputs.add(item("assembly_scrap",1).labelled("Failure output; amount depends on the assembly job"));
            page("recipe/"+machine+"/"+hash(recipe.spec()),machine,inputs,outputs,List.of(),notes,recipe.ticks(),scaled(recipe.energyPerTick(definition(machine).baseEnergyPerTick(null))));
        }
        var support=SupportRecipeLoader.getRegistry();if(support!=null)for(var entry:support.all()){
            var recipe=entry.recipe();if(!ViewerMachines.CONTROLLERS.containsKey(recipe.machine()))continue;
            var inputs=new ArrayList<Ingredient>();inputs.add(ingredient(recipe.input()));if(recipe.secondary()!=null)inputs.add(ingredient(recipe.secondary()));if(recipe.waterMb()>0)inputs.add(fluid(WATER,recipe.waterMb()));
            Ingredient output=item(recipe.outputItem(),recipe.outputCount());var notes=new ArrayList<String>();
            if(recipe.outputItem().equals("lithmod:mask")){
                var design=DESIGN.matcher(entry.id().getPath());if(!design.matches())continue;
                output=mask(design.group(1),Integer.parseInt(design.group(2))).labelled("Select this design in the writer GUI");
                notes.add("Select the mask design in the GUI; compute core counts are selectable where supported.");
                notes.add("Nominal mask shown. Actual uses depend on room cleanliness and server configuration.");
            }
            page("support/"+entry.id().getNamespace()+"/"+entry.id().getPath(),recipe.machine(),inputs,List.of(output),List.of(),notes,recipe.duration(),scaled(recipe.energyPerTick()));
        }
    }
    private void utilities(){
        String m="utility_plant";
        page(m,m,List.of(fluid(WATER,param(m,"water_per_batch_mb",8000)),item("utility_filter",param(m,"filters_per_batch",1))),List.of(fluid(UPW,param(m,"pure_per_batch_mb",8000))),List.of(),List.of("Supply ordinary water and FE. Output is transport-only Ultrapure Water."),param(m,"duration",200),fe(m));
        m="hydrogen_purifier";
        page(m,m,List.of(fluid("arcforge:hydrogen",param(m,"raw_hydrogen_per_batch",8000)),item("utility_filter",param(m,"filter_per_batch",1))),List.of(fluid("lithmod:fab_hydrogen",param(m,"fab_hydrogen_per_batch",6000))),List.of(),List.of("Raw hydrogen comes from Arcforge processing. Use pressurized gas transport for Fab Hydrogen."),param(m,"ticks_per_batch",400),scaled(param(m,"fe_per_tick",600)));
        m="euv_light_source";
        page(m,m,List.of(item("tin_feedstock",1),fluid("lithmod:fab_hydrogen",param(m,"hydrogen_per_tick_mb",10))),List.of(),List.of(),List.of("Service, not a FOUP processing step: connect to the EUV scanner through a beamline.","FE and hydrogen shown per paid exposure tick; one tin feedstock prepays "+param(m,"tin_exposure_ticks",1000)+" exposure ticks.","Requires enclosed cleanroom, vacuum and accepted pulse calibration. Local pump FE is additional."),0,fe(m));
        m="central_vacuum_plant";
        page(m,m,List.of(),List.of(),List.of(),List.of("Shared vacuum service for connected G3/EUV machines; there is no crafted item output.","Demand varies. Standing cost: "+scaled(param(m,"vacuum_standing_fe_per_tick",120))+" FE/t; active floor: "+scaled(param(m,"vacuum_active_fe_per_tick",350))+" FE/t."),0,0);
    }
    private void slicingAndPreparation(){
        for(String boule:List.of("silicon_boule","pure_silicon_boule","electronic_grade_boule"))for(String carrier:List.of("foup_mk1","foup_mk2")){
            page("slicing/"+boule+"/"+carrier,"wafer_slicer",List.of(item(boule,1),item(carrier,1).labelled("Empty reusable carrier")),List.of(item(carrier,1).labelled("Fresh wafers from "+boule)),List.of(),List.of("FOUP icons are carrier placeholders; required/produced wafer stages are described in slot tooltips.","Wafer count is limited by server wafers-per-boule and carrier capacity. Substrate is retained."),param("wafer_slicer","slice_ticks",200),fe("wafer_slicer"));
        }
        page("g2_deposition","deposition_etcher",List.of(foup("Fresh pure or electronic-grade wafers"),item("silicon",param("deposition_etcher","silicon_per_lot",2))),List.of(foup("G2 film deposited; tier/family unassigned")),List.of(),List.of("For T4–T6. T4 goes to the scanner; T5/T6 go to the Coating Track."),param("deposition_etcher","duration",800),fe("deposition_etcher"));
        g3("g3_deposition_etcher",false);g3("g3_etch_cell",true);
        page("disposal","foup_cleaner",List.of(foup("Any unwanted or contaminated wafer lot"),fluid(WATER,param("foup_cleaner","water_mb",1000))),List.of(foup("Same empty carrier"),item("wafer_scrap",param("foup_cleaner","scrap_per_wafer",1)).labelled("Per discarded wafer")),List.of(),List.of("Discard & Clean destroys EVERY wafer in the carrier; this is not productive Wet-Clean processing.","Scrap amount equals wafer count times scrap-per-wafer. No Chip Packages or UPW required."),param("foup_cleaner","duration",200),fe("foup_cleaner"));
    }
    private void g3(String machine,boolean etch){
        var inputs=new ArrayList<Ingredient>();inputs.add(foup(etch?"Exposed T7 wafers, G3 preparation":"Fresh electronic-grade wafers"));if(etch)inputs.add(item("etchant_charge",param(machine,"etchant_per_lot",1)));
        int rinse=param(machine,"linear_rinse_moved",1)==1?0:param(machine,"rinse_duration",200);if(rinse>0)inputs.add(fluid(UPW,param(machine,"rinse_water_mb",4000)));
        long ticks=(long)param(machine,"linear_vacuum_duration",param(machine,"vacuum_duration",400))+rinse+param(machine,"linear_dry_duration",param(machine,"dry_duration",200));
        page(machine,machine,inputs,List.of(foup(etch?"Etched T7; next: Wet-Clean Station":"G3 film deposited; next: Coating Track")),List.of(),List.of("Requires enclosed cleanroom and vacuum. Pump-down and auxiliary pumping add variable time/FE.","Phase FE and preparation quality come from the loaded machine definition; exposure rolls are preserved."),ticks,0);
    }
    private LinearBudget budget(Tier tier){
        String scanner=tier.index()==7?"euv_scanner":"lithography_scanner";int passes=tier.index()==6?param(scanner,"multipattern_passes",2):1;
        return LinearBudget.calculate(tier.layers(),tier.layerTicks(settings),passes,settings.resistPerLayer(),param(scanner,"linear_original_upw_per_tick",tier.index()==7?5:2),param(scanner,"linear_upw_per_tick_mb",tier.index()==7?4:1),param("coating_track","coat_ticks_per_layer",5),param("wet_cleaner","clean_ticks_per_layer",5),tier.index()==7?param("wet_cleaner","t7_rinse_water_mb",4000):0);
    }
    private void waferRoutes(){
        var tiers=TierDataLoader.getRegistry();var rules=DieRulesDataLoader.getRules();if(tiers==null||rules==null)return;
        for(int t=1;t<=7;t++){
            var tier=tiers.byIndex(t).orElseThrow();String scanner=t<=3?"process_cell":t==7?"euv_scanner":"lithography_scanner";var plan=t>=5?budget(tier):null;
            int exposureFe=definition(scanner).energyPerTick(tier,settings);long scanTicks=(long)tier.layers()*(plan==null?tier.layerTicks(settings):plan.scannerTicksPerLayer());
            if(t>=5){
                var coatInputs=new ArrayList<Ingredient>();coatInputs.add(foup((t==7?"G3":"G2")+" film deposited; unassigned"));if(plan.resistTotal()>0)coatInputs.add(item("resist_t"+t,plan.resistTotal()));
                var coatCatalysts=plan.resistTotal()==0?List.of(item("resist_t"+t,1).labelled("Selects tier; not consumed with zero-resist configuration")):List.<Ingredient>of();
                page("coat/t"+t,"coating_track",coatInputs,List.of(foup("Coated T"+t+"; next: scanner")),coatCatalysts,List.of("Resist commits the tier; the mask later chooses family/cores. Refill stacks above 64.","Resist is paid here once. Scanner costs are split across coating, exposure and wet cleaning.","T7 requires enclosed cleanroom; T5/T6 may run outside with a room yield penalty."),Math.max(1,plan.coatTicks()),exposureFe);
                int rinse=t==7?param("wet_cleaner","t7_rinse_duration",200):0;
                var cleanInputs=new ArrayList<Ingredient>();cleanInputs.add(foup("Etched T"+t+" wafers"));if(plan.cleanWaterMb()>0)cleanInputs.add(fluid(UPW,plan.cleanWaterMb()));
                page("clean/t"+t,"wet_cleaner",cleanInputs,List.of(foup("Cleaned T"+t+"; next: advanced packaging")),List.of(),List.of("Productive clean: keeps the wafers. Requires the paid coating/etch route; no new exposure roll.","Costs shown for a newly coated lot; admitted lots retain frozen costs.",t==7?"Rinse: "+rinse+" ticks at "+scaled(param("wet_cleaner","t7_rinse_energy_per_tick",480))+" FE/t; dry: "+plan.cleanDryTicks()+" ticks at "+exposureFe+" FE/t.":"Room exposure is finalized once here. T5/T6 outside-room operation reduces yield."),(long)rinse+plan.cleanDryTicks(),t==7?0:exposureFe);
            }
            if(t>=4&&t<=6)page("etch/t"+t,"etch_cell",List.of(foup("Exposed T"+t+" wafers"),item("etchant_charge",param("etch_cell","etchant_per_lot",1))),List.of(foup("Etched T"+t+"; next: "+(t==4?"advanced packaging":"Wet-Clean Station"))),List.of(),List.of("Retains wafer identity, preparation and exposure rolls; no mask use or second exposure."),param("etch_cell","duration",400),fe("etch_cell"));
            for(var family:FAMILIES){
                if(!rules.canMake(family,t)||rules.effectiveTier(family,t)!=t)continue;
                var in=new ArrayList<Ingredient>();in.add(foup(t<=3?"Fresh compatible wafers":t==4?"G2 film deposited":"Coated T"+t+" wafers"));
                if(t<=4&&settings.resistPerLayer()>0)in.add(item("resist_t"+t,tier.layers()*settings.resistPerLayer()));
                if(t>=5)in.add(fluid(UPW,Math.toIntExact(scanTicks*param(scanner,"linear_upw_per_tick_mb",t==7?4:1))));
                var catalysts=new ArrayList<Ingredient>();catalysts.add(mask(family,t));if(t<=4&&settings.resistPerLayer()==0)catalysts.add(item("resist_t"+t,1).labelled("Matching resist still required; not consumed in zero-resist configuration"));
                page("expose/t"+t+"/"+family,scanner,in,List.of(foup("Exposed T"+t+" "+family+"; "+(t>=4?"next: etch":"next: packaging"))),catalysts,List.of("Requires matching line equipment/modules and accepted calibration where applicable.",t>=5?"No resist in scanner: paid upstream in Coating Track. T6 has two internal passes by default.":"Resist consumed per layer; one mask use per completed lot.",t==7?"Requires enclosed cleanroom, vacuum and a calibrated connected EUV light source.":"Yields vary with calibration, process quality and applicable cleanroom conditions."),scanTicks,exposureFe);
                String packager=t<=3?"test_package":"advanced_test_package";
                page("package/t"+t+"/"+family,packager,List.of(foup((t<=3?"Processed":t==4?"Etched":"Cleaned")+" T"+t+" "+family),item("chip_package",param(packager,"package_per_wafer",1)).labelled("Consumed per successful wafer")),List.of(item(family+"_chip_t"+t,1).labelled("Chip output; quantity depends on lot yield"),item("chip_scrap",1).labelled("Failure output; amount varies")),List.of(),List.of("Normal binning. Output quantities are illustrative; wafer rolls and server settings determine yield.","Returns the same empty FOUP. Each wafer takes "+param(packager,"ticks_per_wafer",10)+" ticks; masks are not consumed by packaging."),0,definition(packager).energyPerTick(tier,settings));
            }
        }
    }
    private void binning(){
        var registry=HighBinningDataLoader.getRegistry().orElse(null);if(registry==null)return;
        for(var recipe:registry.all()){
            var transition=recipe.transition();int source=transition.sourceTier(),target=transition.targetTier();if(target>7)continue;
            page("high_bin/"+recipe.dieType()+"/t"+source,"advanced_test_package",List.of(foup((source<=3?"Processed":source==4?"Etched":"Cleaned")+" T"+source+" "+recipe.dieType()),item("chip_package",param("advanced_test_package","package_per_wafer",1)).labelled("Per successful wafer")),List.of(item(recipe.dieType()+"_chip_t"+target,1).labelled("High-bin success; low yield"),item("chip_scrap",1).labelled("Failures; variable amount")),List.of(),List.of("Enable High Binning before admission. Follow the SOURCE tier's complete wafer route.","Uses the loaded high-binning yield/core rules; no new exposure rolls. Output is not guaranteed.",target==7?"Target T7 additionally requires the packager to be inside an enclosed cleanroom.":"Only logic/compute support high binning; logic ends at T5."),0,definition("advanced_test_package").energyPerTick(TierDataLoader.getRegistry().getByIndex(source),settings));
        }
    }
    private ViewerCatalog(){}
}
