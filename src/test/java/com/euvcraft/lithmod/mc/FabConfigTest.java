package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.FabSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FabConfigTest {

    @Test
    void specBuildsAndDefaultsEqualCoreDefaults() {
        FabConfig config = new FabConfig();

        assertNotNull(FabConfig.getSpec());
        assertEquals(FabSettings.defaults(), config.getSettings());
    }
}