package com.euvcraft.lithmod.mc.compat.jei;

import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.registry.*;
import com.euvcraft.lithmod.mc.viewer.*;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Discovered by JEI only. No common/server class references this optional API boundary. */
@JeiPlugin
public final class EuvcraftJeiPlugin implements IModPlugin {
    static final Map<String,IRecipeType<ViewerPage>> TYPES;
    static {
        var map=new LinkedHashMap<String,IRecipeType<ViewerPage>>();
        ViewerMachines.CONTROLLERS.keySet().forEach(machine->map.put(machine,IRecipeType.create("lithmod","process/"+machine,ViewerPage.class)));
        TYPES=Collections.unmodifiableMap(map);
    }
    private IJeiRuntime runtime;
    private List<ViewerPage> visible=List.of();
    @Override public Identifier getPluginUid(){return Identifier.parse("lithmod:process_viewer");}
    @Override public void registerItemSubtypes(ISubtypeRegistration registration){
        // Wear and compute core choices do not hide the recipe for the same mask design.
        registration.registerSubtypeInterpreter(ModItems.MASK.get(),(stack,context)->{var mask=stack.get(ModDataComponents.MASK_DATA.get());return mask==null?null:mask.dieType()+"/"+mask.tierIndex();});
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration){TYPES.forEach((machine,type)->registration.addRecipeCategories(new ProcessCategory(machine,type,registration.getJeiHelpers().getGuiHelper())));}
    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration){
        registration.addExtraIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,List.of(new net.neoforged.neoforge.fluids.FluidStack(com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids.ULTRAPURE_WATER.get(),1000),new net.neoforged.neoforge.fluids.FluidStack(com.euvcraft.lithmod.mc.multiblock.hydrogen.HydrogenFluids.FAB_HYDROGEN.get(),1000)));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        TYPES.forEach((machine,type)->registration.addCraftingStation(type,BuiltInRegistries.ITEM.getValue(Identifier.parse(ViewerMachines.CONTROLLERS.get(machine)))));
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        visible=ViewerClientStore.pages();TYPES.forEach((machine,type)->registration.addRecipes(type,forMachine(visible,machine)));
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){this.runtime=runtime;ViewerClientStore.listen(this::refresh);refresh();}
    @Override public void onRuntimeUnavailable(){ViewerClientStore.listen(()->{});runtime=null;visible=List.of();}
    private static List<ViewerPage> forMachine(List<ViewerPage> all,String machine){return all.stream().filter(p->p.machine().equals(machine)).toList();}
    private void refresh(){
        if(runtime==null)return;var next=ViewerClientStore.pages();if(visible.equals(next))return;
        try{
            var manager=runtime.getRecipeManager();
            TYPES.forEach((machine,type)->{var old=forMachine(visible,machine);var fresh=forMachine(next,machine);manager.hideRecipes(type,old);manager.addRecipes(type,fresh);manager.unhideRecipes(type,fresh);});
            visible=next;
        }catch(RuntimeException failure){EUVCRAFTMod.LOGGER.error("Could not refresh JEI process pages",failure);}
    }
}
