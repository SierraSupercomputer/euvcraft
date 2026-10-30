package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Operating screen for EUV Light Source (operating panel). Fits 176x238 with tin slot, player inventory, status. */
public final class EuvLightSourceOperatingScreen extends AdvancedMachineScreen<EuvLightSourceOperatingMenu> {

    public EuvLightSourceOperatingScreen(EuvLightSourceOperatingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 280);
    }

    @Override
    protected boolean formed() {
        return menu.value(0) != 0; // isFormed from data slot 0
    }

    @Override
    protected boolean cabinet() {
        return false;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        line(g, Component.translatable("block.lithmod.euv_light_source_operating_panel"), 8, 6, 160);
        line(g,Component.literal("Tin input"),70,22,98);
        line(g,Component.literal(menu.value(1)+" / "+menu.value(2)+" FE"),30,57,138);
        line(g,playerInventoryTitle,8,72,160);
        line(g,Component.literal(formed()?"Formed":"Unformed: check structure"),8,170,160);
        line(g,Component.literal("Fab H2: "+menu.value(3)+" mB"),8,182,160);
        line(g,Component.literal("Tin "+menu.value(4)+"; credit "+menu.value(5)+" ticks"),8,194,160);
        line(g,Component.literal("Pressure: "+menu.value(6)+" mTorr"),8,206,160);
        line(g,Component.literal(menu.value(7)!=0?"Scanner X "+menu.value(8):"No unique loaded beamline"),8,218,160);
        if(menu.value(7)!=0)line(g,Component.literal("Y "+menu.value(9)+"  Z "+menu.value(10)),8,230,160);
        String[] status={"Check structure","Connect beamline to scanner","Use source Calibration Panel","Vacuum not ready","Source needs FE","Source needs Fab H2","Source needs tin","Calibrate scanner","Ready / idle","Supplying active lot"};
        line(g,Component.literal(status[Math.clamp(menu.value(12),0,status.length-1)]),8,244,160);
        line(g,Component.literal(menu.value(11)!=0?"Pulse calibration accepted":"Pulse calibration required"),8,258,160);
    }
}