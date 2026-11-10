package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
abstract class G3Screen<M extends G3Menu> extends AdvancedMachineScreen<M> {
    private final java.util.List<Button> tuningButtons = new java.util.ArrayList<>();
    G3Screen(M menu,Inventory inventory,Component title){super(menu,inventory,title,238);}
    @Override protected boolean formed(){return menu.formed();}
    @Override protected boolean cabinet(){return !menu.operation;}
    private Button button(String text,int x,int y,int width,int id){
        return addRenderableWidget(Button.builder(Component.literal(text),b->{
            if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
        }).bounds(leftPos+x,topPos+y,width,14).build());
    }
    @Override protected void init(){
        super.init();
        tuningButtons.clear();
        if(menu.operation&&menu.writer()){
            button("CPU",30,18,42,0);button("RAM",76,18,42,1);button("NAND",122,18,46,2);
            button("Tier",30,56,40,5);button("Cores",74,56,42,4);button("Write",120,56,48,3);
        }else if(!menu.operation&&!menu.writer()){
            button("Repair",126,35,42,5);button("Vacuum isolation",30,56,138,6);
            tuningButtons.add(button("-",30,18,24,7));
            tuningButtons.add(button("+",58,18,24,8));
            tuningButtons.add(button("Accept tuning",86,18,82,9));
        }
        updateTuningButtons();
    }
    @Override protected void containerTick(){super.containerTick();updateTuningButtons();}
    private void updateTuningButtons(){
        for(int i=0;i<tuningButtons.size();i++){
            Button button=tuningButtons.get(i);
            button.visible=menu.value(19)>=2;
            button.active=button.visible&&formed()&&menu.value(10)==0
                    &&(i!=2||menu.value(21)>=menu.value(23));
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        line(g,title,8,6,160);line(g,playerInventoryTitle,8,72,160);
        line(g,Component.literal(menu.value(15)+" / "+menu.value(16)+" FE"),30,170,138);
        String state=G3ControllerBlockEntity.Status.values()[Math.clamp(menu.value(14),0,G3ControllerBlockEntity.Status.values().length-1)].name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        line(g,Component.literal((formed()?"Formed: ":"Unformed: ")+state),8,182,160);
        if(menu.writer()){String[] designs={"Compute","Memory","NAND"};int selected=menu.value(8);line(g,Component.literal("T"+menu.value(24)+" "+(selected>=0&&selected<3?designs[selected]:"select above")+"; cores "+menu.value(9)),8,198,160);line(g,Component.literal("Writing: "+menu.value(17)+" / "+menu.value(18)+" ticks"),8,212,160);line(g,Component.literal(menu.operation?"Blank + redstone -> G3 reticle":"Use the operating panel above"),8,226,160);}
        else {
            var phase=G3ControllerBlockEntity.PrepPhase.values()[Math.clamp(menu.value(1),0,4)];
            String phaseName=phase.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
            if(menu.value(25)>0&&phase==G3ControllerBlockEntity.PrepPhase.VACUUM_DEP_ETCH)
                phaseName=menu.value(25)<=2?"polishing":"implanting";
            if(menu.value(25)>=3&&phase==G3ControllerBlockEntity.PrepPhase.POWERED_DRY)
                phaseName="thermal activation";
            line(g,Component.literal(phaseName+" "+menu.value(12)+"/"+menu.value(13)),8,193,160);
            line(g,Component.literal("UPW "+menu.value(3)+" mB; vacuum "+menu.value(5)),8,204,160);
            int profile=menu.value(19);
            if(profile>=2){
                String reading=menu.value(25)>=3?"screen.lithmod.t8.dose":profile==2?"screen.lithmod.g3_etch.depth":"screen.lithmod.g3_etch.uniformity";
                line(g,Component.translatable(reading,menu.value(20),menu.value(21)/10),8,215,160);
            } else if(profile==1){
                line(g,Component.translatable("screen.lithmod.g3_etch.nand"),8,215,160);
            }
            if(menu.value(25)==1||menu.value(25)==2)line(g,Component.literal(menu.value(25)==2?"Memory head":"Standard head"),8,215,160);
            Component hint;
            if(menu.value(7)!=0)hint=Component.literal("Repair: filter + redstone + UPW");
            else if(menu.value(25)>0&&menu.value(25)<=2&&menu.value(14)==G3ControllerBlockEntity.Status.ETCH_CALIBRATION_REQUIRED.ordinal())
                hint=Component.literal("Calibrate in lower cabinet");
            else if(profile>=2&&menu.value(22)==0)hint=Component.translatable(menu.operation?"screen.lithmod.g3_etch.use_cabinet":"screen.lithmod.g3_etch.adjust");
            else if(menu.value(25)>0 && menu.value(14)==G3ControllerBlockEntity.Status.NO_REAGENT.ordinal())
                hint=Component.literal(menu.value(25)<=2?"Cabinet: slurry + pad":"Cabinet: dopant charge");
            else if(menu.value(11)==1)hint=Component.literal("Vacuum isolated");
            else if(profile>=2)hint=Component.translatable("screen.lithmod.g3_etch.ready");
            else hint=Component.literal("Dedicated / central vacuum");
            line(g,hint,8,226,160);
        }
    }
}
