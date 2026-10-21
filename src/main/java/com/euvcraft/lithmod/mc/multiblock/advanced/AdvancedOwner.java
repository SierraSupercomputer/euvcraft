package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
/** Shared ownership contract; inventories and energy remain on the actual controller. */
public interface AdvancedOwner {
    FabMachineBlockEntity machine();
    AdvancedFormation formation();
    boolean large();
    default boolean isFormed() { return formation().formed(); }
    default void revalidate() { formation().validate(); }
    default List<BlockPos> members() { return formation().members(); }
}
