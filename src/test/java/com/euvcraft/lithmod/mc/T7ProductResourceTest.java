package com.euvcraft.lithmod.mc;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T7ProductResourceTest {
    static final Set<String> ITEMS=Set.of("compute_chip_t7","memory_chip_t7","nand_chip_t7","cpu_t7","dimm_t7","resist_t7");

    @Test void everyNewProductHasNativeItemAndModelDefinitions() throws Exception {
        for (String id : ITEMS) {
            for (String path : new String[]{"/assets/lithmod/items/"+id+".json","/assets/lithmod/models/item/"+id+".json"}) {
                try (var stream=getClass().getResourceAsStream(path)) { assertNotNull(stream,path); }
            }
        }
    }
}
