package com.euvcraft.lithmod.mc.multiblock.euv;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class EuvSourceCalibrationMenu extends AbstractContainerMenu {
    private final @Nullable EuvLightSourceControllerBlockEntity source;
    private final @Nullable EuvLightSourceMemberBlockEntity panel;
    private final BlockPos pos;
    private final ContainerData values;
    public EuvSourceCalibrationMenu(int id,Inventory inv,@Nullable EuvLightSourceControllerBlockEntity source,BlockPos pos){
        super(EuvMachines.SOURCE_CALIBRATION_MENU.get(),id);this.source=source;this.pos=pos.immutable();
        panel=source!=null&&source.getLevel()!=null&&source.getLevel().getBlockEntity(pos) instanceof EuvLightSourceMemberBlockEntity p?p:null;
        values=source==null?new SimpleContainerData(22):new ContainerData(){public int get(int i){return source.pulses.value(i);}public void set(int i,int v){}public int getCount(){return 22;}};
        addDataSlots(new WideContainerData(values));
    }
    public EuvSourceCalibrationMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,null,buf.readBlockPos());}
    public int value(int i){return values.get(i);}
    @Override public boolean stillValid(Player player){return source!=null&&panel!=null&&source.getLevel()==player.level()&&!source.isRemoved()
        &&player.level().getBlockEntity(source.getBlockPos())==source&&player.level().getBlockEntity(pos)==panel&&panel.lightSource()==source
        &&panel.structureKind()==BlockKind.CALIBRATION_PANEL&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean clickMenuButton(Player player,int id){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer)||player.containerMenu!=this||!stillValid(player)||source.pulses.locked())return false;
        if(id==12)return source.pulses.test();if(id==13)return source.pulses.accept();
        if(id<0||id>=12)return false;int step=id%4==0||id%4==3?source.pulses.value(21):source.pulses.value(20);
        return source.pulses.adjust(id/4,id%4<2?-step:step);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
