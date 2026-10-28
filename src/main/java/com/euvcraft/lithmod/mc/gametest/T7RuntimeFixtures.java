package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.cleanroom.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import com.euvcraft.lithmod.mc.multiblock.euv.*;
import com.euvcraft.lithmod.mc.multiblock.hydrogen.*;
import com.euvcraft.lithmod.mc.multiblock.vacuum.*;
import com.euvcraft.lithmod.mc.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Real registered machines and cuboid rooms, with deterministic loaded geometry. */
final class T7RuntimeFixtures {
    private T7RuntimeFixtures(){}
    record Cell(BlockPos anchor,List<BlockPos> positions,FabMachineBlockEntity machine){}
    static final class Arena implements AutoCloseable {
        final ServerLevel level;
        final GameTestHelper h;
        final Direction facing;
        final Set<BlockPos> written=new LinkedHashSet<>();
        Arena(GameTestHelper h){this(h,Direction.NORTH);}
        Arena(GameTestHelper h,Direction facing){this.h=h;this.level=h.getLevel();this.facing=facing;}
        BlockPos cellPos(BlockPos origin,int u,int v,int d){return switch(facing){case NORTH->origin.offset(u,v,d);case SOUTH->origin.offset(-u,v,-d);case EAST->origin.offset(-d,v,u);case WEST->origin.offset(d,v,-u);default->throw new IllegalArgumentException();};}
        void oriented(BlockPos pos,Block block){var state=block.defaultBlockState();if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing);put(pos,state);}
        BlockPos anchor(){return h.absolutePos(new BlockPos(4,4,4));}
        void put(BlockPos pos,Block block){put(pos,block.defaultBlockState());}
        void put(BlockPos pos,BlockState state){written.add(pos.immutable());level.setBlock(pos,state,3);}
        Cell box(BlockPos anchor,int width,int height,int depth,Block casing,Block controller,Block panel,Map<BlockPos,Block> local){
            var positions=new ArrayList<BlockPos>();
            for(int u=0;u<width;u++)for(int v=0;v<height;v++)for(int d=0;d<depth;d++){var pos=cellPos(anchor,u,v,d);oriented(pos,casing);positions.add(pos);}
            oriented(anchor,controller);oriented(anchor.above(),panel);for(var e:local.entrySet())oriented(cellPos(anchor,e.getKey().getX(),e.getKey().getY(),e.getKey().getZ()),e.getValue());
            var machine=(FabMachineBlockEntity)level.getBlockEntity(anchor);
            if(machine instanceof G3ControllerOwner owner)owner.revalidate();
            if(machine instanceof AdvancedOwner owner)owner.revalidate();
            if(machine instanceof EuvFormationOwner owner)owner.revalidate();
            machine.serverTick();
            return new Cell(anchor,List.copyOf(positions),machine);
        }
        Cell writer(){return box(anchor(),3,3,5,G3Machines.CASING.get(),G3Machines.RETICLE_WRITER_CONTROLLER.get(),G3Machines.OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),G3Machines.POWER_PORT.get(),new BlockPos(0,1,1),G3Machines.WRITING_MODULE.get(),new BlockPos(1,1,1),G3Machines.METROLOGY_MODULE.get()));}
        Cell prep(){return prep(anchor());}
        Cell prep(BlockPos anchor){return box(anchor,5,5,7,G3Machines.CASING.get(),G3Machines.DEPOSITION_ETCHER_CONTROLLER.get(),G3Machines.OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),G3Machines.POWER_PORT.get(),new BlockPos(0,1,1),G3Machines.DEPOSITION_MODULE.get(),new BlockPos(1,1,1),G3Machines.RINSE_MODULE.get(),new BlockPos(2,1,1),G3Machines.VACUUM_MODULE.get(),new BlockPos(3,1,1),G3Machines.WATER_INLET.get()));}
        Cell hydrogen(){return box(anchor(),2,2,3,HydrogenMachines.CASING.get(),HydrogenMachines.CONTROLLER.get(),HydrogenMachines.OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),HydrogenMachines.POWER_PORT.get(),new BlockPos(0,0,1),HydrogenMachines.GAS_INLET.get(),new BlockPos(1,0,1),HydrogenMachines.GAS_OUTLET.get()));}
        Cell plant(){return box(anchor(),3,3,5,VacuumMachines.CASING.get(),VacuumMachines.CONTROLLER.get(),VacuumMachines.OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),VacuumMachines.POWER_PORT.get(),new BlockPos(0,1,1),VacuumMachines.CONDUIT.get(),new BlockPos(2,1,1),VacuumMachines.SERVICE_OUTLET.get()));}
        Cell scanner(){return box(anchor(),5,5,7,EuvMachines.SCANNER_CASING.get(),EuvMachines.SCANNER_CONTROLLER.get(),EuvMachines.SCANNER_OPERATING_PANEL.get(),Map.ofEntries(
            Map.entry(new BlockPos(1,0,0),EuvMachines.SCANNER_POWER_PORT.get()),Map.entry(new BlockPos(4,1,1),EuvMachines.SCANNER_EXPOSURE_MODULE.get()),Map.entry(new BlockPos(0,1,1),EuvMachines.SCANNER_OPTICS_MODULE.get()),Map.entry(new BlockPos(1,1,1),EuvMachines.SCANNER_STAGE_MODULE.get()),Map.entry(new BlockPos(2,1,1),EuvMachines.SCANNER_FILTRATION_MODULE.get()),Map.entry(new BlockPos(3,1,1),EuvMachines.SCANNER_METROLOGY_MODULE.get()),Map.entry(new BlockPos(0,1,2),EuvMachines.SCANNER_VACUUM_MODULE.get()),Map.entry(new BlockPos(1,1,2),EuvMachines.SCANNER_DIAGNOSTIC_PANEL.get()),Map.entry(new BlockPos(2,1,2),EuvMachines.SCANNER_WATER_INLET.get()),Map.entry(new BlockPos(3,1,2),EuvMachines.SCANNER_CALIBRATION_PANEL.get())));}
        Cell source(){return box(anchor().offset(9,0,0),3,3,5,EuvMachines.LIGHT_SOURCE_CASING.get(),EuvMachines.LIGHT_SOURCE_CONTROLLER.get(),EuvMachines.LIGHT_SOURCE_OPERATING_PANEL.get(),Map.of(new BlockPos(1,0,0),EuvMachines.LIGHT_SOURCE_POWER_PORT.get(),new BlockPos(2,1,2),EuvMachines.LIGHT_SOURCE_TIN_INLET.get(),new BlockPos(2,1,1),EuvMachines.LIGHT_SOURCE_HYDROGEN_INLET.get(),new BlockPos(0,1,2),EuvMachines.LIGHT_SOURCE_VACUUM_MODULE.get()));}
        void beam(){for(int x=5;x<=8;x++)put(anchor().offset(x,1,1),EuvMachines.BEAM_LINE.get());}
        Room room(Cell cell,int width,int height,int depth){
            var min=cell.anchor().offset(-2,-2,-2);var max=cell.anchor().offset(width+1,height+1,depth+1);
            for(var mutable:BlockPos.betweenClosed(min,max)){var pos=mutable.immutable();boolean shell=pos.getX()==min.getX()||pos.getX()==max.getX()||pos.getY()==min.getY()||pos.getY()==max.getY()||pos.getZ()==min.getZ()||pos.getZ()==max.getZ();
                if(shell){if(!level.getBlockState(pos).is(EuvMachines.BEAM_LINE.get()))put(pos,CleanroomBlocks.WALL.get());}else if(!written.contains(pos))put(pos,Blocks.AIR);
            }
            var controllerPos=min.offset(1,1,0);put(controllerPos,CleanroomBlocks.CONTROLLER.get());var controller=(CleanroomControllerBlockEntity)level.getBlockEntity(controllerPos);controller.revalidate();h.assertTrue(controller.state()==CleanroomControllerBlockEntity.ENCLOSED,"Native enclosing cuboid validates: "+controller.fault());return new Room(this,controller,min.offset(0,1,1));
        }
        BlockPos block(Cell cell,Block block){return cell.positions().stream().filter(p->level.getBlockState(p).is(block)).findFirst().orElseThrow();}
        @Override public void close(){
            for(var pos:written)if(level.getBlockEntity(pos) instanceof Container container)container.clearContent();
            for(var pos:written)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            if(!written.isEmpty()){int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;for(var p:written){minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());}for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(minX-2,minY-2,minZ-2,maxX+3,maxY+3,maxZ+3)))item.discard();}
        }
    }
    record Room(Arena arena,CleanroomControllerBlockEntity controller,BlockPos breach){
        void open(){arena.put(breach,Blocks.AIR);controller.markTopologyDirty();controller.validateKnown();}
        void seal(){arena.put(breach,CleanroomBlocks.WALL.get());controller.markTopologyDirty();controller.validateKnown();}
    }
    static ItemStack lot(int wafers,boolean prepared){var entries=new ArrayList<Wafer>();for(int i=0;i<wafers;i++)entries.add(Wafer.unprocessed());var contents=FoupContents.of(UUID.randomUUID(),25,entries,WaferSubstrate.ELECTRONIC_GRADE);if(prepared){int[] factors=new int[wafers];Arrays.fill(factors,1000);contents=contents.preparedG3(1000,factors);}var stack=FoupItems.with(ModItems.FOUP_MK2.get(),contents);stack.set(DataComponents.CUSTOM_NAME,Component.literal("T7 Mk2 lot"));return stack;}
    static ServerPlayer player(GameTestHelper h,BlockPos pos){var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"t7-test"),false);var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);return player;}
    static Item tin(){var tag=net.minecraft.tags.TagKey.<Item>create(Registries.ITEM,Identifier.parse("c:ingots/tin"));return BuiltInRegistries.ITEM.stream().filter(i->new ItemStack(i).is(tag)).findFirst().orElseThrow();}
    static void align(EuvScannerControllerBlockEntity scanner){var target=scanner.alignment();int[] goals={target.targetX(),target.targetY(),target.targetFocus()};for(int axis=0;axis<3;axis++)for(int i=0;i<40;i++){var current=scanner.alignment();int value=axis==0?current.x():axis==1?current.y():current.focus();if(value==goals[axis])break;if(!scanner.calibrationControl(axis*2+(value<goals[axis]?1:0)))throw new IllegalStateException("Calibration control refused");}if(!scanner.calibrationControl(6))throw new IllegalStateException("Alignment acceptance refused");var fit=scanner.diagnostic();scanner.diagnosticPaper.setItem(0,new ItemStack(ModItems.BURN_PAPER.get()));if(!scanner.plotDiagnostic(fit.targetA(),fit.targetB(),fit.targetC())||!scanner.acceptDiagnostic())throw new IllegalStateException("Diagnostic acceptance refused");}
}
