package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * G3 Deposition Etcher screen.
 */
public class G3DepositionEtcherScreen extends FabMachineScreen<G3DepositionEtcherMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("lithmod", "textures/gui/g3_deposition_etcher.png");

    public G3DepositionEtcherScreen(G3DepositionEtcherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
    }

    @Override
    protected void init() {
        super.init();
        // Add start prep job button, phase progress display
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // Render vacuum pressure, water amount, prep phase, phase progress, contamination state
    }
}