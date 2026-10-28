package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Calibration menu for EUV scanner (calibration panel). */
public final class EuvScannerCalibrationMenu extends AbstractContainerMenu {

    private final @Nullable EuvScannerControllerBlockEntity scanner;
    private final @Nullable EuvScannerMemberBlockEntity panel;
    private final BlockPos pos;
    private final ContainerData values;

    public EuvScannerCalibrationMenu(int id, Inventory inv, @Nullable EuvScannerControllerBlockEntity scanner, BlockPos pos) {
        super(EuvMachines.SCANNER_CALIBRATION_MENU.get(), id);
        this.scanner = scanner;
        this.pos = pos.immutable();
        panel = scanner != null && scanner.getLevel() != null && scanner.getLevel().getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity m ? m : null;
        values = scanner == null ? new SimpleContainerData(13) : new ContainerData() {
            public int get(int i) {
                var a = scanner.alignment();
                return switch (i) {
                    case 0 -> scanner.isFormed() ? 1 : 0;
                    case 1 -> scanner.calibrated() ? 1 : 0;
                    case 2 -> a.quality();
                    case 3 -> (int) Math.round(scanner.previewMultiplier() * 1000);
                    case 4 -> a.spotX();
                    case 5 -> a.spotY();
                    case 6 -> a.focusError();
                    case 7 -> a.x();
                    case 8 -> a.y();
                    case 9 -> a.focus();
                    case 10 -> scanner.inputsLocked() ? 1 : 0;
                    case 11 -> scanner.minimumQuality();
                    case 12 -> scanner.calibrationQuality();
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 13; }
        };
        addDataSlots(values);
    }

    public EuvScannerCalibrationMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, buf.readBlockPos());
    }

    public int value(int i) { return values.get(i); }

    @Override
    public boolean stillValid(Player p) {
        return scanner != null && panel != null && scanner.getLevel() == p.level()
                && !scanner.isRemoved() && scanner.getLevel().getBlockEntity(scanner.getBlockPos()) == scanner
                && scanner.getLevel().getBlockEntity(pos) == panel && panel.scanner() == scanner
                && panel.structureKind() == BlockKind.CALIBRATION_PANEL
                && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof net.minecraft.server.level.ServerPlayer) || p.containerMenu != this || !stillValid(p) || scanner.inputsLocked()) return false;
        if (id >= 0 && id <= 6) return scanner.calibrationControl(id);
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) { return ItemStack.EMPTY; }
}