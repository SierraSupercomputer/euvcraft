package com.euvcraft.lithmod.mc.client.screen;

import com.euvcraft.lithmod.mc.FabMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Base screen for fab machines.
 * Renders energy bar, progress bar, and provides common UI helpers.
 */
@OnlyIn(Dist.CLIENT)
public class FabMachineScreen<T extends FabMachineMenu> extends AbstractContainerScreen<T> {

    private static final ResourceLocation ENERGY_BAR_SPRITE = ResourceLocation.fromNamespaceAndPath("lithmod", "container/energy_bar");
    private static final ResourceLocation PROGRESS_BAR_SPRITE = ResourceLocation.fromNamespaceAndPath("lithmod", "container/progress_bar");

    protected FabMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderMachineBackground(guiGraphics);

        // Render energy bar
        int energyX = getEnergyBarX();
        int energyY = getEnergyBarY();
        renderEnergyBar(guiGraphics, energyX, energyY, mouseX, mouseY);

        // Render progress bar
        int progressX = getProgressBarX();
        int progressY = getProgressBarY();
        renderProgressBar(guiGraphics, progressX, progressY, mouseX, mouseY);
    }

    /**
     * Renders the machine-specific background (slots, labels, etc.).
     */
    protected void renderMachineBackground(GuiGraphics guiGraphics) {
        // Default: no background
    }

    /**
     * Returns the X position of the energy bar.
     */
    protected abstract int getEnergyBarX();

    /**
     * Returns the Y position of the energy bar.
     */
    protected abstract int getEnergyBarY();

    /**
     * Returns the X position of the progress bar.
     */
    protected abstract int getProgressBarX();

    /**
     * Returns the Y position of the progress bar.
     */
    protected abstract int getProgressBarY();

    /**
     * Renders the energy bar at the given position.
     * Default implementation: 16x52 pixels, filled from bottom.
     */
    protected void renderEnergyBar(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        // Background
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath("lithmod", "container/energy_bar"), x, y, 16, 52);

        // Fill
        float fraction = menu.getEnergyFraction();
        int fillHeight = (int) (52 * fraction);
        if (fillHeight > 0) {
            guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath("lithmod", "container/energy_bar"), x, y + 52 - fillHeight, 16, fillHeight);
        }

        // Tooltip on hover
        if (isHovering(x, y, 16, 52, (double) mouseX, (double) mouseY)) {
            guiGraphics.renderTooltip(font, net.minecraft.network.chat.Component.literal("FE: " + menu.getEnergy() + " / " + menu.getEnergyCapacity()), mouseX, mouseY);
        }
    }

    /**
     * Renders the progress bar at the given position.
     * Default implementation: 24x16 pixels, filled from left.
     */
    protected void renderProgressBar(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!menu.isRunning()) return;

        // Background
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath("lithmod", "container/progress_bar"), x, y, 24, 16);

        // Fill
        float fraction = menu.getProgressFraction();
        int fillWidth = (int) (24 * fraction);
        if (fillWidth > 0) {
            guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath("lithmod", "container/progress_bar"), x, y, fillWidth, 16);
        }

        // Tooltip on hover
        if (isHovering(x, y, 24, 16, (double) mouseX, (double) mouseY)) {
            guiGraphics.renderTooltip(font, net.minecraft.network.chat.Component.literal("Progress: " + menu.getProgress() + " / " + menu.getTotalTicks() + " ticks"), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}