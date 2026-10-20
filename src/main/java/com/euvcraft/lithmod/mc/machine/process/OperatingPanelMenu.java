package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellOperatingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Upper view of the same controller. The captured panel identity prevents stale-menu reuse. */
public final class OperatingPanelMenu extends ProcessCellMenu {
    private final BlockPos corePos;
    private final @Nullable ProcessCellOperatingPanelBlockEntity panel;

    public OperatingPanelMenu(int id, Inventory inventory, @Nullable FabMachineBlockEntity machine, BlockPos corePos) {
        super(ModMultiblocks.OPERATING_PANEL_MENU_TYPE.get(), id, inventory, machine);
        this.corePos = corePos.immutable();
        this.panel = machine != null && machine.getLevel() != null
            && machine.getLevel().getBlockEntity(corePos.above()) instanceof ProcessCellOperatingPanelBlockEntity p ? p : null;
    }

    public OperatingPanelMenu(int id, Inventory inventory, @Nullable FabMachineBlockEntity machine) {
        this(id, inventory, machine, machine == null ? BlockPos.ZERO : machine.getBlockPos());
    }

    /** openMenu(panel, panelPos) supplies the panel position, not the owner's position. */
    public OperatingPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos().below());
    }

    @Override public boolean isOperatingPanel() { return true; }
    public BlockPos corePos() { return corePos; }

    @Override public boolean stillValid(Player player) {
        return super.stillValid(player) && blockEntity != null && panel != null
            && blockEntity.getBlockPos().equals(corePos)
            && blockEntity.getLevel().getBlockEntity(corePos.above()) == panel
            && player.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 1.5, corePos.getZ() + 0.5) <= 64.0;
    }
}
