package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.RefusalReason;
import java.util.Locale;
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
        super(menu, inventory, title, 288);
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
        }).bounds(leftPos + 8, topPos + 260, 160, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        abort.active = menu.inputsLocked();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        String[] sourceIssues={"Source available","Check physical beamline","Original source required","Calibrate source panel","Source utilities not ready"};
        line(g,Component.literal(sourceIssues[Math.clamp(menu.sourceIssue(),0,4)]),8,234,160);
        line(g,Component.literal("Source yield x"+menu.sourceMultiplier()/1000.0),8,246,160);
        line(g, Component.translatable("block.lithmod.euv_scanner_operating_panel"), 8, 6, 160);
        line(g, Component.literal("FOUP     Mask    Resist"), 24, 22, 142);
        line(g, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 26, 56, 142);

        line(g,playerInventoryTitle,8,72,160);
        line(g,Component.literal(formed()?"Formed; calibration "+menu.multiplierPermille()/10.0+"%":"Unformed: check structure"),8,168,160);
        line(g,statusText(),8,180,160);
        line(g,Component.literal("UPW "+menu.water()+"; vacuum "+(menu.vacuumOk()?"ready":"pumping")),8,192,160);
        line(g,Component.literal(menu.hasLightSource()?"Source: ready":"Source: check source panel"),8,204,160);
        line(g,Component.literal("Layer "+menu.currentLayer()+"/"+menu.totalLayers()+"  "+menu.progress()+"/"+menu.ticks()+" t"),8,216,160);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        g.fill(leftPos + 8, topPos + 227, leftPos + 168, topPos + 230, 0xFF555555);
        int fill = menu.ticks() > 0 ? (int) Math.clamp(160L * menu.progress() / menu.ticks(), 0, 160) : 0;
        if (fill > 0) g.fill(leftPos + 8, topPos + 227, leftPos + 8 + fill, topPos + 230, 0xFF55BB99);
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