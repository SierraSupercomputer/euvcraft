package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRule;
import com.euvcraft.lithmod.core.DieRules;
import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DieRuleJsonDataTest {
    @Test
    void bundledJsonLoads() {
        DieRulesDataLoader loader = new DieRulesDataLoader();
        DieRules rules = loader.fallback();
        assertNotNull(rules);
        assertEquals(4, rules.rules().size());
        TierRegistry tiers = tierRegistry();
        assertEquals(1, rules.ruleFor("logic").firstTier());
        assertEquals(5, rules.ruleFor("logic").capTier());
        System.out.println(rules.availabilityTable());
    }

    private TierRegistry tierRegistry() {
        java.util.List<com.euvcraft.lithmod.core.Tier> tiers = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            tiers.add(new com.euvcraft.lithmod.core.Tier("T" + i, i, "Tier" + i, "src", 100, 10, 10, i, 90.0, 100, (i >= 6 ? 4 : null), i == 6));
        }
        return TierRegistry.build(tiers, "test");
    }
}
