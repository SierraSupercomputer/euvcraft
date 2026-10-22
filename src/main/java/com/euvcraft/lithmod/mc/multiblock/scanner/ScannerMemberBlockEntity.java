package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
public final class ScannerMemberBlockEntity extends AdvancedMemberBlockEntity {
 @Override public boolean supportsOwner(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner candidate){return candidate instanceof ScannerControllerBlockEntity;}

 private @Nullable UUID installation;
 public ScannerMemberBlockEntity(BlockPos pos,BlockState state){super(ScannerMachines.MEMBER_BE.get(),pos,state);}
 @Override public BlockKind structureKind(){return getBlockState().getBlock() instanceof ScannerMemberBlock block?block.kind():BlockKind.OTHER;}
 public UUID installation(){
  if(level==null||level.isClientSide())throw new IllegalStateException("Server installation identity only");
  if(installation==null){installation=UUID.randomUUID();setChanged();}return installation;
 }
 public @Nullable ScannerControllerBlockEntity scanner(){
  if(level==null)return null;
  if(structureKind()==BlockKind.OPERATING_PANEL)return level.getBlockEntity(worldPosition.below()) instanceof ScannerControllerBlockEntity s?s:null;
  var owner=controller();
  return owner instanceof ScannerControllerBlockEntity s&&level.getBlockEntity(s.getBlockPos())==s&&!s.isRemoved()?s:null;
 }
 @Override public Component getDisplayName(){return Component.translatable("block.lithmod."+(structureKind()==BlockKind.DIAGNOSTIC_PANEL?"scanner_diagnostic_panel":structureKind()==BlockKind.CALIBRATION_PANEL?"scanner_calibration_panel":"scanner_operating_panel"));}
 @Override public @Nullable AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){
  var s=scanner();if(s==null)return null;
  if(structureKind()==BlockKind.DIAGNOSTIC_PANEL)return new DiagnosticMenu(id,inventory,s,worldPosition);
  return structureKind()==BlockKind.CALIBRATION_PANEL?new ScannerDashboardMenu(id,inventory,s,worldPosition,true):new ScannerOperatingMenu(id,inventory,s,worldPosition);
 }
 @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);try{installation=UUID.fromString(input.getString("installation").orElse(""));}catch(IllegalArgumentException e){installation=null;}}
 @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);if(installation!=null)output.putString("installation",installation.toString());}
}



