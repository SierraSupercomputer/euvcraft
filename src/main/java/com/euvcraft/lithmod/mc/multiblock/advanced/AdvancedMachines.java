package com.euvcraft.lithmod.mc.multiblock.advanced;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
public final class AdvancedMachines {
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
 public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
 public static final DeferredRegister<MenuType<?>> MENU_TYPES=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
 public static final DeferredBlock<AdvancedControllerBlock> DEPOSITION_ETCHER_CONTROLLER=BLOCKS.registerBlock("deposition_etcher",AdvancedControllerBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredBlock<AdvancedControllerBlock> ADVANCED_TEST_PACKAGE_CONTROLLER=BLOCKS.registerBlock("advanced_test_package",AdvancedControllerBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredBlock<AdvancedOperatingPanelBlock> ADVANCED_OPERATING_PANEL=BLOCKS.registerBlock("advanced_operating_panel",AdvancedOperatingPanelBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredBlock<AdvancedCasingBlock> ADVANCED_CASING=BLOCKS.registerBlock("advanced_casing",AdvancedCasingBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredBlock<AdvancedPowerPortBlock> ADVANCED_POWER_PORT=BLOCKS.registerBlock("advanced_power_port",AdvancedPowerPortBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<DepositionEtcherControllerBlockEntity>> DEPOSITION_ETCHER_CONTROLLER_BE=BLOCK_ENTITIES.register("deposition_etcher",()->new BlockEntityType<>(DepositionEtcherControllerBlockEntity::new,DEPOSITION_ETCHER_CONTROLLER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<AdvancedTestPackageControllerBlockEntity>> ADVANCED_TEST_PACKAGE_CONTROLLER_BE=BLOCK_ENTITIES.register("advanced_test_package",()->new BlockEntityType<>(AdvancedTestPackageControllerBlockEntity::new,ADVANCED_TEST_PACKAGE_CONTROLLER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<AdvancedMemberBlockEntity>> ADVANCED_MEMBER_BE=BLOCK_ENTITIES.register("advanced_member",()->new BlockEntityType<>(AdvancedMemberBlockEntity::new,ADVANCED_CASING.get(),ADVANCED_POWER_PORT.get(),ADVANCED_OPERATING_PANEL.get()));
 public static final DeferredHolder<MenuType<?>,MenuType<DepositionEtcherMenu>> DEPOSITION_ETCHER_MENU=MENU_TYPES.register("deposition_etcher",()->IMenuTypeExtension.create(DepositionEtcherMenu::new));
 public static final DeferredHolder<MenuType<?>,MenuType<AdvancedTestPackageMenu>> ADVANCED_TEST_PACKAGE_MENU=MENU_TYPES.register("advanced_test_package",()->IMenuTypeExtension.create(AdvancedTestPackageMenu::new));
 public static final DeferredHolder<MenuType<?>,MenuType<AdvancedOperatingPanelMenu>> ADVANCED_OPERATING_PANEL_MENU=MENU_TYPES.register("advanced_operating_panel",()->IMenuTypeExtension.create(AdvancedOperatingPanelMenu::new));
 public static void register(IEventBus bus){BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);MENU_TYPES.register(bus);bus.addListener(AdvancedMachines::capabilities);}
 private static void capabilities(RegisterCapabilitiesEvent event){
  event.registerBlockEntity(Capabilities.Energy.BLOCK,ADVANCED_MEMBER_BE.get(),(member,side)->member.energy());
 }
 private AdvancedMachines(){}
}
