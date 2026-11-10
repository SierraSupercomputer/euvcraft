package com.euvcraft.lithmod.mc;


import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import java.util.Map;

/**
 * Registers tooltip appenders for all fab multiblock controller block items.
 *
 * <p>The appender adds a frame category line on normal hover. When the player holds Shift,
 * it expands into individual localized requirement lines: dimensions, casing name, power port
 * requirements, panels/modules as relevant. For the cleanroom controller it shows cuboid shell
 * information instead of a fixed-size template.</p>
 *
 * <p>Registered on the client event bus only; safe to read {@code Minecraft.getInstance().hasShiftDown()}.
 * No world lookups, no broad collection mutation, no custom data requirements. Uses a small
 * immutable metadata map keyed by the actual registry item IDs.</p>
 */
@EventBusSubscriber(modid = EUVCRAFTMod.MODID, value = Dist.CLIENT)
public final class MachineTooltipAppenders {

    private MachineTooltipAppenders() {}

    /**
     * Metadata for a controller's tooltip requirements.
     */
    private record ControllerMeta(
            String frameCategoryKey,      // e.g. "tooltip.lithmod.frame.assembly"
            String dimensionsKey,         // e.g. "tooltip.lithmod.dims.2x2x3"
            String casingKey,             // e.g. "tooltip.lithmod.casing.process_cell"
            String powerPortKey,          // e.g. "tooltip.lithmod.port.process_cell"
            String[] extraKeys            // operating panel, water inlet, modules, etc.
    ) {}

