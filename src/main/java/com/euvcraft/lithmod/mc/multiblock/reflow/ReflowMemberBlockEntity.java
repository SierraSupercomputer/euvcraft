package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Member of the reflow oven. Links are rebuilt from formation, never persisted; the only
 * persisted member state is the calibration panel's installation identity, which seeds the
 * thermal response and fingerprints accepted calibration.
 */
public final class ReflowMemberBlockEntity extends AdvancedMemberBlockEntity {

    private @Nullable UUID installation;

    public ReflowMemberBlockEntity(BlockPos pos, BlockState state) {
        super(ReflowMachines.MEMBER_BE.get(), pos, state);
    }

    @Override
    public boolean supportsOwner(AdvancedOwner candidate) {
        return candidate instanceof ReflowControllerBlockEntity;
    }

    @Override
    public BlockKind structureKind() {
        return getBlockState().getBlock() instanceof ReflowMemberBlock block ? block.kind() : BlockKind.OTHER;
    }

    @Override
    protected boolean energyPort() {
        return getBlockState().getBlock() == ReflowMachines.POWER_PORT.get();
    }

    /** The owning reflow oven, or null when this member is not attached to a live oven. */
    public @Nullable ReflowControllerBlockEntity reflowController() {
        if (level == null) {
            return null;
        }
        AdvancedOwner owner = controller();
        return owner instanceof ReflowControllerBlockEntity oven
                && level.getBlockEntity(oven.getBlockPos()) == oven && !oven.isRemoved() ? oven : null;
    }

    /** Server-only stable installation identity of this physical member. */
    public UUID installation() {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("Server installation identity only");
        }
        if (installation == null) {
            installation = UUID.randomUUID();
            setChanged();
        }
        return installation;
    }

    @Override
    public Component getDisplayName() {
        String id = switch (structureKind()) {
            case OPERATING_PANEL -> "reflow_operating_panel";
            case CALIBRATION_PANEL -> "reflow_calibration_panel";
            case POWER_PORT -> "reflow_power_port";
            default -> "reflow_casing";
        };
        return Component.translatable("block.lithmod." + id);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        ReflowControllerBlockEntity oven = reflowController();
        if (oven == null) {
            return null;
        }
        return switch (structureKind()) {
            case OPERATING_PANEL -> new ReflowOperatingMenu(id, inventory, oven, worldPosition);
            case CALIBRATION_PANEL -> new ReflowCalibrationMenu(id, inventory, oven, worldPosition);
            default -> null;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            installation = UUID.fromString(input.getString("installation").orElse(""));
        } catch (IllegalArgumentException malformed) {
            installation = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (installation != null) {
            output.putString("installation", installation.toString());
        }
    }
}
