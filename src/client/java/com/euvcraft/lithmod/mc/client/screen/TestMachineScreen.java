package com.euvcraft.lithmod.mc.client.screen;

import com.euvcraft.lithmod.mc.block.entity.TestMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Screen for the test machine.
 */
@OnlyIn(Dist.CLIENT)
public class TestMachineScreen extends com.euvcraft.lithmod.mc.client.screen.FabMachineScreen<TestMachineMenu> {

    public TestMachineScreen(TestMachineMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics guiGraphics) {
        // Draw input/output labels
        guiGraphics.drawString(font, "Input (FOUP)", 80, 20, 0x404040);
        guiGraphics.drawString(font, "Output", 80, 70, 0x404040);
    }

    @Override
    protected int getEnergyBarX() {
        return 8;
    }

    @Override
    protected int getEnergyBarY() {
        return 20;
    }

    @Override
    protected int getProgressBarX() {
        return 80;
    }

    @Override
    protected int getProgressBarY() {
        return 50;
    }
}