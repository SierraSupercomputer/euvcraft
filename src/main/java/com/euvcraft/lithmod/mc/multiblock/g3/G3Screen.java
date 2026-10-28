package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
abstract class G3Screen<M extends G3Menu> extends AdvancedMachineScreen<M> {
    G3Screen(M menu,Inventory inventory,Component title){super(menu,inventory,title,238);}
    @Override protected boolean formed(){return menu.formed();}
    @Override protected boolean cabinet(){return !menu.operation;}
    private void button(String text,int x,int y,int width,int id){addRenderableWidget(Button.builder(Component.literal(text),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+y,width,14).build());}
    @Override protected void init(){super.init();if(menu.operation&&menu.writer()){button("CPU",30,18,42,0);button("RAM",76,18,42,1);button("NAND",122,18,46,2);button("Cores",30,56,64,4);button("Write",100,56,68,3);}else if(!menu.operation&&!menu.writer()){button("Repair",126,35,42,5);button("Vacuum isolation",30,56,138,6);}}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        line(g,title,8,6,160);line(g,playerInventoryTitle,8,72,160);
        line(g,Component.literal(menu.value(15)+" / "+menu.value(16)+" FE"),30,170,138);
        String state=G3ControllerBlockEntity.Status.values()[Math.clamp(menu.value(14),0,G3ControllerBlockEntity.Status.values().length-1)].name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        line(g,Component.literal((formed()?"Formed: ":"Unformed: ")+state),8,184,160);
        if(menu.writer()){String[] designs={"Compute","Memory","NAND"};int selected=menu.value(8);line(g,Component.literal("Design: "+(selected>=0&&selected<3?designs[selected]:"select above")+"; cores "+menu.value(9)),8,198,160);line(g,Component.literal("Writing: "+menu.value(17)+" / "+menu.value(18)+" ticks"),8,212,160);line(g,Component.literal(menu.operation?"Blank + redstone -> G3 reticle":"Use the operating panel above"),8,226,160);}
        else{var phase=G3ControllerBlockEntity.PrepPhase.values()[Math.clamp(menu.value(1),0,4)];line(g,Component.literal(phase.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')+" "+menu.value(12)+"/"+menu.value(13)),8,198,160);line(g,Component.literal("UPW "+menu.value(3)+" mB; vacuum "+menu.value(5)),8,212,160);line(g,Component.literal(menu.value(7)==0?(menu.value(11)==1?"Vacuum isolated":"Dedicated / central vacuum"):"Repair: filter + redstone + UPW"),8,226,160);}
    }
}

