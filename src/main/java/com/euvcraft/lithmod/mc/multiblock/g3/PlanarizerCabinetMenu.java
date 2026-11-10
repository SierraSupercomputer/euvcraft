package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.multiblock.linear.T8StageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The sample carrier is independent of the production inventory and its item ports. */
public final class PlanarizerCabinetMenu extends G3Menu {
    public static final int PRESSURE_DOWN = 10;
    public static final int PRESSURE_UP = 11;
    public static final int SPEED_DOWN = 12;
    public static final int SPEED_UP = 13;
    public static final int TEST_POLISH = 14;
    public static final int ACCEPT_POLISH = 15;

    public PlanarizerCabinetMenu(int id, Inventory inventory, T8StageBlockEntity machine) {
        this(id, inventory, machine, machine.getBlockPos());
    }

    public PlanarizerCabinetMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    private PlanarizerCabinetMenu(int id, Inventory inventory, @Nullable T8StageBlockEntity machine, BlockPos pos) {
        super(G3Machines.PLANARIZER_CABINET_MENU.get(), id, inventory, machine, pos, false, G3MachineKind.DEPOSITION_ETCHER);
    }

    @Override
    protected void addMachineSlots() {
        var machine = blockEntity instanceof T8StageBlockEntity stage ? stage : null;
        var sample = machine == null ? new SimpleContainer(1) : machine.polishSample();
        addCustomSlot(new Slot(sample, 0, 39, 41) {
            @Override public boolean mayPlace(ItemStack stack) {
                return machine == null ? FoupItems.isFoup(stack) : machine.acceptsPolishSample(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addMachineSlot(1, 67, 41);
        addMachineSlot(2, 95, 41);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < PRESSURE_DOWN || id > ACCEPT_POLISH) return super.clickMenuButton(player, id);
        return player instanceof ServerPlayer server && server.containerMenu == this && stillValid(player)
                && controller instanceof T8StageBlockEntity stage && stage.polishCommand(id);
    }

    int pressure() { return value(26); }
    int speed() { return value(27); }
    int removalError() { return value(28); }
    int edgeError() { return value(29); }
    int testQuality() { return value(30); }
    int acceptedQuality() { return value(31); }
    int minimumQuality() { return value(32); }
    boolean canTune() { return formed() && value(10) == 0 && value(7) == 0; }
}
