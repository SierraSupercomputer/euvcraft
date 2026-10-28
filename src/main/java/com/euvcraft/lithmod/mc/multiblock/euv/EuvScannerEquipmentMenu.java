package com.euvcraft.lithmod.mc.multiblock.euv;
import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import org.jspecify.annotations.Nullable;

/** The cabinet's saved maintenance inventory is separate from the operating inputs. */
public final class EuvScannerEquipmentMenu extends AbstractContainerMenu {
    private final @Nullable EuvScannerControllerBlockEntity scanner;
    private final BlockPos pos;
    private final ContainerData values;
    public EuvScannerEquipmentMenu(int id,Inventory inventory,@Nullable EuvScannerControllerBlockEntity scanner,BlockPos pos){
        super(EuvMachines.SCANNER_EQUIPMENT_MENU.get(),id);this.scanner=scanner;this.pos=pos.immutable();
        var materials=scanner==null?new SimpleContainer(2):scanner.repairItems;
        for(int i=0;i<2;i++){final int index=i;addSlot(new Slot(materials,i,i==0?26:98,35){
            @Override public boolean mayPlace(ItemStack stack){return index==0?stack.is(ModItems.UTILITY_FILTER.get()):stack.is(Items.REDSTONE);}
            @Override public void setChanged(){super.setChanged();if(scanner!=null)scanner.setChanged();}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,142));
        values=scanner==null?new SimpleContainerData(12):new ContainerData(){
            public int get(int i){return switch(i){case 0,1,2,3,4->scanner.equipment(i+3).isEmpty()?0:1;case 5,6->scanner.isFormed()?1:0;case 7->scanner.contaminationState().phase().ordinal();case 8->scanner.upwTank.amount();case 9->scanner.buffer().getAmountAsInt();case 10->scanner.buffer().getCapacityAsInt();case 11->scanner.vacuumIsolated()?1:0;default->0;};}
            public void set(int i,int value){}public int getCount(){return 12;}
        };addDataSlots(new WideContainerData(values));
    }
    public EuvScannerEquipmentMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){this(id,inventory,null,buffer.readBlockPos());}
    public int value(int i){return values.get(i);}
    @Override public boolean stillValid(Player player){return scanner!=null&&!scanner.isRemoved()&&scanner.getLevel()==player.level()&&pos.equals(scanner.getBlockPos())&&player.level().getBlockEntity(pos)==scanner&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean clickMenuButton(Player player,int id){if(!(player instanceof net.minecraft.server.level.ServerPlayer)||player.containerMenu!=this||!stillValid(player)||!scanner.isFormed())return false;return id==0?scanner.repair():id==1&&scanner.toggleVacuumIsolation();}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var slot=slots.get(index);var stack=slot.getItem();if(stack.isEmpty())return ItemStack.EMPTY;var original=stack.copy();
        if(index<2){if(!moveItemStackTo(stack,2,slots.size(),true))return ItemStack.EMPTY;}
        else if(stack.is(ModItems.UTILITY_FILTER.get())){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(stack.is(Items.REDSTONE)){if(!moveItemStackTo(stack,1,2,false))return ItemStack.EMPTY;}else return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,original);scanner.setChanged();return original;
    }
}
