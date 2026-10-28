package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Operating screen for EUV Light Source (operating panel). Fits 176x238 with tin slot, player inventory, status. */
public final class EuvLightSourceOperatingScreen extends AdvancedMachineScreen<EuvLightSourceOperatingMenu> {

    public EuvLightSourceOperatingScreen(EuvLightSourceOperatingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 238);
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
        line(g,Component.literal(menu.value(7)!=0?"Paired scanner":"Connect beamline to scanner"),8,218,160);
    }
}