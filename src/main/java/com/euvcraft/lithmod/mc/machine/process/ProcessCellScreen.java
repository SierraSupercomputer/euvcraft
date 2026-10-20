package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;

/**
 * Screen for the Process Cell (both Equipment Cabinet and Operating Panel views):
 * base machine bars plus a coarse status footer below the player inventory/hotbar.
 * <p>
 * The menu image is 176x240 so the footer sits below the hotbar (which ends at y=160).
 * Inventory label Y is set to 72 explicitly (base calculates height-94 which would overlap).
 * Machine grid rows remain at 35 and 53. Energy bar at (8,17), progress bar at (146,17).
 * Role subtitle at y=26. Footer status at y=168, layer at 178, design at 188, formed at 198.
 * Abort button bounds: leftPos+8, topPos+212, 160x20 (only for Operating Panel).
 * </p>
 * <p>
 * Label texts fit 160 width or wrap/clip without overlapping inventory/button.
 * Title and FE text from base are preserved. The Abort button is added in init(),
 * active only when {@link ProcessCellMenu#inputsLocked()} for Operating Panel menu.
 * Container tick update uses the standard screen tick hook.
 * </p>
 */
public class ProcessCellScreen extends FabMachineScreen<ProcessCellMenu> {

    /** Width of the menu image, the vanilla container width. */
    private static final int IMAGE_WIDTH = 176;
    /** Height of the menu image, chosen so the footer sits below the hotbar (ends at 160). */
    private static final int IMAGE_HEIGHT = 240;
    /** Right margin for right-aligned labels. */
    private static final int MARGIN = 8;
    /** Footer row Y positions relative to topPos. */
    private static final int FOOTER_STATUS_Y = 168;
    private static final int FOOTER_LAYER_Y = 178;
    private static final int FOOTER_DESIGN_Y = 188;
    private static final int FOOTER_FORMED_Y = 198;
    /** Role subtitle Y position relative to topPos. */
    private static final int SUBTITLE_Y = 57;
    /** Abort button bounds relative to top-left. */
    private static final int ABORT_BTN_X = 8;
    private static final int ABORT_BTN_Y = 212;
    private static final int ABORT_BTN_WIDTH = 160;
    private static final int ABORT_BTN_HEIGHT = 20;
    /** Label color (neutral grey matching base screen, opaque). */
    private static final int LABEL_COLOR = 0xFF404040;
    /** Subtitle color (slightly lighter grey). */
    private static final int SUBTITLE_COLOR = 0xFF606060;
    /** Maximum text width for footer lines. */
    private static final int MAX_TEXT_WIDTH = 160;

    private Button abortButton;
    private Component roleSubtitle;

    public ProcessCellScreen(ProcessCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        // Base calculates inventoryLabelY = imageHeight - 94 = 146, which overlaps hotbar.
        // Set explicitly to 72 to match the machine grid row alignment.
        this.inventoryLabelY = 72;
        // Determine role subtitle based on menu type
        this.roleSubtitle = menu.isOperatingPanel()
                ? Component.translatable("screen.lithmod.process_cell.operating_panel")
                : Component.translatable("screen.lithmod.process_cell.equipment_cabinet");
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
        // Abort button: only for Operating Panel menu, active only when inputs are locked
        if (this.menu.isOperatingPanel()) {
            this.abortButton = Button.builder(Component.translatable("button.lithmod.process_cell.abort"),
                            btn -> handleAbortClick())
                    .bounds(this.leftPos + ABORT_BTN_X, this.topPos + ABORT_BTN_Y,
                            ABORT_BTN_WIDTH, ABORT_BTN_HEIGHT)
                    .build();
            this.addRenderableWidget(this.abortButton);
            updateAbortButtonActive();
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Update button active state each tick based on menu's locked state
        if (this.abortButton != null) {
            updateAbortButtonActive();
        }
    }

    private void updateAbortButtonActive() {
        if (this.abortButton != null) {
            this.abortButton.active = this.menu.inputsLocked();
        }
    }

    private void handleAbortClick() {
        // Send button click to server via game mode
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ProcessCellMenu.ABORT_BUTTON);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Free row below machine slots; keep clear of the FE label at y=19.
        drawCenteredSubtitle(graphics, this.roleSubtitle, SUBTITLE_Y);

        // Footer status line at y=168
        Component statusText = statusText();
        drawClippedRightAligned(graphics, statusText, FOOTER_STATUS_Y);

        // Layer line at y=178
        Component layerText = layerText();
        drawClippedRightAligned(graphics, layerText, FOOTER_LAYER_Y);

        // Design (tier+die) line at y=188
        Component designText = designText();
        drawClippedRightAligned(graphics, designText, FOOTER_DESIGN_Y);

        // Formed line at y=198
        Component formedText = formedText();
        drawClippedRightAligned(graphics, formedText, FOOTER_FORMED_Y);
    }

