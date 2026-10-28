package com.euvcraft.lithmod.mc;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T7ProductResourceTest {
    static final Set<String> BLOCKS=Set.of("g3_reticle_writer","g3_deposition_etcher","g3_casing","g3_power_port","g3_operating_panel","g3_water_inlet","g3_writing_module","g3_metrology_module","g3_deposition_module","g3_rinse_module","g3_vacuum_module","euv_scanner_controller","euv_scanner_operating_panel","euv_scanner_calibration_panel","euv_scanner_exposure_module","euv_scanner_optics_module","euv_scanner_stage_module","euv_scanner_filtration_module","euv_scanner_metrology_module","euv_scanner_vacuum_module","euv_scanner_diagnostic_panel","euv_scanner_water_inlet","euv_scanner_power_port","euv_scanner_casing","euv_light_source_controller","euv_light_source_operating_panel","euv_light_source_tin_inlet","euv_light_source_hydrogen_inlet","euv_light_source_vacuum_module","euv_light_source_power_port","euv_light_source_casing","euv_beam_line","hydrogen_purifier","hydrogen_casing","hydrogen_power_port","hydrogen_gas_inlet","hydrogen_gas_outlet","hydrogen_operating_panel","central_vacuum_plant","vacuum_casing","vacuum_power_port","vacuum_operating_panel","vacuum_service_conduit","cleanroom_vacuum_port","vacuum_service_outlet");
    static final Set<String> MODELS=java.util.stream.Stream.concat(BLOCKS.stream(),BLOCKS.stream().filter(id->!id.equals("euv_beam_line")).map(id->id+"_formed")).collect(java.util.stream.Collectors.toUnmodifiableSet());
    static final Set<String> ITEMS=Set.of("compute_chip_t7","memory_chip_t7","nand_chip_t7","cpu_t7","dimm_t7","resist_t7","euv_light_source","euv_optics","euv_stage","euv_filtration","euv_metrology");

    @Test void everyNewProductHasNativeItemAndModelDefinitions() throws Exception {
        for (String id : ITEMS) {
            for (String path : new String[]{"/assets/lithmod/items/"+id+".json","/assets/lithmod/models/item/"+id+".json"}) {
                try (var stream=getClass().getResourceAsStream(path)) { assertNotNull(stream,path); }
            }
        }
    }
}
