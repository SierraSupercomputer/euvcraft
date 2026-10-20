package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Pattern Generator: the base machine bars plus one short line saying why the
 * machine is refusing to run.
 *
 * <p>26.3 draws through the extraction model, so the reason is written in
 * {@link #extractLabels} rather than in a render pass, and through the same
 * {@code graphics.text} call the base screen uses for its own labels. Nothing is sampled per
 * frame: the status comes from the value the server synced, so the line says what the server
 * believes and changes when the server's answer changes.</p>
 *
 * <p>The line is a translatable key chosen from the machine's own four status values, never
 * a die type, a tier, a recipe id or a file name. A player is told the design is not allowed,
 * not which technical identifier failed, so nothing here can leak a data pack id into the
 * interface.</p>
 *
 * <p>The class is only ever referenced from {@link MaskMachineClientEvents}, which is
 * annotated {@code Dist.CLIENT}, so a dedicated server never loads it.</p>
 */
public class PatternGeneratorScreen extends FabMachineScreen<PatternGeneratorMenu> {

    /** Width of the menu image, the vanilla container width. */
    private static final int IMAGE_WIDTH = 176;
    /** Height of the menu image, chosen so the hotbar row at y = 142 sits inside it. */
    private static final int IMAGE_HEIGHT = 166;
    /** The refusal line's row, shared with the base screen's inventory label and to its right. */
    private static final int STATUS_Y = 72;
    /** Right margin of the refusal line, the same margin the base screen's labels use. */
    private static final int STATUS_MARGIN = 8;
    /** A warm red, so a refusal never reads as a normal label (opaque). */
    private static final int STATUS_COLOR = 0xFFD05050;

    public PatternGeneratorScreen(PatternGeneratorMenu menu, Inventory playerInventory,
                                  Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    protected int energyBarX() {
        return 30;
    }

    @Override
    protected int energyBarY() {
        return 17;
    }

    @Override
    protected int progressBarX() {
        return 122;
    }

    @Override
    protected int progressBarY() {
        return 35;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component reason = refusalReason();
        if (reason == null) {
            return;
        }
        // Right-aligned on the inventory label's row: the base screen writes that label at
        // the left margin, so a short reason to its right cannot collide with it, and the
        // machine slots are all above this row.
        graphics.text(this.font, reason, IMAGE_WIDTH - STATUS_MARGIN - this.font.width(reason),
            STATUS_Y, STATUS_COLOR, false);
    }

    /**
     * The short reason for the current refusal, or {@code null} when there is nothing to say.
     *
     * <p>{@code null} rather than an empty string, so an untranslated key can never be drawn
     * as a literal string of underscores by accident.</p>
     */
    private Component refusalReason() {
        String key = switch (this.menu.status()) {
            case PatternGeneratorBlockEntity.STATUS_NOT_A_MASK ->
                "status.lithmod.pattern_generator.not_a_mask";
            case PatternGeneratorBlockEntity.STATUS_MISSING_DESIGN ->
                "status.lithmod.pattern_generator.missing_design";
            case PatternGeneratorBlockEntity.STATUS_DESIGN_REFUSED ->
                "status.lithmod.pattern_generator.design_refused";
            default -> null;
        };
        return key == null ? null : Component.translatable(key);
    }
}
