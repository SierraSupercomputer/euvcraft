package com.euvcraft.lithmod.mc.multiblock.euv;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** Deliberate pulse tuning: markers preview settings; only paid trials update the measured trace. */
public final class EuvSourceCalibrationScreen extends AbstractContainerScreen<EuvSourceCalibrationMenu> {
    private final List<Button> controls=new ArrayList<>();private Button test,accept;
    public EuvSourceCalibrationScreen(EuvSourceCalibrationMenu menu,Inventory inv,Component title){super(menu,inv,title,320,260);}
    private void send(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        super.init();controls.clear();
        String[] labels={"−5","−1","+1","+5"};
        for(int axis=0;axis<3;axis++)for(int step=0;step<4;step++){final int id=axis*4+step;
            controls.add(addRenderableWidget(Button.builder(Component.literal(labels[step]),b->send(id)).bounds(leftPos+170+step*33,topPos+46+axis*36,30,20).build()));}
        test=addRenderableWidget(Button.builder(Component.literal("Test pulse (FE / H2 / tin)"),b->send(12)).bounds(leftPos+10,topPos+158,146,20).build());
        accept=addRenderableWidget(Button.builder(Component.literal("Accept calibration"),b->send(13)).bounds(leftPos+164,topPos+158,146,20).build());
    }
    @Override protected void containerTick(){
        super.containerTick();boolean live=menu.value(0)!=0&&menu.value(8)==0;
        for(int i=0;i<controls.size();i++){controls.get(i).active=live;int sign=i%4<2?-1:1;int amount=i%4==0||i%4==3?menu.value(21):menu.value(20);controls.get(i).setMessage(Component.literal((sign>0?"+":"−")+amount));}
        test.active=live&&menu.value(9)==0;accept.active=live&&menu.value(6)!=0&&menu.value(4)>=menu.value(19);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractBackground(g,mx,my,partial);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFFD0D0D0);
        int x=leftPos+10,y=topPos+30;g.fill(x,y,x+146,y+112,0xFF142333);
        // Desired output band and fixed pre-pulse reference.
        g.fill(x+4,y+45,x+142,y+55,0xFF255744);g.fill(x+35,y+5,x+36,y+105,0xFF91CFFF);
        int droplet=x+Math.clamp(35+menu.value(11),5,140),main=x+Math.clamp(80+menu.value(12),5,140);
        g.fill(droplet,y+90,droplet+2,y+102,0xFF55DDAA);g.fill(main,y+80,main+2,y+103,0xFFDD77FF);
        if(menu.value(4)>=0){
            int peak=Math.clamp(menu.value(10),0,2000);int previousY=y+101;
            // Display the last measured pulse, never a free score/trace for untested controls.
            for(int i=1;i<141;i++){double t=(i-80-Math.clamp(menu.value(12),-65,55))/15.0;int response=(int)Math.round(50.0*peak/1000.0*Math.exp(-.5*t*t));int nextY=y+101-Math.clamp(response,0,98);
                g.fill(x+i,Math.min(previousY,nextY),x+i+1,Math.max(previousY,nextY)+1,0xFFFFC65A);previousY=nextY;}
        }
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int width){g.text(font,font.plainSubstrByWidth(s,width),x,y,0xFF303030,false);}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        text(g,"EUV source pulse calibration",8,7,304);text(g,"Green: tin  Blue: pre  Purple: main",10,18,300);
        String[] names={"Droplet phase","Main-pulse delay","Pulse amplitude"};for(int i=0;i<3;i++)text(g,names[i]+": "+menu.value(i+1),170,34+i*36,140);
        text(g,"Markers / amber: last paid test",10,145,300);
        String[] issues={"Ready for a paid test","Form the source / install modules","Locked: source serves an active lot","Invalid JSON calibration settings","Pump down the source first","Supply FE for the test","Supply Fab Hydrogen for the test","Supply tin feedstock for the test"};
        text(g,issues[Math.clamp(menu.value(9),0,issues.length-1)],10,185,300);
        if(menu.value(4)>=0){text(g,"Last quality "+menu.value(4)+"/100; minimum "+menu.value(19)+(menu.value(6)==0?" (settings changed)":""),10,198,300);
            text(g,"Phase "+direction(menu.value(11))+"; delay "+direction(menu.value(12))+"; amplitude "+direction(menu.value(13)),10,211,300);}
        text(g,"Test: "+menu.value(22)+" FE / "+menu.value(23)+" mB H2 / "+menu.value(24)+" tin ticks",10,242,300);
        text(g,menu.value(5)!=0?"Accepted — source yield x"+menu.value(7)/1000.0:"Adjust, test, then accept. No scanner needed.",10,226,300);
    }
    private String direction(int error){return error<0?"increase":error>0?"decrease":"aligned";}
}
