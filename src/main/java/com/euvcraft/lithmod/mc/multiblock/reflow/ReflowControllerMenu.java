package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;

/**
 * Status-only view of the lower reflow controller. It exposes no machine slots: the operating
 * panel owns the chip/blank input and output slots, and the calibration panel owns the coupon,
 * controls and graph.
 */
public final class ReflowControllerMenu extends FabMachineMenu {

    private final @Nullable ReflowControllerBlockEntity oven;
    private final BlockPos controllerPos;
    private final ContainerData status;

    public ReflowControllerMenu(int id, Inventory inventory,
            @Nullable ReflowControllerBlockEntity oven, BlockPos controllerPos) {
        super(ReflowMachines.CONTROLLER_MENU.get(), id, inventory, oven, 2, 5);
        this.oven = oven;
        this.controllerPos = controllerPos.immutable();
        this.status = ReflowStatusData.of(oven);
        addDataSlots(this.status);
    }

    public ReflowControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    @Override
    protected void addMachineSlots() {
        // Deliberately empty: this view never shows or moves machine contents.
    }

    public int status(int index) {
        return status.get(index);
    }

    public boolean formed() {
        return status.get(ReflowStatusData.FORMED) == 1;
    }

    public boolean calibrated() {
        return status.get(ReflowStatusData.CALIBRATED) == 1;
    }

    public boolean ready() {
        return status.get(ReflowStatusData.READY) == 1;
    }

    public boolean inputsLocked() {
        return status.get(ReflowStatusData.LOCKED) == 1;
    }

    public boolean paused() {
        return status.get(ReflowStatusData.PAUSED) == 1;
    }

    public int reason() {
        return status.get(ReflowStatusData.REASON);
    }

    public int acceptedQuality() {
        return status.get(ReflowStatusData.ACCEPTED_QUALITY);
    }

    public int acceptedPermille() {
        return status.get(ReflowStatusData.ACCEPTED_PERMILLE);
    }

    public int minimumQuality() {
        return status.get(ReflowStatusData.MIN_QUALITY);
    }

    public int scrapPerFailure() {
        return status.get(ReflowStatusData.SCRAP_PER_FAILURE);
    }

    @Override
    public boolean stillValid(Player player) {
        return oven != null
                && oven.getLevel() == player.level()
                && !oven.isRemoved()
                && oven.getLevel() != null
                && oven.getLevel().getBlockEntity(controllerPos) == oven
                && oven.getBlockState().getBlock() instanceof ReflowControllerBlock
                && player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                        controllerPos.getZ() + 0.5) <= 64.0;
    }

    /** No server-side buttons on the status view. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    /** The base menu check is stricter than an identity-only comparison; keep it explicit. */
    static boolean entityAt(@Nullable FabMachineBlockEntity machine, BlockPos pos) {
        return machine != null && machine.getLevel() != null
                && machine.getLevel().getBlockEntity(pos) == machine;
    }
}
