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
                .bounds(leftPos + 8, topPos + 18, 75, 14).build());
        isolateBtn = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.euv.vacuum_isolation"), b -> send(1))
                .bounds(leftPos + 93, topPos + 18, 75, 14).build());
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
        text(g,"Filter",18,54,60);text(g,"Redstone",98,54,60);
        text(g,playerInventoryTitle.getString(),8,72,160);
        int installed=0;for(int i=0;i<6;i++)installed+=menu.value(i)!=0?1:0;
        text(g,(menu.value(6)!=0?"Formed":"Unformed")+"; modules "+installed+"/6",8,170,160);
        String[] condition={"Clean","Repair required","Beam + Diagnostic required"};
        text(g,condition[Math.clamp(menu.value(7),0,2)],8,182,160);
        text(g,menu.value(9)+" / "+menu.value(10)+" FE",8,194,160);
        text(g,"UPW: "+menu.value(8)+" mB",8,206,160);
        text(g,menu.value(11)==1?"Vacuum isolated":"Central vacuum enabled",8,218,160);
    }
}