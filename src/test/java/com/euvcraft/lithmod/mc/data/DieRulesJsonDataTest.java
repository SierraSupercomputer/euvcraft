package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRule;
import com.euvcraft.lithmod.core.DieRules;
import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DieRulesJsonDataTest {

    @Test
    void bundledJsonMatchesReferenceTable() throws Exception {
        String path = "/data/lithmod/die_rules/rules.json";
        List<DieRule> parsed;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing bundled die rule file: " + path);
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
            parsed = DieRuleCodec.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }

        DieRules rules = DieRules.build(tierRegistry(), parsed, "bundled JSON");
        assertEquals(4, rules.rules().size());

        assertEquals(DieTypes.LOGIC, rules.ruleFor(DieTypes.LOGIC).dieType());
        assertEquals(1, rules.firstTier(DieTypes.LOGIC));
        assertEquals(5, rules.capTier(DieTypes.LOGIC));

        assertEquals(2, rules.firstTier(DieTypes.MEMORY));
        assertNull(rules.capTier(DieTypes.MEMORY));

        assertEquals(3, rules.firstTier(DieTypes.COMPUTE));
        assertTrue(rules.usesCoreCount(DieTypes.COMPUTE));

        assertEquals(3, rules.firstTier(DieTypes.NAND));
        assertFalse(rules.usesCoreCount(DieTypes.NAND));
    }

    @Test
    void loaderFallbackMatchesBundledJson() {
        DieRules fallback = new DieRulesDataLoader().fallback();
        assertNotNull(fallback);
        assertEquals(4, fallback.rules().size());
        assertEquals(List.of("logic", "memory", "compute", "nand"),
            fallback.rules().stream().map(DieRule::dieType).toList());
    }

    private TierRegistry tierRegistry() {
        List<Tier> tiers = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            tiers.add(new Tier("T" + i, i, "Tier" + i, "src", 100, 10, 10, i, 100, 90.0, 100, (i >= 6 ? 4 : null), i == 6));
        }
        return TierRegistry.build(tiers, "test");
    }
}