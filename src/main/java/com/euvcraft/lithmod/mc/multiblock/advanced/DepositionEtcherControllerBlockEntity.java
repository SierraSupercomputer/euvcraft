package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.registry.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import org.jspecify.annotations.Nullable;

/** One frozen job owns the original carrier until completion or break recovery. */
public final class DepositionEtcherControllerBlockEntity extends AdvancedControllerBlockEntity {
    public static final int SLOT_FOUP_INPUT=0,SLOT_SILICON_REAGENT=1,SLOT_PREPARED_FOUP_OUTPUT=2;
    private ItemStack pending=ItemStack.EMPTY;
    private int duration,perTick,progress;
    private boolean recovery;
    public DepositionEtcherControllerBlockEntity(BlockPos pos,BlockState state){
        super(AdvancedMachines.DEPOSITION_ETCHER_CONTROLLER_BE.get(),pos,state,2,1);
    }
    @Override protected String machineId(){return "deposition_etcher";}
    public boolean hasJob(){return !pending.isEmpty();}
    public int jobProgress(){return progress;}
    public int jobDuration(){return duration;}
    private @Nullable FoupContents eligible(ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||!FoupItems.isFoup(stack))return null;
        try {
            var lot=FoupItems.trustedContentsOf(stack);
            if(lot.isEmpty()||lot.grade()!=WaferSubstrate.PURE||lot.preparationGeneration()!=1||lot.isStarted()
                ||lot.wafers().stream().anyMatch(w->w.layerIndex()!=0))return null;
            return lot;
        } catch(RuntimeException invalid){return null;}
    }
    @Override public void serverTick(){
        super.serverTick();
        if(level==null||level.isClientSide())return;
        if(recovery){
            if(!pending.isEmpty()&&slot(2).isEmpty()){setSlot(2,pending);pending=ItemStack.EMPTY;recovery=false;setChanged();}
            syncJob();return;
        }
        if(!isFormed()||definition().isEmpty()){syncJob();return;}
        if(pending.isEmpty()){
            var definition=definition().orElseThrow();
            int nextDuration=definition.param("duration").orElse(0);
            int silicon=definition.param("silicon_per_lot").orElse(0);
            int nextCost=definition.energyPerTick(null,EUVCRAFTMod.settings());
            if(nextDuration<1||nextDuration>72000||silicon<1||silicon>64||nextCost<1
                ||nextCost>buffer().getCapacityAsInt()||buffer().getAmountAsLong()<nextCost
                ||!slot(2).isEmpty()||eligible(slot(0))==null
                ||!slot(1).is(ModItems.SILICON.get())||slot(1).getCount()<silicon){syncJob();return;}
            pending=slot(0).copy();clearSlot(0);
            slot(1).shrink(silicon);if(slot(1).isEmpty())clearSlot(1);
            duration=nextDuration;perTick=nextCost;progress=0;setChanged();
        } else if(slot(2).isEmpty()&&energySource().draw(perTick)==perTick){
            progress++;
            if(progress==duration){
                var original=eligible(pending);
                if(original==null){recovery=true;syncJob();setChanged();return;}
                ItemStack output=pending.copy();
                output.set(ModDataComponents.FOUP_CONTENTS.get(),original.withPreparationGeneration(2));
                pending=ItemStack.EMPTY;setSlot(2,output);progress=0;
            }
            setChanged();
        }
        syncJob();
    }
    private void syncJob(){
        data().set(DATA_PROGRESS,progress);data().set(DATA_TICKS,pending.isEmpty()?0:duration);
        data().set(DATA_ENERGY,buffer().getAmountAsInt());data().set(DATA_CAPACITY,buffer().getCapacityAsInt());
    }
    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);
        if(!pending.isEmpty()){
            output.store("t4_pending",ItemStack.OPTIONAL_CODEC,pending);
            output.putInt("t4_duration",duration);output.putInt("t4_cost",perTick);output.putInt("t4_progress",progress);
            output.putBoolean("t4_recovery",recovery);
        }
    }
    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);
        pending=input.read("t4_pending",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        duration=input.getInt("t4_duration").orElse(0);perTick=input.getInt("t4_cost").orElse(0);
        progress=input.getInt("t4_progress").orElse(0);recovery=input.getBooleanOr("t4_recovery",false);
        if(!pending.isEmpty()&&(eligible(pending)==null||duration<1||duration>72000||perTick<1||progress<0||progress>=duration))
            recovery=true;
        syncJob();
    }
    @Override protected void dropExtraContents(@Nullable Level level,BlockPos pos){
        if(!pending.isEmpty()){
            var real=pending;pending=ItemStack.EMPTY;
            dropContainer(level,pos,new SimpleContainer(real));
        }
    }
}
