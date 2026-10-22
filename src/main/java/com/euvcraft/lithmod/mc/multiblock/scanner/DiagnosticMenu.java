package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.registry.ModItems;import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.world.SimpleContainer;import net.minecraft.world.entity.player.*;import net.minecraft.world.inventory.*;import net.minecraft.world.item.ItemStack;import org.jspecify.annotations.Nullable;
/** Only public graph samples cross the menu; hidden wafer data and target coefficients do not. */
public final class DiagnosticMenu extends AbstractContainerMenu{
 private final @Nullable ScannerControllerBlockEntity scanner;private final @Nullable ScannerMemberBlockEntity panel;private final BlockPos pos;private final ContainerData values;private int a,b,c;
 public DiagnosticMenu(int id,Inventory inv,@Nullable ScannerControllerBlockEntity scanner,BlockPos pos){super(ScannerMachines.DIAGNOSTIC_MENU.get(),id);this.scanner=scanner;this.pos=pos.immutable();panel=scanner!=null&&scanner.getLevel()!=null&&scanner.getLevel().getBlockEntity(pos) instanceof ScannerMemberBlockEntity m?m:null;
  if(scanner!=null){a=scanner.diagnostic().a();b=scanner.diagnostic().b();c=scanner.diagnostic().c();}
  var paper=scanner==null?new SimpleContainer(1):scanner.diagnosticPaper;
  addSlot(new Slot(paper,0,283,145){@Override public boolean mayPlace(ItemStack stack){return stack.is(ModItems.BURN_PAPER.get())&&(scanner==null||!scanner.inputsLocked());}@Override public boolean mayPickup(Player p){return scanner==null||!scanner.inputsLocked();}@Override public void setChanged(){super.setChanged();if(scanner!=null)scanner.setChanged();}});
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,79+col*18,190+row*18));for(int col=0;col<9;col++)addSlot(new Slot(inv,col,79+col*18,244));
  values=scanner==null?new SimpleContainerData(94):new ContainerData(){public int get(int i){var f=scanner.diagnostic();if(i>=11&&i<52)return (int)Math.round(f.targetAt(-2+(i-11)*.1)*100);if(i>=52&&i<93)return (int)Math.round(f.candidateAt(-2+(i-52)*.1)*100);return switch(i){case 0->scanner.isFormed()?1:0;case 1->scanner.diagnosed()?1:0;case 2->f.quality();case 3->f.a();case 4->f.b();case 5->f.c();case 6->scanner.inputsLocked()?1:0;case 7->scanner.diagnosticMinimum();case 8->(int)Math.round(scanner.diagnosticPreview()*1000);case 9->scanner.diagnosticQuality();case 10->scanner.immersion()?1:0;case 93->scanner.diagnosticPlotted()?1:0;default->0;};}public void set(int i,int v){}public int getCount(){return 94;}};addDataSlots(values);
 }
 public DiagnosticMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,null,buf.readBlockPos());}public int value(int i){return values.get(i);}
 @Override public boolean stillValid(Player p){return scanner!=null&&panel!=null&&scanner.getLevel()==p.level()&&!scanner.isRemoved()&&scanner.getLevel().getBlockEntity(scanner.getBlockPos())==scanner&&scanner.getLevel().getBlockEntity(pos)==panel&&panel.scanner()==scanner&&panel.structureKind()==BlockKind.DIAGNOSTIC_PANEL&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
 @Override public boolean clickMenuButton(Player p,int id){if(!(p instanceof net.minecraft.server.level.ServerPlayer)||p.containerMenu!=this||!stillValid(p)||scanner.inputsLocked())return false;
  if(id>=0&&id<=400){a=id-200;return true;}if(id>=401&&id<=801){b=id-601;return true;}if(id>=802&&id<=1202){c=id-1002;return true;}
  if(id==2000)return scanner.plotDiagnostic(a,b,c);if(id==2001)return scanner.acceptDiagnostic();return false;
 }
 @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size()||scanner==null||scanner.inputsLocked()||!stillValid(p))return ItemStack.EMPTY;var slot=slots.get(index);var stack=slot.getItem();var original=stack.copy();if(index==0){if(!moveItemStackTo(stack,1,slots.size(),true))return ItemStack.EMPTY;}else{if(!stack.is(ModItems.BURN_PAPER.get())||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,original);scanner.setChanged();return original;}
}


