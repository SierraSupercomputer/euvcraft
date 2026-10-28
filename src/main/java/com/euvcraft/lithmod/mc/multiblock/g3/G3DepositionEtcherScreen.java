package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * G3 Deposition Etcher screen.
 */
public class G3DepositionEtcherScreen extends AdvancedMachineScreen<G3DepositionEtcherMenu> {

    public G3DepositionEtcherScreen(G3DepositionEtcherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean formed() {
        return this.menu.controller.isFormed();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // Render vacuum pressure, water amount, prep phase, phase progress, contamination state
        if (this.menu.controller != null) {
            line(graphics, Component.translatable("screen.lithmod.g3.phase", this.menu.controller.currentPhase().name()), 8, 42, 160);
            line(graphics, Component.translatable("screen.lithmod.g3.phase_progress", this.menu.controller.phaseProgress(), this.menu.controller.phaseDuration()), 8, 54, 160);
            line(graphics, Component.translatable("screen.lithmod.g3.vacuum", this.menu.controller.vacuumPressure(), this.menu.controller.vacuumOperatingPressure()), 8, 66, 160);
            line(graphics, Component.translatable("screen.lithmod.g3.water", this.menu.controller.waterAmount(), this.menu.controller.waterCapacity()), 8, 78, 160);
            line(graphics, Component.translatable("screen.lithmod.g3.contamination", this.menu.controller.contaminationPhase().name()), 8, 90, 160);
        }
    }
}