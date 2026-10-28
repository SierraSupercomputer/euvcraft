package com.euvcraft.lithmod.mc.multiblock.g3;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * G3 operating panel block — placed directly above the controller.
 * Opens the machine's operating UI when right-clicked.
 */
public class G3OperatingPanelBlock extends G3MemberBlock implements EntityBlock {

    public G3OperatingPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new G3MemberBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, p, s, be) -> {
            if (be instanceof G3MemberBlockEntity member) {
                member.serverTick();
            }
        };
    }

    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,net.minecraft.world.level.Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!level.isClientSide()&&level.getBlockEntity(pos.below()) instanceof G3ControllerBlockEntity owner){owner.revalidate();player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,p)->new G3OperatingPanelMenu(id,inventory,owner,pos),owner.getDisplayName()),buffer->{buffer.writeBlockPos(pos);buffer.writeEnum(owner.getKind());});}
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}