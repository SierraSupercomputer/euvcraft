package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * G3 Reticle Writer menu.
 * Binds within GUI and no inventory overlap.
 * Buttons server-authoritative and formed/distance checked.
 */
public class G3ReticleWriterMenu extends FabMachineMenu {

    private final G3ReticleWriterControllerBlockEntity controller;

    public G3ReticleWriterMenu(int id, Inventory inventory, G3ReticleWriterControllerBlockEntity controller) {
        this(id, inventory, controller, ContainerLevelAccess.create(controller.getLevel(), controller.getBlockPos()));
    }

    public G3ReticleWriterMenu(int id, Inventory inventory, G3ReticleWriterControllerBlockEntity controller, ContainerLevelAccess access) {
        super(G3Machines.RETICLE_WRITER_MENU.get(), id, inventory, controller, 2, 3);
        this.controller = controller;
    }

    public G3ReticleWriterMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, getController(inventory, buf));
    }

    private static G3ReticleWriterControllerBlockEntity getController(Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Level level = inventory.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof G3ReticleWriterControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("G3 Reticle Writer controller not found at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Slot 0: Blank mask input
        addMachineSlot(G3ReticleWriterControllerBlockEntity.SLOT_BLANK_MASK, 44, 35);
        // Slot 1: Redstone input
        addMachineSlot(G3ReticleWriterControllerBlockEntity.SLOT_REDSTONE, 44, 53);
        // Slot 2: Reticle output
        addOutputSlot(G3ReticleWriterControllerBlockEntity.SLOT_RETICLE_OUTPUT, 116, 35);
    }

    @Override
    protected int getMachineSlotCount() {
        return 3;
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && this.controller.isFormed()
                && this.controller.getLevel().getBlockEntity(this.controller.getBlockPos()) == this.controller;
    }
}