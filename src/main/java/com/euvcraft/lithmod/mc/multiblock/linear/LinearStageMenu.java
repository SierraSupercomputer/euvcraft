package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.machine.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
public final class LinearStageMenu extends FabMachineMenu {
    @Override protected void addDataSlots(ContainerData values){super.addDataSlots(new com.euvcraft.lithmod.mc.cleanroom.WideContainerData(values));}
    private final boolean panel;
    private final ContainerData formation,status;
    public LinearStageMenu(int id,Inventory inventory,@Nullable LinearStageBlockEntity machine,boolean panel){
        super(LinearMachines.STAGE_PANEL_MENU.get(),id,inventory,machine,3,4);
        this.panel=panel;formation=machine!=null?machine.formation().data():new SimpleContainerData(1);status=machine!=null?machine.stageData():new SimpleContainerData(4);addDataSlots(formation);addDataSlots(status);
    }
    public LinearStageMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer,boolean panel){this(id,inventory,(LinearStageBlockEntity)null,panel);if(panel){buffer.readBlockPos();buffer.readBoolean();}}
    @Override protected void addMachineSlots(){
        if(machineSlots().getContainerSize()==0)return;
        addFoupSlot(0,38,35);
        addCustomSlot(new Slot(machineSlots(),1,62,35){@Override public boolean mayPlace(ItemStack stack){return blockEntity instanceof LinearStageBlockEntity stage?stage.acceptsReagent(stack):com.euvcraft.lithmod.mc.ResistCatalog.tierOf(stack)>=5||stack.is(ModItems.UTILITY_FILTER.get());}});
        addCustomSlot(new Slot(machineSlots(),2,86,35){@Override public boolean mayPlace(ItemStack stack){return stack.is(net.minecraft.world.item.Items.REDSTONE);}});
        addOutputSlot(3,122,35);
    }
    public boolean panel(){return panel;}public boolean formed(){return formation.get(0)==1;}public int status(){return status.get(0);}public int water(){return status.get(1);}public int waterCapacity(){return status.get(2);}public boolean requiresRepair(){return status.get(3)==1;}
    @Override public boolean clickMenuButton(Player player,int button){return panel&&button==1&&player instanceof net.minecraft.server.level.ServerPlayer&&player.containerMenu==this&&stillValid(player)&&blockEntity instanceof LinearStageBlockEntity stage&&stage.repair();}
    @Override public boolean stillValid(Player player){
        if(!super.stillValid(player))return false;
        if(!panel)return true;
        var pos=blockEntity.getBlockPos().above();return blockEntity.getLevel().getBlockEntity(pos) instanceof AdvancedMemberBlockEntity member&&member.belowOwner()==blockEntity&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
}
