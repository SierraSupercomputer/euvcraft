package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * G3 Reticle Writer screen.
 */
public class G3ReticleWriterScreen extends AdvancedMachineScreen<G3ReticleWriterMenu> {

    public G3ReticleWriterScreen(G3ReticleWriterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean formed() {
        return this.menu.controller.isFormed();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // Render job progress, energy, core count, selected recipe
        if (this.menu.controller != null) {
            line(graphics, Component.translatable("screen.lithmod.g3.core_count", this.menu.controller.corePreference()), 8, 42, 160);
            line(graphics, Component.translatable("screen.lithmod.g3.job_progress", this.menu.progress(), this.menu.ticks()), 8, 54, 160);
        }
    }
}