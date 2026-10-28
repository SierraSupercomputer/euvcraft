package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * The hydrogen purifier controller GUI: filter slot, batch progress, tank status.
 * Filter inventory input1 output0. Menu totalInventorySize1.
 */
public final class HydrogenMenu extends FabMachineMenu {

    private final ContainerData tanks;

    // Tank indices for the synced wire
    public static final int TANK_WIRE_FORMED = 0;
    public static final int TANK_WIRE_RAW = 1;
    public static final int TANK_WIRE_RAW_CAPACITY = 2;
    public static final int TANK_WIRE_PURE = 3;
    public static final int TANK_WIRE_PURE_CAPACITY = 4;
    public static final int TANK_WIRE_ACTIVE = 5;
    public static final int TANK_WIRE_BATCH_RAW = 6;
    public static final int TANK_WIRE_BATCH_OUTPUT = 7;
    public static final int TANK_WIRE_BATCH_FILTERS = 8;
    public static final int TANK_WIRE_BATCH_FE = 9;
    public static final int TANK_WIRE_BATCH_TICKS = 10;
    public static final int TANK_WIRE_COUNT = 11;

    public HydrogenMenu(int id, Inventory inv, @Nullable HydrogenControllerBlockEntity machine) {
        super(HydrogenMachines.MENU.get(), id, inv, machine, 1, 0);
        tanks = machine == null ? new SimpleContainerData(TANK_WIRE_COUNT) : new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case TANK_WIRE_FORMED -> machine.isFormed() ? 1 : 0;
                    case TANK_WIRE_RAW -> machine.rawHydrogenAmount();
                    case TANK_WIRE_RAW_CAPACITY -> machine.rawHydrogenCapacity();
                    case TANK_WIRE_PURE -> machine.pureHydrogenAmount();
                    case TANK_WIRE_PURE_CAPACITY -> machine.pureHydrogenCapacity();
                    case TANK_WIRE_ACTIVE -> machine.hasJob() ? 1 : 0;
                    case TANK_WIRE_BATCH_RAW -> machine.batchRawMb();
                    case TANK_WIRE_BATCH_OUTPUT -> machine.batchOutputMb();
                    case TANK_WIRE_BATCH_FILTERS -> machine.batchFilters();
                    case TANK_WIRE_BATCH_FE -> machine.batchFePerTick();
                    case TANK_WIRE_BATCH_TICKS -> machine.batchTicks();
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return TANK_WIRE_COUNT; }
        };
        addDataSlots(tanks);
    }

    public HydrogenMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (HydrogenControllerBlockEntity) null);
    }

    @Override protected void addMachineSlots() {
        // Slot 0: filter (utility_filter) - only input slot, no output slot for fluid-only output
        addCustomSlot(new Slot(machineSlots(), 0, 35, 35) {
            @Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
                return stack.is(ModItems.UTILITY_FILTER.get());
            }
        });
    }

    public boolean formed() { return tanks.get(TANK_WIRE_FORMED) == 1; }
    public int rawHydrogen() { return tanks.get(TANK_WIRE_RAW); }
    public int rawHydrogenCapacity() { return tanks.get(TANK_WIRE_RAW_CAPACITY); }
    public int pureHydrogen() { return tanks.get(TANK_WIRE_PURE); }
    public int pureHydrogenCapacity() { return tanks.get(TANK_WIRE_PURE_CAPACITY); }

    /** Viewer-facing batch state, all read back from the same synced ints the server wrote. */
    public boolean active() { return tanks.get(TANK_WIRE_ACTIVE) == 1; }

    /** Current or next batch inputs/costs: admitted job values while it runs, settings otherwise. */
    public int batchRawMb() { return tanks.get(TANK_WIRE_BATCH_RAW); }
    public int batchOutputMb() { return tanks.get(TANK_WIRE_BATCH_OUTPUT); }
    public int batchFilters() { return tanks.get(TANK_WIRE_BATCH_FILTERS); }
    public int batchFePerTick() { return tanks.get(TANK_WIRE_BATCH_FE); }
    public int batchTicks() { return tanks.get(TANK_WIRE_BATCH_TICKS); }

    /** Stacks in machine slot 0 on this side (the client mirrors the server stack), for the ready check. */
    public int filterCount() { return slots.isEmpty() ? 0 : slots.get(0).getItem().getCount(); }
}