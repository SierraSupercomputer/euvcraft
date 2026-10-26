package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * A zone member owns neither items nor FE. It supports exactly one owner: the zone
 * refiner controller. The operating panel opens the upper panel menu over the machine
 * below it; the water inlet exposes the UPW lens.
 */
public final class ZoneMemberBlockEntity extends AdvancedMemberBlockEntity {

    public ZoneMemberBlockEntity(BlockPos pos, BlockState state) {
        super(ZoneMachines.MEMBER_BE.get(), pos, state);
    }

    @Override public boolean supportsOwner(AdvancedOwner candidate) {
        return candidate instanceof ZoneControllerBlockEntity;
    }

    @Override public BlockKind structureKind() {
        var block = getBlockState().getBlock();
        if (block == ZoneMachines.CASING.get()) return BlockKind.CASING;
        if (block == ZoneMachines.POWER_PORT.get()) return BlockKind.POWER_PORT;
        if (block == ZoneMachines.OPERATING_PANEL.get()) return BlockKind.OPERATING_PANEL;
        if (block == ZoneMachines.WATER_INLET.get()) return BlockKind.WATER_INLET;
        return BlockKind.OTHER;
    }

    @Override protected boolean energyPort() {
        return getBlockState().getBlock() == ZoneMachines.POWER_PORT.get();
    }

    /**
     * The inlet's UPW lens, or null for every other zone member and for a lens whose
     * controller is not a live zone machine.
     */
    public @Nullable ResourceHandler<FluidResource> handler() {
        var owner = controller();
        return owner instanceof ZoneControllerBlockEntity zone && structureKind() == BlockKind.WATER_INLET
            ? new ZoneFluidHandler(this, zone)
            : null;
    }

    @Override public Component getDisplayName() {
        var below = belowOwner();
        return below == null
            ? Component.translatable("block.lithmod.zone_operating_panel")
            : below.machine().getDisplayName();
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (structureKind() != BlockKind.OPERATING_PANEL) {
            return null;
        }
        var below = belowOwner();
        return below == null ? null : new ZoneOperatingPanelMenu(id, inventory, below.machine(), worldPosition.below());
    }
}
