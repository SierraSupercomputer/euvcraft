package com.euvcraft.lithmod.mc.viewer;

import java.util.*;

/** Machine definition id -> real crafting station; panels and casings are never stations. */
public final class ViewerMachines {
    public static final Map<String,String> CONTROLLERS;
    static {
        var map=new LinkedHashMap<String,String>();
        for(var id:List.of("crystal_puller","wafer_slicer","pattern_generator","test_package","foup_cleaner","boule_purifier","ebeam_mask_writer","deposition_etcher","etch_cell","lithography_scanner","advanced_test_package","high_bin_test_package","utility_plant","pick_and_place","zone_refiner","reflow_oven","hydrogen_purifier","central_vacuum_plant","g3_deposition_etcher","g3_etch_cell","g3_reticle_writer","coating_track","wet_cleaner"))map.put(id,"lithmod:"+id);
        map.put("process_cell","lithmod:process_cell_controller");
        map.put("scrap_recycler","lithmod:scrap_recycler");
        map.put("lithography_scanner","lithmod:scanner_controller");
        map.put("euv_scanner","lithmod:euv_scanner_controller");
        map.put("high_na_scanner","lithmod:high_na_scanner_controller");
        map.put("t8_planarizer","lithmod:t8_planarizer");
        map.put("t8_implanter","lithmod:t8_implanter");
        map.put("euv_light_source","lithmod:euv_light_source_controller");
        CONTROLLERS=Collections.unmodifiableMap(map);
    }
    private ViewerMachines(){}
}
