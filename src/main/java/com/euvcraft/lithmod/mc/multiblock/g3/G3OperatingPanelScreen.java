package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * G3 Operating Panel screen.
 * Shows status of the machine the panel belongs to.
 */
public class G3OperatingPanelScreen extends AdvancedMachineScreen<G3OperatingPanelMenu> {

    public G3OperatingPanelScreen(G3OperatingPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean formed() {
        return this.menu.controller != null && this.menu.controller.isFormed();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // Render machine status, energy, job progress
    }
}