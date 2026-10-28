package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class HydrogenScreen extends AdvancedMachineScreen<HydrogenMenu> {

    public HydrogenScreen(HydrogenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 238);
    }

    @Override protected boolean formed() { return menu.formed(); }
    @Override protected boolean cabinet() { return false; }

    // Lower band (local y, opaque 176x238 window, hotbar well ends at 159):
    // 170 formed status; formed: 182 batch status, 194 batch costs, 206 raw, 218 pure;
    // unformed: 184/198 tank rows; the outlet note sits at 228 on its own line.
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        line(g, title, 8, 6, 160);
        line(g, Component.literal("Filter"), 30, 23, 42);
        line(g, Component.literal("Output"), 121, 23, 47);
        line(g, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 30, 57, 140);
        line(g, playerInventoryTitle, 8, 72, 160);
        line(g, Component.literal(formed() ? "Formed" : "Unformed: check structure"), 8, 170, 160);
        if (formed()) {
            line(g, batchStatus(), 8, 182, 160);
            line(g, batchCosts(), 8, 194, 160);
            line(g, Component.literal("Raw H2: " + menu.rawHydrogen() + " / " + menu.rawHydrogenCapacity() + " mB"), 8, 206, 160);
            line(g, Component.literal("Fab H2: " + menu.pureHydrogen() + " / " + menu.pureHydrogenCapacity() + " mB"), 8, 218, 160);
        } else {
            line(g, Component.literal("Raw H2: " + menu.rawHydrogen() + " / " + menu.rawHydrogenCapacity() + " mB"), 8, 184, 160);
            line(g, Component.literal("Fab H2: " + menu.pureHydrogen() + " / " + menu.pureHydrogenCapacity() + " mB"), 8, 198, 160);
        }
        line(g, Component.literal("Fab H2 outlet to pressurized conduit"), 8, 228, 160);
    }

    /** Batch progress banner. Pauses mirror the server's own per-tick conditions exactly. */
    private Component batchStatus() {
        int raw = menu.batchRawMb(), output = menu.batchOutputMb(), filters = menu.batchFilters(), fe = menu.batchFePerTick();
        if (menu.active()) {
            if (output <= 0 || fe <= 0) return Component.literal("Paused: settings not loaded");
            int free = menu.pureHydrogenCapacity() - menu.pureHydrogen();
            if (free < output) return Component.literal("Paused: pure tank full");
            if (menu.energy() < fe) return Component.literal("Paused: waiting for " + fe + " FE/t");
            return Component.literal("Batch: " + menu.progress() + "/" + menu.ticks() + " ticks");
        }
        if (raw <= 0 || output <= 0 || filters <= 0 || fe <= 0) return Component.literal("Idle: settings not loaded");
        if (menu.rawHydrogen() < raw) return Component.literal("Idle: waiting for " + (raw - menu.rawHydrogen()) + " mB raw H2");
        if (menu.filterCount() < filters) return Component.literal("Idle: insert " + (filters - menu.filterCount()) + " filter(s)");
        int free = menu.pureHydrogenCapacity() - menu.pureHydrogen();
        if (free < output) return Component.literal("Idle: pure tank needs " + output + " mB free");
        if (menu.energy() < fe) return Component.literal("Idle: needs " + fe + " FE stored");
        return Component.literal("Ready: next batch queued");
    }

    /** The current or next batch's costs, straight from the synced menu state. */
    private Component batchCosts() {
        return Component.literal("Output: " + menu.batchOutputMb() + " mB Fab H2 | Filters: " + menu.batchFilters() + " | " + menu.batchFePerTick() + " FE/t");
    }
}