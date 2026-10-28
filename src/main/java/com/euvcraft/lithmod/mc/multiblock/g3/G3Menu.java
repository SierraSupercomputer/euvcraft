package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;
abstract class G3Menu extends FabMachineMenu {
    final @Nullable G3ControllerBlockEntity controller;
    final BlockPos core;
    final @Nullable G3MemberBlockEntity panel;
    final boolean operation;
    final G3MachineKind kind;
    final ContainerData values;
    G3Menu(MenuType<?> type,int id,Inventory inventory,@Nullable G3ControllerBlockEntity controller,BlockPos core,boolean operation,G3MachineKind kind){
        super(type,id,inventory,controller,kind==G3MachineKind.RETICLE_WRITER?2:3,kind==G3MachineKind.RETICLE_WRITER?3:4);
        this.controller=controller;this.core=core.immutable();this.operation=operation;this.kind=kind;
        panel=controller!=null&&controller.getLevel()!=null&&controller.getLevel().getBlockEntity(core.above()) instanceof G3MemberBlockEntity p?p:null;
        values=controller==null?new SimpleContainerData(19):new ContainerData(){public int get(int i){return controller.publicData(i);}public void set(int i,int v){}public int getCount(){return 19;}};
        addDataSlots(new WideContainerData(values));
    }
    public int value(int i){return values.get(i);}
    public boolean formed(){return value(0)==1;}
    public boolean writer(){return kind==G3MachineKind.RETICLE_WRITER;}
    @Override public boolean stillValid(Player player){
        if(controller==null||!super.stillValid(player)||controller.isRemoved()||controller.getLevel()!=player.level()||player.level().getBlockEntity(core)!=controller)return false;
        return !operation||panel!=null&&formed()&&player.level().getBlockEntity(core.above())==panel&&panel.isOperatingPanel()&&panel.controller()==controller&&player.distanceToSqr(core.getX()+.5,core.getY()+1.5,core.getZ()+.5)<=64;
    }
    @Override public boolean clickMenuButton(Player player,int id){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server)||server.containerMenu!=this||controller==null||!stillValid(player)||!controller.isFormed())return false;
        if(operation&&writer()){if(id>=0&&id<=2)return controller.selectDesign(id);if(id==3){int selected=controller.selectedDesign();return selected>=0&&controller.startWriterJob("lithmod:g3_"+java.util.List.of("compute","memory","nand").get(selected)+"_t7",controller.corePreference());}if(id==4)return controller.cycleCores();}
        if(!writer()){if(operation&&id==3)return controller.startPrepJob();if(!operation&&id==5)return controller.repair();if(!operation&&id==6)return controller.toggleIsolation();}
        return false;
    }
}

