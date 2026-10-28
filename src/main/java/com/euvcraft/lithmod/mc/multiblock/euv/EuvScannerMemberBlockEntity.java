package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** EUV Scanner member block entity - owns neither items nor FE. Links rebuilt after reload, never persisted. */
public class EuvScannerMemberBlockEntity extends BlockEntity implements MenuProvider,com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumAccess {

    private @Nullable EuvFormationOwner owner;
    private boolean formed;
    private @Nullable UUID installation;

    public EuvScannerMemberBlockEntity(BlockPos pos, BlockState state) {
        this(EuvMachines.SCANNER_MEMBER_BE.get(), pos, state);
    }

    protected EuvScannerMemberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public BlockKind structureKind() {
        var block = getBlockState().getBlock();
        return block instanceof EuvScannerMemberBlock b ? b.kind() : BlockKind.OTHER;
    }

    public @Nullable EuvFormationOwner controller() {
        return owner;
    }

    public boolean isMemberOfFormedCell() {
        return formed;
    }

    public @Nullable EuvScannerControllerBlockEntity scanner() {
        if (level == null) return null;
        if (structureKind() == BlockKind.OPERATING_PANEL) {
            return level.getBlockEntity(worldPosition.below()) instanceof EuvScannerControllerBlockEntity s ? s : null;
        }
        var o = controller();
        return o instanceof EuvScannerControllerBlockEntity s
                && level.getBlockEntity(s.getBlockPos()) == s
                && !s.isRemoved() ? s : null;
    }

    public void attach(@Nullable EuvFormationOwner next, boolean formed) {
        if (owner == next && this.formed == formed) return;
        owner = next;
        this.formed = formed;
        if (level != null && !level.isClientSide()) level.invalidateCapabilities(worldPosition);
    }

    public UUID installation() {
        if (level == null || level.isClientSide()) throw new IllegalStateException("Server installation identity only");
        if (installation == null) {
            installation = UUID.randomUUID();
            setChanged();
        }
        return installation;
    }

    public boolean supportsOwner(EuvFormationOwner candidate) {
        return candidate instanceof EuvScannerControllerBlockEntity;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (owner != null && (owner.machine().isRemoved()
                || level.getBlockEntity(owner.machine().getBlockPos()) != owner.machine())) {
            attach(null, false);
        }
        if (owner == null && getBlockState().getValue(EuvScannerControllerBlock.FORMED)) {
            var state = getBlockState().setValue(EuvScannerControllerBlock.FORMED, false)
                    .setValue(EuvScannerControllerBlock.PART, 0);
            level.setBlock(worldPosition, state, 2);
        }
    }

    @Override
    public Component getDisplayName() {
        String key = switch (structureKind()) {
            case DIAGNOSTIC_PANEL -> "euv_scanner_diagnostic_panel";
            case CALIBRATION_PANEL -> "euv_scanner_calibration_panel";
            case OPERATING_PANEL -> "euv_scanner_operating_panel";
            default -> "euv_scanner_" + structureKind().name().toLowerCase();
        };
        return Component.translatable("block.lithmod." + key);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        var s = scanner();
        if (s == null) return null;
        return switch (structureKind()) {
            case DIAGNOSTIC_PANEL -> new EuvScannerDiagnosticMenu(id, inventory, s, worldPosition);
            case CALIBRATION_PANEL -> new EuvScannerCalibrationMenu(id, inventory, s, worldPosition);
            case OPERATING_PANEL -> new EuvScannerOperatingMenu(id, inventory, s, worldPosition);
            default -> null; // Other members don't open menus directly
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            installation = UUID.fromString(input.getString("installation").orElse(""));
        } catch (IllegalArgumentException e) {
            installation = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (installation != null) output.putString("installation", installation.toString());
    }
    @Override public com.euvcraft.lithmod.mc.multiblock.vacuum.VacuumConsumer vacuumConsumer(){var owner=scanner();return formed&&structureKind()==BlockKind.VACUUM_MODULE&&owner!=null&&owner.isFormed()?owner:null;}
}