package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.OperatingPanelMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The block entity behind {@link ProcessCellOperatingPanelBlock}.
 *
 * <p>All behaviour is in {@link ProcessCellMemberBlockEntity}; this type exists so the operating
 * panel has a block entity type of its own, which the block and capability registries both key on.
 * The panel is a casing member that the controller recognises as the required OPERATING_PANEL kind.</p>
 *
 * <p>This block entity also acts as a {@link MenuProvider} for the operating panel menu. It does not
 * own any inventory; the menu it creates references the controller block entity directly below.</p>
 */
public class ProcessCellOperatingPanelBlockEntity extends ProcessCellMemberBlockEntity implements MenuProvider {

    public ProcessCellOperatingPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_OPERATING_PANEL_BE.get(), pos, state);
    }

    /**
     * Server tick: clears formed state when the owning controller is gone or has unformed.
     * This mirrors the casing/power port backstop logic.
     */
    @Override
    public void serverTick() {
        super.serverTick();
    }

    @Override
    public Component getDisplayName() {
        // Delegate to the controller if available, otherwise use a default
        if (controller() != null) {
            return controller().getDisplayName();
        }
        return Component.translatable("block.lithmod.process_cell_operating_panel");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // The extra data (core position) is sent via the packet from openMenu
        // We need to send it ourselves since we're not using the standard openMenu(path)
        // For now, we'll create the menu with the known core position (below this panel)
        BlockPos corePos = worldPosition.below();
        ProcessCellControllerBlockEntity controller = controller();
        if (controller != null) {
            return new OperatingPanelMenu(containerId, playerInventory, controller, corePos);
        }
        // Fallback: create client-side menu that will read corePos from buffer
        return new OperatingPanelMenu(containerId, playerInventory, (FabMachineBlockEntity) null, corePos);
    }
}