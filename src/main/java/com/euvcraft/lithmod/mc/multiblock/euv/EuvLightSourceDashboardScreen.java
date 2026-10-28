package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dashboard screen for EUV Light Source (controller). Simple status display. */
public final class EuvLightSourceDashboardScreen extends AbstractContainerScreen<EuvLightSourceDashboardMenu> {

    public EuvLightSourceDashboardScreen(EuvLightSourceDashboardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFD0D0D0);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int width) {
        g.text(font, font.plainSubstrByWidth(s, width), x, y, 0xFF303030, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        text(g, Component.translatable("block.lithmod.euv_light_source_controller").getString(), 8, 7, 160);
        text(g, "Formed: " + (menu.value(0) != 0 ? "Yes" : "No"), 8, 32, 160);
        text(g, "Vacuum: " + (menu.value(1) != 0 ? "OK" : "LOW"), 8, 48, 160);
        text(g, "H₂: " + menu.value(2) + " mb", 8, 64, 160);
        text(g, "Tin: " + menu.value(3), 8, 80, 160);
        text(g, "Pressure: " + menu.value(4) + " kPa", 8, 96, 160);
    }
}