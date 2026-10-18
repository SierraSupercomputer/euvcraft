package com.euvcraft.lithmod.mc.machine.packaging;

import com.euvcraft.lithmod.core.PackagingRefusalReason;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Test and Package machine: the base machine bars plus a coarse state and the
 * wafers done of the total.
 *
 * <p>26.3 draws through the extraction model, so both lines are written in
 * {@link #extractLabels} rather than in a render pass, and through the same
 * {@code graphics.text} call the base screen uses for its own labels. Nothing is sampled per
 * frame: the state and the counts come from the values the server synced, so the screen says
 * exactly what the server believes and changes when the server's answer changes.</p>
 *
 * <p>The state is one translatable key chosen from the machine's own coarse states, never a
 * per-wafer result, a die type, a tier or a file name. A paused machine is simply paused: the
 * screen is told no reason, because a pause reason that named what ran out would leak the
 * wafer's result. The two words a player sees are therefore always safe to show.</p>
 *
 * <p>The class is only ever referenced from {@link PackagingMachineClientEvents}, which is
 * annotated {@code Dist.CLIENT}, so a dedicated server never loads it.</p>
 */
public class TestPackageScreen extends FabMachineScreen<TestPackageMenu> {

    /** Width of the menu image, the vanilla container width. */
    private static final int IMAGE_WIDTH = 176;
    /** Height of the menu image, chosen so the hotbar row at y = 142 sits inside it. */
    private static final int IMAGE_HEIGHT = 166;
    /** Right margin of both lines, the same margin the base screen's labels use. */
    private static final int MARGIN = 8;
    /** The state's row: the top right, well clear of the title on the left. */
    private static final int STATE_Y = 6;
    /** The wafer count's row, shared with the base screen's inventory label and to its right. */
    private static final int COUNT_Y = 72;
    /** A neutral grey for both lines, matching the base screen's own labels. */
    private static final int LABEL_COLOR = 0x404040;

    public TestPackageScreen(TestPackageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
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

        // Top right: the machine's coarse state. The base screen's title is at the left of
        // the same row, so the two cannot collide.
        Component state = stateText();
        graphics.text(this.font, state, IMAGE_WIDTH - MARGIN - this.font.width(state),
            STATE_Y, LABEL_COLOR, false);

        // Right-aligned on the inventory label's row: the base screen writes that label at
        // the left margin, so a short count to its right cannot collide with it, and every
        // machine slot is above this row.
        Component counts = Component.translatable("label.lithmod.test_package.wafers",
            this.menu.wafersDone(), this.menu.wafersTotal());
        graphics.text(this.font, counts, IMAGE_WIDTH - MARGIN - this.font.width(counts),
            COUNT_Y, LABEL_COLOR, false);
    }

    /**
     * The translated state for the value the server synced.
     *
     * <p>The refusal states are addressed by their offset from
     * {@link TestPackageBlockEntity#STATE_REFUSED_BASE} and turned into a key by their own
     * enum, so the screen never keeps a second copy of the reason order and a reason added
     * later cannot silently show the wrong words.</p>
     */
    private Component stateText() {
        int state = this.menu.state();
        if (state == TestPackageBlockEntity.STATE_RUNNING) {
            return Component.translatable("state.lithmod.test_package.running");
        }
        if (state == TestPackageBlockEntity.STATE_PAUSED) {
            return Component.translatable("state.lithmod.test_package.paused");
        }
        int offset = state - TestPackageBlockEntity.STATE_REFUSED_BASE;
        if (offset >= 0 && offset < PackagingRefusalReason.values().length) {
            return Component.translatable(refusalKey(PackagingRefusalReason.values()[offset]));
        }
        return Component.translatable("state.lithmod.test_package.idle");
    }

    /** The translation key for one refusal reason. */
    private static String refusalKey(PackagingRefusalReason reason) {
        return switch (reason) {
            case NO_LOT -> "state.lithmod.test_package.no_lot";
            case LOT_INCOMPLETE -> "state.lithmod.test_package.lot_incomplete";
            case NO_SPEC -> "state.lithmod.test_package.no_spec";
            case NO_CHIP_FOR_DIE -> "state.lithmod.test_package.no_chip_for_die";
            case GENERATION_UNMET -> "state.lithmod.test_package.generation_unmet";
        };
    }
}
