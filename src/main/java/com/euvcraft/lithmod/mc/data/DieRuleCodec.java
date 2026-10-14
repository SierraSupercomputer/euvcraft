package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Codec for DieRule records, used for JSON data loading.
 */
public final class DieRuleCodec {
    private DieRuleCodec() {}

    public static final Codec<DieRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("die_type").forGetter(DieRule::dieType),
        Codec.INT.fieldOf("first_tier").forGetter(DieRule::firstTier),
        Codec.INT.optionalFieldOf("cap_tier").forGetter(r -> Optional.ofNullable(r.capTier())),
        Codec.BOOL.optionalFieldOf("uses_core_count", false).forGetter(DieRule::usesCoreCount)
    ).apply(instance, (dieType, firstTier, capTier, usesCoreCount) ->
        new DieRule(dieType, firstTier, capTier.orElse(null), usesCoreCount)
    ));

    public static final Codec<java.util.List<DieRule>> LIST_CODEC = CODEC.listOf();
}
