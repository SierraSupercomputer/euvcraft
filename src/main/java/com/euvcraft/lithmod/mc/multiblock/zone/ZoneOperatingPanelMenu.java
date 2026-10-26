package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The upper operating panel's menu: boule in, refined boule out, plus the UPW status.
 *
 * <p>Every edit is locked while the controller has a job: the input refuses placements and
 * the output refuses pickups until the admitted run completes or its recovery returns the
 * original input. The input's placement rule is data-driven — a stack is accepted only when
 * some resolved one-input recipe for this machine matches it — and is decided server-side;
 * a client menu cannot read the server's recipe registry, so it predicts permissively and
 * the server's decision wins.</p>
 *
 * <p>{@link #stillValid} is strict: the exact controller block entity, the exact panel
 * block entity on the cell directly above it, a formed owner, and distance.</p>
 */
public final class ZoneOperatingPanelMenu extends FabMachineMenu {
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_OUTPUT = 1;

    private final BlockPos corePos;
    private final @Nullable ZoneMemberBlockEntity panel;
    private final ContainerData formation;
    private final ContainerData upw;

    public ZoneOperatingPanelMenu(int id, Inventory inventory, @Nullable FabMachineBlockEntity machine, BlockPos core) {
        super(ZoneMachines.PANEL_MENU.get(), id, inventory, machine, 1, 2);
        corePos = core.immutable();
        panel = machine != null && machine.getLevel() != null
            && machine.getLevel().getBlockEntity(corePos.above()) instanceof ZoneMemberBlockEntity p ? p : null;
        formation = machine instanceof AdvancedOwner owner ? owner.formation().data() : new SimpleContainerData(1);
        upw = machine instanceof ZoneControllerBlockEntity zone ? new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> zone.waterAmount();
                    case 1 -> zone.waterCapacity();
                    case 2 -> zone.hasJob() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 3; }
        } : new SimpleContainerData(3);
        addDataSlots(formation);
        addDataSlots(upw);
    }

    public ZoneOperatingPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos().below());
    }

    @Override protected void addMachineSlots() {
        addCustomSlot(new Slot(machineSlots(), SLOT_INPUT, 62, 35) {
            @Override public boolean mayPlace(ItemStack stack) {
                if (editsLocked()) return false;
                var be = blockEntity;
                // The client cannot read the server recipe registry: it predicts permissively.
                return be == null || acceptsInput(be, stack);
            }
        });
        addCustomSlot(new Slot(machineSlots(), SLOT_OUTPUT, 98, 35) {
            @Override public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override public boolean mayPickup(Player player) {
                return !editsLocked();
            }
        });
    }

    /** Server-side, data-driven input rule; never hard-codes a recipe item or count. */
    private static boolean acceptsInput(FabMachineBlockEntity machine, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (MachineRecipe recipe : machine.recipes()) {
            if (recipe.inputSlotCount() != 1) {
                continue;
            }
            if (MachineRecipeRunner.matchesSlot(recipe, 0, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * All edits lock while the controller has a job — a run in progress or a pending
     * recovery. The server reads the machine directly; the client reads the synced flag.
     */
    private boolean editsLocked() {
        return blockEntity instanceof ZoneControllerBlockEntity zone ? zone.hasJob() : upw.get(2) == 1;
    }

    public boolean formed() {
        return formation.get(0) == 1;
    }

    public int waterAmount() {
        return upw.get(0);
    }

    public int waterCapacity() {
        return upw.get(1);
    }

    public boolean hasJob() {
        return editsLocked();
    }

    @Override public boolean stillValid(Player player) {
        if (!super.stillValid(player)) {
            return false;
        }
        var be = blockEntity;
        if (be == null || be.getLevel() == null || panel == null) {
            return false;
        }
        if (!be.getBlockPos().equals(corePos) || be.getLevel().getBlockEntity(corePos) != be) {
            return false;
        }
        if (be.getLevel().getBlockEntity(corePos.above()) != panel
            || panel.getBlockState().getBlock() != ZoneMachines.OPERATING_PANEL.get()) {
            return false;
        }
        if (!(panel.controller() instanceof ZoneControllerBlockEntity owner) || !owner.isFormed()
            || !panel.isMemberOfFormedCell()) {
            return false;
        }
        return player.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 1.5, corePos.getZ() + 0.5) <= 64;
    }
}
