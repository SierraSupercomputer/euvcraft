package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

/** Operating menu for EUV Light Source. */
public final class EuvLightSourceOperatingMenu extends AbstractContainerMenu {

    private final @Nullable EuvLightSourceControllerBlockEntity lightSource;
    private final BlockPos panelPos;
    private final ContainerData data;

    public EuvLightSourceOperatingMenu(int containerId, Inventory playerInventory, @Nullable EuvLightSourceControllerBlockEntity lightSource, BlockPos pos) {
        super(EuvMachines.LIGHT_SOURCE_OPERATING_MENU.get(), containerId);
        this.lightSource = lightSource;
        this.panelPos = pos.immutable();
        this.data = lightSource == null ? new SimpleContainerData(6) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> lightSource.buffer().getAmountAsInt();
                    case 1 -> lightSource.buffer().getCapacityAsInt();
                    case 2 -> lightSource.hydrogenTank.amount();
                    case 3 -> lightSource.tinStored();
                    case 4 -> lightSource.vacuumState().pressureMicroTorr() / 1000;
                    case 5 -> lightSource.reservedScannerId != null ? 1 : 0;
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 6; }
        };
        addDataSlots(this.data);
    }

    public EuvLightSourceOperatingMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos());
    }

    public int value(int i) { return data.get(i); }

    @Override
    public boolean stillValid(Player player) {
        return lightSource != null && lightSource.getLevel() == player.level()
                && !lightSource.isRemoved() && lightSource.getLevel().getBlockEntity(lightSource.getBlockPos()) == lightSource
                && player.distanceToSqr(panelPos.getX() + .5, panelPos.getY() + .5, panelPos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}