package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import com.euvcraft.lithmod.core.cleanroom.FilterReport;
import java.util.List;
import java.util.ArrayList;
public final class CleanroomMenu extends FabMachineMenu {
 @Override protected void addDataSlots(ContainerData values){super.addDataSlots(new WideContainerData(values));}
 private final ContainerData room;
 private final boolean filter;
 public static final int ROOM_TAB=0,FILTERS_TAB=1,PREVIOUS_PAGE=2,NEXT_PAGE=3;
 private static final int HEADER=7,ROW_WIDTH=8;
 private final ContainerData report=new SimpleContainerData(HEADER+FilterReport.PAGE_SIZE*ROW_WIDTH);
 private boolean filtersTab;
 private int requestedPage;
 private long lastReportTick=Long.MIN_VALUE;
 public CleanroomMenu(int id,Inventory inv,FabMachineBlockEntity machine,boolean filter){
  super(CleanroomBlocks.MENU.get(),id,inv,machine,filter?1:0,filter?1:0);this.filter=filter;
  room=machine==null?new SimpleContainerData(19):new ContainerData(){public int get(int i){
   if(machine instanceof CleanroomFilterBlockEntity f)return switch(i){case 0->1;case 1->f.remaining()/20;case 2->f.working()?1:0;default->0;};
   var r=(CleanroomControllerBlockEntity)machine;var b=r.bounds();return switch(i){case 0->r.state();case 1->r.cleanliness()/100;case 2->r.ppm();case 3->r.roomYield();case 4->b==null?0:b.volume();case 5->r.activeFilters();case 6->r.filterCount();case 7->r.doors();case 8->r.powered()?1:0;case 9->r.trend();case 10->b==null?0:b.maxX()-b.minX()-1;case 11->b==null?0:b.maxY()-b.minY()-1;case 12->b==null?0:b.maxZ()-b.minZ()-1;case 13->r.trend()>0?(1000000-r.cleanliness()+r.trend()-1)/r.trend():-1;case 14->r.faultPos()==null?0:1;case 15->r.faultPos()==null?0:r.faultPos().getX();case 16->r.faultPos()==null?0:r.faultPos().getY();case 17->r.faultPos()==null?0:r.faultPos().getZ();case 18->b==null?0:(int)((long)b.volume()*100/CleanroomRulesLoader.rules().referenceVolume());default->0;};}
   public void set(int i,int v){}public int getCount(){return 19;}};addDataSlots(room);addDataSlots(report);
 }
 public CleanroomMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,null,readFilter(buf));}
 private static boolean readFilter(RegistryFriendlyByteBuf buf){buf.readBlockPos();return buf.readBoolean();}
 @Override protected void addMachineSlots(){if(machineSlots().getContainerSize()>0)addCustomSlot(new Slot(machineSlots(),0,80,35){@Override public boolean mayPlace(ItemStack s){return s.is(CleanroomItems.MEDIUM.get());}});}
 public boolean filter(){return filter;}public int value(int i){return room.get(i);}
 @Override protected Slot addSlot(Slot slot){
  if(slot.container instanceof Inventory inv)return super.addSlot(new Slot(inv,slot.getContainerSlot(),slot.x,slot.y){
   @Override public boolean isActive(){return !filtersTab;}
  });
  return super.addSlot(slot);
 }
 public boolean filtersTab(){return filtersTab;}
 public void setFiltersTab(boolean show){filtersTab=!filter&&show;}
 @Override public boolean clickMenuButton(Player player,int button){
  if(filter||!(blockEntity instanceof CleanroomControllerBlockEntity)||player.containerMenu!=this||!stillValid(player))return false;
  switch(button){
   case ROOM_TAB->filtersTab=false;
   case FILTERS_TAB->{filtersTab=true;requestedPage=0;}
   case PREVIOUS_PAGE->{if(!filtersTab)return false;requestedPage=Math.max(0,requestedPage-1);}
   case NEXT_PAGE->{if(!filtersTab)return false;requestedPage=Math.min(reportPages()-1,requestedPage+1);}
   default->{return false;}
  }
  refreshReport(true);return true;
 }
 private void refreshReport(boolean force){
  if(!filtersTab||!(blockEntity instanceof CleanroomControllerBlockEntity controller)||controller.isRemoved()||controller.getLevel()==null)return;
  long now=controller.getLevel().getGameTime();
  if(!force&&lastReportTick!=Long.MIN_VALUE&&now-lastReportTick<20)return;
  lastReportTick=now;var page=FilterReport.page(CleanroomFilterDiagnostics.snapshot(controller),requestedPage);requestedPage=page.page();
  report.set(0,page.total());report.set(1,page.page());report.set(2,page.pages());report.set(3,page.working());report.set(4,page.attention());report.set(5,page.unavailable());report.set(6,page.rows().size());
  for(int i=0;i<FilterReport.PAGE_SIZE;i++){
   int at=HEADER+i*ROW_WIDTH;
   if(i>=page.rows().size()){for(int j=0;j<ROW_WIDTH;j++)report.set(at+j,0);continue;}
   var row=page.rows().get(i);int[] values={row.x(),row.y(),row.z(),row.state().ordinal(),row.energy(),row.capacity(),row.spare(),row.remainingTicks()};
   for(int j=0;j<ROW_WIDTH;j++)report.set(at+j,values[j]);
  }
 }
 @Override public void broadcastChanges(){refreshReport(false);super.broadcastChanges();}
 public int reportTotal(){return Math.max(0,report.get(0));}
 public int reportPage(){return Math.max(0,report.get(1));}
 public int reportPages(){return Math.max(1,report.get(2));}
 public int reportWorking(){return Math.max(0,report.get(3));}
 public int reportAttention(){return Math.max(0,report.get(4));}
 public int reportUnavailable(){return Math.max(0,report.get(5));}
 public List<FilterReport.Row> reportRows(){
  var rows=new ArrayList<FilterReport.Row>();var states=FilterReport.State.values();
  for(int i=0;i<Math.clamp(report.get(6),0,FilterReport.PAGE_SIZE);i++){
   int at=HEADER+i*ROW_WIDTH;var state=states[Math.clamp(report.get(at+3),0,states.length-1)];boolean unknown=state==FilterReport.State.UNAVAILABLE||state==FilterReport.State.MISSING;
   // Data halves arrive as standard menu properties; transitional values must not crash rendering.
   rows.add(new FilterReport.Row(report.get(at),report.get(at+1),report.get(at+2),state,
    unknown?-1:Math.max(0,report.get(at+4)),unknown?-1:Math.max(0,report.get(at+5)),unknown?-1:Math.max(0,report.get(at+6)),unknown?-1:Math.max(0,report.get(at+7))));
  }
  return List.copyOf(rows);
 }
}
