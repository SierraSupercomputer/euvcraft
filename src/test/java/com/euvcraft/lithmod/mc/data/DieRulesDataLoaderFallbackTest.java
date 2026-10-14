package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DieRulesDataLoaderFallbackTest {
    @Test
    void bundledFallbackIsNeverNull() {
        DieRulesDataLoader loader = new DieRulesDataLoader();
        DieRules rules = loader.fallback();
        assertNotNull(rules);
    }
}