    /**
     * Immutable map of controller item registry name -> tooltip metadata.
     * Keys are the actual item registry IDs (namespace omitted, e.g. "process_cell_controller").
     */
    private static final Map<String, ControllerMeta> CONTROLLER_META = Map.ofEntries(
            Map.entry("coating_track",new ControllerMeta("tooltip.lithmod.frame.advanced","tooltip.lithmod.dims.3x2x5","tooltip.lithmod.casing.advanced_fab","tooltip.lithmod.port.advanced",new String[]{"tooltip.lithmod.panel.operating_panel_above","tooltip.lithmod.linear.coating"})),
            Map.entry("wet_cleaner",new ControllerMeta("tooltip.lithmod.frame.advanced","tooltip.lithmod.dims.2x2x3","tooltip.lithmod.casing.advanced_fab","tooltip.lithmod.port.advanced",new String[]{"tooltip.lithmod.panel.operating_panel_above","tooltip.lithmod.linear.water"})),
            Map.entry("t8_planarizer",new ControllerMeta("tooltip.lithmod.frame.g3","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.g3","tooltip.lithmod.port.g3",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t8.polish_head","tooltip.lithmod.t7.rinse","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.hard_room"})),
            Map.entry("t8_implanter",new ControllerMeta("tooltip.lithmod.frame.g3","tooltip.lithmod.dims.5x3x7","tooltip.lithmod.casing.g3","tooltip.lithmod.port.g3",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t8.implant_head","tooltip.lithmod.t7.rinse","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.hard_room"})),
            Map.entry("g3_etch_cell",new ControllerMeta("tooltip.lithmod.frame.g3","tooltip.lithmod.dims.5x5x7","tooltip.lithmod.casing.g3","tooltip.lithmod.port.g3",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.etch_module","tooltip.lithmod.t7.rinse","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.hard_room"})),
            Map.entry("etch_cell",new ControllerMeta("tooltip.lithmod.frame.advanced","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.advanced_fab","tooltip.lithmod.port.advanced",new String[]{"tooltip.lithmod.panel.operating_panel_above"})),
            Map.entry("g3_reticle_writer",new ControllerMeta("tooltip.lithmod.frame.g3","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.g3","tooltip.lithmod.port.g3",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.writing","tooltip.lithmod.t7.metrology","tooltip.lithmod.t7.soft_room"})),
            Map.entry("g3_deposition_etcher",new ControllerMeta("tooltip.lithmod.frame.g3","tooltip.lithmod.dims.5x5x7","tooltip.lithmod.casing.g3","tooltip.lithmod.port.g3",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.deposition","tooltip.lithmod.t7.rinse","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.hard_room"})),
            Map.entry("high_na_scanner_controller",new ControllerMeta("tooltip.lithmod.frame.euv_scanner","tooltip.lithmod.dims.5x5x7","tooltip.lithmod.casing.euv_scanner","tooltip.lithmod.port.euv_scanner",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.beam_calibration","tooltip.lithmod.t7.diagnostic","tooltip.lithmod.t8.high_na_optics","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.beamline","tooltip.lithmod.t7.hard_room"})),
            Map.entry("euv_scanner_controller",new ControllerMeta("tooltip.lithmod.frame.euv_scanner","tooltip.lithmod.dims.5x5x7","tooltip.lithmod.casing.euv_scanner","tooltip.lithmod.port.euv_scanner",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.beam_calibration","tooltip.lithmod.t7.diagnostic","tooltip.lithmod.t7.scanner_modules","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.upw","tooltip.lithmod.t7.beamline","tooltip.lithmod.t7.hard_room"})),
            Map.entry("euv_light_source_controller",new ControllerMeta("tooltip.lithmod.frame.euv_source","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.euv_source","tooltip.lithmod.port.euv_source",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.source_pulse","tooltip.lithmod.t7.tin","tooltip.lithmod.t7.hydrogen","tooltip.lithmod.t7.vacuum","tooltip.lithmod.t7.beamline"})),
            Map.entry("hydrogen_purifier",new ControllerMeta("tooltip.lithmod.frame.hydrogen","tooltip.lithmod.dims.2x2x3","tooltip.lithmod.casing.hydrogen","tooltip.lithmod.port.hydrogen",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.gas_inlet","tooltip.lithmod.t7.gas_outlet","tooltip.lithmod.t7.filter"})),
            Map.entry("central_vacuum_plant",new ControllerMeta("tooltip.lithmod.frame.vacuum","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.vacuum","tooltip.lithmod.port.vacuum",new String[]{"tooltip.lithmod.t7.operating","tooltip.lithmod.t7.service_outlet","tooltip.lithmod.t7.vacuum_network"})),
            Map.entry("zone_refiner",new ControllerMeta("tooltip.lithmod.frame.zone","tooltip.lithmod.dims.3x3x5","tooltip.lithmod.casing.zone","tooltip.lithmod.port.zone",new String[]{"tooltip.lithmod.panel.operating_panel_above","tooltip.lithmod.required.zone_water_inlet"})),
            Map.entry("reflow_oven",new ControllerMeta("tooltip.lithmod.frame.reflow","tooltip.lithmod.dims.3x3x2","tooltip.lithmod.casing.reflow","tooltip.lithmod.port.reflow",new String[]{"tooltip.lithmod.panel.operating_panel_above","tooltip.lithmod.required.reflow_calibration_panel"})),
            // Process Cell: 2x2x3, Process Cell Casing, 1-2 Process Cell Power Ports, Operating Panel above
            Map.entry("process_cell_controller", new ControllerMeta(
                    "tooltip.lithmod.frame.assembly",
                    "tooltip.lithmod.dims.2x2x3",
                    "tooltip.lithmod.casing.process_cell",
                    "tooltip.lithmod.port.process_cell",
                    new String[] { "tooltip.lithmod.panel.operating_panel_above" }
            )),

            // Boule Purifier: 2x2x3, Support Casing, 1-2 Support Power Ports, optional Support Water Inlet
            Map.entry("boule_purifier", new ControllerMeta(
                    "tooltip.lithmod.frame.support",
                    "tooltip.lithmod.dims.2x2x3",
                    "tooltip.lithmod.casing.support",
                    "tooltip.lithmod.port.support",
                    new String[] { "tooltip.lithmod.optional.support_water_inlet" }
            )),

            // E-beam Mask Writer: 2x2x3, Support Casing, 1-2 Support Power Ports
            Map.entry("ebeam_mask_writer", new ControllerMeta(
                    "tooltip.lithmod.frame.support",
                    "tooltip.lithmod.dims.2x2x3",
                    "tooltip.lithmod.casing.support",
                    "tooltip.lithmod.port.support",
                    new String[] {}
            )),

            // Deposition / Etch: 3x3x5, Advanced Fab Casing, 1-2 Advanced Power Ports, Operating Panel above
            Map.entry("deposition_etcher", new ControllerMeta(
                    "tooltip.lithmod.frame.advanced",
                    "tooltip.lithmod.dims.3x3x5",
                    "tooltip.lithmod.casing.advanced_fab",
                    "tooltip.lithmod.port.advanced",
                    new String[] { "tooltip.lithmod.panel.operating_panel_above" }
            )),

            // Advanced Test & Package: 2x2x3, Advanced Fab Casing, 1-2 Advanced Power Ports, Operating Panel above
            Map.entry("high_bin_test_package",new ControllerMeta("tooltip.lithmod.frame.advanced","tooltip.lithmod.dims.2x2x3","tooltip.lithmod.casing.advanced_fab","tooltip.lithmod.port.advanced",new String[]{"tooltip.lithmod.panel.operating_panel_above","tooltip.lithmod.high_bin.program"})),
            Map.entry("advanced_test_package", new ControllerMeta(
                    "tooltip.lithmod.frame.advanced",
                    "tooltip.lithmod.dims.2x2x3",
                    "tooltip.lithmod.casing.advanced_fab",
                    "tooltip.lithmod.port.advanced",
                    new String[] { "tooltip.lithmod.panel.operating_panel_above" }
            )),

            // Lithography Scanner: 3x3x5, Advanced Fab Casing, 1-2 Advanced Power Ports, Scanner Operating Panel above,
            // 5 modules + Calibration Panel in middle layer, optional Diagnostic Panel + Water Inlet for T5 immersion
            Map.entry("scanner_controller", new ControllerMeta(
                    "tooltip.lithmod.frame.advanced",
                    "tooltip.lithmod.dims.3x3x5",
                    "tooltip.lithmod.casing.advanced_fab",
                    "tooltip.lithmod.port.advanced",
                    new String[] {
                            "tooltip.lithmod.panel.scanner_operating_panel_above",
                            "tooltip.lithmod.modules.exposure",
                            "tooltip.lithmod.modules.optics",
                            "tooltip.lithmod.modules.stage",
                            "tooltip.lithmod.modules.filtration",
                            "tooltip.lithmod.modules.metrology",
                            "tooltip.lithmod.panel.calibration_panel",
                            "tooltip.lithmod.t5.immersion",
                            "tooltip.lithmod.t5.diagnostic",
                            "tooltip.lithmod.t5.water",
                            "tooltip.lithmod.optional.multipatterning"
                    }
            )),

            // Utility Plant: 2x2x3, Advanced Fab Casing, exactly 1 Utility Water Inlet + 1 Utility Pure Outlet, 1-2 power ports
            Map.entry("utility_plant", new ControllerMeta(
                    "tooltip.lithmod.frame.advanced",
                    "tooltip.lithmod.dims.2x2x3",
                    "tooltip.lithmod.casing.advanced_fab",
                    "tooltip.lithmod.port.advanced",
                    new String[] {
                            "tooltip.lithmod.utility.water_inlet_one",
                            "tooltip.lithmod.utility.pure_outlet_one"
                    }
            )),

            // Pick and Place: 2x2x2, Assembly Casing, 1-2 Assembly Power Ports
            Map.entry("pick_and_place", new ControllerMeta(
                    "tooltip.lithmod.frame.assembly",
                    "tooltip.lithmod.dims.2x2x2",
                    "tooltip.lithmod.casing.assembly",
                    "tooltip.lithmod.port.assembly",
                    new String[] {}
            )),

            // Cleanroom Controller: Hollow sealed cuboid shell, controller, powered filters
            Map.entry("cleanroom_controller", new ControllerMeta(
                    "tooltip.lithmod.frame.cleanroom",
                    "tooltip.lithmod.dims.cuboid_shell",
                    "tooltip.lithmod.casing.cleanroom_shell",
                    "tooltip.lithmod.port.cleanroom_powered_filters",
                    new String[] { "tooltip.lithmod.cleanroom.no_fixed_power_port" }
            ))
    );

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.HEAD,
                (stack, context, display, player, flag, builder) -> {
                    String itemId = getItemRegistryName(stack);
                    if (itemId == null) return;

                    ControllerMeta meta = CONTROLLER_META.get(itemId);
                    if (meta == null) return;

                    // Creative search also builds tooltips off-thread; never poll native input there.
                    var minecraft = Minecraft.getInstance();
                    boolean shift = minecraft.isSameThread() && minecraft.hasShiftDown();

                    // Always show frame category line
                    builder.accept(Component.translatable(meta.frameCategoryKey)
                            .withStyle(ChatFormatting.GOLD));

                    if (shift) {
                        // Expanded: individual requirement lines
                        builder.accept(Component.translatable(meta.dimensionsKey)
                                .withStyle(ChatFormatting.GRAY));
                        builder.accept(Component.translatable(meta.casingKey)
                                .withStyle(ChatFormatting.GRAY));
                        builder.accept(Component.translatable(meta.powerPortKey)
                                .withStyle(ChatFormatting.GRAY));

                        for (String extraKey : meta.extraKeys) {
                            builder.accept(Component.translatable(extraKey)
                                    .withStyle(ChatFormatting.GRAY));
                        }
                    } else {
                        // Collapsed: hint to hold Shift
                        builder.accept(Component.translatable("tooltip.lithmod.hold_shift_for_details")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                });
    }

    /**
     * Returns the registry name (path only, e.g. "process_cell_controller") of the item in the stack,
     * or null if not a registered block item from our mod.
     */
    private static String getItemRegistryName(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var item = stack.getItem();
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;
        if (!EUVCRAFTMod.MODID.equals(key.getNamespace())) return null;
        return key.getPath();
    }
}
