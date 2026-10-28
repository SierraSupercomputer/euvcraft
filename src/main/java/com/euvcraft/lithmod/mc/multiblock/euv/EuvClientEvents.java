package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.multiblock.scanner.DiagnosticScreen;
import com.euvcraft.lithmod.mc.multiblock.scanner.ScannerDashboardScreen;
import com.euvcraft.lithmod.mc.multiblock.scanner.ScannerOperatingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Registers all EUV machine screens on the client. */
@EventBusSubscriber(modid = "lithmod", value = Dist.CLIENT)
public final class EuvClientEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // EUV Scanner (5 panels)
        event.register(EuvMachines.SCANNER_OPERATING_MENU.get(), EuvScannerOperatingScreen::new);
        event.register(EuvMachines.SCANNER_CALIBRATION_MENU.get(), EuvScannerCalibrationScreen::new);
        event.register(EuvMachines.SCANNER_DIAGNOSTIC_MENU.get(), EuvScannerDiagnosticScreen::new);
        event.register(EuvMachines.SCANNER_DASHBOARD_MENU.get(), EuvScannerDashboardScreen::new);
        event.register(EuvMachines.SCANNER_EQUIPMENT_MENU.get(), EuvScannerEquipmentScreen::new);

        // EUV Light Source (2 panels)
        event.register(EuvMachines.LIGHT_SOURCE_OPERATING_MENU.get(), EuvLightSourceOperatingScreen::new);
        event.register(EuvMachines.LIGHT_SOURCE_DASHBOARD_MENU.get(), EuvLightSourceDashboardScreen::new);
    }
}
