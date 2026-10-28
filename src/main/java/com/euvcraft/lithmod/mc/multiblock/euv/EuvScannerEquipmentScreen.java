package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Equipment screen for EUV scanner (lower cabinet). Shows repair/isolation buttons and material slots. */
public final class EuvScannerEquipmentScreen extends AbstractContainerScreen<EuvScannerEquipmentMenu> {

    private Button repairBtn, isolateBtn;

    public EuvScannerEquipmentScreen(EuvScannerEquipmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 238);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        repairBtn = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.euv.repair"), b -> send(0))
                .bounds(leftPos + 8, topPos + 10, 75, 20).build());
        isolateBtn = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.euv.vacuum_isolation"), b -> send(1))
                .bounds(leftPos + 93, topPos + 10, 75, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (repairBtn != null) repairBtn.active = menu.value(6) != 0 && menu.slots.get(0).hasItem() && menu.slots.get(1).hasItem();
        if (isolateBtn != null) isolateBtn.active = menu.value(6) != 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        // Opaque panel background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFD0D0D0);
        // Slot wells for repair items (slots 0,1) and player inventory
        for (var slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF777777);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFFA0A0A0);
        }
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int width) {
        g.text(font, font.plainSubstrByWidth(s, width), x, y, 0xFF303030, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        text(g, Component.translatable("block.lithmod.euv_scanner_equipment_cabinet").getString(), 8, 7, 160);
        text(g, "Filter", 18, 35, 60);
        text(g, "Redstone", 98, 35, 60);

        // Status lines inside panel
        text(g, "Formed: " + (menu.value(6) != 0 ? "Yes" : "No"), 8, 60, 160);
        text(g, "Equipment slots:", 8, 76, 160);
        String[] names = {"Exposure", "Optics", "Stage", "Filtration", "Metrology", "Vacuum"};
        for (int i = 0; i < 6; i++) {
            text(g, names[i] + ": " + (menu.value(i) != 0 ? "Installed" : "Empty"), 12, 92 + i * 14, 150);
        }
        text(g, "Contamination: " + menu.value(7) + "  UPW: " + menu.value(8) + "  FE: " + menu.value(9) + "/" + menu.value(10), 8, 180, 160);
        text(g, "Vacuum isolation: (API gap: no getter on controller)", 8, 194, 160);
    }
}