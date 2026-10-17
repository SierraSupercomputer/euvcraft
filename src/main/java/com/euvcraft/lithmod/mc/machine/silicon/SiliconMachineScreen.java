package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.screen.FabMachineScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The layout both machines of the silicon chain share: one input column on the left, the
 * energy bar beside it and the progress bar under the slots.
 *
 * <p>Two machines with two different menus still draw the same bars in the same places, so
 * the coordinates live here and each concrete screen only supplies its menu type. Drawing
 * itself stays in {@link FabMachineScreen}, which reads the bars from the synced menu data
 * and never from the machine.</p>
 *
 * <p>This class sits in common code like the framework's own screens do and is only ever
 * referenced from the client event subscriber annotated {@code Dist.CLIENT}, so a dedicated
 * server never loads it.</p>
 */
public abstract class SiliconMachineScreen<M extends FabMachineMenu> extends FabMachineScreen<M> {

    /** Background size of both silicon chain menus. */
    protected static final int IMAGE_WIDTH = 176;
    protected static final int IMAGE_HEIGHT = 166;

    protected SiliconMachineScreen(M menu, Inventory playerInventory, Component title) {
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