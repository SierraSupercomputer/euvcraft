package com.euvcraft.lithmod.mc.multiblock.assembly;

import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedFormation;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AssemblyControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner, MenuProvider {
    private static final int SLOT_INPUT_1 = 0;
    private static final int SLOT_INPUT_2 = 1;
    private static final int SLOT_OUTPUT = 2;
    private static final int INPUT_SLOTS = 2;
    private static final int OUTPUT_SLOTS = 1;
    private static final int STORAGE_SLOTS = 0;

    private final AssemblyFormation formation;

    public AssemblyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AssemblyMachines.CONTROLLER_BE.get(), pos, state, INPUT_SLOTS, OUTPUT_SLOTS, STORAGE_SLOTS);
        this.formation = new AssemblyFormation(this);
    }

    @Override
    protected String machineId() {
        return "pick_and_place";
    }

    @Override
    public FabMachineBlockEntity machine() {
        return this;
    }

    @Override
    public AssemblyFormation formation() {
        return formation;
    }

    @Override
    public boolean large() {
        return false;
    }

    @Override
    public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return AssemblyStructureMatcher.match(anchor, facing, lookup);
    }

    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        refreshDefinition();

        // Revalidate formation every tick
        formation.validate();

        // Only run the base machine logic (recipes, energy, inventory) while formed
        if (isFormed()) {
            super.serverTick();
        } else {
            // Still sync data so the menu shows energy/progress correctly
            syncData();
        }
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new AssemblyMenu(containerId, playerInventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod.pick_and_place_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return createMachineMenu(containerId, playerInventory, this);
    }

    @Override
    public void setRemoved() {
        formation.detach();
        super.setRemoved();
    }
}