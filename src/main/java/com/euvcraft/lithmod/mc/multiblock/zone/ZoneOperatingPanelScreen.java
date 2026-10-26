package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The zone operating panel's screen: the input and output boule slots plus the UPW status.
 * All text stays inside the opaque window; nothing clips the player inventory.
 */
public final class ZoneOperatingPanelScreen extends AdvancedMachineScreen<ZoneOperatingPanelMenu> {
    public ZoneOperatingPanelScreen(ZoneOperatingPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected boolean formed() {
        return menu.formed();
    }

    @Override protected boolean cabinet() {
        return false;
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        line(graphics,title,8,6,160);
        line(graphics,playerInventoryTitle,8,72,160);
        line(graphics,Component.literal(menu.energy()+" / "+menu.energyCapacity()+" FE"),30,56,138);
        line(graphics,Component.literal(formed()?"Formed":"Unformed: build 3x3x5"),8,168,160);
        line(graphics,Component.literal("Progress: "+menu.progress()+" / "+menu.ticks()),8,180,160);
        line(graphics, Component.literal("Input"), 56, 24, 32);
        line(graphics, Component.literal("Output"), 90, 24, 40);
        line(graphics, Component.literal("Ultrapure water: " + menu.waterAmount() + " / " + menu.waterCapacity() + " mB"), 8, 192, 160);
        line(graphics, Component.literal(menu.hasJob()
            ? "Job in progress: slots are locked until it completes"
            : "Place a boule in the input slot"), 8, 204, 160);
    }
}
