package com.euvcraft.lithmod.mc.routing;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.routing.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-side route projection. Display advice never acts as processing authority. */
public final class FoupRouting {
    public static void refresh(ItemStack stack){
        if(stack.isEmpty()||!FoupItems.isFoup(stack)||stack.getCount()!=1)return;
        var lot=stack.get(ModDataComponents.FOUP_CONTENTS.get());
        if(lot==null||FoupContentsCodec.isClientView(lot))return;
        var data=stack.get(ModDataComponents.FOUP_ROUTING.get());
        if(lot.isEmpty())data=FoupRoutingData.initial();
        else if(data==null&&lot.run()==null)data=new FoupRoutingData(lot.isPrepared()?FabProgress.initial().deposited():FabProgress.initial(),"",0,lot.isContaminated());
        if(data!=null){
            if(data.contaminated()!=lot.isContaminated())data=new FoupRoutingData(data.progress(),data.dieType(),data.cores(),lot.isContaminated());
            if(!data.equals(stack.get(ModDataComponents.FOUP_ROUTING.get())))stack.set(ModDataComponents.FOUP_ROUTING.get(),data);
        }
        var matrix=SupportLineDataLoader.getMatrix();var tiers=TierDataLoader.getRegistry();
        if(data!=null&&matrix!=null&&tiers!=null){
            var options=FabNextSteps.options(lot.publicView(),data.progress(),data.contaminated(),matrix,tiers,Set.of(4));
            if(!data.contaminated()&&lot.run()!=null&&data.progress().stage().ordinal()<FabProgress.Stage.EXPOSED.ordinal()){
                int tier=lot.run().tierIndex();options=Map.of(tier<=3?"process_cell_controller":tier==7?"euv_scanner_controller":"lithography_scanner",List.of(tier));
            }
            if(!options.equals(stack.get(ModDataComponents.FOUP_NEXT_STEPS.get())))stack.set(ModDataComponents.FOUP_NEXT_STEPS.get(),options);
        }
    }

    public static void deposited(ItemStack stack){
        var lot=FoupItems.trustedContentsOf(stack);
        if(lot==null||lot.isEmpty()||lot.isStarted()||!lot.isPrepared()||lot.isContaminated())throw new IllegalStateException("Invalid deposited carrier");
        var old=stack.get(ModDataComponents.FOUP_ROUTING.get());
        if(old!=null&&old.progress().stage()!=FabProgress.Stage.UNPROCESSED&&old.progress().stage()!=FabProgress.Stage.DEPOSITED)throw new IllegalStateException("Cannot replace processed state with deposition");
        stack.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(FabProgress.initial().deposited(),"",0,false));refresh(stack);
    }

    /** Called by the scanner only when its pinned ProcessRun actually finishes. */
    public static void exposureFinished(ItemStack stack){
        var lot=FoupItems.trustedContentsOf(stack);
        if(lot==null||lot.isEmpty()||lot.run()==null||lot.isContaminated())throw new IllegalStateException("Missing finished exposure data");
        var run=lot.run();
        stack.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(new FabProgress(FabProgress.Stage.EXPOSED,run.tierIndex(),FabProgress.CURRENT_VERSION),run.dieType(),run.cores(),false));refresh(stack);
    }

    public static void exposureActive(ItemStack stack){
        var lot=FoupItems.trustedContentsOf(stack);if(lot==null||lot.run()==null||lot.isContaminated())throw new IllegalStateException("Missing active exposure data");
        var run=lot.run();stack.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(new FabProgress(FabProgress.Stage.EXPOSING,run.tierIndex(),FabProgress.CURRENT_VERSION),run.dieType(),run.cores(),false));
        refresh(stack);
    }

    public static boolean matchesPrivateLot(ItemStack stack){
        var lot=FoupItems.trustedContentsOf(stack);var data=stack.get(ModDataComponents.FOUP_ROUTING.get());
        if(lot==null||data==null||data.contaminated()!=lot.isContaminated())return false;
        if(lot.isEmpty())return data.equals(FoupRoutingData.initial());
        return switch(data.progress().stage()){
            case UNPROCESSED -> lot.preparationGeneration()==1&&lot.run()==null;
            case DEPOSITED -> lot.isPrepared()&&lot.run()==null;
            case COATED -> lot.isPrepared()&&(lot.run()==null||lot.run().tierIndex()==data.progress().tier());
            case EXPOSING,EXPOSED,ETCHED,CLEANED -> lot.run()!=null&&lot.run().tierIndex()==data.progress().tier()&&lot.run().dieType().equals(data.dieType())&&lot.run().cores()==data.cores();
        };
    }

    public static boolean packagingReady(ItemStack stack){
        var lot=FoupItems.trustedContentsOf(stack);
        if(lot==null||lot.run()==null||lot.isEmpty()||lot.isContaminated())return false;
        if(lot.run().tierIndex()!=4)return true;
        var data=stack.get(ModDataComponents.FOUP_ROUTING.get());
        return data!=null&&data.progress().stage()==FabProgress.Stage.ETCHED&&matchesPrivateLot(stack);
    }

    public static boolean canExpose(ItemStack stack,int tier){
        if(tier!=4)return true;
        var lot=FoupItems.trustedContentsOf(stack);var data=stack.get(ModDataComponents.FOUP_ROUTING.get());
        return lot!=null&&lot.run()==null&&lot.isPrepared()&&!lot.isContaminated()&&data!=null&&data.progress().stage()==FabProgress.Stage.DEPOSITED&&matchesPrivateLot(stack);
    }

    private FoupRouting(){}
}
