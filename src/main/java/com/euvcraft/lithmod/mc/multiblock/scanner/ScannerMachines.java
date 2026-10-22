package com.euvcraft.lithmod.mc.multiblock.scanner;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
public final class ScannerMachines {
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
 public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
 public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
 public static final DeferredBlock<ScannerControllerBlock> CONTROLLER=BLOCKS.registerBlock("scanner_controller",ScannerControllerBlock::new,p->p.strength(4,8).requiresCorrectToolForDrops());
 private static DeferredBlock<ScannerMemberBlock> member(String id,BlockKind kind){return BLOCKS.registerBlock(id,p->new ScannerMemberBlock(p,kind),p->p.strength(4,8).requiresCorrectToolForDrops());}
 public static final DeferredBlock<ScannerMemberBlock> OPERATING_PANEL=member("scanner_operating_panel",BlockKind.OPERATING_PANEL),CALIBRATION_PANEL=member("scanner_calibration_panel",BlockKind.CALIBRATION_PANEL),EXPOSURE_MODULE=member("scanner_exposure_module",BlockKind.EXPOSURE_MODULE),OPTICS_MODULE=member("scanner_optics_module",BlockKind.OPTICS_MODULE),STAGE_MODULE=member("scanner_stage_module",BlockKind.STAGE_MODULE),FILTRATION_MODULE=member("scanner_filtration_module",BlockKind.FILTRATION_MODULE),METROLOGY_MODULE=member("scanner_metrology_module",BlockKind.METROLOGY_MODULE);
 public static final DeferredBlock<ScannerMemberBlock> DIAGNOSTIC_PANEL=member("scanner_diagnostic_panel",BlockKind.DIAGNOSTIC_PANEL), WATER_INLET=member("scanner_water_inlet",BlockKind.WATER_INLET), IMMERSION_MODULE=member("scanner_immersion_module",BlockKind.OPTICS_MODULE);
 public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<DiagnosticMenu>> DIAGNOSTIC_MENU=MENUS.register("scanner_diagnostic",()->IMenuTypeExtension.create(DiagnosticMenu::new));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ScannerControllerBlockEntity>> CONTROLLER_BE=BLOCK_ENTITIES.register("scanner_controller",()->new BlockEntityType<>(ScannerControllerBlockEntity::new,CONTROLLER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ScannerMemberBlockEntity>> MEMBER_BE=BLOCK_ENTITIES.register("scanner_member",()->new BlockEntityType<>(ScannerMemberBlockEntity::new,OPERATING_PANEL.get(),CALIBRATION_PANEL.get(),EXPOSURE_MODULE.get(),OPTICS_MODULE.get(),STAGE_MODULE.get(),FILTRATION_MODULE.get(),METROLOGY_MODULE.get(),DIAGNOSTIC_PANEL.get(),WATER_INLET.get(),IMMERSION_MODULE.get()));
 public static final DeferredHolder<MenuType<?>,MenuType<ScannerOperatingMenu>> OPERATING_MENU=MENUS.register("scanner_operation",()->IMenuTypeExtension.create(ScannerOperatingMenu::new));
 public static final DeferredHolder<MenuType<?>,MenuType<ScannerDashboardMenu>> DASHBOARD_MENU=MENUS.register("scanner_dashboard",()->IMenuTypeExtension.create(ScannerDashboardMenu::new));
 public static void register(IEventBus bus){BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);MENUS.register(bus);bus.addListener(ScannerMachines::capabilities);}
 private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e){e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,MEMBER_BE.get(),(be,side)->be.structureKind()==BlockKind.WATER_INLET&&be.scanner()!=null?new ScannerWaterHandler(be,be.scanner()):null);}
 private ScannerMachines(){}
}

