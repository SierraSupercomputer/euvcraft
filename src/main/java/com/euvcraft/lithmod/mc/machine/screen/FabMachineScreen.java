package com.euvcraft.lithmod.mc.machine.screen;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Base screen for fab machines: a vertical energy bar, a horizontal progress bar, and the
 * layout hooks a concrete machine fills in.
 *
 * <p>26.3 draws through the extraction model rather than a {@code GuiGraphics}: a screen
 * overrides {@link #extractBackground} (and optionally {@link #extractTooltip}) and is handed
 * a {@link GuiGraphicsExtractor}. This is the same shape Encoded Logistics'
 * {@code LithographyPressScreen} uses.</p>
 *
 * <p>Nothing here rolls or samples anything per frame: the bars are drawn straight from the
 * values the server synced through the menu's data slots, so the screen shows exactly what
 * the server believes.</p>
 *
 * <p>The class lives in the {@code mc.machine.screen} package and is only ever referenced from
 * the client event subscriber, which is annotated {@code Dist.CLIENT}, so a dedicated server
 * never loads it.</p>
 */
public abstract class FabMachineScreen<M extends FabMachineMenu> extends AbstractContainerScreen<M> {

    /** Sprite for slot backgrounds. */
    public static final Identifier SLOT_BACKGROUND =
        Identifier.fromNamespaceAndPath("lithmod", "widget/slot_background");

    /** Sprite sheet size; slot background is drawn as a sub-rectangle of one image. */
    public static final int SPRITE_SIZE = 64;

    /** Height of the energy bar in pixels. */
    public static final int ENERGY_BAR_HEIGHT = 52;
    /** Width of the energy bar in pixels. */
    public static final int ENERGY_BAR_WIDTH = 12;
    /** Width of the progress bar in pixels. */
    public static final int PROGRESS_BAR_WIDTH = 24;
    /** Height of the progress bar in pixels. */
    public static final int PROGRESS_BAR_HEIGHT = 16;

    private static final int ENERGY_BAR_EMPTY_COLOR = 0xFF1A1A2E;
    private static final int ENERGY_BAR_FILL_COLOR = 0xFF00FF88;
    private static final int PROGRESS_BAR_EMPTY_COLOR = 0xFF1A1A2E;
    private static final int PROGRESS_BAR_FILL_COLOR = 0xFFFFD700;
    /** Opaque dark grey matching vanilla container labels (0xFF404040). */
    private static final int ENERGY_TEXT_COLOR = 0xFF404040;
    private static final int TOOLTIP_COLOR = 0xF0F0F0;

    /** Light neutral panel fill. */
    private static final int PANEL_FILL = 0xFFD0D0D0;
    /** Panel bevel highlight (top/left). */
    private static final int PANEL_BEVEL_LIGHT = 0xFFEEEEEE;
    /** Panel bevel shadow (bottom/right). */
    private static final int PANEL_BEVEL_DARK = 0xFF9E9E9E;
    /** Slot well background color (subtle inset). */
    private static final int SLOT_WELL_COLOR = 0xFFB0B0B0;

    protected FabMachineScreen(M menu, Inventory playerInventory, Component title,
                               int imageWidth, int imageHeight) {
        super(menu, playerInventory, title, imageWidth, imageHeight);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = imageHeight - 94;
    }

    /** Left edge of the energy bar, relative to the menu's top-left corner. */
    protected abstract int energyBarX();

    /** Top edge of the energy bar, relative to the menu's top-left corner. */
    protected abstract int energyBarY();

    /** Left edge of the progress bar, relative to the menu's top-left corner. */
    protected abstract int progressBarX();

    /** Top edge of the progress bar, relative to the menu's top-left corner. */
    protected abstract int progressBarY();

    /** Whether the energy bar is drawn. Defaults to true. */
    protected boolean hasEnergyBar() {
        return true;
    }

    /** Whether the progress bar is drawn. Defaults to true. */
    protected boolean hasProgressBar() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanelBackground(graphics);
        drawSlotWells(graphics);
        if (hasEnergyBar()) {
            drawEnergyBar(graphics);
        }
        if (hasProgressBar()) {
            drawProgressBar(graphics);
        }
    }

    /**
     * Draws a simple light neutral opaque beveled panel behind slots and bars.
     * Panel spans the full menu image area.
     */
    private void drawPanelBackground(GuiGraphicsExtractor graphics) {
        // Background extraction runs before the container's label/slot pose translation.
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // Main fill
        graphics.fill(x, y, x + w, y + h, PANEL_FILL);

        // Bevel: top and left highlight
        graphics.fill(x, y, x + w, y + 1, PANEL_BEVEL_LIGHT);
        graphics.fill(x, y, x + 1, y + h, PANEL_BEVEL_LIGHT);

        // Bevel: bottom and right shadow
        graphics.fill(x, y + h - 1, x + w, y + h, PANEL_BEVEL_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, PANEL_BEVEL_DARK);
    }

    /**
     * Draws 18x18 slot wells centered on each active slot's coordinates.
     * Covers all machine and player inventory slots.
     */
    private void drawSlotWells(GuiGraphicsExtractor graphics) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_WELL_COLOR);
            // Subtle inner bevel for depth
            graphics.fill(sx, sy, sx + 18, sy + 1, PANEL_BEVEL_LIGHT);
            graphics.fill(sx, sy, sx + 1, sy + 18, PANEL_BEVEL_LIGHT);
            graphics.fill(sx, sy + 17, sx + 18, sy + 18, PANEL_BEVEL_DARK);
            graphics.fill(sx + 17, sy, sx + 18, sy + 18, PANEL_BEVEL_DARK);
        }
    }

    private void drawEnergyBar(GuiGraphicsExtractor graphics) {
        int x = leftPos + energyBarX();
        int y = topPos + energyBarY();

        // Dark empty background
        graphics.fill( x, y, x + ENERGY_BAR_WIDTH, y + ENERGY_BAR_HEIGHT, ENERGY_BAR_EMPTY_COLOR);

        // Energy fill rising bottom-up
        float fraction = menu.energyFraction();
        if (fraction > 0.0f) {
            fraction = Math.min(1.0f, Math.max(0.0f, fraction));
            int fill = Math.round(ENERGY_BAR_HEIGHT * fraction);
            if (fill > 0) {
                graphics.fill(
                    x, y + ENERGY_BAR_HEIGHT - fill,
                    x + ENERGY_BAR_WIDTH, y + ENERGY_BAR_HEIGHT,
                    ENERGY_BAR_FILL_COLOR);
            }
        }
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics) {
        int x = leftPos + progressBarX();
        int y = topPos + progressBarY();

        // Dark empty background
        graphics.fill( x, y, x + PROGRESS_BAR_WIDTH, y + PROGRESS_BAR_HEIGHT, PROGRESS_BAR_EMPTY_COLOR);

        // Progress fill left-to-right
        float fraction = menu.progressFraction();
        if (fraction > 0.0f) {
            fraction = Math.min(1.0f, Math.max(0.0f, fraction));
            int fill = Math.round(PROGRESS_BAR_WIDTH * fraction);
            if (fill > 0) {
                graphics.fill(
                    x, y,
                    x + fill, y + PROGRESS_BAR_HEIGHT,
                    PROGRESS_BAR_FILL_COLOR);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ENERGY_TEXT_COLOR, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
            ENERGY_TEXT_COLOR, false);
        if (hasEnergyBar() && menu.energyCapacity() > 0) {
            graphics.text(this.font, menu.energy() + " / " + menu.energyCapacity(),
                energyBarX() + ENERGY_BAR_WIDTH + 2, energyBarY() + 2, ENERGY_TEXT_COLOR, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;

        if (hasEnergyBar() && inside(localX, localY, energyBarX(), energyBarY(),
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT)) {
            showTooltip(graphics, mouseX, mouseY, List.of(Component.translatable(
                "screen.lithmod.energy", menu.energy(), menu.energyCapacity())));
            return;
        }
        if (hasProgressBar() && inside(localX, localY, progressBarX(), progressBarY(),
                PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT)) {
            showTooltip(graphics, mouseX, mouseY, List.of(Component.translatable(
                "screen.lithmod.progress", menu.progress(), menu.ticks())));
        }
    }

    private void showTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<Component> lines) {
        List<Component> coloured = lines.stream()
            .map(line -> (Component) Component.literal("").append(line).withStyle(style -> style.withColor(TOOLTIP_COLOR)))
            .toList();
        graphics.setComponentTooltipForNextFrame(this.font, coloured, mouseX, mouseY);
    }

    private static boolean inside(double localX, double localY, int x, int y, int width, int height) {
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }
}

