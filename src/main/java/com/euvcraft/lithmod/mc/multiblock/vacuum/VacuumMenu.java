package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

/**
 * The central vacuum plant controller GUI: network status, capacity, demand, clients.
 */
public final class VacuumMenu extends FabMachineMenu {

    private final ContainerData network;

    public static final int NETWORK_WIRE_FORMED = 0;
    public static final int NETWORK_WIRE_CAPACITY = 1;
    public static final int NETWORK_WIRE_DEMAND = 2;
    public static final int NETWORK_WIRE_FE_PAUSED = 3;
    public static final int NETWORK_WIRE_CONFLICT = 4;
    public static final int NETWORK_WIRE_CLIENTS = 5;
    public static final int NETWORK_WIRE_ENABLED = 6;
    public static final int NETWORK_WIRE_PLANT_TICK = 7;
    public static final int NETWORK_WIRE_COUNT = 8;

    public VacuumMenu(int id, Inventory inv, @Nullable VacuumControllerBlockEntity machine) {
        super(VacuumMachines.MENU.get(), id, inv, machine, 0, 0);
        network = machine == null ? new SimpleContainerData(NETWORK_WIRE_COUNT) : new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case NETWORK_WIRE_FORMED -> machine.isFormed() ? 1 : 0;
                    case NETWORK_WIRE_CAPACITY -> machine.networkCapacity();
                    case NETWORK_WIRE_DEMAND -> machine.networkDemand();
                    case NETWORK_WIRE_FE_PAUSED -> machine.networkFePaused();
                    case NETWORK_WIRE_CONFLICT -> machine.networkConflict() ? 1 : 0;
                    case NETWORK_WIRE_CLIENTS -> machine.networkClientCount();
                    case NETWORK_WIRE_ENABLED -> machine.enabled() ? 1 : 0;
                    case NETWORK_WIRE_PLANT_TICK -> (int) (machine.plantTick() & 0x7FFFFFFF);
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return NETWORK_WIRE_COUNT; }
        };
        addDataSlots(new com.euvcraft.lithmod.mc.cleanroom.WideContainerData(network));
    }

    public VacuumMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (VacuumControllerBlockEntity) null);
    }

    @Override protected void addMachineSlots() {
        // No item slots for the vacuum plant
    }

    public boolean formed() { return network.get(NETWORK_WIRE_FORMED) == 1; }
    public int networkCapacity() { return network.get(NETWORK_WIRE_CAPACITY); }
    public int networkDemand() { return network.get(NETWORK_WIRE_DEMAND); }
    public int networkFePaused() { return network.get(NETWORK_WIRE_FE_PAUSED); }
    public boolean networkConflict() { return network.get(NETWORK_WIRE_CONFLICT) == 1; }
    public int networkClientCount() { return network.get(NETWORK_WIRE_CLIENTS); }
    public boolean enabled() { return network.get(NETWORK_WIRE_ENABLED) == 1; }
    public long plantTick() { return network.get(NETWORK_WIRE_PLANT_TICK); }
}