    /** Draws a centered subtitle under the main title. */
    private void drawCenteredSubtitle(GuiGraphicsExtractor graphics, Component text, int y) {
        int textWidth = this.font.width(text);
        int x = (IMAGE_WIDTH - textWidth) / 2;
        graphics.text(this.font, text, this.leftPos + x, this.topPos + y, SUBTITLE_COLOR, false);
    }

    /** Draws a right-aligned component, clipping to MAX_TEXT_WIDTH if needed. */
    private void drawClippedRightAligned(GuiGraphicsExtractor graphics, Component text, int y) {
        int textWidth = this.font.width(text);
        int x = IMAGE_WIDTH - MARGIN;
        if (textWidth > MAX_TEXT_WIDTH) {
            // Clip by drawing a substring that fits - we can't easily wrap in extractLabels,
            // so we truncate with ellipsis for safety.
            String full = text.getString();
            String clipped = full;
            while (this.font.width(clipped + "...") > MAX_TEXT_WIDTH && clipped.length() > 0) {
                clipped = clipped.substring(0, clipped.length() - 1);
            }
            if (clipped.length() < full.length()) {
                clipped = clipped + "...";
            }
            graphics.text(this.font, Component.literal(clipped), this.leftPos + x - this.font.width(clipped), this.topPos + y, LABEL_COLOR, false);
        } else {
            graphics.text(this.font, text, this.leftPos + x - textWidth, this.topPos + y, LABEL_COLOR, false);
        }
    }

    /** Builds the status line: state or refusal reason. */
    private Component statusText() {
        int status = this.menu.status();
        if (status == ProcessCellMenu.STATUS_ABORTED) {
            return Component.translatable("state.lithmod.process_cell.stopped_after_abort");
        }
        if (status == ProcessCellMenu.STATUS_DEFINITION_MISSING) {
            return Component.translatable("state.lithmod.process_cell.missing_definition");
        }
        int refusal = this.menu.refusal();
        if (refusal > 0 && refusal <= RefusalReason.Code.values().length) {
            return Component.translatable("state.lithmod.process_cell."
                + RefusalReason.Code.values()[refusal - 1].name().toLowerCase(java.util.Locale.ROOT));
        }
        if (status >= 0 && status < ProcessState.values().length) {
            ProcessState state = ProcessState.values()[status];
            return Component.translatable("state.lithmod.process_cell." + state.name().toLowerCase(java.util.Locale.ROOT));
        }
        return Component.translatable("state.lithmod.process_cell.idle");
    }

    /** Builds the layer line: "Layer k of N" or "Layer: -" when no run. */
    private Component layerText() {
        int current = this.menu.currentLayer();
        int total = this.menu.totalLayers();
        if (total > 0) {
            return Component.translatable("label.lithmod.process_cell.layer", current, total);
        }
        return Component.translatable("label.lithmod.process_cell.layer_none");
    }

    /** Builds the design line: "Tier T, Die Type" or "Design: -" when no contract. */
    private Component designText() {
        int tierIdx = this.menu.tierIndex(); // actual public tier index
        int dieIdx = this.menu.dieIndex() - 1;   // stored as +1
        if (tierIdx > 0 && dieIdx >= 0 && tierIdx <= 8 && dieIdx < DieTypes.ALL.size()) {
            Component tier = Component.literal("T" + tierIdx);
            String dieKey = "dieType.lithmod." + DieTypes.ALL.get(dieIdx);
            return Component.translatable("label.lithmod.process_cell.design",
                    tier, Component.translatable(dieKey));
        }
        return Component.translatable("label.lithmod.process_cell.design_none");
    }

    /** Builds the formed line: "Formed" or "Unformed". */
    private Component formedText() {
        if (this.menu.formed() == 1) {
            return Component.translatable("state.lithmod.process_cell.formed");
        }
        return Component.translatable("state.lithmod.process_cell.unformed");
    }
}
