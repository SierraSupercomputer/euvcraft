package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The zone controller's lower status screen: no slots of its own, status text kept inside
 * the opaque window below the player's fixed inventory.
 */
public final class ZoneStatusScreen extends AdvancedMachineScreen<ZoneStatusMenu> {
    public ZoneStatusScreen(ZoneStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected boolean formed() {
        return menu.formed();
    }

    @Override protected boolean cabinet() {
        return false;
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        line(graphics, Component.literal("Ultrapure water: " + menu.waterAmount() + " / " + menu.waterCapacity() + " mB"), 8, 192, 160);
        line(graphics, Component.literal(menu.hasJob()
            ? "Job running: " + menu.progress() + " / " + menu.ticks() + " ticks"
            : "Idle: load a boule at the operating panel above"), 8, 204, 160);
        line(graphics, Component.literal("Water inlet accepts ultrapure water only"), 8, 216, 160);
    }
}
