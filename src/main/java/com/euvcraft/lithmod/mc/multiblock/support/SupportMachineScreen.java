package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The one screen class for both support shells.
 *
 * <p>The menu image is 176x220. The player inventory is fixed at y=84 and the hotbar at y=142, and
 * {@code inventoryLabelY} is set to 72 explicitly (the base would compute 220-94=126, which would
 * collide with the hotbar), exactly like {@code ProcessCellScreen}.</p>
 *
 * <p>Machine wells are the base screen's own slot rendering: energy bar at (8,17) 12x52, progress
 * bar at (146,17) 24x16, footer rows at 168 and 178 so nothing overlaps. The two shells differ
 * only in which footer lines make sense: the e-beam has no water, so its water row says "-".</p>
 */
public class SupportMachineScreen extends FabMachineScreen<SupportMachineMenu> {

    /** Width of the menu image, the vanilla container width. */
    private static final int IMAGE_WIDTH = 176;
    /** Height of the menu image: hotbar at 142..160, footer below that. */
    private static final int IMAGE_HEIGHT = 220;
    /** Right margin for right-aligned labels. */
    private static final int MARGIN = 8;
    /** Footer row Y positions relative to topPos. */
    private static final int FOOTER_FORMED_Y = 168;
    private static final int FOOTER_WATER_Y = 178;
    /** Label color (neutral grey matching the base screen, opaque). */
    private static final int LABEL_COLOR = 0xFF404040;

    public SupportMachineScreen(SupportMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        // Base computes imageHeight - 94 = 126, which would overlap the hotbar; fix to 72,
        // matching the machine slot row, exactly like ProcessCellScreen.
        this.inventoryLabelY = 72;
    }

    @Override
    protected int energyBarX() {
        return 8;
    }

    @Override
    protected int energyBarY() {
        return 17;
    }

    @Override
    protected int progressBarX() {
        return 146;
    }

    @Override
    protected int progressBarY() {
        return 17;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Formed line at y=168 keeps the footer clear of the hotbar (ends at 160).
        drawRightAligned(graphics, formedText(), FOOTER_FORMED_Y);
        // Water line at y=178; the e-beam shell reports no water and shows "-".
        drawRightAligned(graphics, waterText(), FOOTER_WATER_Y);
    }

    /** Draws a right-aligned component at a footer row. */
    private void drawRightAligned(GuiGraphicsExtractor graphics, Component text, int y) {
        int textWidth = this.font.width(text);
        graphics.text(this.font, text, this.leftPos + IMAGE_WIDTH - MARGIN - textWidth,
            this.topPos + y, LABEL_COLOR, false);
    }

    /** Builds the formed line for the footer. */
    private Component formedText() {
        SupportMachineMenu menu = this.menu;
        Component formed = menu.formed() == 1
            ? Component.translatable("state.lithmod.process_cell.formed")
            : Component.translatable("state.lithmod.process_cell.unformed");
        return Component.translatable("label.lithmod.support.formed", formed);
    }

    /** Builds the water line for the footer. */
    private Component waterText() {
        SupportMachineMenu menu = this.menu;
        if (menu.waterCapacity() <= 0) {
            return Component.translatable("label.lithmod.support.water_none");
        }
        return Component.translatable("label.lithmod.support.water", menu.water(), menu.waterCapacity());
    }
}
