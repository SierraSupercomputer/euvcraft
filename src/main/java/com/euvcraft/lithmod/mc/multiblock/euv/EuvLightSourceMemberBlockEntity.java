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

/** EUV Light Source member block entity - owns neither items nor FE. Links rebuilt after reload, never persisted. */
public class EuvLightSourceMemberBlockEntity extends BlockEntity implements MenuProvider {

    private @Nullable EuvFormationOwner owner;
    private boolean formed;
    private @Nullable UUID installation;

    public EuvLightSourceMemberBlockEntity(BlockPos pos, BlockState state) {
        this(EuvMachines.LIGHT_SOURCE_MEMBER_BE.get(), pos, state);
    }

    protected EuvLightSourceMemberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public BlockKind structureKind() {
        var block = getBlockState().getBlock();
        return block instanceof EuvLightSourceMemberBlock b ? b.kind() : BlockKind.OTHER;
    }

    public @Nullable EuvFormationOwner controller() {
        return owner;
    }

    public boolean isMemberOfFormedCell() {
        return formed;
    }

    public @Nullable EuvLightSourceControllerBlockEntity lightSource() {
        if (level == null) return null;
        var o = controller();
        return o instanceof EuvLightSourceControllerBlockEntity ls
                && level.getBlockEntity(ls.getBlockPos()) == ls
                && !ls.isRemoved() ? ls : null;
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
        return candidate instanceof EuvLightSourceControllerBlockEntity;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (owner != null && (owner.machine().isRemoved()
                || level.getBlockEntity(owner.machine().getBlockPos()) != owner.machine())) {
            attach(null, false);
        }
        if (owner == null && getBlockState().getValue(EuvLightSourceControllerBlock.FORMED)) {
            var state = getBlockState().setValue(EuvLightSourceControllerBlock.FORMED, false)
                    .setValue(EuvLightSourceControllerBlock.PART, 0);
            level.setBlock(worldPosition, state, 2);
        }
    }

    @Override
    public Component getDisplayName() {
        String key = switch (structureKind()) {
            case OPERATING_PANEL -> "euv_light_source_operating_panel";
            default -> "euv_light_source_" + structureKind().name().toLowerCase();
        };
        return Component.translatable("block.lithmod." + key);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        var ls = lightSource();
        if (ls == null) return null;
        return switch (structureKind()) {
            case OPERATING_PANEL -> new EuvLightSourceOperatingMenu(id, inventory, ls, worldPosition);
            default -> null;
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
}