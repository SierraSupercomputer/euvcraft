package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class VacuumScreen extends AdvancedMachineScreen<VacuumMenu> {

    public VacuumScreen(VacuumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 238);
    }

    @Override protected boolean formed() { return menu.formed(); }
    @Override protected boolean cabinet() { return false; }

    // Lower band (local y, opaque 176x238 window, hotbar well ends at 159):
    // 170 formed status; formed: 182 network status, 194 capacity/demand, 206 FE, 218 clients;
    // unformed: 184/198 status; note at 228.
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        line(g, title, 8, 6, 160);
        line(g, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 30, 57, 140);
        line(g, playerInventoryTitle, 8, 72, 160);
        line(g, Component.literal(formed() ? "Formed" : "Unformed: check structure"), 8, 170, 160);
        if (formed()) {
            line(g, networkStatus(), 8, 182, 160);
            line(g, capacityDemand(), 8, 194, 160);
            line(g, feStatus(), 8, 206, 160);
            line(g, clientsStatus(), 8, 218, 160);
        } else {
            line(g, Component.literal("Enabled: " + (menu.enabled() ? "Yes" : "No")), 8, 184, 160);
            line(g, Component.literal("Plant tick: " + menu.plantTick()), 8, 198, 160);
        }
        line(g, Component.literal("Service outlets connect to consumer ports"), 8, 228, 160);
    }

    private Component networkStatus() {
        if (menu.networkConflict()) {
            return Component.literal("Status: CONFLICT - Multiple plants on network");
        }
        if (!menu.enabled()) {
            return Component.literal("Status: DISABLED");
        }
        if (menu.networkClientCount() == 0) {
            return Component.literal("Status: IDLE - No consumers connected");
        }
        return Component.literal("Status: ACTIVE - Servicing " + menu.networkClientCount() + " consumer(s)");
    }

    private Component capacityDemand() {
        return Component.literal("Capacity: " + menu.networkCapacity() + " µTorr/t | Demand: " + menu.networkDemand() + " µTorr/t");
    }

    private Component feStatus() {
        if (menu.networkFePaused() > 0) {
            return Component.literal("FE: PAUSED - Need " + menu.networkFePaused() + " FE/t for service");
        }
        return Component.literal("FE: OK - Supplying service");
    }

    private Component clientsStatus() {
        return Component.literal("Connected clients: " + menu.networkClientCount());
    }
}