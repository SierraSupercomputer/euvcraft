package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

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
 *
 * <p>For the e-beam shell, the footer area (y >= 176) contains the design selector and the
 * WriteMask Start button. The selector cycles through seven labelled designs with left/right
 * arrows; the Start button is only enabled when a design is selected and the machine is formed
 * and idle.</p>
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

    // E-beam UI constants (footer area starts at y=176)
    private static final int EBEAM_FOOTER_START_Y = 176;
    private static final int EBEAM_SELECTOR_Y = 180;
    private static final int EBEAM_START_BUTTON_Y = 200;
    private static final int EBEAM_BUTTON_WIDTH = 20;
    private static final int EBEAM_BUTTON_HEIGHT = 16;
    private static final int EBEAM_START_WIDTH = 80;
    private static final int EBEAM_START_HEIGHT = 16;

    private Button leftArrow;
    private Button rightArrow;
    private Button startButton;
    private int lastSelectedDesign = -2; // force initial sync

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
    protected void init() {
        super.init();
        if (this.menu.kind() == SupportMachineKind.EBEAM) {
            int centerX = this.leftPos + IMAGE_WIDTH / 2;
            // Left arrow button
            this.leftArrow = this.addRenderableWidget(Button.builder(Component.literal("\u25C0"), b -> {
                this.menu.clickMenuButton(this.minecraft.player, prevDesignButtonId());
            }).bounds(centerX - 55, this.topPos + EBEAM_SELECTOR_Y, EBEAM_BUTTON_WIDTH, EBEAM_BUTTON_HEIGHT).build());
            // Right arrow button
            this.rightArrow = this.addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> {
                this.menu.clickMenuButton(this.minecraft.player, nextDesignButtonId());
            }).bounds(centerX + 35, this.topPos + EBEAM_SELECTOR_Y, EBEAM_BUTTON_WIDTH, EBEAM_BUTTON_HEIGHT).build());
            // WriteMask Start button
            this.startButton = this.addRenderableWidget(Button.builder(Component.translatable("button.lithmod.ebeam.start"), b -> {
                this.menu.clickMenuButton(this.minecraft.player, SupportMachineMenu.EBEAM_START_BUTTON);
            }).bounds(centerX - EBEAM_START_WIDTH / 2, this.topPos + EBEAM_START_BUTTON_Y, EBEAM_START_WIDTH, EBEAM_START_HEIGHT).build());
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Formed line at y=168 keeps the footer clear of the hotbar (ends at 160).
        drawRightAligned(graphics, formedText(), FOOTER_FORMED_Y);
        // Water line at y=178; the e-beam shell reports no water and shows "-".
        drawRightAligned(graphics, waterText(), FOOTER_WATER_Y);

        if (this.menu.kind() == SupportMachineKind.EBEAM) {
            renderEbeamSelector(graphics);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.menu.kind() == SupportMachineKind.EBEAM) {
            updateEbeamButtonStates();
        }
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

    // ---------------------------------------------------------------- e-beam UI

    /** Renders the design selector label in the footer area. */
    private void renderEbeamSelector(GuiGraphicsExtractor graphics) {
        int selected = this.menu.ebeamSelectedDesign();
        if (selected >= 0 && selected < 7) {
            String designName = getDesignDisplayName(selected);
            String dieType = SupportMachineMenu.intToDieType(this.menu.ebeamSelectedDieType());
            int tier = this.menu.ebeamSelectedTier();
            Component label = Component.translatable("label.lithmod.ebeam.selected", designName,
                Component.translatable("dieType.lithmod." + dieType), tier);
            int centerX = this.leftPos + IMAGE_WIDTH / 2;
            graphics.text(this.font, label, centerX - this.font.width(label) / 2,
                this.topPos + EBEAM_SELECTOR_Y - 12, LABEL_COLOR, false);
        } else {
            Component label = Component.translatable("label.lithmod.ebeam.no_selection");
            int centerX = this.leftPos + IMAGE_WIDTH / 2;
            graphics.text(this.font, label, centerX - this.font.width(label) / 2,
                this.topPos + EBEAM_SELECTOR_Y - 12, 0xFF808080, false);
        }
    }

    /** Updates the enabled state of e-beam buttons based on machine state. */
    private void updateEbeamButtonStates() {
        boolean formed = this.menu.formed() == 1;
        boolean running = this.menu.ebeamJobRunning();
        int selected = this.menu.ebeamSelectedDesign();
        boolean hasSelection = selected >= 0 && selected < 7;
        boolean designAvailable = hasSelection && this.menu.ebeamDesignAvailable(selected);

        if (this.leftArrow != null) {
            this.leftArrow.active = formed && !running && hasSelection;
        }
        if (this.rightArrow != null) {
            this.rightArrow.active = formed && !running && hasSelection;
        }
        if (this.startButton != null) {
            this.startButton.active = formed && !running && hasSelection && designAvailable;
        }
    }

    /** Returns the button ID for the previous design (cycles). */
    private int prevDesignButtonId() {
        int selected = this.menu.ebeamSelectedDesign();
        if (selected <= 0) selected = 7;
        return SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + (selected - 1);
    }

    /** Returns the button ID for the next design (cycles). */
    private int nextDesignButtonId() {
        int selected = this.menu.ebeamSelectedDesign();
        if (selected < 0) selected = 0;
        return SupportMachineMenu.EBEAM_DESIGN_BUTTON_BASE + ((selected + 1) % 7);
    }

    /** Returns the display name for a design index. */
    private static String getDesignDisplayName(int index) {
        return switch (index) {
            case 0 -> "Logic T1";
            case 1 -> "Logic T2";
            case 2 -> "Memory T2";
            case 3 -> "Logic T3";
            case 4 -> "Memory T3";
            case 5 -> "Compute T3";
            case 6 -> "NAND T3";
            default -> "Unknown";
        };
    }
}
