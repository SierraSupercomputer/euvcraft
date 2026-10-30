package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

/** Operating menu for EUV scanner (operating panel). */
public final class EuvScannerOperatingMenu extends ProcessCellMenu {

    private final BlockPos panelPos;
    private final @Nullable EuvScannerMemberBlockEntity panel;
    private final @Nullable EuvScannerControllerBlockEntity scanner;
    private final ContainerData scannerData;

    public EuvScannerOperatingMenu(int id, Inventory inventory, @Nullable EuvScannerControllerBlockEntity scanner, BlockPos pos) {
        super(EuvMachines.SCANNER_OPERATING_MENU.get(), id, inventory, scanner, 3);
        this.scanner = scanner;
        panelPos = pos.immutable();
        panel = scanner != null && scanner.getLevel() != null && scanner.getLevel().getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity m ? m : null;
        scannerData = scanner == null ? new SimpleContainerData(12) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> scanner.operatingCalibrated() ? 1 : 0;
                    case 1 -> scanner.calibrationQuality();
                    case 2 -> (int) Math.round(scanner.calibratedMultiplier() * 1000);
                    case 3 -> scanner.upwTank.amount();
                    case 4 -> scanner.diagnosed() ? 1 : 0;
                    case 5 -> scanner.vacuumState().canProcess(scanner.vacuumSpec()) ? 1 : 0;
                    case 6 -> scanner.upwPerTick();
                    case 7 -> scanner.hasReservedLightSource() ? 1 : 0;
                    case 8 -> scanner.contaminationState().requiresRepair() ? 1 : 0;
                    case 9 -> scanner.operatingIssue();
                    case 10 -> scanner.sourceIssue();
                    case 11 -> scanner.sourceMultiplier();
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 12; }
        };
        addDataSlots(scannerData);
    }

    public EuvScannerOperatingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    @Override
    public boolean isOperatingPanel() { return true; }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && scanner != null && panel != null
                && scanner.getLevel().getBlockEntity(panelPos) == panel
                && panel.scanner() == scanner
                && panelPos.equals(scanner.getBlockPos().above())
                && player.distanceToSqr(panelPos.getX() + .5, panelPos.getY() + .5, panelPos.getZ() + .5) <= 64;
    }

    public int sourceIssue(){return scannerData.get(10);}
    public int sourceMultiplier(){return scannerData.get(11);}
    public int operatingIssue() { return scannerData.get(9); }
    public boolean hasLightSource() { return scannerData.get(7) == 1; }
    public boolean contaminated() { return scannerData.get(8) == 1; }
    public int waterCost() { return scannerData.get(6); }
    public int water() { return scannerData.get(3); }
    public boolean diagnosed() { return scannerData.get(4) == 1; }
    public boolean vacuumOk() { return scannerData.get(5) == 1; }
    public boolean calibrated() { return scannerData.get(0) != 0; }
    public int quality() { return scannerData.get(1); }
    public int multiplierPermille() { return scannerData.get(2); }
}