package com.euvcraft.lithmod.mc.multiblock.linear;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
final class StageWaterJournal extends SnapshotJournal<Integer>{
    private final LinearStageBlockEntity owner;
    StageWaterJournal(LinearStageBlockEntity owner){this.owner=owner;}
    @Override protected Integer createSnapshot(){return owner.upw.amount();}
    @Override protected void revertToSnapshot(Integer n){owner.upw.restoreAmount(n);}
    @Override protected void onRootCommit(Integer n){owner.setChanged();}
}
