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
        line(g, Component.literal("Tin input"), 8, 22, 160);
        line(g, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 8, 38, 160);
        line(g, Component.literal("H₂: " + menu.value(2) + " mb"), 8, 50, 160);
        line(g, Component.literal("Tin stored: " + menu.value(3)), 8, 62, 160);
        line(g, Component.literal("Tin credit: " + menu.value(4) + " ticks"), 8, 74, 160);
        line(g, Component.literal("Vacuum: " + menu.value(5) + " kPa"), 8, 86, 160);
        line(g, Component.literal("Formed: " + (menu.value(6) != 0 ? "Yes" : "No")), 8, 98, 160);
        line(g, Component.literal("Paired: " + (menu.value(7) != 0 ? "Yes" : "No")), 8, 110, 160);
        line(g, playerInventoryTitle, 8, 124, 160);
        line(g, Component.translatable(formed() ? "state.lithmod.process_cell.formed" : "state.lithmod.process_cell.unformed"), 8, 200, 160);
        line(g, Component.translatable("screen.lithmod.progress", menu.progress(), menu.ticks()), 8, 212, 160);
    }
}