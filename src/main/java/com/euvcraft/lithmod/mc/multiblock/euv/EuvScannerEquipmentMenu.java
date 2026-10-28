package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Equipment menu for EUV scanner. */
public final class EuvScannerEquipmentMenu extends AbstractContainerMenu {

    private final @Nullable EuvScannerControllerBlockEntity scanner;
    private final BlockPos pos;
    private final ContainerData values;

    public EuvScannerEquipmentMenu(int id, Inventory inventory, @Nullable EuvScannerControllerBlockEntity scanner, BlockPos pos) {
        super(EuvMachines.SCANNER_EQUIPMENT_MENU.get(), id);
        this.scanner = scanner;
        this.pos = pos.immutable();
        values = scanner == null ? new SimpleContainerData(7) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> scanner.equipment(3).isEmpty() ? 0 : 1;
                    case 1 -> scanner.equipment(4).isEmpty() ? 0 : 1;
                    case 2 -> scanner.equipment(5).isEmpty() ? 0 : 1;
                    case 3 -> scanner.equipment(6).isEmpty() ? 0 : 1;
                    case 4 -> scanner.equipment(7).isEmpty() ? 0 : 1;
                    case 5 -> scanner.equipment(8).isEmpty() ? 0 : 1;
                    case 6 -> scanner.isFormed() ? 1 : 0;
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 7; }
        };
        addDataSlots(values);
    }

    public EuvScannerEquipmentMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    public int value(int i) { return values.get(i); }

    @Override
    public boolean stillValid(Player player) {
        return scanner != null && scanner.getLevel() == player.level()
                && !scanner.isRemoved() && scanner.getLevel().getBlockEntity(scanner.getBlockPos()) == scanner
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}