package com.euvcraft.lithmod.mc;

import com.euvcraft.lithmod.core.FabSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabConfigTest {

    @Test
    void specBuildsAndDefaultsEqualCoreDefaults() {
        FabConfig config = new FabConfig();

        assertNotNull(FabConfig.getSpec());
        assertEquals(FabSettings.defaults(), config.getSettings());
    }

    @Test
    void debugCommandsAreOffByDefault() {
        assertFalse(FabSettings.DEFAULT_DEBUG_COMMANDS,
            "the debug command tree must be opt-in");
        assertFalse(FabSettings.defaults().debugCommands());
        assertFalse(FabSettings.builder().build().debugCommands());
        assertTrue(FabSettings.builder().debugCommands(true).build().debugCommands());
    }

    @Test
    void debugCommandsSurviveToBuilder() {
        FabSettings settings = FabSettings.builder().debugCommands(true).build();
        assertTrue(settings.toBuilder().build().debugCommands());
    }
}