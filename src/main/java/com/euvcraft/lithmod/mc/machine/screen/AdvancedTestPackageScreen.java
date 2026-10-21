package com.euvcraft.lithmod.mc.machine.screen;

import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedTestPackageMenu;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedTestPackageControllerBlockEntity;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Advanced Test Package Equipment Cabinet (lower cabinet).
 * Shows equipment/status only if no permanent items.
 */
public class AdvancedTestPackageScreen extends FabMachineScreen<AdvancedTestPackageMenu> {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 240;
    private static final int MARGIN = 8;
    private static final int FOOTER_STATUS_Y = 168;
    private static final int FOOTER_WAFERS_Y = 178;
    private static final int FOOTER_FORMED_Y = 188;
    private static final int SUBTITLE_Y = 57;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int SUBTITLE_COLOR = 0xFF606060;
    private static final int MAX_TEXT_WIDTH = 160;

    public AdvancedTestPackageScreen(AdvancedTestPackageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
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

        // Subtitle
        Component subtitle = Component.translatable("screen.lithmod.advanced_test_package.equipment_cabinet");
        drawCenteredSubtitle(graphics, subtitle, SUBTITLE_Y);

        // Footer status line
        Component statusText = statusText();
        drawClippedRightAligned(graphics, statusText, FOOTER_STATUS_Y);

        // Wafers line
        Component wafersText = wafersText();
        drawClippedRightAligned(graphics, wafersText, FOOTER_WAFERS_Y);

        // Formed line
        Component formedText = formedText();
        drawClippedRightAligned(graphics, formedText, FOOTER_FORMED_Y);
    }

    private void drawCenteredSubtitle(GuiGraphicsExtractor graphics, Component text, int y) {
        String label = text.getString();
        if (this.font.width(label) > MAX_TEXT_WIDTH) {
            while (!label.isEmpty() && this.font.width(label + "...") > MAX_TEXT_WIDTH) label = label.substring(0, label.length() - 1);
            label += "...";
        }
        int x = (IMAGE_WIDTH - this.font.width(label)) / 2;
        graphics.text(this.font, label, x, y, SUBTITLE_COLOR, false);
    }

    private void drawClippedRightAligned(GuiGraphicsExtractor graphics, Component text, int y) {
        int textWidth = this.font.width(text);
        int x = IMAGE_WIDTH - MARGIN;
        if (textWidth > MAX_TEXT_WIDTH) {
            String full = text.getString();
            String clipped = full;
            while (this.font.width(clipped + "...") > MAX_TEXT_WIDTH && clipped.length() > 0) {
                clipped = clipped.substring(0, clipped.length() - 1);
            }
            if (clipped.length() < full.length()) {
                clipped = clipped + "...";
            }
            graphics.text(this.font, Component.literal(clipped), x - this.font.width(clipped), y, LABEL_COLOR, false);
        } else {
            graphics.text(this.font, text, x - textWidth, y, LABEL_COLOR, false);
        }
    }

    private Component statusText() {
        int status = menu.state();
        if (status == TestPackageBlockEntity.STATE_IDLE) {
            return Component.translatable("state.lithmod.advanced_test_package.idle");
        }
        if (status == TestPackageBlockEntity.STATE_RUNNING) {
            return Component.translatable("state.lithmod.advanced_test_package.running");
        }
        if (status == TestPackageBlockEntity.STATE_PAUSED) {
            return Component.translatable("state.lithmod.advanced_test_package.paused");
        }
        if (status >= TestPackageBlockEntity.STATE_REFUSED_BASE) {
            int refusalIdx = status - TestPackageBlockEntity.STATE_REFUSED_BASE;
            if (refusalIdx >= 0 && refusalIdx < com.euvcraft.lithmod.core.PackagingRefusalReason.values().length) {
                return Component.translatable("state.lithmod.advanced_test_package."
                        + com.euvcraft.lithmod.core.PackagingRefusalReason.values()[refusalIdx].name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return Component.translatable("state.lithmod.advanced_test_package.idle");
    }

    private Component wafersText() {
        int done = menu.wafersDone();
        int total = menu.wafersTotal();
        if (total > 0) {
            return Component.translatable("label.lithmod.advanced_test_package.wafers", done, total);
        }
        return Component.translatable("label.lithmod.advanced_test_package.wafers_none");
    }

    private Component formedText() {
        if (menu.getBlockEntity() instanceof AdvancedTestPackageControllerBlockEntity be) {
            if (be.isFormed()) {
                return Component.translatable("state.lithmod.advanced_test_package.formed");
            }
        }
        return Component.translatable("state.lithmod.advanced_test_package.unformed");
    }
}