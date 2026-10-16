package com.euvcraft.lithmod.mc.machine.screen;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

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

    /** Sprite for the energy bar, drawn from the bottom up. */
    public static final Identifier ENERGY_BAR =
        Identifier.fromNamespaceAndPath("lithmod", "widget/energy_bar");
    /** Sprite for the progress bar, drawn from the left. */
    public static final Identifier PROGRESS_BAR =
        Identifier.fromNamespaceAndPath("lithmod", "widget/progress_bar");

    /** Sprite sheet size; both bars are drawn as sub-rectangles of one image. */
    public static final int SPRITE_SIZE = 64;

    /** Height of the energy bar in pixels. */
    public static final int ENERGY_BAR_HEIGHT = 52;
    /** Width of the energy bar in pixels. */
    public static final int ENERGY_BAR_WIDTH = 12;
    /** Width of the progress bar in pixels. */
    public static final int PROGRESS_BAR_WIDTH = 24;
    /** Height of the progress bar in pixels. */
    public static final int PROGRESS_BAR_HEIGHT = 16;

    private static final int ENERGY_TEXT_COLOR = 0x404040;
    private static final int TOOLTIP_COLOR = 0xF0F0F0;

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
        if (hasEnergyBar()) {
            drawEnergyBar(graphics);
        }
        if (hasProgressBar()) {
            drawProgressBar(graphics);
        }
    }

    private void drawEnergyBar(GuiGraphicsExtractor graphics) {
        int x = leftPos + energyBarX();
        int y = topPos + energyBarY();

        // The empty socket.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR,
            SPRITE_SIZE, SPRITE_SIZE, 0, 0, x, y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT);

        int fill = Math.round(ENERGY_BAR_HEIGHT * menu.energyFraction());
        if (fill > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR,
                SPRITE_SIZE, SPRITE_SIZE, 0, 0,
                x, y + ENERGY_BAR_HEIGHT - fill, ENERGY_BAR_WIDTH, fill);
        }
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics) {
        int x = leftPos + progressBarX();
        int y = topPos + progressBarY();

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_BAR,
            SPRITE_SIZE, SPRITE_SIZE, 0, 0, x, y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);

        int fill = Math.round(PROGRESS_BAR_WIDTH * menu.progressFraction());
        if (fill > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_BAR,
                SPRITE_SIZE, SPRITE_SIZE, 0, 0, x, y, fill, PROGRESS_BAR_HEIGHT);
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
            showTooltip(graphics, mouseX, mouseY, List.of(Component.literal(
                "FE " + menu.energy() + " / " + menu.energyCapacity())));
            return;
        }
        if (hasProgressBar() && inside(localX, localY, progressBarX(), progressBarY(),
                PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT)) {
            showTooltip(graphics, mouseX, mouseY, List.of(Component.literal(
                "Progress " + menu.progress() + " / " + menu.ticks())));
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
