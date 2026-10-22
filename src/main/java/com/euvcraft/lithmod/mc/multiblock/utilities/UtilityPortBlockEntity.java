package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;import net.neoforged.neoforge.transfer.fluid.FluidResource;import org.jspecify.annotations.Nullable;
public final class UtilityPortBlockEntity extends AdvancedMemberBlockEntity{
 @Override public boolean supportsOwner(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner candidate){return candidate instanceof UtilityControllerBlockEntity;}

 public UtilityPortBlockEntity(BlockPos p,BlockState s){super(UtilityMachines.PORT_BE.get(),p,s);}
 @Override public BlockKind structureKind(){return getBlockState().getBlock() instanceof UtilityPortBlock b?b.kind:BlockKind.OTHER;}
 public @Nullable ResourceHandler<FluidResource> handler(){var o=controller();return o instanceof UtilityControllerBlockEntity plant?new UtilityFluidHandler(this,plant,structureKind()==BlockKind.WATER_INLET):null;}
}

