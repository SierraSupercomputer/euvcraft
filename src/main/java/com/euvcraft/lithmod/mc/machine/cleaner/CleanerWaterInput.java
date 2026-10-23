package com.euvcraft.lithmod.mc.machine.cleaner;
import java.util.Objects;import net.minecraft.world.level.material.Fluids;import net.neoforged.neoforge.transfer.ResourceHandler;import net.neoforged.neoforge.transfer.fluid.FluidResource;import net.neoforged.neoforge.transfer.transaction.*;
/** All faces share one journal, accepting plain ordinary water and never extracting it. */
public final class CleanerWaterInput implements ResourceHandler<FluidResource> {
 private final FoupCleanerBlockEntity owner;private final SnapshotJournal<Integer> journal;
 CleanerWaterInput(FoupCleanerBlockEntity o){owner=o;journal=new SnapshotJournal<>(){protected Integer createSnapshot(){return o.water.amount();}protected void revertToSnapshot(Integer n){o.water.restoreAmount(n);}protected void onRootCommit(Integer old){if(o.live())o.setChanged();}};}
 private static FluidResource water(){return FluidResource.of(Fluids.WATER);}private static void index(int i){Objects.checkIndex(i,1);}
 public int size(){return 1;}public FluidResource getResource(int i){index(i);return owner.live()&&owner.water.amount()>0?water():FluidResource.EMPTY;}public long getAmountAsLong(int i){index(i);return owner.live()?owner.water.amount():0;}public long getCapacityAsLong(int i,FluidResource r){index(i);return owner.live()&&(r.isEmpty()||water().equals(r))?owner.water.capacity():0;}public boolean isValid(int i,FluidResource r){index(i);return owner.live()&&water().equals(r);}
 public int insert(int i,FluidResource r,int n,TransactionContext tx){index(i);Objects.requireNonNull(tx);if(r.isEmpty()||n<0)throw new IllegalArgumentException("Resource and nonnegative amount required");if(!owner.live()||!water().equals(r))return 0;int accepted=owner.water.fill(n,true);if(accepted>0){journal.updateSnapshots(tx);owner.water.fill(accepted,false);}return accepted;}
 public int extract(int i,FluidResource r,int n,TransactionContext tx){index(i);if(r.isEmpty()||n<0)throw new IllegalArgumentException("Resource and nonnegative amount required");Objects.requireNonNull(tx);return 0;}
}
