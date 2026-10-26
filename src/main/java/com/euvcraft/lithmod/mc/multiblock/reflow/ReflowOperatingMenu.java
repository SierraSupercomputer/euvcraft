package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Upper operating panel: the real chip slot, blank slot, product output and scrap output.
 * Everything is locked while a job is admitted or running.
 */
public final class ReflowOperatingMenu extends FabMachineMenu {

    private final @Nullable ReflowControllerBlockEntity oven;
    private final @Nullable ReflowMemberBlockEntity panel;
    private final BlockPos panelPos;
    private ContainerData status;

    public ReflowOperatingMenu(int id, Inventory inventory,
            @Nullable ReflowControllerBlockEntity oven, BlockPos panelPos) {
        super(ReflowMachines.OPERATING_MENU.get(), id, inventory, oven, 2, 5);
        this.oven = oven;
        this.panelPos = panelPos.immutable();
        this.panel = oven != null && oven.getLevel() != null
                && oven.getLevel().getBlockEntity(panelPos) instanceof ReflowMemberBlockEntity member
                        ? member : null;
        this.status = ReflowStatusData.of(oven);
        addDataSlots(this.status);
    }

    public ReflowOperatingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    @Override
    protected void addMachineSlots() {
        addCustomSlot(new Slot(machineSlots(), ReflowControllerBlockEntity.SLOT_CHIP, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !lockedNow() && validChip(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return !lockedNow();
            }
        });
        addCustomSlot(new Slot(machineSlots(), ReflowControllerBlockEntity.SLOT_BLANK, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !lockedNow() && (stack.is(ModItems.CPU_BLANK.get())
                        || stack.is(ModItems.DIMM_BLANK.get()));
            }

            @Override
            public boolean mayPickup(Player player) {
                return !lockedNow();
            }
        });
        addOutputSlot(ReflowControllerBlockEntity.SLOT_PRODUCT, 116, 35);
        addOutputSlot(ReflowControllerBlockEntity.SLOT_SCRAP, 152, 35);
    }

    /**
     * A T6 compute or memory chip. Unknown items are allowed through the menu because the JSON
     * recipe remains authoritative; known NAND/logic and pre-T6 designs are refused here.
     */
    static boolean validChip(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return ChipCatalog.designOf(stack.getItem())
                .map(design -> design.tierIndex() == 6
                        && (DieTypes.COMPUTE.equals(design.dieType())
                                || DieTypes.MEMORY.equals(design.dieType())))
                .orElse(true);
    }

    private boolean lockedNow() {
        if (blockEntity instanceof ReflowControllerBlockEntity controller) {
            return controller.inputsLocked();
        }
        return status != null && status.get(ReflowStatusData.LOCKED) != 0;
    }

    public boolean inputsLocked() {
        return lockedNow();
    }

    public int status(int index) {
        return status.get(index);
    }

    public boolean formed() {
        return status.get(ReflowStatusData.FORMED) == 1;
    }

    public boolean calibrated() {
        return status.get(ReflowStatusData.CALIBRATED) == 1;
    }

    public boolean ready() {
        return status.get(ReflowStatusData.READY) == 1;
    }

    public boolean paused() {
        return status.get(ReflowStatusData.PAUSED) == 1;
    }

    public int reason() {
        return status.get(ReflowStatusData.REASON);
    }

    public int acceptedPermille() {
        return status.get(ReflowStatusData.ACCEPTED_PERMILLE);
    }

    public int acceptedQuality() {
        return status.get(ReflowStatusData.ACCEPTED_QUALITY);
    }

    public int minimumQuality() {
        return status.get(ReflowStatusData.MIN_QUALITY);
    }

    public int scrapPerFailure() {
        return status.get(ReflowStatusData.SCRAP_PER_FAILURE);
    }

    @Override
    public boolean stillValid(Player player) {
        if (oven == null || panel == null || blockEntity == null) {
            return false;
        }
        if (oven.getLevel() != player.level() || oven.isRemoved() || oven.getLevel() == null) {
            return false;
        }
        if (oven.getLevel().getBlockEntity(oven.getBlockPos()) != oven
                || oven.getLevel().getBlockEntity(panelPos) != panel) {
            return false;
        }
        return panel.structureKind() == com.euvcraft.lithmod.core.multiblock.BlockKind.OPERATING_PANEL
                && panel.reflowController() == oven
                && panelPos.equals(oven.getBlockPos().above())
                && oven.isFormed()
                && player.distanceToSqr(panelPos.getX() + 0.5, panelPos.getY() + 0.5,
                        panelPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }
}
