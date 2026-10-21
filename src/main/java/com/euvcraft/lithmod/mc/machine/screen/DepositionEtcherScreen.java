package com.euvcraft.lithmod.mc.machine.screen;

import com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Deposition Etcher (both Equipment Cabinet and Operating Panel views).
 * Shows energy bar, progress bar (job progress), and formation status.
 */
public class DepositionEtcherScreen extends FabMachineScreen<DepositionEtcherMenu> {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 240;
    private static final int MARGIN = 8;
    private static final int FOOTER_STATUS_Y = 168;
    private static final int FOOTER_PROGRESS_Y = 178;
    private static final int FOOTER_FORMED_Y = 188;
    private static final int SUBTITLE_Y = 57;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int SUBTITLE_COLOR = 0xFF606060;
    private static final int MAX_TEXT_WIDTH = 160;

    private Component roleSubtitle;

    public DepositionEtcherScreen(DepositionEtcherMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        this.inventoryLabelY = 72;
        this.roleSubtitle = menu.getBlockEntity() instanceof com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity
                ? Component.translatable("screen.lithmod.deposition_etcher.operating_panel")
                : Component.translatable("screen.lithmod.deposition_etcher.equipment_cabinet");
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
        drawCenteredSubtitle(graphics, this.roleSubtitle, SUBTITLE_Y);

        // Footer status line
        Component statusText = statusText();
        drawClippedRightAligned(graphics, statusText, FOOTER_STATUS_Y);

        // Progress line
        Component progressText = progressText();
        drawClippedRightAligned(graphics, progressText, FOOTER_PROGRESS_Y);

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
        com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity.JobState state = menu.jobState();
        if (state == com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity.JobState.IDLE) {
            return Component.translatable("state.lithmod.deposition_etcher.idle");
        }
        if (state == com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity.JobState.RUNNING) {
            return Component.translatable("state.lithmod.deposition_etcher.running");
        }
        if (state == com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity.JobState.PAUSED) {
            return Component.translatable("state.lithmod.deposition_etcher.paused");
        }
        if (state == com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity.JobState.FINISHED) {
            return Component.translatable("state.lithmod.deposition_etcher.finished");
        }
        return Component.translatable("state.lithmod.deposition_etcher.idle");
    }

    private Component progressText() {
        int progress = menu.jobProgress();
        int total = menu.jobDuration();
        if (total > 0) {
            return Component.translatable("label.lithmod.deposition_etcher.progress", progress, total);
        }
        return Component.translatable("label.lithmod.deposition_etcher.progress_none");
    }

    private Component formedText() {
        if (menu.getBlockEntity() instanceof com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity be) {
            if (be.isFormed()) {
                return Component.translatable("state.lithmod.deposition_etcher.formed");
            }
        }
        return Component.translatable("state.lithmod.deposition_etcher.unformed");
    }
}