package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

/**
 * The zone controller's lower GUI: status only. It exposes no machine slots at all —
 * the boule slots live on the operating panel above the controller.
 */
public final class ZoneStatusMenu extends FabMachineMenu {
    private final ContainerData zone;

    public ZoneStatusMenu(int id, Inventory inventory, @Nullable ZoneControllerBlockEntity machine) {
        super(ZoneMachines.STATUS_MENU.get(), id, inventory, machine, 1, 2);
        zone = machine == null ? new SimpleContainerData(4) : new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> machine.isFormed() ? 1 : 0;
                    case 1 -> machine.waterAmount();
                    case 2 -> machine.waterCapacity();
                    case 3 -> machine.hasJob() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 4; }
        };
        addDataSlots(zone);
    }

    public ZoneStatusMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, (ZoneControllerBlockEntity) null);
    }

    @Override protected void addMachineSlots() {}

    public boolean formed() {
        return zone.get(0) == 1;
    }

    public int waterAmount() {
        return zone.get(1);
    }

    public int waterCapacity() {
        return zone.get(2);
    }

    public boolean hasJob() {
        return zone.get(3) == 1;
    }
}
