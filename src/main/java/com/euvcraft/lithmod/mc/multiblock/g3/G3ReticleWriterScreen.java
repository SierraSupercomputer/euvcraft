package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * G3 Reticle Writer screen.
 */
public class G3ReticleWriterScreen extends FabMachineScreen<G3ReticleWriterMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("lithmod", "textures/gui/g3_reticle_writer.png");

    public G3ReticleWriterScreen(G3ReticleWriterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
    }

    @Override
    protected void init() {
        super.init();
        // Add recipe selection, core count cycle, start/stop buttons
        // Recipe selection dropdown
        // Core count display and cycle button
        // Start job button (validates selection, formed, distance)
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // Render job progress, energy, core count, selected recipe
    }
}