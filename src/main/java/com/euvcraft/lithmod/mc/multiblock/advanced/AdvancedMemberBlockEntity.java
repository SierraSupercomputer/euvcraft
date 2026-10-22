package com.euvcraft.lithmod.mc.multiblock.advanced;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Members own neither items nor FE. Links are rebuilt after reload, never persisted. */
public class AdvancedMemberBlockEntity extends BlockEntity implements MenuProvider {
    private @Nullable AdvancedOwner owner;
    private boolean formed;
    public AdvancedMemberBlockEntity(BlockPos pos,BlockState state){
        this(AdvancedMachines.ADVANCED_MEMBER_BE.get(),pos,state);
    }
    protected AdvancedMemberBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public com.euvcraft.lithmod.core.multiblock.BlockKind structureKind(){
        var block=getBlockState().getBlock();
        if(block==AdvancedMachines.ADVANCED_POWER_PORT.get())return com.euvcraft.lithmod.core.multiblock.BlockKind.POWER_PORT;
        if(block==AdvancedMachines.ADVANCED_OPERATING_PANEL.get())return com.euvcraft.lithmod.core.multiblock.BlockKind.OPERATING_PANEL;
        if(block==AdvancedMachines.ADVANCED_CASING.get())return com.euvcraft.lithmod.core.multiblock.BlockKind.CASING;
        return com.euvcraft.lithmod.core.multiblock.BlockKind.OTHER;
    }
    public @Nullable AdvancedOwner controller(){return owner;}
    public boolean isMemberOfFormedCell(){return formed;}
    public void attach(@Nullable AdvancedOwner next,boolean formed){
        if(owner==next&&this.formed==formed)return;
        owner=next;this.formed=formed;
        if(level!=null&&!level.isClientSide())level.invalidateCapabilities(worldPosition);
    }
    public void serverTick(){
        if(level==null||level.isClientSide())return;
        if(owner!=null&&(owner.machine().isRemoved()||level.getBlockEntity(owner.machine().getBlockPos())!=owner.machine())){
            attach(null,false);
        }
        if(owner==null&&getBlockState().getValue(AdvancedVisualProperties.FORMED)){
            var state=getBlockState().setValue(AdvancedVisualProperties.FORMED,false)
                .setValue(AdvancedVisualProperties.PART,0).setValue(AdvancedVisualProperties.LARGE,false);
            level.setBlock(worldPosition,state,2);
        }
    }
    public @Nullable AdvancedOwner belowOwner(){
        if(level==null)return null;
        return level.getBlockEntity(worldPosition.below()) instanceof AdvancedOwner candidate?candidate:null;
    }
    private boolean valid(AdvancedOwner expected){
        if(level==null||level.isClientSide()||isRemoved()||level.getBlockEntity(worldPosition)!=this
            ||getBlockState().getBlock()!=AdvancedMachines.ADVANCED_POWER_PORT.get()
            ||expected.machine().isRemoved()||level.getBlockEntity(expected.machine().getBlockPos())!=expected.machine())return false;
        expected.revalidate();
        return owner==expected&&formed&&expected.isFormed()&&expected.members().contains(worldPosition);
    }
    public @Nullable EnergyHandler energy(){
        var expected=owner;
        if(expected==null||!valid(expected))return null;
        return new EnergyHandler(){
            public long getAmountAsLong(){return valid(expected)?expected.machine().energy().getAmountAsLong():0;}
            public long getCapacityAsLong(){return valid(expected)?expected.machine().energy().getCapacityAsLong():0;}
            public int insert(int amount,TransactionContext transaction){
                return valid(expected)?expected.machine().energy().insert(amount,transaction):0;
            }
            public int extract(int amount,TransactionContext transaction){return 0;}
        };
    }
    @Override public Component getDisplayName(){
        var below=belowOwner();return below==null?Component.translatable("block.lithmod.advanced_operating_panel"):below.machine().getDisplayName();
    }
    @Override public @Nullable AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){
        var below=belowOwner();
        return below==null?null:new AdvancedOperatingPanelMenu(id,inventory,below.machine(),worldPosition.below());
    }
}
