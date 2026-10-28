package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidVariant;
import net.neoforged.neoforge.transfer.fluid.FluidHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Shared member block entity for the hydrogen purifier: casing, power port, gas inlet, gas outlet, operating panel.
 * Extends the same pattern as UtilityPortBlockEntity but with hydrogen-specific fluid handling.
 */
public final class HydrogenMemberBlockEntity extends UtilityPortBlockEntity {

    private final HydrogenTankHandler fluidHandler;

    public HydrogenMemberBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenMachines.MEMBER_BE.get(), pos, state);
        this.fluidHandler = new HydrogenTankHandler(this);
    }

    @Override
    public boolean supportsOwner(Object owner) {
        return owner instanceof HydrogenControllerBlockEntity;
    }

    @Override
    protected IFluidHandler createFluidHandler() {
        return fluidHandler;
    }

    @Override
    protected EnergyHandler createEnergyHandler() {
        return new SimpleEnergyHandler(0, 0, 0); // only power ports expose energy
    }

    /**
     * Fluid handler for gas inlet (accepts raw H2) and gas outlet (outputs fab H2).
     * Other member kinds return null from the capability.
     */
    public static final class HydrogenTankHandler implements IFluidHandler {

        private final HydrogenMemberBlockEntity member;
        private final HydrogenControllerBlockEntity controller;

        HydrogenTankHandler(HydrogenMemberBlockEntity member) {
            this.member = member;
            var level = member.getLevel();
            var ctrlPos = member.getBlockPos();
            BlockEntity ctrlEntity = level != null ? level.getBlockEntity(ctrlPos) : null;
            this.controller = ctrlEntity instanceof HydrogenControllerBlockEntity be ? be : null;
        }

        @Override
        public int getTanks() {
            return controller != null ? 2 : 0; // raw tank + pure tank
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (controller == null) return FluidStack.EMPTY;
            return switch (tank) {
                case 0 -> new FluidStack(HydrogenFluids.RAW_HYDROGEN.get(), controller.rawHydrogen.amount());
                case 1 -> new FluidStack(HydrogenFluids.FAB_HYDROGEN.get(), controller.pureHydrogen.amount());
                default -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) {
            if (controller == null) return 0;
            return switch (tank) {
                case 0 -> controller.rawHydrogen.capacity();
                case 1 -> controller.pureHydrogen.capacity();
                default -> 0;
            };
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (controller == null) return false;
            if (tank == 0) { // raw hydrogen inlet only accepts arcforge:hydrogen
                return stack.is(HydrogenFluids.RAW_HYDROGEN.get());
            }
            if (tank == 1) { // pure hydrogen outlet only outputs fab_hydrogen
                return stack.is(HydrogenFluids.FAB_HYDROGEN.get());
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (controller == null || member.kind != BlockKind.GAS_INLET) return 0;
            if (!resource.is(HydrogenFluids.RAW_HYDROGEN.get())) return 0;
            int filled = controller.rawHydrogen.fill(resource.getAmount(), action.simulate());
            if (filled > 0 && !action.simulate()) {
                member.setChanged();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (controller == null || member.kind != BlockKind.GAS_OUTLET) return FluidStack.EMPTY;
            if (!resource.is(HydrogenFluids.FAB_HYDROGEN.get())) return FluidStack.EMPTY;
            int drained = controller.pureHydrogen.drainExact(resource.getAmount(), action.simulate()) ? resource.getAmount() : 0;
            if (drained > 0) {
                return new FluidStack(resource, drained);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxAmount, FluidAction action) {
            if (controller == null || member.kind != BlockKind.GAS_OUTLET) return FluidStack.EMPTY;
            int available = controller.pureHydrogen.amount();
            int drained = Math.min(maxAmount, available);
            if (drained <= 0) return FluidStack.EMPTY;
            if (controller.pureHydrogen.drainExact(drained, action.simulate())) {
                if (!action.simulate()) member.setChanged();
                return new FluidStack(HydrogenFluids.FAB_HYDROGEN.get(), drained);
            }
            return FluidStack.EMPTY;
        }
    }
}