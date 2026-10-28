package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;
import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Operating screen for EUV scanner (operating panel). Shows layer/pass/progress, UPW, vacuum, source, repair/calibration refusal. */
public final class EuvScannerOperatingScreen extends AdvancedMachineScreen<EuvScannerOperatingMenu> {

    private Button abort;

    public EuvScannerOperatingScreen(EuvScannerOperatingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 260);
    }

    @Override
    protected boolean formed() {
        return menu.formed() != 0;
    }

    @Override
    protected boolean cabinet() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        abort = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.process_cell.abort"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 8, topPos + 232, 160, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        abort.active = menu.inputsLocked();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        line(g, Component.translatable("block.lithmod.euv_scanner_operating_panel"), 8, 6, 160);
        line(g, Component.literal("FOUP     Mask    Resist"), 24, 22, 142);
        line(g, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 26, 56, 142);

        // UPW
        line(g, Component.literal("UPW: " + menu.water() + " / " + menu.upwPerTick() + " mb/t"), 26, 70, 142);
        // Vacuum
        line(g, Component.literal("Vacuum: " + (menu.vacuumOk() ? "OK" : "LOW")), 26, 82, 142);
        // Light source
        line(g, Component.literal("Source: " + (menu.hasLightSource() ? "Reserved" : "None")), 26, 94, 142);

        line(g, playerInventoryTitle, 8, 72 + 60, 160);
        line(g, Component.translatable(formed() ? "state.lithmod.process_cell.formed" : "state.lithmod.process_cell.unformed"), 8, 168 + 60, 160);

        if (!menu.calibrated() && !menu.inputsLocked()) {
            line(g, Component.literal(menu.contaminated() ? "Retune Beam + Diagnostic" : "Use Beam + Diagnostic Panels"), 8, 180 + 60, 160);
        } else {
            line(g, Component.literal(menu.inputsLocked() ? "Calibration fixed for this lot" : "Calibration yield: " + menu.multiplierPermille() / 10.0 + "%"), 8, 180 + 60, 160);
        }
        line(g, statusText(), 8, 192 + 60, 160);
        line(g, Component.literal("Layer " + menu.currentLayer() + "/" + menu.totalLayers() + "  UPW " + menu.water()), 8, 204 + 60, 160);
        int ticks = menu.ticks(), progress = menu.progress();
        line(g, Component.literal("Exposure: " + progress + "/" + ticks + " t" + (ticks > 0 ? " (" + (int)(100L * progress / ticks) + "%)" : "")), 8, 216 + 60, 160);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        g.fill(leftPos + 8, topPos + 227 + 60, leftPos + 168, topPos + 230 + 60, 0xFF555555);
        int fill = menu.ticks() > 0 ? (int) Math.clamp(160L * menu.progress() / menu.ticks(), 0, 160) : 0;
        if (fill > 0) g.fill(leftPos + 8, topPos + 227 + 60, leftPos + 8 + fill, topPos + 230 + 60, 0xFF55BB99);
    }

    private Component refusalText(int issue) {
        return Component.translatable("state.lithmod.process_cell." + RefusalReason.Code.values()[issue - 1].name().toLowerCase(Locale.ROOT));
    }

    private Component statusText() {
        if (menu.aborted()) return Component.translatable("state.lithmod.process_cell.stopped_after_abort");
        if (menu.status() == ProcessCellMenu.STATUS_DEFINITION_MISSING) return Component.translatable("state.lithmod.process_cell.missing_definition");
        int issue = menu.operatingIssue();
        if (issue > 0 && issue <= RefusalReason.Code.values().length) return refusalText(issue);
        int refusal = menu.refusal();
        if (refusal > 0 && refusal <= RefusalReason.Code.values().length) return refusalText(refusal);
        int status = menu.status();
        return Component.translatable("state.lithmod.process_cell." + (status >= 0 && status < ProcessState.values().length ? ProcessState.values()[status] : ProcessState.IDLE).name().toLowerCase(Locale.ROOT));
    }
}