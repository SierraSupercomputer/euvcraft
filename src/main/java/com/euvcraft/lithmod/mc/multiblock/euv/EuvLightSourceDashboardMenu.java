package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Dashboard menu for EUV Light Source. */
public final class EuvLightSourceDashboardMenu extends AbstractContainerMenu {

    private final @Nullable EuvLightSourceControllerBlockEntity lightSource;
    private final BlockPos consolePos;
    private final ContainerData values;

    public EuvLightSourceDashboardMenu(int id, Inventory inventory, @Nullable EuvLightSourceControllerBlockEntity lightSource, BlockPos pos) {
        super(EuvMachines.LIGHT_SOURCE_DASHBOARD_MENU.get(), id);
        this.lightSource = lightSource;
        this.consolePos = pos.immutable();
        this.values = lightSource == null ? new SimpleContainerData(5) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> lightSource.isFormed() ? 1 : 0;
                    case 1 -> lightSource.vacuumState().canProcess(lightSource.vacuumSpec()) ? 1 : 0;
                    case 2 -> lightSource.hydrogenTank.amount();
                    case 3 -> lightSource.tinStored();
                    case 4 -> lightSource.vacuumState().pressureMicroTorr() / 1000;
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 5; }
        };
        addDataSlots(values);
    }

    public EuvLightSourceDashboardMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    public int value(int i) { return values.get(i); }

    @Override
    public boolean stillValid(Player player) {
        return lightSource != null && lightSource.getLevel() == player.level()
                && !lightSource.isRemoved() && lightSource.getLevel().getBlockEntity(lightSource.getBlockPos()) == lightSource
                && player.distanceToSqr(consolePos.getX() + .5, consolePos.getY() + .5, consolePos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}