package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import org.jspecify.annotations.Nullable;

/** Equipment menu for EUV scanner. Exposes lower cabinet: repair filter + redstone + player inventory. */
public final class EuvScannerEquipmentMenu extends FabMachineMenu {

    private final @Nullable EuvScannerControllerBlockEntity scanner;
    private final BlockPos pos;
    private final ContainerData values;
    private final Container repairItems = new SimpleContainer(2);

    public EuvScannerEquipmentMenu(int containerId, Inventory playerInventory, @Nullable EuvScannerControllerBlockEntity scanner, BlockPos pos) {
        super(EuvMachines.SCANNER_EQUIPMENT_MENU.get(), containerId, playerInventory, scanner, 2, 2);
        this.scanner = scanner;
        this.pos = pos.immutable();
        this.values = scanner == null ? new SimpleContainerData(11) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> scanner.equipment(3).isEmpty() ? 0 : 1;
                    case 1 -> scanner.equipment(4).isEmpty() ? 0 : 1;
                    case 2 -> scanner.equipment(5).isEmpty() ? 0 : 1;
                    case 3 -> scanner.equipment(6).isEmpty() ? 0 : 1;
                    case 4 -> scanner.equipment(7).isEmpty() ? 0 : 1;
                    case 5 -> scanner.equipment(8).isEmpty() ? 0 : 1;
                    case 6 -> scanner.isFormed() ? 1 : 0;
                    case 7 -> scanner.contaminationState().phase().ordinal();
                    case 8 -> scanner.upwTank.amount();
                    case 9 -> scanner.buffer().getAmountAsInt();
                    case 10 -> scanner.buffer().getCapacityAsInt();
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 11; }
        };
        addDataSlots(new WideContainerData(values));
    }

    public EuvScannerEquipmentMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos());
    }

    @Override
    protected void addMachineSlots() {
        // Slot 0: Repair filter (utility filter)
        addCustomSlot(new Slot(repairItems, 0, 26, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.UTILITY_FILTER.get());
            }
        });
        // Slot 1: Repair redstone
        addCustomSlot(new Slot(repairItems, 1, 98, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.REDSTONE);
            }
        });
    }

    public int value(int i) { return values.get(i); }

    @Override
    public boolean stillValid(Player player) {
        return scanner != null && scanner.getLevel() == player.level()
                && !scanner.isRemoved() && scanner.getLevel().getBlockEntity(scanner.getBlockPos()) == scanner
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer) || player.containerMenu != this || !stillValid(player)) return false;
        if (id == 0) return scanner.repair(); // Repair button
        if (id == 1) return scanner.toggleVacuumIsolation(); // Vacuum isolation toggle
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || scanner == null || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        var stack = slot.getItem();
        var original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (index == 1) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Player inventory to repair slots
            if (stack.is(ModItems.UTILITY_FILTER.get()) && moveItemStackTo(stack, 0, 1, false)) {
                // moved to filter slot
            } else if (stack.is(net.minecraft.world.item.Items.REDSTONE) && moveItemStackTo(stack, 1, 2, false)) {
                // moved to redstone slot
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, original);
        scanner.setChanged();
        return original;
    }
}