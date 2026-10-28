package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * G3 Operating Panel screen.
 * Shows status of the machine the panel belongs to.
 */
public class G3OperatingPanelScreen extends FabMachineScreen<G3OperatingPanelMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("lithmod", "textures/gui/g3_operating_panel.png");

    public G3OperatingPanelScreen(G3OperatingPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
    }

    @Override
    protected void init() {
        super.init();
        // Status-only display, no input slots
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // Render machine status, energy, job progress
    }
}