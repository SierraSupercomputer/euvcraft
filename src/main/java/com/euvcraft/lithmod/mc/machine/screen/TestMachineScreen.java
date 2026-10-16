package com.euvcraft.lithmod.mc.machine.screen;

import com.euvcraft.lithmod.mc.machine.test.TestMachineMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Screen for the test-only machine; only wires up where the base screen's bars sit. */
public class TestMachineScreen extends FabMachineScreen<TestMachineMenu> {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    public TestMachineScreen(TestMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    protected int energyBarX() {
        return 12;
    }

    @Override
    protected int energyBarY() {
        return 18;
    }

    @Override
    protected int progressBarX() {
        return 78;
    }

    @Override
    protected int progressBarY() {
        return 52;
    }
}
