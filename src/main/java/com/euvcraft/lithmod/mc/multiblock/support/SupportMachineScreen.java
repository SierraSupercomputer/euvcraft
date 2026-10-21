package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

/** Support menus keep the player inventory fixed; controls sit below the hotbar. */
public class SupportMachineScreen extends FabMachineScreen<SupportMachineMenu> {
    private static final int COLOR = 0xFF404040;
    private Button previous, next, start;

    public SupportMachineScreen(SupportMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 220);
        inventoryLabelY = 72;
    }
    @Override protected int energyBarX() { return 8; }
    @Override protected int energyBarY() { return 17; }
    @Override protected int progressBarX() { return 146; }
    @Override protected int progressBarY() { return 17; }

    @Override protected void init() {
        super.init();
        if (menu.kind() != SupportMachineKind.EBEAM) return;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            int selected = menu.ebeamSelectedDesign();
            send(selected <= 0 ? 6 : selected - 1);
        }).bounds(leftPos + 8, topPos + 180, 20, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            int selected = menu.ebeamSelectedDesign();
            send(selected < 0 ? 0 : (selected + 1) % 7);
        }).bounds(leftPos + 148, topPos + 180, 20, 16).build());
        start = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.ebeam.start"),
            b -> send(SupportMachineMenu.EBEAM_START_BUTTON))
            .bounds(leftPos + 8, topPos + 200, 88, 16).build());
        updateButtons();
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override public void containerTick() {
        super.containerTick();
        updateButtons();
    }
    private void updateButtons() {
        if (start == null) return;
        boolean canSelect = menu.formed() == 1 && !menu.ebeamJobRunning();
        previous.active = canSelect;
        next.active = canSelect;
        start.active = canSelect && menu.ebeamDesignAvailable(menu.ebeamSelectedDesign());
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component formed = Component.translatable(menu.formed() == 1
            ? "state.lithmod.process_cell.formed" : "state.lithmod.process_cell.unformed");
        if (menu.kind() == SupportMachineKind.PURIFIER) {
            right(graphics, Component.translatable("label.lithmod.support.formed", formed), 168);
            right(graphics, Component.translatable("label.lithmod.support.water", menu.water(), menu.waterCapacity()), 178);
            return;
        }
        Component status = menu.ebeamJobRunning()
            ? Component.translatable(menu.ebeamJobPaused() ? "label.lithmod.ebeam.paused" : "label.lithmod.ebeam.running")
            : Component.translatable("label.lithmod.support.formed", formed);
        right(graphics, status, 168);
        int selected = menu.ebeamSelectedDesign();
        Component design = selected < 0 ? Component.translatable("label.lithmod.ebeam.no_selection")
            : Component.translatable("label.lithmod.ebeam.design",
                Component.translatable("dieType.lithmod." + SupportMachineMenu.intToDieType(menu.ebeamSelectedDieType())),
                menu.ebeamSelectedTier());
        graphics.text(font, design, (176 - font.width(design)) / 2, 184, COLOR, false);
        if (selected >= 0) {
            Component ingredients = menu.ebeamDesignAvailable(selected) || menu.ebeamJobRunning()
                ? Component.translatable("label.lithmod.ebeam.ingredients", menu.ebeamBlankCost(), menu.ebeamRedstoneCost())
                : Component.translatable("label.lithmod.ebeam.unavailable");
            graphics.text(font, ingredients, 26, 58, COLOR, false);
            right(graphics, Component.literal(compact(menu.ebeamTotalEnergy()) + " FE"), 204);
        }
    }
    private void right(GuiGraphicsExtractor graphics, Component text, int y) {
        graphics.text(font, text, 168 - font.width(text), y, COLOR, false);
    }
    private static String compact(long value) {
        if (value >= 1_000_000_000_000L) return String.format(Locale.ROOT, "%.1fT", value / 1e12);
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1e9);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        if (value >= 1_000L) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        return Long.toString(value);
    }
}
