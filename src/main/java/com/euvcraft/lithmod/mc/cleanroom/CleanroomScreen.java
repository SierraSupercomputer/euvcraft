package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.components.Button;
import com.euvcraft.lithmod.core.cleanroom.FilterReport;
import java.util.Locale;
/** Status stays inside an opaque panel, below the fixed player inventory. */
public final class CleanroomScreen extends FabMachineScreen<CleanroomMenu>{
 private Button roomTab,filterTab,previous,next;
 public CleanroomScreen(CleanroomMenu m,Inventory i,Component t){super(m,i,t,284,232);inventoryLabelY=72;}
 @Override protected int progressBarX(){return 0;}@Override protected int progressBarY(){return 0;}
 @Override protected int energyBarX(){return 12;}@Override protected int energyBarY(){return 18;}@Override protected boolean hasProgressBar(){return false;}
 @Override protected boolean hasEnergyBar(){return !menu.filtersTab();}
 private static String text(String key,Object... args){return Component.translatable("screen.lithmod.cleanroom."+key,args).getString();}
 @Override protected void init(){
  super.init();if(menu.filter())return;
  roomTab=addRenderableWidget(Button.builder(Component.translatable("screen.lithmod.cleanroom.room_tab"),b->tab(false)).bounds(leftPos+184,topPos+4,42,16).build());
  filterTab=addRenderableWidget(Button.builder(Component.translatable("screen.lithmod.cleanroom.filters_tab"),b->tab(true)).bounds(leftPos+230,topPos+4,46,16).build());
  previous=addRenderableWidget(Button.builder(Component.literal("<"),b->send(CleanroomMenu.PREVIOUS_PAGE)).bounds(leftPos+8,topPos+212,24,16).build());
  next=addRenderableWidget(Button.builder(Component.literal(">"),b->send(CleanroomMenu.NEXT_PAGE)).bounds(leftPos+252,topPos+212,24,16).build());updateButtons();
 }
 private void send(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
 private void tab(boolean show){menu.setFiltersTab(show);send(show?CleanroomMenu.FILTERS_TAB:CleanroomMenu.ROOM_TAB);updateButtons();}
 private void updateButtons(){if(roomTab==null)return;roomTab.active=menu.filtersTab();filterTab.active=!menu.filtersTab();previous.visible=next.visible=menu.filtersTab();previous.active=menu.reportPage()>0;next.active=menu.reportPage()+1<menu.reportPages();}
 @Override public void containerTick(){super.containerTick();updateButtons();}
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
  if(menu.filtersTab()&&mx>=leftPos+8&&mx<leftPos+276&&my>=topPos+48&&my<topPos+208&&vertical!=0){
   if(vertical>0&&menu.reportPage()>0)send(CleanroomMenu.PREVIOUS_PAGE);
   else if(vertical<0&&menu.reportPage()+1<menu.reportPages())send(CleanroomMenu.NEXT_PAGE);
   return true;
  }
  return super.mouseScrolled(mx,my,horizontal,vertical);
 }
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){
  super.extractBackground(g,mx,my,partial);if(!menu.filtersTab())return;
  for(int i=0;i<FilterReport.PAGE_SIZE;i++){int y=topPos+48+i*32;g.fill(leftPos+8,y,leftPos+266,y+31,i%2==0?0xffd8d8d8:0xffc8c8c8);}
  g.fill(leftPos+270,topPos+48,leftPos+276,topPos+208,0xff909090);
  int height=Math.max(12,160/menu.reportPages()),y=menu.reportPages()==1?0:(160-height)*menu.reportPage()/(menu.reportPages()-1);
  g.fill(leftPos+270,topPos+48+y,leftPos+276,topPos+48+y+height,0xff505050);
 }
 private void line(GuiGraphicsExtractor g,String s,int x,int y){g.text(font,font.plainSubstrByWidth(s,268-x+8),x,y,0xff404040,false);}
 @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){g.text(font,font.plainSubstrByWidth(title.getString(),menu.filter()?268:172),8,6,0xff404040,false);
  if(menu.filtersTab()){filterRows(g);return;}
  line(g,menu.energy()+" / "+menu.energyCapacity()+" FE",30,20);line(g,playerInventoryTitle.getString(),8,72);
  if(menu.filter()){line(g,"Filter medium",64,58);line(g,"Medium life: "+menu.value(1)+" s",8,174);line(g,"Requires FE and an enclosed room",8,188);line(g,"Service pauses filtration gradually",8,202);return;}
  String status=switch(menu.value(0)){case 1->"Enclosed";case 2->"Breached: inspect shell/controller";case 3->"Unknown: room chunk unavailable";default->"Unformed: build a sealed cuboid";};
  line(g,status,30,34);
  line(g,"Interior: "+menu.value(10)+" x "+menu.value(11)+" x "+menu.value(12)+" ("+menu.value(4)+")",30,48);
  line(g,"Clean: "+(menu.value(1)/100.0)+"%   Yield: "+(menu.value(3)/10.0)+"%",30,62);
  line(g,"PPM: "+menu.value(2)+"   Trend: "+(menu.value(9)/10000.0)+"% / s",8,174);
  line(g,"Power: "+(menu.value(8)==1?"on":"off")+"   Filters: "+menu.value(5)+" / "+menu.value(6),8,188);
  line(g,"Doors: "+menu.value(7)+"   Leak factor: "+String.format(java.util.Locale.ROOT,"%.2f",menu.value(18)/100.0),8,202);
  line(g,menu.value(14)==1?"Fault: "+menu.value(15)+", "+menu.value(16)+", "+menu.value(17):menu.value(13)>=0?"To pristine: ~"+menu.value(13)+" s":"Recovery: add powered filtration",8,216);

 }
 private void filterRows(GuiGraphicsExtractor g){
  line(g,text("filter_summary",menu.reportTotal(),menu.reportWorking(),menu.reportAttention()),8,28);
  if(menu.reportTotal()==0){line(g,text("no_filters"),14,56);return;}
  var rows=menu.reportRows();for(int i=0;i<rows.size();i++){
   var row=rows.get(i);int y=50+i*32;
   g.text(font,font.plainSubstrByWidth(text("coordinates",row.x(),row.y(),row.z()),246),14,y,0xff303030,false);
   g.text(font,font.plainSubstrByWidth(text("filter_status."+row.state().name().toLowerCase(Locale.ROOT)),140),14,y+10,row.state().attention()?0xffa02b20:0xff236323,false);
   boolean unknown=row.state()==FilterReport.State.UNAVAILABLE||row.state()==FilterReport.State.MISSING;
   g.text(font,text("filter_spare",unknown?"?":row.spare()),160,y+10,0xff404040,false);
   String energy=unknown?"?":compact(row.energy())+"/"+compact(row.capacity());
   g.text(font,text("filter_energy",energy),14,y+20,0xff404040,false);
   g.text(font,text("filter_life",unknown?"?":((row.remainingTicks()+19L)/20)+" s"),160,y+20,0xff404040,false);
  }
  String page=text("filter_page",menu.reportPage()+1,menu.reportPages());g.text(font,page,(imageWidth-font.width(page))/2,216,0xff404040,false);
 }
 private static String compact(int value){return value>=1000000?String.format(Locale.ROOT,"%.1fM",value/1000000.0):Integer.toString(value);}
}
