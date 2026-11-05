package com.euvcraft.lithmod.mc.cleanroom;

import com.euvcraft.lithmod.core.cleanroom.FilterReport;
import com.euvcraft.lithmod.core.cleanroom.FilterReport.Row;
import com.euvcraft.lithmod.core.cleanroom.FilterReport.State;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** Read-only snapshots of this controller's known filters, without chunk acquisition or resource use. */
public final class CleanroomFilterDiagnostics {
    public static List<Row> snapshot(CleanroomControllerBlockEntity controller){
        if(!(controller.getLevel() instanceof ServerLevel level)||controller.isRemoved())return List.of();
        var rows=new ArrayList<Row>();var rules=CleanroomRulesLoader.rules();
        for(var pos:controller.knownFilters()){
            if(!level.hasChunkAt(pos)){rows.add(new Row(pos.getX(),pos.getY(),pos.getZ(),State.UNAVAILABLE,-1,-1,-1,-1));continue;}
            if(!(level.getBlockEntity(pos) instanceof CleanroomFilterBlockEntity filter)||filter.isRemoved()){
                rows.add(new Row(pos.getX(),pos.getY(),pos.getZ(),State.MISSING,-1,-1,-1,-1));continue;
            }
            int energy=filter.buffer().getAmountAsInt(),capacity=filter.buffer().getCapacityAsInt(),remaining=filter.remaining();
            int spare=filter.slot(0).is(CleanroomItems.MEDIUM.get())?filter.slot(0).getCount():0;
            long needed=remaining>=20?0:(20L-remaining+rules.filterMediaTicks()-1)/rules.filterMediaTicks();
            State state=filter.definition().isEmpty()?State.NO_DEFINITION:
                energy<rules.filterFePerTick()*20L?State.NO_POWER:
                spare<needed?State.NO_MEDIUM:
                controller.state()!=CleanroomControllerBlockEntity.ENCLOSED?State.ROOM_NOT_READY:
                !controller.powered()?State.CONTROLLER_OFF:
                filter.working()?State.FILTERING:State.READY;
            rows.add(new Row(pos.getX(),pos.getY(),pos.getZ(),state,energy,capacity,spare,remaining));
        }
        return List.copyOf(rows);
    }
    private CleanroomFilterDiagnostics(){}
}
